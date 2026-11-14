#!/usr/bin/env bash
set -Eeuo pipefail
root=$(realpath "$1")
exe=$(cat "$root/exe.txt")
game=$(dirname "$exe")
exec > >(tee "$root/summary.txt") 2>&1
printf 'Tropics probe: %s\n' "$exe"
sha256sum "$root/tropics.msi" "$root/dxvk.tar.gz" "$exe"
file "$exe"
tar xzf "$root/dxvk.tar.gz" -C "$root"
cp "$root"/dxvk-2.6.1/x32/{d3d10core,d3d11,dxgi}.dll "$game/"
export DISPLAY=:99 WINEARCH=win32 WINEPREFIX="$root/prefix" WINEDEBUG=-all LIBGL_ALWAYS_SOFTWARE=1
sudo Xorg :99 -noreset -nolisten tcp -config "$GITHUB_WORKSPACE/tools/graphics-benchmark/xorg-dummy.conf" -logfile "$root/xorg.log" >"$root/xorg-output.log" 2>&1 &
xpid=$!
cleanup() { wineserver -k || true; sudo kill "$xpid" 2>/dev/null || true; }
trap cleanup EXIT
for i in {1..30}; do xdpyinfo >/dev/null 2>&1 && break; sleep 1; done
xdpyinfo >/dev/null
wineboot -u >"$root/wineboot.log" 2>&1
cd "$game"
for mode in dxvk wined3d; do
  wineserver -k || true
  if [[ $mode == dxvk ]]; then
    export WINEDLLOVERRIDES='d3d10,d3d10_1=b;d3d10core,d3d11,dxgi=n;d3dcompiler_43=b'
  else
    export WINEDLLOVERRIDES='d3d10,d3d10_1,d3d10core,d3d11,dxgi,d3dcompiler_43=b'
  fi
  echo "===== $mode: DX10 probe ====="
  export WINEDEBUG=+loaddll
  timeout -k 5s 150s wine "$exe" -video_app direct3d10 -sound_app null -video_fullscreen 0 -video_mode -1 -video_width 640 -video_height 480 -video_vsync 0 -video_multisample 0 -data_path ./ -engine_config data/unigine.cfg -system_script tropics/unigine.cpp -extern_define PHORONIX,RELEASE >"$root/$mode.log" 2>&1 &
  pid=$!
  sleep 40
  if kill -0 "$pid" 2>/dev/null; then
    xwd -root -silent | convert xwd:- "$root/$mode.png" || true
  fi
  set +e
  wait "$pid"
  status=$?
  set -e
  echo "exit=$status"
  echo 'Relevant DLL loads:'
  grep -Ei 'loaddll.*(d3d10|d3d11|dxgi|d3dcompiler)' "$root/$mode.log" | tail -25 || true
  echo 'Tropics messages:'
  grep -aEi 'fps|frames|error|failed|direct3d10|unigine|render|score|exception|assert' "$root/$mode.log" | tail -55 || true
  wineserver -k || true
done
# A probe alone is not a completed benchmark comparison.
echo 'Probe only: no validated four-run FPS comparison.'
