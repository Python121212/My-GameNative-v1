#!/usr/bin/env bash
# Linux のソフトウェア描画による予備比較。実機の性能とは異なる。
set -euo pipefail
[[ $# == 2 && ! -e $1 && -f $2 ]] || { echo '使い方: run-linux-software.sh <未使用出力ディレクトリ> <DXVK tar.gz>' >&2; exit 2; }
out=$1; package=$(realpath "$2"); mkdir -p "$out"; out=$(realpath "$out")
export LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe WINEDEBUG=-all
shopt -s nullglob
icds=(/usr/share/vulkan/icd.d/lvp_icd*x86_64*.json /usr/share/vulkan/icd.d/lvp_icd.json)
(( ${#icds[@]} == 1 )) || { echo "lavapipe ICD を一つに特定できません: ${icds[*]}" >&2; ls /usr/share/vulkan/icd.d >&2; exit 1; }
export VK_ICD_FILENAMES="${icds[0]}"
mkdir "$out/build" "$out/wined3d" "$out/dxvk" "$out/dxvk-package"
tar -xzf "$package" -C "$out/dxvk-package"
dxvk="$out/dxvk-package/dxvk-2.6.1/x64"
for dll in d3d9 d3d11 dxgi; do test -f "$dxvk/$dll.dll"; cp "$dxvk/$dll.dll" "$out/dxvk/"; done
x86_64-w64-mingw32-g++ -O2 -static -o "$out/build/gn-d3d.exe" tools/graphics-benchmark/d3d-smoke.cpp -ld3d9 -ld3d11 -ld3dcompiler -ldxgi -lole32 -luuid
cp "$out/build/gn-d3d.exe" "$out/wined3d/"; cp "$out/build/gn-d3d.exe" "$out/dxvk/"
{
    uname -a
    wine --version
    x86_64-w64-mingw32-g++ --version | head -1
    sha256sum "$package" "$out/build/gn-d3d.exe"
    echo "VK_ICD_FILENAMES=$VK_ICD_FILENAMES; LIBGL_ALWAYS_SOFTWARE=$LIBGL_ALWAYS_SOFTWARE; GALLIUM_DRIVER=$GALLIUM_DRIVER"
    vulkaninfo --summary 2>&1 || true
    glxinfo -B 2>&1 || true
} > "$out/environment.txt"
# Wine prefix と DLL を分けて、既存の Wine 設定やゲームデータには触れない。
for backend in wined3d dxvk; do
    export WINEPREFIX="$out/prefix-$backend" WINEARCH=win64
    WINEDLLOVERRIDES='winemenubuilder.exe=d;mscoree,mshtml=' timeout 120 wineboot -u > "$out/$backend-wineboot.txt" 2>&1
    timeout 30 wineserver -w
    if [[ $backend == dxvk ]]; then
        export WINEDLLOVERRIDES='d3d9,d3d11,dxgi=n;winemenubuilder.exe=d;mscoree,mshtml='
    else
        export WINEDLLOVERRIDES='d3d9,d3d11,dxgi=b;winemenubuilder.exe=d;mscoree,mshtml='
    fi
    # ロードされた DLL と初回動作をログで確認する。計測からは除外。
    (cd "$out/$backend"; WINEDEBUG=+loaddll timeout 120 wine ./gn-d3d.exe d3d9 baseline) > "$out/$backend-load.txt" 2>&1
    grep -iE 'd3d9\.dll.*(native|builtin)' "$out/$backend-load.txt" | tail -3 || { echo "$backend の DLL ロード情報を確認できません" >&2; exit 1; }
    if [[ $backend == dxvk ]]; then kind=native; else kind=builtin; fi
    grep -iE "d3d9\\.dll.*$kind" "$out/$backend-load.txt" >/dev/null || { echo "$backend の d3d9 DLL が期待した種類ではありません" >&2; exit 1; }
    (cd "$out/$backend"; WINEDEBUG=+loaddll timeout 120 wine ./gn-d3d.exe d3d11 baseline) > "$out/$backend-load11.txt" 2>&1 || { tail -n 30 "$out/$backend-load11.txt"; exit 1; }
    for dll in d3d11 dxgi; do
        grep -iE "$dll\.dll.*$kind" "$out/$backend-load11.txt" | tail -1 || { echo "$backend の $dll DLL が期待した種類ではありません" >&2; exit 1; }
    done
    timeout 30 wineserver -w
 done
printf 'api,workload,round,backend,seconds,fps,peak_rss_kib\n' > "$out/results.csv"
for api in d3d9 d3d11; do
    for workload in baseline calls resolution texture; do
        for round in 1 2 3 4; do
            # 同じ順番が続く偏りを抑える。各方式で各4回。
            if (( round % 2 )); then order=(dxvk wined3d); else order=(wined3d dxvk); fi
            for backend in "${order[@]}"; do
                export WINEPREFIX="$out/prefix-$backend"
                if [[ $backend == dxvk ]]; then
                    export WINEDLLOVERRIDES='d3d9,d3d11,dxgi=n;winemenubuilder.exe=d;mscoree,mshtml='
                else
                    export WINEDLLOVERRIDES='d3d9,d3d11,dxgi=b;winemenubuilder.exe=d;mscoree,mshtml='
                fi
                log="$out/$api-$workload-$round-$backend.txt"
                rss="$out/$api-$workload-$round-$backend-rss.txt"
                (cd "$out/$backend"; /usr/bin/time -f '%M' -o "$rss" timeout 120 wine ./gn-d3d.exe "$api" "$workload") > "$log" 2>&1 || { tail -n 40 "$log"; echo "$api $workload $backend $round 失敗" >&2; exit 1; }
                result=$(grep -E '^RESULT api=' "$log" | tr -d '\r')
                [[ $result == "RESULT api=$api frames=180 "*'pixel=ok'* ]] || { echo '出力またはピクセル確認が不正です' >&2; exit 1; }
                sec=$(sed -n 's/.* seconds=\([0-9.]*\).*/\1/p' <<< "$result")
                fps=$(sed -n 's/.* fps=\([0-9.]*\).*/\1/p' <<< "$result")
                peak=$(cat "$rss")
                [[ -n $sec && -n $fps && $peak =~ ^[0-9]+$ ]] || exit 1
                printf '%s,%s,%s,%s,%s,%s,%s\n' "$api" "$workload" "$round" "$backend" "$sec" "$fps" "$peak" >> "$out/results.csv"
                timeout 30 wineserver -w
            done
        done
    done
done
python3 - "$out/results.csv" <<'PY2' | tee "$out/summary.txt"
import csv,statistics,sys
r=list(csv.DictReader(open(sys.argv[1])))
for api in ('d3d9','d3d11'):
 for workload in ('baseline','calls','resolution','texture'):
  for backend in ('dxvk','wined3d'):
   subset=[x for x in r if x['api']==api and x['workload']==workload and x['backend']==backend]
   if len(subset)!=4: raise SystemExit('4回の測定が揃っていません')
   fps=[float(x['fps']) for x in subset]; rss=[int(x['peak_rss_kib']) for x in subset]
   print(f"{api} {workload} {backend}: FPS {fps} 平均 {statistics.mean(fps):.3f}; 最大RSS KiB {rss} 平均 {statistics.mean(rss):.0f}")
print('x86_64 Linux ソフトウェア描画の合成試験。最大RSSはGPU共有メモリや全プロセス合計ではない。')
PY2
