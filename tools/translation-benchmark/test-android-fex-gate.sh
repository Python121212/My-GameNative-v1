#!/usr/bin/env bash
# 既存の APK 起動・描画検査の後、Android 上で FEX 関連のアプリ側判定を検査する。
set -euo pipefail
mkdir -p emulator-results
set +e
adb shell am instrument -w -r -e class app.gamenative.FexCoreIntegrationSmokeTest \
  app.gamenative.test/androidx.test.runner.AndroidJUnitRunner 2>&1 \
  | tr -d '\r' | tee emulator-results/fex-gate.txt
status=${PIPESTATUS[0]}
set -e
if (( status != 0 )) || ! grep -Eq '^OK \(1 test\)' emulator-results/fex-gate.txt; then
  echo '::error::FEX のアプリ側マップ判定に失敗しました。'
  exit 1
fi
printf '%s\n' 'Android の FEX マップ判定は成功。FEXCore DLL 自体の実行ではありません。'
