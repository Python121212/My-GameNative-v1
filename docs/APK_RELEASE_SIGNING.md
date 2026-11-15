# APK リリース署名手順 (コマンドライン版)

My-GameNative-v1 の APK に対し、**試験版（デバッグ署名）**と**安定版（リリース署名）**の両方をコマンドラインで署名する手順です。

対象ブランチ: `arena/210b97b4-my-gamenative-v1`

---

## 1. 前提条件

Android SDK の build-tools が必要です。以下のコマンドが使える状態にしてください。

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export PATH="$ANDROID_HOME/build-tools/<バージョン>:$PATH"

# 確認
which apksigner zipalign keytool
```

`apksigner` は Android SDK Build-Tools 24.0.0 以降に含まれます。古い環境では `jarsigner` をフォールバックとして使用します。

---

## 2. 署名モードの違い

| 項目 | 試験版 (test) | 安定版 (stable) |
|------|--------------|----------------|
| 目的 | 動作確認・内部配布 | ストアリリース・公開配布 |
| 署名鍵 | Android デバッグ鍵 (`debug.keystore`) | 専用リリース鍵 (独自キーストア) |
| スクリプト引数 | `./tools/sign-apk.sh test <apk>` | `./tools/sign-apk.sh stable <apk> <keystore> <alias>` |
| 出力ファイル | `*-test-signed.apk` | `*-stable-signed.apk` |

> **注意:** Google Play などへの公開には必ず「安定版」のリリース鍵を使用してください。デバッグ鍵では公開できません。

---

## 3. スクリプト配置と実行権限

スクリプトは `tools/sign-apk.sh` に配置済みです。初回のみ実行権限を付与します。

```bash
chmod +x tools/sign-apk.sh
```

---

## 4. 試験版に署名する (デバッグ鍵)

デバッグキーストアが無い場合は自動作成されます。

```bash
# 例: legacy フレーバーのリリース (Unsigned) APK を試験版署名
./tools/sign-apk.sh test \
  app/build/outputs/apk/legacy/release/app-legacy-release-unsigned.apk

# 出力例: app/legacy-release-test-signed.apk
```

### 内部処理
1. `~/.android/debug.keystore` が無ければ新規作成 (`androiddebugkey` / `android`)
2. `zipalign` で 4 バイト境界整列
3. `apksigner` でデバッグ鍵で署名 (古い SDK なら `jarsigner`)
4. `apksigner verify` で署名を検証

---

## 5. 安定版に署名する (リリース鍵)

### ステップ A: リリース用キーストアを新規作成 (初回のみ)

ストア公開用の鍵ペアを作成します。**パスワードとキーストアファイルは紛失しないよう安全に保管してください** (紛失するとアップデート公開が不可能になります)。

```bash
./tools/sign-apk.sh genkeystore \
  ~/.android/release.keystore \
  pluvia
```

対話でストアパスワードと鍵パスワードを入力します。または環境変数で指定も可能です:

```bash
export RELEASE_STORE_PASS=<ストアパスワード>
export RELEASE_KEY_PASS=<鍵パスワード>
```

### ステップ B: Gradle 用 keystore.properties を作成 (任意)

プロジェクトの Gradle 署名設定 (`app/build.gradle.kts` の `pluvia` 署名設定) と連携させる場合:

```properties
# app/keystores/keystore.properties
storeFile=/home/user/.android/release.keystore
storePassword=<ストアパスワード>
keyAlias=pluvia
keyPassword=<鍵パスワード>
```

`app/keystores/` は `.gitignore` 対象になっている可能性があるため、コミットしないよう注意してください。

### ステップ C: 安定版 APK に署名

```bash
# 例: modern フレーバーのリリース (Unsigned) APK を安定版署名
./tools/sign-apk.sh stable \
  app/build/outputs/apk/modern/release/app-modern-release-unsigned.apk \
  ~/.android/release.keystore \
  pluvia

# 出力例: app/modern-release-stable-signed.apk
```

パスワードを環境変数で設定済みなら引数は省略可能です:

```bash
./tools/sign-apk.sh stable \
  app/build/outputs/apk/modern/release/app-modern-release-unsigned.apk \
  ~/.android/release.keystore \
  pluvia
```

### 内部処理
1. リリースキーストアの存在を確認
2. `zipalign` で 4 バイト境界整列
3. `apksigner` でリリース鍵で署名 (古い SDK なら `jarsigner`)
4. `apksigner verify` で署名を検証

---

## 6. Unsigned APK の生成 (Gradle)

署名スクリプトに渡す「未署名 APK」は Gradle でビルドします。プロジェクトには `release-signed` ビルドタイプ (`pluvia` 署名設定) が既定であり、`release` はデバッグ署名設定になっている点に注意してください。

```bash
# 未署名リリース APK を生成 (各フレーバー: legacy / legacyXr / modern / modernXr)
./gradlew assembleLegacyRelease
./gradlew assembleModernRelease

# 生成先の例:
# app/build/outputs/apk/legacy/release/app-legacy-release-unsigned.apk
# app/build/outputs/apk/modern/release/app-modern-release-unsigned.apk
```

`release-signed` ビルドタイプをそのまま使う場合は `keystores/keystore.properties` が必要です。未署名 APK をスクリプトで個別に署名する運用を推奨します。

---

## 7. 署名済み APK の検証

```bash
# apksigner で詳細検証
apksigner verify --verbose app/legacy-release-test-signed.apk
apksigner verify --verbose app/modern-release-stable-signed.apk

# 署名情報の表示
apksigner print-certs app/modern-release-stable-signed.apk
```

成功時は `VERIFIED` と表示されます。

---

## 8. ファイル構成

```
tools/sign-apk.sh          # 署名スクリプト (本ドキュメント対象)
docs/APK_RELEASE_SIGNING.md # 本ドキュメント
app/keystores/keystore.properties  # Gradle 署名設定用 (未作成、git 除外推奨)
~/.android/debug.keystore   # 試験版用デバッグ鍵 (自動作成)
~/.android/release.keystore  # 安定版用リリース鍵 (手動作成)
```

---

## 9. トラブルシューティング

| 現象 | 原因・対処 |
|------|-----------|
| `apksigner: command not found` | `ANDROID_HOME/build-tools/<ver>` を `PATH` に追加 |
| `keystore file not found` | リリースキーストアのパスを確認、`genkeystore` で作成 |
| `Password verification failed` | ストアパスワード／鍵パスワードが一致しない。環境変数 `RELEASE_STORE_PASS` / `RELEASE_KEY_PASS` を確認 |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 既にインストール済みのアプリと署名鍵が異なる。一度アンインストールしてからインストール |
| `apksigner verify` が失敗 | 署名が破損、または zipalign 後に再署名し忘れ |

---

## 10. 補足: プロジェクト既存の署名設定

`app/build.gradle.kts` におけるビルドタイプ:

| ビルドタイプ | 署名設定 | 用途 |
|-------------|---------|------|
| `debug` | `debug` (Android 標準) | デバッグビルド |
| `release` | `debug` (※プロジェクト既定) | リリースビルドだがデバッグ鍵 |
| `release-signed` | `pluvia` (`keystore.properties` 参照) | ストア向けリリース |
| `release-gold` | `pluvia` + `.gold` サフィックス | ゴールドエディション |

コマンドラインで個別に署名する場合は、Gradle の署名設定に依存せず `sign-apk.sh` を使用できます。

---

## 11. GitHub Actions で APK を生成する

`.github/workflows/build-apk.yml` にて、GitHub Actions 上で **試験版** と **安定版** の両方をビルド・署名できます。

### トリガー

| イベント | 動作 |
|---------|------|
| `push` to `main` / `arena/*` | 試験版 (デバッグ署名) を自動生成 |
| `workflow_dispatch` | バリアント・リリース種別・リリース作成を手動選択 |

### ワークフロー dispatch 入力

| 入力 | 説明 | 選択肢 | 既定 |
|------|------|--------|------|
| `variant` | Android フレーバー | `legacy`, `legacyXr`, `modern`, `modernXr` | `modern` |
| `release_type` | リリース種別 | `test`, `stable` | `test` |
| `create_release` | GitHub Release を作成 | `true`, `false` | `false` |

### 試験版 (test)

Android デバッグ鍵 (`~/.android/debug.keystore` / `androiddebugkey`) で署名します。Secrets は不要です。

```bash
# GitHub UI の Actions タブ → "Build APK" → Run workflow
# release_type: test を選択して実行
```

出力アーティファクト: `gamenative-<variant>-test-apk` (`*-test-signed.apk`)

### 安定版 (stable)

リリースキーストアで署名します。リポジトリの **Secrets** に以下を設定してください。

| Secret | 説明 |
|--------|------|
| `KEYSTORE` | リリースキーストアを base64 エンコードした文字列 |
| `KEYSTORE_KEY_ALIAS` | 鍵のエイリアス |
| `KEYSTORE_KEY_PASSWORD` | 鍵のパスワード |
| `KEYSTORE_STORE_PASSWORD` | キーストアのパスワード |

```bash
# 例: キーストアを base64 エンコードして Secrets に設定
base64 -i ~/.android/release.keystore | pbcopy  # macOS
# または
base64 ~/.android/release.keystore             # Linux
```

出力アーティファクト: `gamenative-<variant>-stable-apk` (`*-stable-signed.apk`)

`create_release: true` を指定すると、GitHub Release も自動作成されます。

### アーティファクトの取得

Actions ランの「Artifacts」セクションからダウンロード可能です。 retention は 14 日です。

### 関連ファイル

- `.github/workflows/build-apk.yml` — GitHub Actions ワークフロー定義
- `tools/sign-apk.sh` — ローカル用コマンドライン署名スクリプト
- `docs/APK_RELEASE_SIGNING.md` — 本ドキュメント

---

**作成日:** 2026-11-15
**対象:** `arena/210b97b4-my-gamenative-v1` (branched from `171b8c06633d2eb8a54a2da6bd9bd2d8963f0aa4`)
