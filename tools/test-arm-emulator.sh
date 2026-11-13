#!/usr/bin/env bash
# Android 35 x86_64 の ARM 変換機能を利用した基本描画検査。
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p emulator-results
capture_failure() {
    status=$?
    if (( status != 0 )); then
        adb logcat -d -v brief -t 5000 > emulator-results/failure-log.txt 2>&1 || true
        adb logcat -b crash -d -v brief > emulator-results/crash-log.txt 2>&1 || true
        details=$(tail -n 60 emulator-results/crash-log.txt | sed -e 's/%/%25/g' | awk '{printf "%s%%0A", $0}' || true)
        echo "::error::アプリのクラッシュログ%0A$details"
    fi
}
trap capture_failure EXIT
shopt -s nullglob
apps=(app/build/outputs/apk/modern/debug/*.apk)
tests=(app/build/outputs/apk/androidTest/modern/debug/*.apk)
if (( ${#apps[@]} != 1 || ${#tests[@]} != 1 )); then
    echo 'アプリまたはテスト APK を一つに特定できません。' >&2
    exit 1
fi
{
    echo "端末 ABI: $(adb shell getprop ro.product.cpu.abilist | tr -d '\r')"
    echo "変換ブリッジ: $(adb shell getprop ro.dalvik.vm.native.bridge | tr -d '\r')"
    echo "Android: $(adb shell getprop ro.build.version.release | tr -d '\r')"
} | tee emulator-results/device.txt
adb install -r "${apps[0]}"
adb install -r "${tests[0]}"
adb shell dumpsys package app.gamenative | tr -d '\r' > emulator-results/package.txt
if ! grep -Eq 'primaryCpuAbi=arm64-v8a([[:space:]]|$)' emulator-results/package.txt; then
    echo 'ARM64 ライブラリが選択されていないため、ARM の確認として扱えません。' >&2
    exit 1
fi
# adb はテスト失敗時も成功終了する場合があるため、出力も必ず検証する。
set +e
adb shell am instrument -w -r \
    -e class 'app.gamenative.ArmNativeBridgeSmokeTest,app.gamenative.GraphicsDeviceSmokeTest,com.winlator.renderer.TextureFilterOptimizationTest,com.winlator.renderer.EffectBufferAllocationTest' \
    app.gamenative.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | tr -d '\r' | tee emulator-results/instrumentation.txt
status=${PIPESTATUS[0]}
set -e
adb logcat -d -s 'GraphicsDeviceSmokeTest:I' '*:S' > emulator-results/graphics-log.txt || true
cat emulator-results/graphics-log.txt
if (( status != 0 )) || ! grep -Eq '^OK \(4 tests?\)' emulator-results/instrumentation.txt; then
    details=$(tail -n 60 emulator-results/instrumentation.txt | sed -e 's/%/%25/g' | awk '{printf "%s%%0A", $0}')
    echo "::error::テストに失敗しました。%0A$details"
    exit 1
fi
echo 'ARM64 ライブラリの読み込み、OpenGL ES の基本描画、フィルター設定の確認が成功しました。'

# アプリ本体を起動し、画面表示とプロセスの生存を確認する。
adb shell am force-stop app.gamenative
adb logcat -c
adb shell pm grant app.gamenative android.permission.POST_NOTIFICATIONS || true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
adb shell am start -W -n app.gamenative/.MainActivity | tr -d '\r' | tee emulator-results/launch.txt
if ! grep -q 'Status: ok' emulator-results/launch.txt; then
    echo 'アプリの起動に失敗しました。' >&2
    exit 1
fi
initial_pid=''
for _ in {1..30}; do
    initial_pid=$(adb shell pidof app.gamenative 2>/dev/null | tr -d '\r' || true)
    [[ -n "$initial_pid" ]] && break
    sleep 1
done
if [[ -z "$initial_pid" ]]; then
    echo 'アプリのプロセスが起動していません。' >&2
    exit 1
fi
for _ in {1..10}; do
    sleep 1
    pid=$(adb shell pidof app.gamenative 2>/dev/null | tr -d '\r' || true)
    if [[ "$pid" != "$initial_pid" ]]; then
        echo '起動後10秒以内にアプリが終了または再起動しました。' >&2
        exit 1
    fi
done
adb shell dumpsys activity activities | tr -d '\r' > emulator-results/activities.txt
if ! grep -E 'topResumedActivity|mResumedActivity' emulator-results/activities.txt | grep -q 'app.gamenative/.MainActivity'; then
    echo '::error::GameNative のメイン画面が前面に表示されていません。'
    exit 1
fi
adb exec-out screencap -p > emulator-results/app-start.png
test -s emulator-results/app-start.png
printf 'APK が起動し、メイン画面の前面表示と10秒間のプロセス生存を確認しました。\n'
