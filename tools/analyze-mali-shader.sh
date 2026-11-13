#!/usr/bin/env bash
# Vulkan または OpenGL ES シェーダーを Mali Offline Compiler で解析する。
set -euo pipefail
cd "$(dirname "$0")/.."

usage() {
    cat <<'USAGE'
使い方: tools/analyze-mali-shader.sh [vulkan|gles] [フラグメントシェーダー]
既定: vulkan app/src/main/cpp/winlator/window.frag
環境変数: MALIOC、GLSLANG_VALIDATOR、MALI_CORE、MALIOC_REPORT_DIR、
          MALIOC_CACHE_DIR、MALIOC_CACHE_CONTEXT、MALIOC_NO_CACHE
USAGE
}
if [[ "${1:-}" == --help || "${1:-}" == -h ]]; then usage; exit 0; fi
if (( $# > 2 )); then usage >&2; exit 2; fi
mode=${1:-vulkan}
shader=${2:-app/src/main/cpp/winlator/window.frag}
core=${MALI_CORE:-Mali-G715}
report_dir=${MALIOC_REPORT_DIR:-build/malioc}
cache_dir=${MALIOC_CACHE_DIR:-${HOME}/.cache/gamenative-malioc}
cache_context=${MALIOC_CACHE_CONTEXT:-default}
malioc=${MALIOC:-malioc}
validator=${GLSLANG_VALIDATOR:-glslangValidator}
mkdir -p "$report_dir"
report="$report_dir/report.txt"
: > "$report"
fail() {
    echo "$1" | tee -a "$report" >&2
    exit "$2"
}
if [[ "$mode" != vulkan && "$mode" != gles ]]; then
    fail 'エラー: モードは vulkan または gles を指定してください。' 2
fi
if [[ ! -f "$shader" || ! -r "$shader" ]]; then
    fail "エラー: シェーダーを読み込めません: $shader" 2
fi
malioc_path=$(command -v "$malioc") || fail 'エラー: malioc が見つかりません。Arm の公式ツールをインストールし、PATH または MALIOC を設定してください。' 127
if [[ "$mode" == vulkan ]]; then
    validator_path=$(command -v "$validator") || fail 'エラー: glslangValidator が見つかりません。PATH または GLSLANG_VALIDATOR を設定してください。' 127
fi
hash_file() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum < "$1" | awk '{print $1}'
    else
        shasum -a 256 < "$1" | awk '{print $1}'
    fi
}
hash_text() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum | awk '{print $1}'
    else
        shasum -a 256 | awk '{print $1}'
    fi
}
# バージョンが取得できない場合は安全のためキャッシュを使わない。
cache_ok=1
mali_version=$("$malioc_path" --version 2>&1) || cache_ok=0
validator_version='不要'
validator_hash='不要'
if [[ "$mode" == vulkan ]]; then
    validator_version=$("$validator_path" --version 2>&1) || cache_ok=0
    validator_hash=$(hash_file "$validator_path")
fi
[[ "${MALIOC_NO_CACHE:-0}" == 1 ]] && cache_ok=0
key=$(printf '%s\0' 'gamenative-mali-cache-v1' "$mode" "$core" "$shader" \
    "$(hash_file "$shader")" "$(hash_file "$0")" "$(hash_file "$malioc_path")" \
    "$mali_version" "$validator_hash" "$validator_version" "$cache_context" | hash_text)
cache_file="$cache_dir/$key/report.txt"
if (( cache_ok )) && [[ -s "$cache_file" ]]; then
    cp "$cache_file" "$report"
    echo "キャッシュを再利用しました: $key"
    cat "$report"
    printf '解析結果: %s\n' "$report"
    exit 0
fi
{
    printf '対象: %s\nAPI: %s\nシェーダー: %s\n' "$core" "$mode" "$shader"
    printf 'SHA-256: %s\n' "$(hash_file "$shader")"
    printf 'キャッシュキー: %s\n' "$key"
    echo '--- malioc バージョン ---'
    printf '%s\n' "$mali_version"
    if [[ "$mode" == vulkan ]]; then
        echo '--- glslangValidator バージョン ---'
        printf '%s\n' "$validator_version"
    fi
} 2>&1 | tee -a "$report"
if [[ "$mode" == vulkan ]]; then
    spv="$report_dir/shader.frag.spv"
    rm -f "$spv"
    echo '--- GLSL から Vulkan SPIR-V を生成 ---' | tee -a "$report"
    "$validator_path" -V -S frag "$shader" -o "$spv" 2>&1 | tee -a "$report"
    echo '--- Mali Offline Compiler ---' | tee -a "$report"
    "$malioc_path" --core "$core" --vulkan --fragment "$spv" 2>&1 | tee -a "$report"
else
    echo '--- Mali Offline Compiler ---' | tee -a "$report"
    "$malioc_path" --core "$core" --opengles --fragment "$shader" 2>&1 | tee -a "$report"
fi
# 成功時のみ一時ファイルから原子的に保存する。エラー結果は再利用しない。
if (( cache_ok )); then
    mkdir -p "$(dirname "$cache_file")"
    tmp=$(mktemp "${cache_file}.XXXXXX")
    cp "$report" "$tmp"
    mv -f "$tmp" "$cache_file"
    echo "解析結果をキャッシュしました: $key"
else
    echo 'キャッシュは無効です。'
fi
printf '解析結果: %s\n' "$report"
