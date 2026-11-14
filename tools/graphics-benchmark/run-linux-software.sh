#!/usr/bin/env bash
# Linux のソフトウェア描画による予備比較。実機の性能とは異なる。
set -euo pipefail
[[ $# == 2 && ! -e $1 && -f $2 ]] || { echo '使い方: run-linux-software.sh <未使用出力ディレクトリ> <DXVK tar.gz>' >&2; exit 2; }
out=$1; package=$(realpath "$2"); mkdir -p "$out"; out=$(realpath "$out")
export LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe WINEDEBUG=-all
export VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/lvp_icd.x86_64.json
[[ -f $VK_ICD_FILENAMES ]] || { echo 'lavapipe ICD がありません' >&2; exit 1; }
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
    (cd "$out/$backend"; WINEDEBUG=+loaddll timeout 120 wine ./gn-d3d.exe d3d9) > "$out/$backend-load.txt" 2>&1
    grep -iE 'd3d9\.dll.*(native|builtin)' "$out/$backend-load.txt" | tail -3 || { echo "$backend の DLL ロード情報を確認できません" >&2; exit 1; }
    if [[ $backend == dxvk ]]; then kind=native; else kind=builtin; fi
    grep -iE "d3d9\\.dll.*$kind" "$out/$backend-load.txt" >/dev/null || { echo "$backend の d3d9 DLL が期待した種類ではありません" >&2; exit 1; }
    timeout 30 wineserver -w
 done
printf 'api,round,backend,seconds,fps\n' > "$out/results.csv"
for api in d3d9 d3d11; do
    for round in 1 2 3 4; do
        for backend in dxvk wined3d; do
            export WINEPREFIX="$out/prefix-$backend"
            if [[ $backend == dxvk ]]; then
                export WINEDLLOVERRIDES='d3d9,d3d11,dxgi=n;winemenubuilder.exe=d;mscoree,mshtml='
            else
                export WINEDLLOVERRIDES='d3d9,d3d11,dxgi=b;winemenubuilder.exe=d;mscoree,mshtml='
            fi
            log="$out/$api-$round-$backend.txt"
            (cd "$out/$backend"; timeout 120 wine ./gn-d3d.exe "$api") > "$log" 2>&1 || { cat "$log"; echo "$api $backend $round 失敗" >&2; exit 1; }
            cat "$log"
            result=$(grep -E '^RESULT api=' "$log" | tr -d '\r')
            [[ $result == "RESULT api=$api frames=180 "*'pixel=ok'* ]] || { echo '出力またはピクセル確認が不正です' >&2; exit 1; }
            sec=$(sed -n 's/.* seconds=\([0-9.]*\).*/\1/p' <<< "$result")
            fps=$(sed -n 's/.* fps=\([0-9.]*\).*/\1/p' <<< "$result")
            [[ -n $sec && -n $fps ]] || exit 1
            printf '%s,%s,%s,%s,%s\n' "$api" "$round" "$backend" "$sec" "$fps" >> "$out/results.csv"
            timeout 30 wineserver -w
        done
    done
done
python3 - "$out/results.csv" <<'PY' | tee "$out/summary.txt"
import csv,statistics,sys
r=list(csv.DictReader(open(sys.argv[1])))
for api in ('d3d9','d3d11'):
 for backend in ('dxvk','wined3d'):
  a=[float(x['fps']) for x in r if x['api']==api and x['backend']==backend]
  if len(a)!=4: raise SystemExit('4回の測定が揃っていません')
  print(f'{api} {backend}: 各回 FPS {a}, 平均 {statistics.mean(a):.3f}, 範囲 {min(a):.3f}–{max(a):.3f}')
print('x86_64 Linux ソフトウェア描画の合成試験。Android 実機・ゲーム性能ではない。')
PY
