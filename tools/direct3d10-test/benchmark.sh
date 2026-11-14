#!/usr/bin/env bash
set -euo pipefail
[[ $# == 2 && ! -e $1 && -f $2 ]] || { echo '使い方: benchmark.sh <未使用出力先> <DXVK tar.gz>' >&2; exit 2; }
out=$1; package=$(realpath "$2"); mkdir -p "$out"; out=$(realpath "$out")
export LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe WINEDEBUG=-all WINEARCH=win64
shopt -s nullglob
icds=(/usr/share/vulkan/icd.d/lvp_icd*x86_64*.json /usr/share/vulkan/icd.d/lvp_icd.json)
(( ${#icds[@]} == 1 )) || { echo 'lavapipe ICD が一意ではありません' >&2; exit 1; }
export VK_ICD_FILENAMES="${icds[0]}"
mkdir "$out/dxvk" "$out/wined3d" "$out/package"; tar -xzf "$package" -C "$out/package"
for dll in d3d10core d3d11 dxgi; do cp "$out/package/dxvk-2.6.1/x64/$dll.dll" "$out/dxvk/"; done
x86_64-w64-mingw32-g++ -std=c++17 -Wall -Wextra -O2 -static -o "$out/dxvk/test.exe" tools/direct3d10-test/benchmark.cpp -ld3d10 -ld3dcompiler -ldxgi -lole32 -luuid
cp "$out/dxvk/test.exe" "$out/wined3d/test.exe"
{ uname -a; wine --version; x86_64-w64-mingw32-g++ --version | head -1; sha256sum "$package" "$out/dxvk/test.exe" "$out/dxvk/"*.dll; vulkaninfo --summary 2>&1 || true; glxinfo -B 2>&1 || true; } > "$out/environment.txt"
run_one() {
    local backend=$1 label=$2 debug=$3
    export WINEPREFIX="$out/prefix-$backend"
    if [[ $backend == dxvk ]]; then
        export WINEDLLOVERRIDES='d3d10core,d3d11,dxgi=n;winemenubuilder.exe=d;mscoree,mshtml='
    else
        export WINEDLLOVERRIDES='d3d10core,d3d11,dxgi=b;winemenubuilder.exe=d;mscoree,mshtml='
    fi
    local log="$out/$backend-$label.txt"
    if ! (cd "$out/$backend"; WINEDEBUG="$debug" timeout 120 wine ./test.exe) > "$log" 2>&1; then tail -80 "$log" >&2; echo "$backend $label 失敗" >&2; return 1; fi
    grep -E '^RESULT d3d10 frames=300 seconds=[0-9.]+ fps=[0-9.]+ pixels=ok present=ok query=ok' "$log" >/dev/null || { echo '結果が不完全です' >&2; return 1; }
    timeout 30 wineserver -w
    echo "$backend $label $(grep '^RESULT' "$log" | tr -d '\r')"
}
for backend in dxvk wined3d; do
    export WINEPREFIX="$out/prefix-$backend"
    WINEDLLOVERRIDES='winemenubuilder.exe=d;mscoree,mshtml=' timeout 120 wineboot -u > "$out/$backend-wineboot.txt" 2>&1
    timeout 30 wineserver -w
    run_one "$backend" warmup '+loaddll'
    if [[ $backend == dxvk ]]; then kind=native; else kind=builtin; fi
    for dll in d3d10core d3d11 dxgi; do
        grep -iE "$dll\\.dll.*$kind" "$out/$backend-warmup.txt" | tail -1 || { echo "$backend の $dll ロード未確認" >&2; exit 1; }
    done
done
for n in 1 2 3 4; do
    if (( n % 2 )); then order=(dxvk wined3d); else order=(wined3d dxvk); fi
    for backend in "${order[@]}"; do run_one "$backend" "$n" '-all'; done
done
python3 - "$out" <<'PY' | tee "$out/summary.txt"
import re, statistics, sys
from pathlib import Path
p = Path(sys.argv[1]); values = {}
for backend in ('dxvk', 'wined3d'):
    values[backend] = []
    for n in range(1, 5):
        text = (p / f'{backend}-{n}.txt').read_text(errors='replace')
        m = re.search(r'^RESULT d3d10 frames=300 seconds=([\d.]+) fps=([\d.]+) pixels=ok present=ok query=ok', text, re.M)
        if not m or float(m[1]) <= 0 or float(m[2]) <= 0:
            raise SystemExit(f'{backend} {n}: 計測結果なし')
        values[backend].append(float(m[2]))
    print(f'{backend}: FPS={values[backend]} 平均={statistics.mean(values[backend]):.3f}')
print('合成負荷・同一 runner・ソフトウェア描画。FPS はゲームや Android 実機の性能ではない。')
PY
