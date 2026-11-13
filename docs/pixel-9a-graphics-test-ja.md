# Google Pixel 9a の描画確認

Pixel 9a 実機での確認用手順です。このリポジトリだけでは実機の動作を保証できません。エミュレーターの成功結果は Pixel 9a の GPU やドライバーの動作を証明しません。

## 1. 実機の接続と基本描画テスト

1. Pixel 9a で開発者向けオプションと USB デバッグを有効にし、PC に接続して端末上で接続を許可します。
2. [CLI ビルド手順](cli-build-ja.md)の JDK と Android SDK を用意し、`adb devices` で端末が `device` と表示されること、`adb shell getprop ro.product.model` で Pixel 9a であることを確認します。複数の端末が接続されている場合は `ANDROID_SERIAL` に対象のシリアルを指定してください。シリアルは公開しないでください。
3. リポジトリのルートで以下を実行します。APK とテスト APK が生成・インストールされ、テストが実機で実行されます。既存アプリと署名が異なる場合、上書きインストールは失敗します。アンインストールするとアプリのデータが消えるため、先にバックアップしてください。

   ```sh
   test -f local.properties || touch local.properties
   ./gradlew :app:connectedModernDebugAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.class=app.gamenative.GraphicsDeviceSmokeTest
   adb logcat -d -s GraphicsDeviceSmokeTest:I '*:S'
   ```

テストは OpenGL ES 2 のオフスクリーン描画先を作り、赤色を描いてピクセルを読み戻します。成功しても **GameNative の画面表示、Vulkan、ゲーム描画の成功は証明されません**。出力された GPU 名、ベンダー、OpenGL バージョンを記録し、ソフトウェアレンダラーが表示された場合は実 GPU の確認として扱わないでください。

## 2. アプリとゲームの実画面確認

1. アプリを起動し、初期画面が表示され、操作できることを確認します。
2. 正規に所有している動作確認用ゲームを選び、インストールして起動します。ゲームタイトル、ストア、バージョン、端末の Android バージョン、アプリのコミット／APK、ゲームの描画設定やドライバー設定を記録します。
3. ゲームのメニューと実際のプレイ画面が表示・更新されることを確認します。黒画面、点滅、表示崩れ、クラッシュ、操作不能があれば発生箇所と再現手順を記録します。必要に応じて別の対応設定でも試し、変更した設定を記録してください。
4. 一つのゲームが動いても、すべてのゲームや Vulkan／DirectX 経路が動くとは限りません。利用したいゲームごとに確認してください。

スクリーンショットやログにはアカウント名・ゲーム情報などが含まれる可能性があります。共有前に確認し、認証情報や個人情報を削除してください。`adb logcat` 全体の公開は避けてください。

## 判定の記録例

- 端末モデル／Android バージョン：
- アプリのコミットまたはリリース：
- GPU／ドライバーと OpenGL の情報：
- 基本描画テスト：成功／失敗（エラー）：
- ゲーム名／バージョン／ストア：
- 使用した描画設定：
- メニュー／プレイ画面：成功／失敗（再現手順）：

## 実機を持たずにクラウドで確認する

GitHub Actions と Firebase Test Lab のクラウド実機を使う方法です。Codespaces からも GitHub の Actions 画面を操作できますが、Codespaces 自体のエミュレーターは Pixel 9a の GPU やドライバーを再現しません。クラウド端末の利用可能なモデルは変わるため、**Pixel 9a が提供されていることは保証できません**。Pixel 9a が一覧になければ、別の機種の結果を Pixel 9a の結果として扱わないでください。

### 初期設定

1. Firebase と連携した Google Cloud プロジェクトを用意し、Firebase Test Lab、Cloud Testing API、Cloud Tool Results API を有効にします。利用枠、料金、プロジェクトの請求設定を事前に確認してください。テストは課金対象になる場合があります。
2. GitHub Actions から Google Cloud にアクセスするため、[Workload Identity Federation](https://github.com/google-github-actions/auth#setting-up-workload-identity-federation) とサービスアカウントを設定します。プロバイダーの条件は、このリポジトリと信頼するブランチに限定してください。サービスアカウントには Test Lab の実行および結果保存に必要な最小限の権限を付与します。
3. GitHub のリポジトリ設定の **Settings → Secrets and variables → Actions → Variables** に、`GCP_PROJECT_ID`、`GCP_WIF_PROVIDER`（Workload Identity Provider の完全なリソース名）、`GCP_SERVICE_ACCOUNT`（サービスアカウントのメールアドレス）を設定します。これらは識別子であり、秘密鍵をチャットやリポジトリに貼り付ける必要はありません。信頼できないコードが認証を利用できないよう、ワークフローの実行権限を制限してください。
4. この変更を確認してデフォルトブランチに取り込むと、GitHub の Actions 画面に `クラウド実機の描画テスト` が手動実行用として表示されます。

### 実行

1. Actions で `クラウド実機の描画テスト` を選び、`Run workflow` から `operation=list` を実行します。ログの端末一覧で **Pixel 9a** とそのモデル ID を確認します。見つかった ID を `model` に指定して `operation=list` を再実行し、詳細から対応する Android バージョン ID を確認します。
2. Pixel 9a が利用可能な場合、再度 `Run workflow` を選び、`operation=test` と確認した `model`、`android_version` を指定します。アプリとテスト APK をビルドし、選択したクラウド端末上で `GraphicsDeviceSmokeTest` を実行します。結果やログへのリンクは Actions の実行ログに表示されます。
3. 成功・失敗と端末名、Android バージョン、GPU の情報を Test Lab の結果で確認します。実行時の提供状況や混雑により、端末の確保に失敗することがあります。

このテストは **OpenGL ES のオフスクリーン描画確認のみ**です。実際のゲーム、画面への表示、Vulkan、DirectX の動作までは確認しません。Pixel 9a のゲーム互換性を判断するには、同じ実機上で対象ゲームの起動と描画を別途確認する必要があります。クラウドへのログイン情報、ゲームデータ、画面画像などの送信には注意してください。

## GitHub Actions のエミュレーターで確認する

`ARM 変換エミュレーター描画確認` は Google Cloud の設定なしで、アプリとテスト APK をビルドして Android 35 エミュレーターで検査します。デフォルトブランチに取り込んだ後、GitHub の Actions 画面から手動実行できます。Codespaces からも操作できます。

この方法は **ARM64 の仮想 CPU ではなく**、x86_64 システムイメージの ARM ネイティブコード変換機能を利用します。テストはインストールされた APK の `primaryCpuAbi` が `arm64-v8a` であること、同梱 ARM64 ライブラリを読み込めること、OpenGL ES で描いた色を読み戻せることを確認します。結果と GPU 情報は Actions のログと成果物 `arm-emulator-graphics-result` で確認できます。ARM 変換に対応していないイメージや KVM を利用できない環境では失敗します。

**Pixel 9a の GPU・ドライバーや実際のゲームの描画を再現するテストではありません。** エミュレーターはソフトウェア描画を使用します。成功しても Pixel 9a でゲームが動くとは判断できません。ゲーム画面や端末固有の問題は別途実機で確認してください。
