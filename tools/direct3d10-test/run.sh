#!/usr/bin/env bash
# 隔離された Wine prefix と公式 DXVK を使用。速度ベンチマークではない。
set -euo pipefail
[[ $# == 3 && ! -e $1 && -f $2 && ($3 == dxvk || $3 == wined3d) ]] || { echo '使い方: run.sh <新規出力先> <DXVK tar.gz> <dxvk|wined3d>' >&2; exit 2; }
out=$1; package=$(realpath "$2"); backend=$3; mkdir -p "$out"; out=$(realpath "$out")
export LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe WINEDEBUG=-all WINEARCH=win64 WINEPREFIX="$out/prefix"
shopt -s nullglob
icds=(/usr/share/vulkan/icd.d/lvp_icd*x86_64*.json /usr/share/vulkan/icd.d/lvp_icd.json)
(( ${#icds[@]} == 1 )) || { echo 'lavapipe ICD が一意ではありません' >&2; exit 1; }
export VK_ICD_FILENAMES="${icds[0]}"
mkdir "$out/dist" "$out/package"; tar -xzf "$package" -C "$out/package"
if [[ $backend == dxvk ]]; then
    for dll in d3d10core d3d11 dxgi; do cp "$out/package/dxvk-2.6.1/x64/$dll.dll" "$out/dist/"; done
    export WINEDLLOVERRIDES='d3d10core,d3d11,dxgi=n;winemenubuilder.exe=d;mscoree,mshtml='
else
    export WINEDLLOVERRIDES='d3d10core,d3d11,dxgi=b;winemenubuilder.exe=d;mscoree,mshtml='
fi
x86_64-w64-mingw32-g++ -std=c++17 -Wall -Wextra -O2 -static -o "$out/dist/test.exe" tools/direct3d10-test/smoke.cpp -ld3d10 -ld3dcompiler -ldxgi -lole32 -luuid
{ uname -a; wine --version; x86_64-w64-mingw32-g++ --version | head -1; sha256sum "$package" "$out/dist/test.exe" "$out/dist/"*.dll 2>/dev/null || true; vulkaninfo --summary 2>&1 || true; glxinfo -B 2>&1 || true; } > "$out/environment.txt"
timeout 120 wineboot -u > "$out/wineboot.txt" 2>&1
timeout 30 wineserver -w
for n in 1 2 3 4; do
    if [[ $n == 1 ]]; then debug=+loaddll; else debug=-all; fi
    if ! (cd "$out/dist"; WINEDEBUG="$debug" timeout 120 wine ./test.exe) > "$out/run-$n.txt" 2>&1; then
        tail -80 "$out/run-$n.txt" >&2; echo "$backend 第$n回の描画検証失敗" >&2; exit 1
    fi
    grep -F 'RESULT d3d10 device=ok shader=ok draw=ok pixels=ok present_calls=8' "$out/run-$n.txt" >/dev/null || { echo '結果が不完全です' >&2; exit 1; }
    timeout 30 wineserver -w
    echo "$backend 第${n}回: デバイス・シェーダー・描画・画素・8回のPresent が成功"
done
if [[ $backend == dxvk ]]; then kind=native; else kind=builtin; fi
for dll in d3d10core d3d11 dxgi; do
    grep -iE "$dll\\.dll.*$kind" "$out/run-1.txt" | tail -1 || { echo "$backend の $dll ($kind) のロードが確認できません" >&2; exit 1; }
done
echo '完了。x86-64 Linux Wine のソフトウェア描画であり Android 実機ではありません。'
