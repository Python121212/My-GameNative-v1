#!/usr/bin/env bash
# Android 35 x86_64 の ARM 変換機能を利用した基本描画検査。
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p emulator-results
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
    -e class 'app.gamenative.ArmNativeBridgeSmokeTest,app.gamenative.GraphicsDeviceSmokeTest' \
    app.gamenative.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | tr -d '\r' | tee emulator-results/instrumentation.txt
status=${PIPESTATUS[0]}
set -e
adb logcat -d -s 'GraphicsDeviceSmokeTest:I' '*:S' > emulator-results/graphics-log.txt || true
cat emulator-results/graphics-log.txt
if (( status != 0 )) || ! grep -Eq '^OK \(2 tests?\)' emulator-results/instrumentation.txt; then
    echo 'ARM64 ライブラリまたは OpenGL のテストに失敗しました。' >&2
    exit 1
fi
echo 'ARM64 ライブラリの読み込みと OpenGL ES の基本描画が成功しました。'
