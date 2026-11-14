#!/usr/bin/env bash
# 失敗時の補助診断。別の HOME と別の ELF で状態を隔離する。
set -euo pipefail
[[ $# == 1 && ! -e $1 ]] || { echo '未使用の出力ディレクトリを指定してください' >&2; exit 2; }
mkdir -p "$1"
out=$(cd "$1" && pwd)
for test in exit branch; do
    dir="$out/$test"
    mkdir -p "$dir/home" "$dir/runtime" "$dir/cache" "$dir/config"
    chmod 700 "$dir/runtime"
    x86_64-linux-gnu-as "tools/translation-benchmark/cache-$test-x86_64.s" -o "$dir/test.o"
    x86_64-linux-gnu-ld -o "$dir/test" "$dir/test.o"
    export HOME="$dir/home" XDG_RUNTIME_DIR="$dir/runtime" XDG_CACHE_HOME="$dir/cache" XDG_CONFIG_HOME="$dir/config"
    # FEXServer は既定では UID 単位の抽象ソケットを共有するため、
    # HOME だけでは隔離できない。各試験で別のサーバーを使用する。
    export FEX_SERVERSOCKETPATH="fex-diag-${GITHUB_RUN_ID:-$$}-${test}"
    export FEX_ENABLECODECACHINGWIP=1
    echo "=== $test: 期待する終了コード 42 ==="
    for phase in record generate disabled cached; do
        set +e
        if [[ $phase == disabled ]]; then
            FEX_ENABLECODECACHINGWIP=0 timeout 90 FEX "$dir/test" >"$dir/$phase.txt" 2>&1
            status=$?
        elif [[ $phase == cached ]]; then
            timeout 90 strace -f -yy -e trace=openat,mmap,exit,exit_group -o "$dir/trace.txt" FEX "$dir/test" >"$dir/$phase.txt" 2>&1
            status=$?
        else
            timeout 90 FEX "$dir/test" >"$dir/$phase.txt" 2>&1
            status=$?
        fi
        set -e
        printf '%s: 終了コード %d\n' "$phase" "$status"
    done
    find "$dir/cache" -type f -printf '%p %s bytes\n'
    grep -E 'mmap\(.*</[^>]*fex-emu/cache/[^>]*>.*\) *= 0x' "$dir/trace.txt" || echo 'キャッシュ mmap は未確認'
done | tee "$out/summary.txt"
