#!/usr/bin/env bash
# 同じ runner 上で debug と同一の署名鍵を使った release 候補を上書きし、基本起動を検査する。
set -euo pipefail
shopt -s nullglob
apks=(app/build/outputs/apk/modern/release/*.apk)
if (( ${#apks[@]} != 1 )); then echo 'release APK を特定できません' >&2; exit 1; fi
mkdir -p emulator-results
sha256sum "${apks[0]}" | tee emulator-results/release-apk-sha256.txt
adb install -r "${apks[0]}"
adb shell am force-stop app.gamenative
adb shell am start -W -n app.gamenative/.MainActivity | tr -d '\r' | tee emulator-results/release-launch.txt
grep -q 'Status: ok' emulator-results/release-launch.txt
pid=$(adb shell pidof app.gamenative | tr -d '\r')
[[ -n "$pid" ]]
for _ in {1..60}; do
  sleep 1
  [[ "$(adb shell pidof app.gamenative | tr -d '\r')" == "$pid" ]] || { echo 'release 候補が終了または再起動しました' >&2; exit 1; }
done
adb shell dumpsys activity activities | tr -d '\r' > emulator-results/release-activities.txt
grep -E 'topResumedActivity|mResumedActivity' emulator-results/release-activities.txt | grep -q 'app.gamenative/.MainActivity'
sha256sum --check emulator-results/release-apk-sha256.txt
printf '%s\n' 'release 候補の前面表示と60秒生存を確認。ゲームや実機の安定性は未確認。'
