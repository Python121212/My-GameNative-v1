#!/usr/bin/env bash
# 一時 Wine prefix・公式ゲームと SDL の診断用プロキシだけを使用する。
set -euo pipefail
[[ $# == 4 && ! -e $1 && -f $2 && -f $3 && -f $4 ]] || { echo '使い方: run-freedoom.sh <未使用出力先> <Chocolate Doom zip> <Freedoom zip> <DXVK tar.gz>' >&2; exit 2; }
out=$1; chocolate=$(realpath "$2"); freedoom=$(realpath "$3"); dxvk_package=$(realpath "$4"); mkdir -p "$out"; out=$(realpath "$out")
export LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe WINEDEBUG=-all SDL_RENDER_DRIVER=direct3d
shopt -s nullglob
icds=(/usr/share/vulkan/icd.d/lvp_icd*x86_64*.json /usr/share/vulkan/icd.d/lvp_icd.json)
(( ${#icds[@]} == 1 )) || { echo 'lavapipe ICD が見つからない' >&2; exit 1; }
export VK_ICD_FILENAMES="${icds[0]}"
mkdir "$out/dist" "$out/wad" "$out/dxvk-package"
unzip -q "$chocolate" -d "$out/dist"; unzip -q "$freedoom" -d "$out/wad"; tar -xzf "$dxvk_package" -C "$out/dxvk-package"
mapfile -t exes < <(find "$out/dist" -type f -iname chocolate-doom.exe)
mapfile -t wads < <(find "$out/wad" -type f -iname freedoom2.wad)
[[ ${#exes[@]} == 1 && ${#wads[@]} == 1 ]] || { echo 'ゲームまたは WAD を特定できません' >&2; exit 1; }
source_dir=$(dirname "${exes[0]}")
mapfile -t sdls < <(find "$source_dir" -maxdepth 1 -type f -iname SDL2.dll)
[[ ${#sdls[@]} == 1 ]] || { echo 'SDL2.dll を特定できません' >&2; exit 1; }
python3 tools/graphics-benchmark/create-sdl-profile.py "${sdls[0]}" "$out/sdl.def"
x86_64-w64-mingw32-gcc -shared -O2 -o "$out/SDL2.dll" tools/graphics-benchmark/sdl-profile.c "$out/sdl.def"
{
  uname -a; wine --version; sha256sum "$chocolate" "$freedoom" "$dxvk_package" "${exes[0]}" "${wads[0]}" "${sdls[0]}" "$out/SDL2.dll"
  echo 'SDL_RENDER_DRIVER=direct3d (Direct3D 9), LIBGL_ALWAYS_SOFTWARE=1'
  vulkaninfo --summary 2>&1 || true; glxinfo -B 2>&1 || true
} > "$out/environment.txt"
for backend in dxvk wined3d; do
    mkdir "$out/$backend"; cp -a "$source_dir"/. "$out/$backend/"
    cp "${wads[0]}" "$out/$backend/freedoom2.wad"
    mv "$out/$backend/SDL2.dll" "$out/$backend/SDL2_real.dll"; cp "$out/SDL2.dll" "$out/$backend/SDL2.dll"
    if [[ $backend == dxvk ]]; then cp "$out/dxvk-package/dxvk-2.6.1/x64/d3d9.dll" "$out/$backend/"; fi
    export WINEPREFIX="$out/prefix-$backend" WINEARCH=win64
    WINEDLLOVERRIDES='winemenubuilder.exe=d;mscoree,mshtml=' timeout 120 wineboot -u >"$out/$backend-wineboot.txt" 2>&1
    timeout 30 wineserver -w
 done
run_once() {
    local backend=$1 variant=$2 label=$3 mode=$4 status text
    export WINEPREFIX="$out/prefix-$backend"
    unset DXVK_CONFIG DXVK_CONFIG_FILE WINE_D3D_CONFIG
    if [[ $backend == dxvk ]]; then
        export WINEDLLOVERRIDES='d3d9=n;winemenubuilder.exe=d;mscoree,mshtml='
    else
        export WINEDLLOVERRIDES='d3d9=b;winemenubuilder.exe=d;mscoree,mshtml='
    fi
    if [[ $variant == profile ]]; then export GN_SDL_PROFILE=1; else export GN_SDL_PROFILE=0; fi
    rm -f "$out/$backend/frame.raw" "$out/$backend/renderer.txt" "$out/$backend/stdout.txt" "$out/$backend/stderr.txt" "$out/$backend/sdl-profile.csv"
    set +e
    (cd "$out/$backend"; WINEDEBUG="$mode" timeout 180 wine ./chocolate-doom.exe -iwad freedoom2.wad -timedemo demo1 -window -nosound -nogui) >"$out/$backend-$variant-$label.txt" 2>&1
    status=$?
    set -e
    # Chocolate Doom の timedemo は結果を I_Error で通知し、終了コード -1 (255) を返す仕様。
    [[ $status == 255 ]] || { echo "$backend $label: 想定外の終了状態 $status" >&2; tail -40 "$out/$backend-$variant-$label.txt" >&2; return 1; }
    for file in stdout.txt stderr.txt; do [[ ! -f "$out/$backend/$file" ]] || cat "$out/$backend/$file" >>"$out/$backend-$variant-$label.txt"; done
    text=$(grep -Eo 'timed [0-9]+ gametics in [0-9]+ realtics \([0-9.]+ fps\)' "$out/$backend-$variant-$label.txt" | tail -1 || true)
    [[ -n $text ]] || { echo 'デモ完走の結果がありません' >&2; tail -40 "$out/$backend-$variant-$label.txt" >&2; return 1; }
    [[ -s "$out/$backend/frame.raw" && -f "$out/$backend/renderer.txt" ]] || { echo '描画結果またはレンダラーを取得できません' >&2; return 1; }
    [[ $(tr -d '\r\n' < "$out/$backend/renderer.txt") == direct3d ]] || { echo 'SDL が Direct3D 9 以外にフォールバックしました' >&2; cat "$out/$backend/renderer.txt" >&2; return 1; }
    cp "$out/$backend/frame.raw" "$out/$backend-$variant-$label.raw"
    if [[ $variant == profile ]]; then
        [[ -s "$out/$backend/sdl-profile.csv" ]] || { echo 'プロファイル記録がありません' >&2; return 1; }
        cp "$out/$backend/sdl-profile.csv" "$out/$backend-$variant-$label.csv"
    fi
    echo "$backend $variant $label: $text"
    timeout 30 wineserver -w
}
# 各方式・計測ありなしで事前実行し、DLL と画像記録を確認する。
for backend in dxvk wined3d; do
    for variant in control profile; do
        run_once "$backend" "$variant" warmup '+loaddll'
        if [[ $backend == dxvk ]]; then kind=native; else kind=builtin; fi
        log="$out/$backend-$variant-warmup.txt"
        grep -iE "d3d9\\.dll.*$kind" "$log" >/dev/null || { echo "$backend DLL が未確認" >&2; exit 1; }
    done
done
conditions=(dxvk:control wined3d:control dxvk:profile wined3d:profile)
for round in 1 2 3 4; do
    for offset in 0 1 2 3; do
        condition=${conditions[$(((round-1+offset)%4))]}
        run_once "${condition%%:*}" "${condition##*:}" "$round" '-all'
    done
done
python3 tools/graphics-benchmark/summarize-freedoom-profile.py "$out" | tee "$out/summary.txt"
