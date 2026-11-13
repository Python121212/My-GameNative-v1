#!/usr/bin/env bash
# 公式の 7-Zip Extra に含まれる Windows x86-64 版を専用ディレクトリに準備する。
set -euo pipefail
if [[ $# -ne 1 || -e "$1" ]]; then
    echo "使用方法: $0 <まだ存在しない出力ディレクトリ>" >&2
    exit 2
fi
out="$1"
mkdir -p "$out"
archive="$out/7z2606-extra.7z"
curl --silent --show-error --fail --location --retry 2 --connect-timeout 30 --max-time 300 --max-filesize 134217728 \
    'https://github.com/ip7z/7zip/releases/download/26.06/7z2606-extra.7z' -o "$archive"
printf '%s  %s\n' '396c180f12d2247c24b0b1fcd482c6fc2a2700f61211ffede1061dd971f5e104' "$archive" | sha256sum -c -
sha256sum "$archive" > "$out/archive-sha256.txt"
# 必要な公式バイナリだけを取り出す。既存ディレクトリは上書きしない。
7z e -y "$archive" 'x64/7za.exe' 'x64/7za.dll' "-o$out" > "$out/extract-log.txt"
test -s "$out/7za.exe"
file "$out/7za.exe" | tee "$out/executable-type.txt"
grep -q 'PE32+ executable.*x86-64' "$out/executable-type.txt" || { echo 'Windows x86-64 実行ファイルではありません。' >&2; exit 1; }
(cd "$out" && sha256sum 7za.exe > executable-sha256.txt)
echo 'Windows x86-64 用の 7-Zip ベンチマークを用意しました。'
