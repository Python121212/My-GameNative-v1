#!/usr/bin/env bash
# Vulkan または OpenGL ES シェーダーを Mali Offline Compiler で解析する。
set -euo pipefail
cd "$(dirname "$0")/.."

usage() {
    cat <<'USAGE'
使い方: tools/analyze-mali-shader.sh [vulkan|gles] [フラグメントシェーダー]
既定: vulkan app/src/main/cpp/winlator/window.frag
環境変数: MALIOC、GLSLANG_VALIDATOR、MALI_CORE、MALIOC_REPORT_DIR
USAGE
}
if [[ "${1:-}" == --help || "${1:-}" == -h ]]; then usage; exit 0; fi
if (( $# > 2 )); then usage >&2; exit 2; fi
mode=${1:-vulkan}
shader=${2:-app/src/main/cpp/winlator/window.frag}
core=${MALI_CORE:-Mali-G715}
report_dir=${MALIOC_REPORT_DIR:-build/malioc}
malioc=${MALIOC:-malioc}
validator=${GLSLANG_VALIDATOR:-glslangValidator}
mkdir -p "$report_dir"
report="$report_dir/report.txt"
# 毎回レポートを初期化し、失敗を過去の成功と取り違えない。
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
if ! command -v "$malioc" >/dev/null 2>&1; then
    fail 'エラー: malioc が見つかりません。Arm の公式ツールをインストールし、PATH または MALIOC を設定してください。' 127
fi
if [[ "$mode" == vulkan ]] && ! command -v "$validator" >/dev/null 2>&1; then
    fail 'エラー: glslangValidator が見つかりません。PATH または GLSLANG_VALIDATOR を設定してください。' 127
fi
{
    printf '対象: %s\nAPI: %s\nシェーダー: %s\n' "$core" "$mode" "$shader"
    printf 'SHA-256: '
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$shader" | awk '{print $1}'
    else
        shasum -a 256 "$shader" | awk '{print $1}'
    fi
    echo '--- malioc バージョン ---'
    "$malioc" --version || true
} 2>&1 | tee -a "$report"
if [[ "$mode" == vulkan ]]; then
    spv="$report_dir/shader.frag.spv"
    rm -f "$spv"
    echo '--- GLSL から Vulkan SPIR-V を生成 ---' | tee -a "$report"
    "$validator" -V -S frag "$shader" -o "$spv" 2>&1 | tee -a "$report"
    echo '--- Mali Offline Compiler ---' | tee -a "$report"
    "$malioc" --core "$core" --vulkan --fragment "$spv" 2>&1 | tee -a "$report"
else
    echo '--- Mali Offline Compiler ---' | tee -a "$report"
    "$malioc" --core "$core" --opengles --fragment "$shader" 2>&1 | tee -a "$report"
fi
printf '解析結果: %s\n' "$report"
