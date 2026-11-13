#!/usr/bin/env bash
# 一時環境だけで Linux 版 FEX の記録・キャッシュ生成・実ファイルの mmap を調査する。
set -euo pipefail
if [[ $# != 1 || -e $1 ]]; then echo '未使用の出力ディレクトリを指定してください。' >&2; exit 2; fi
out="$1"
mkdir -p "$out/home" "$out/runtime" "$out/cache" "$out/config"
chmod 700 "$out/runtime"
out="$(cd "$out" && pwd)"
export HOME="$out/home" XDG_RUNTIME_DIR="$out/runtime" XDG_CACHE_HOME="$out/cache" XDG_CONFIG_HOME="$out/config"
export FEX_ENABLECODECACHINGWIP=1
x86_64-linux-gnu-as tools/translation-benchmark/hello-x86_64.s -o "$out/hello.o"
x86_64-linux-gnu-ld -o "$out/hello-x86_64" "$out/hello.o"
command -v FEX | tee "$out/fex-binary.txt"
FEX --version > "$out/fex-version.txt" 2>&1 || true
if ! command -v FEXOfflineCompiler | tee "$out/compiler.txt"; then
    echo 'FEXOfflineCompiler が PATH 上にありません。Linux パッケージ内容を確認してください。' >&2
    exit 1
fi
# 初回で記録、次回にオフライン生成を要求する（FEX の版で動作は異なりうる）。
for run in 1 2; do
    timeout 90 FEX "$out/hello-x86_64" 2>&1 | tee "$out/run-$run.txt"
    grep -Fx 'translation-smoke-ok' "$out/run-$run.txt"
done
find "$out/cache" -type f -printf '%p %s bytes\n' | tee "$out/files.txt"
if ! find "$out/cache" -type f -path '*/fex-emu/cache/*' -size +0c | grep -q .; then
    echo '非空の生成済みキャッシュを確認できません。' >&2
    exit 1
fi
# open 成功とそのファイルの mmap 成功は読み込みの証拠だが、キャッシュされた命令の実行自体の証明ではない。
set +e
timeout 90 strace -f -yy -e trace=openat,mmap -o "$out/trace.txt" FEX "$out/hello-x86_64" 2>&1 | tee "$out/run-3.txt"
status=${PIPESTATUS[0]}
set -e
if (( status != 0 )); then
    echo "strace 付き実行に失敗しました: $status" >&2
    tail -n 25 "$out/trace.txt" >&2 || true
    exit "$status"
fi
grep -Fx 'translation-smoke-ok' "$out/run-3.txt"
if ! grep -E 'mmap\(.*</[^>]*fex-emu/cache/[^>]*>.*\) *= 0x' "$out/trace.txt" > "$out/mapped-cache.txt"; then
    echo 'キャッシュの mmap 成功を確認できません。' >&2
    exit 1
fi
echo 'Linux 版 FEX のキャッシュファイルの mmap 成功を確認しました。命令実行は未確認です。'
