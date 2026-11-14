#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
cat > "$tmp/adb" <<'MOCK'
#!/usr/bin/env bash
[[ $1 == -s && $2 == test-device ]] || exit 3
shift 2
case "$*" in
  'get-state') echo "${STATE:-device}";;
  'shell uname -m') echo "${KERNEL:-aarch64}";;
  'shell getprop ro.product.cpu.abilist') echo "${ABI:-arm64-v8a,armeabi-v7a}";;
  'shell getprop ro.dalvik.vm.native.bridge') echo "${BRIDGE:-0}";;
  'shell getprop ro.kernel.qemu') echo "${QEMU:-0}";;
  'shell getprop ro.build.version.release') echo 15;;
  *) echo "予期しない adb 呼び出し: $*" >&2; exit 4;;
esac
MOCK
chmod +x "$tmp/adb"
export PATH="$tmp:$PATH"
script=tools/translation-benchmark/check-android-translation.sh
bash "$script" test-device | grep -q 'ARM64 ネイティブ候補'
KERNEL=x86_64 ABI=x86_64,arm64-v8a BRIDGE=libhoudini.so bash "$script" test-device | grep -q '追加の変換が必要'
BRIDGE=libunknown.so bash "$script" test-device | grep -q '判定: 不明'
QEMU=1 bash "$script" test-device | grep -q '判定: 不明'
if STATE=offline bash "$script" test-device >/dev/null 2>&1; then echo 'オフライン端末を拒否できません' >&2; exit 1; fi
if bash "$script" >/dev/null 2>&1; then echo 'シリアル未指定を拒否できません' >&2; exit 1; fi
echo '端末の判定と非破壊の adb 呼び出しのモック検査: 成功'
