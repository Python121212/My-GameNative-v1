#!/usr/bin/env bash
# 外部の malioc を使わず、キャッシュとエラー処理を検証する。
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/bin" "$tmp/cache" "$tmp/report"
cat > "$tmp/bin/malioc" <<'MOCK'
#!/usr/bin/env bash
if [[ "${1:-}" == --version ]]; then echo 'mali mock 1'; exit 0; fi
echo run >> "$MOCK_CALLS"
if [[ "${MOCK_FAIL:-0}" == 1 ]]; then echo 'mock failure' >&2; exit 9; fi
printf 'mock success: %s\n' "$*"
MOCK
cat > "$tmp/bin/glslangValidator" <<'MOCK'
#!/usr/bin/env bash
if [[ "${1:-}" == --version ]]; then echo 'glslang mock 1'; exit 0; fi
[[ "$1" == -V && "$2" == -S && "$3" == frag && "$5" == -o ]]
printf 'mock spirv' > "$6"
MOCK
chmod +x "$tmp/bin/"*
printf '初期シェーダー\n' > "$tmp/shader.frag"
export PATH="$tmp/bin:$PATH" MALIOC_REPORT_DIR="$tmp/report" MALIOC_CACHE_DIR="$tmp/cache" MOCK_CALLS="$tmp/calls"
: > "$MOCK_CALLS"
run() { "$root/tools/analyze-mali-shader.sh" vulkan "$tmp/shader.frag" > "$tmp/output"; }
count() { wc -l < "$MOCK_CALLS" | tr -d ' '; }
run
[[ $(count) == 1 ]]
run
[[ $(count) == 1 ]] && grep -q 'キャッシュを再利用' "$tmp/output"
printf '変更\n' >> "$tmp/shader.frag"
run
[[ $(count) == 2 ]]
MALI_CORE=another-core run
[[ $(count) == 3 ]]
MALIOC_CACHE_CONTEXT=other run
[[ $(count) == 4 ]]
MALIOC_NO_CACHE=1 run
[[ $(count) == 5 ]]
# 失敗した結果が保存されないことを確認する。
if MOCK_FAIL=1 MALI_CORE=error-core run; then
    echo '解析失敗を検出できませんでした。' >&2
    exit 1
fi
MALI_CORE=error-core run
[[ $(count) == 7 ]]
grep -q 'mock success' "$tmp/report/report.txt"
echo 'Mali 解析キャッシュのテストに成功しました（モック使用）。'
