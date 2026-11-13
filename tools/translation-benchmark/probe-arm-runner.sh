#!/usr/bin/env bash
# ARM ホストおよび FEXCore パックの確認。GameNative の動作・性能テストではない。
set -euo pipefail
out="${1:-translation-probe-results}"
mkdir -p "$out"
{
    printf 'アーキテクチャ: '; uname -m
    uname -a
    printf '\nCPU:\n'
    sed -n '1,40p' /proc/cpuinfo
} | tee "$out/host.txt"
if [[ "$(uname -m)" != aarch64 ]]; then
    echo 'ARM64 ホストではありません。' >&2
    exit 1
fi
url='https://downloads.gamenative.app/FEXCore-2611-gn-6.wcp'
pack="$out/fexcore.wcp"
echo 'FEXCore キャッシュ対応候補を一時取得します。'
curl --fail --location --retry 2 --connect-timeout 30 --max-time 300 --max-filesize 536870912 "$url" -o "$pack"
sha256sum "$pack" | tee "$out/pack-sha256.txt"
# 展開はせず、内容の一覧のみを保存する。パック自体は成果物に含めない。
xz -dc "$pack" | tar -tf - > "$out/pack-files.txt"
if grep -Ei '(^|/)FEXOfflineCompiler64\.exe$' "$out/pack-files.txt" > "$out/offline-compiler.txt"; then
    echo 'パックに FEXOfflineCompiler64.exe が含まれています。これだけではキャッシュの動作は証明できません。'
else
    echo 'FEXOfflineCompiler64.exe が見つかりません。'
    exit 1
fi
