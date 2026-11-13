# Android CLI ビルドの準備

この手順は APK を自動生成しません。Linux/macOS の端末向けです。

## 必要なもの

- JDK 17（`JAVA_HOME` を設定）
- Android SDK Command-line Tools と SDK Platform 36、Build Tools、NDK `27.3.13750724`
- Gradle、Google Maven、Maven Central、Gradle Plugin Portal、Sonatype snapshots にアクセスできるネットワーク
- 十分なディスク容量とメモリ。既定の Gradle JVM 最大ヒープは 8 GB です。

Android Studio または [公式 Command-line Tools](https://developer.android.com/tools) で SDK を用意し、`ANDROID_HOME` を SDK のパスに設定します。Command-line Tools をインストール後、例えば次を実行します。

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
sdkmanager --licenses
sdkmanager 'platform-tools' 'platforms;android-36' 'build-tools;35.0.0' 'ndk;27.3.13750724'
./tools/check-android-build.sh
```

`local.properties` に `sdk.dir` がある場合は Gradle がそれを優先します。必要なら自分の環境に合わせて設定してください。このファイルや署名鍵、API キーはコミットしないでください。`sdkmanager` や初回 Gradle 実行にはネットワークが必要です。

## APK を作らず Gradle を準備

```sh
./gradlew --version  # Gradle 8.12.1 を取得
./gradlew help       # プロジェクト設定・プラグイン解決を確認
```

`help` は APK を作りませんが、ビルド用の全依存関係を取得するわけではありません。実際のビルドの成功も保証しません。環境診断スクリプトはローカルの主要なツールを確認するだけで、ネットワークや全依存関係は検証しません。

## 後で APK を作る場合

Android 10 以降・arm64 向けの開発用は `./gradlew :app:assembleModernDebug`、旧 Android 向けは `./gradlew :app:assembleLegacyDebug`。出力先は `app/build/outputs/apk/` 以下です。debug は開発用署名です。`release` もこのプロジェクトでは debug 鍵で署名されます。配布用の `release-signed` / `release-gold` には別途適切な署名設定が必要です。

## ビルド時間について

既存の `gradle.properties` で並列実行、ビルドキャッシュ、設定キャッシュは有効です。デーモンと `~/.gradle` のキャッシュを維持し、必要な単一バリアントの debug ビルドを選び、毎回 `clean` しないことで再ビルドを速くできます。初回は依存関係のダウンロードとコンパイルが必要です。メモリ不足なら `~/.gradle/gradle.properties` 等で `org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8` とするか、`--max-workers` を減らしてください。キャッシュと並列化の効果は環境により異なります。
