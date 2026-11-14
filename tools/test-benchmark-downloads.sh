#!/usr/bin/env bash
# 一時領域で公式ファイルの取得・ハッシュ・PE 形式を確認。EXE は実行しない。
set -euo pipefail
cd "$(dirname "$0")/.."
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
urls=(
  'https://github.com/ip7z/7zip/releases/download/26.06/7z2606-x64.exe'
  'https://github.com/The412Banner/AIO-Graphics-Test/releases/download/2.2.0/AIO-Graphics-Test-64bit.exe'
)
hashes=(
  '9b697f65ecc2d9b0152f5ee96d72dde522a7827690349d9307935dacf40f42ec'
  '5b5163850383c9cf3c011574be4fdc6000fd3c0961b9ed78f66bc09c1a06746d'
)
for i in 0 1; do
  grep -Fq "${urls[$i]}" app/src/main/java/app/gamenative/utils/BenchmarkDownloads.kt
  curl --fail --silent --show-error --location --retry 3 --max-time 120 --output "$tmp/$i.exe" "${urls[$i]}"
  echo "${hashes[$i]}  $tmp/$i.exe" | sha256sum -c -
  python3 - "$tmp/$i.exe" <<'PY'
import struct, sys
with open(sys.argv[1], 'rb') as f:
    assert f.read(2) == b'MZ'
    f.seek(0x3c)
    pe = struct.unpack('<I', f.read(4))[0]
    f.seek(pe)
    assert f.read(4) == b'PE\0\0'
    assert struct.unpack('<H', f.read(2))[0] == 0x8664, 'x64 ではありません'
PY
done
echo '公式ファイルの取得・ハッシュ・Windows x64 形式を確認（実行は未検証）。'
