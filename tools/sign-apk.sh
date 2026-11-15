#!/usr/bin/env bash
#
# sign-apk.sh — My-GameNative-v1 APK リリース署名スクリプト (コマンドライン版)
#
# 試験版（デバッグ署名）と安定版（リリース署名）の両方に対応。
# Android SDK build-tools (apksigner, zipalign, keytool) を使用。
#
# 使い方:
#   ./tools/sign-apk.sh test  <unsigned.apk>           # 試験版: デバッグ鍵で署名
#   ./tools/sign-apk.sh stable <unsigned.apk> <keystore.jks> <alias> [key_pass] [store_pass]
#                                                       # 安定版: リリース鍵で署名
#
# 例:
#   ./tools/sign-apk.sh test app/build/outputs/apk/legacy/release/app-legacy-release-unsigned.apk
#   ./tools/sign-apk.sh stable app/build/outputs/apk/modern/release/app-modern-release-unsigned.apk \
#       ~/.android/release.keystore pluvia
#
set -euo pipefail

###############################################################################
# 設定
###############################################################################
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# デバッグ用デフォルトキーストア (Android 標準)
DEBUG_KEYSTORE="${HOME}/.android/debug.keystore"
DEBUG_ALIAS="androiddebugkey"
DEBUG_KEY_PASS="android"
DEBUG_STORE_PASS="android"

# 出力サフィックス
TEST_SUFFIX="-test-signed"
STABLE_SUFFIX="-stable-signed"

###############################################################################
# ヘルパー
###############################################################################
log()  { printf '[sign-apk] %s\n' "$*"; }
err()  { printf '[sign-apk] エラー: %s\n' "$*" >&2; }
die()  { err "$*"; exit 1; }

require_tool() {
  if ! command -v "$1" >/dev/null 2>&1; then
    die "必須ツール '$1' が見つかりません。Android SDK build-tools を PATH に追加してください (例: export PATH=\"\$ANDROID_HOME/build-tools/<ver>:\$PATH\")"
  fi
}

check_android_sdk() {
  require_tool zipalign
  require_tool apksigner || require_tool jarsigner
  require_tool keytool
}

# APK の入力チェック
validate_apk() {
  local apk="$1"
  [[ -f "$apk" ]] || die "APK ファイルが見つかりません: $apk"
  [[ "$apk" == *.apk ]] || die "APK ファイルである必要があります: $apk"
}

# 出力ファイル名を生成
output_name() {
  local apk="$1" suffix="$2"
  local base
  base="$(basename "${apk%.apk}")"
  echo "${base}${suffix}.apk"
}

###############################################################################
# デバッグキーストアの作成 (試験版用)
###############################################################################
ensure_debug_keystore() {
  if [[ -f "${DEBUG_KEYSTORE}" ]]; then
    log "デバッグキーストアを使用: ${DEBUG_KEYSTORE}"
    return 0
  fi

  log "デバッグキーストアが見つからないため作成します: ${DEBUG_KEYSTORE}"
  mkdir -p "$(dirname "${DEBUG_KEYSTORE}")"
  keytool -genkeypair \
    -v \
    -keystore "${DEBUG_KEYSTORE}" \
    -storepass "${DEBUG_STORE_PASS}" \
    -keypass "${DEBUG_KEY_PASS}" \
    -alias "${DEBUG_ALIAS}" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" \
    >/dev/null 2>&1 \
    || die "デバッグキーストアの作成に失敗しました"
  log "デバッグキーストアを作成しました: ${DEBUG_KEYSTORE}"
}

###############################################################################
# 試験版署名 (デバッグ鍵)
###############################################################################
sign_test() {
  local apk="$1"
  validate_apk "$apk"
  check_android_sdk
  ensure_debug_keystore

  local out
  out="$(output_name "$apk" "$TEST_SUFFIX")"
  out="${ROOT_DIR}/${out}"

  log "=== 試験版 (デバッグ署名) ==="
  log "入力:  ${apk}"
  log "出力:  ${out}"
  log "キーストア: ${DEBUG_KEYSTORE} (alias: ${DEBUG_ALIAS})"

  # zipalign (署名前に整列)
  local aligned="${out%.apk}-aligned.apk"
  zipalign -v -p 4 "$apk" "$aligned" \
    || die "zipalign に失敗しました"

  # apksigner で署名 (推奨)
  if command -v apksigner >/dev/null 2>&1; then
    apksigner sign \
      --ks "${DEBUG_KEYSTORE}" \
      --ks-key-alias "${DEBUG_ALIAS}" \
      --ks-pass "pass:${DEBUG_STORE_PASS}" \
      --key-pass "pass:${DEBUG_KEY_PASS}" \
      --out "$out" \
      "$aligned" \
      || die "apksigner による署名に失敗しました"
  else
    # jarsigner フォールバック (古い SDK)
    jarsigner \
      -verbose \
      -sigalg SHA1withRSA \
      -digestalg SHA1 \
      -keystore "${DEBUG_KEYSTORE}" \
      -storepass "${DEBUG_STORE_PASS}" \
      -keypass "${DEBUG_KEY_PASS}" \
      "$aligned" "${DEBUG_ALIAS}" \
      || die "jarsigner による署名に失敗しました"
    cp "$aligned" "$out"
  fi

  rm -f "$aligned"

  # 署名検証
  log "署名を検証中..."
  if command -v apksigner >/dev/null 2>&1; then
    apksigner verify --verbose "$out" \
      || die "署名検証に失敗しました"
  fi

  log "完了: 試験版署名済み APK -> ${out}"
}

###############################################################################
# 安定版署名 (リリース鍵)
###############################################################################
sign_stable() {
  local apk="$1" keystore="$2" alias="$3"
  local key_pass="${4:-}"
  local store_pass="${5:-}"
  validate_apk "$apk"
  check_android_sdk
  [[ -f "$keystore" ]] || die "リリースキーストアが見つかりません: $keystore"

  # パス未指定時はプロンプト (安全のため環境変数もチェック)
  if [[ -z "$store_pass" ]]; then
    store_pass="${RELEASE_STORE_PASS:-}"
  fi
  if [[ -z "$key_pass" ]]; then
    key_pass="${RELEASE_KEY_PASS:-}"
  fi

  local ks_pass_arg=()
  local key_pass_arg=()
  if [[ -n "$store_pass" ]]; then ks_pass_arg=(--ks-pass "pass:${store_pass}"); fi
  if [[ -n "$key_pass" ]]; then key_pass_arg=(--key-pass "pass:${key_pass}"); fi

  local out
  out="$(output_name "$apk" "$STABLE_SUFFIX")"
  out="${ROOT_DIR}/${out}"

  log "=== 安定版 (リリース署名) ==="
  log "入力:    ${apk}"
  log "出力:    ${out}"
  log "キーストア: ${keystore} (alias: ${alias})"

  # zipalign
  local aligned="${out%.apk}-aligned.apk"
  zipalign -v -p 4 "$apk" "$aligned" \
    || die "zipalign に失敗しました"

  # apksigner で署名
  if command -v apksigner >/dev/null 2>&1; then
    apksigner sign \
      --ks "$keystore" \
      --ks-key-alias "$alias" \
      "${ks_pass_arg[@]+"${ks_pass_arg[@]}"}" \
      "${key_pass_arg[@]+"${key_pass_arg[@]}"}" \
      --out "$out" \
      "$aligned" \
      || die "apksigner による署名に失敗しました"
  else
    jarsigner \
      -verbose \
      -sigalg SHA256withRSA \
      -digestalg SHA-256 \
      -keystore "$keystore" \
      "${ks_pass_arg[@]+"${ks_pass_arg[@]}"}" \
      "${key_pass_arg[@]+"${key_pass_arg[@]}"}" \
      "$aligned" "$alias" \
      || die "jarsigner による署名に失敗しました"
    cp "$aligned" "$out"
  fi

  rm -f "$aligned"

  # 署名検証
  log "署名を検証中..."
  if command -v apksigner >/dev/null 2>&1; then
    apksigner verify --verbose "$out" \
      || die "署名検証に失敗しました"
  fi

  log "完了: 安定版署名済み APK -> ${out}"
}

###############################################################################
# キーストア新規作成 (安定版用リリース鍵)
###############################################################################
create_release_keystore() {
  local keystore="$1" alias="$2"
  [[ -n "$keystore" ]] || die "キーストアパスを指定してください"
  [[ -n "$alias" ]] || die "エイリアスを指定してください"
  if [[ -f "$keystore" ]]; then
    die "キーストアは既に存在します: $keystore (上書きは行いません)"
  fi

  check_android_sdk
  mkdir -p "$(dirname "$keystore")"

  log "リリース用キーストアを作成中: ${keystore} (alias: ${alias})"
  keytool -genkeypair \
    -v \
    -keystore "$keystore" \
    -alias "$alias" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=My-GameNative,O=My-GameNative,C=JP" \
    || die "キーストアの作成に失敗しました"

  log "作成完了: ${keystore}"
  log "注意: storePass / keyPass を安全に保管し、app/keystores/keystore.properties に反映してください"
}

###############################################################################
# 使用法表示
###############################################################################
usage() {
  cat <<EOF
Usage: $0 {test|stable|genkeystore} <apk> [keystore] [alias] [key_pass] [store_pass]

  test  <apk>
        試験版 (デバッグ署名) に署名する。
  stable <apk> <keystore> <alias> [key_pass] [store_pass]
        安定版 (リリース署名) に署名する。
  genkeystore <keystore> <alias>
        リリース用キーストアを新規作成する。

環境変数:
  RELEASE_STORE_PASS  リリースキーストアのストアパスワード
  RELEASE_KEY_PASS    リリース鍵のパスワード
  ANDROID_HOME        Android SDK のパス (build-tools を PATH に含めること)

例:
  $0 test app/build/outputs/apk/legacy/release/app-legacy-release-unsigned.apk
  $0 stable app/build/outputs/apk/modern/release/app-modern-release-unsigned.apk \
        ~/.android/release.keystore pluvia
EOF
}

###############################################################################
# メイン
###############################################################################
main() {
  if [[ $# -lt 1 ]]; then
    usage
    exit 1
  fi

  local mode="$1"; shift

  case "$mode" in
    test)
      [[ $# -ge 1 ]] || { usage; exit 1; }
      sign_test "$1"
      ;;
    stable)
      [[ $# -ge 3 ]] || { usage; exit 1; }
      sign_stable "$1" "$2" "$3" "${4:-}" "${5:-}"
      ;;
    genkeystore)
      [[ $# -ge 2 ]] || { usage; exit 1; }
      create_release_keystore "$1" "$2"
      ;;
    *)
      err "不明なモード: $mode"
      usage
      exit 1
      ;;
  esac
}

main "$@"
