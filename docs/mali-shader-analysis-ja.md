# Mali Offline Compiler によるシェーダー解析

ARM 変換エミュレーターによる基本描画テストに加え、Arm の Mali Offline Compiler（`malioc`）で Mali-G715 向けのシェーダーのコンパイルと静的な性能推定を行います。これは実行時テストではありません。実際のゲームの描画、ドライバーの不具合、FPS、Pixel 9a での互換性を保証しません。実際の端末に対応するコア名も、実機の GPU 情報と使用するツールの対応一覧で確認してください。

## 準備

- 対応する環境に [Arm の Mali Offline Compiler](https://developer.arm.com/Tools%20and%20Software/Mali%20Offline%20Compiler) を公式配布元からインストールし、ライセンス条件を確認します。ツールはこのリポジトリには含まれません。
- Vulkan GLSL を解析する場合、`glslangValidator`（glslang）もインストールします。
- `malioc --list` でツールが `Mali-G715` に対応しているか確認します。対応していない場合は適切なバージョンを用意するか、対象の GPU を確認したうえで `MALI_CORE` を指定してください。別のコアに変更した結果は G715 の結果ではありません。

## 実行

リポジトリのルートで次を実行します。

```sh
./tools/analyze-mali-shader.sh
```

既定の入力は `app/src/main/cpp/winlator/window.frag` です。VulkanRendererContext のシェーダーソースを、プロジェクトの CMake と同様に `glslangValidator -V` で SPIR-V に変換したうえで、`malioc --core Mali-G715 --vulkan --fragment` で解析します。これは元の GLSL をそのまま OpenGL ES として解析するのではありません。動的に生成されるゲームのシェーダーは対象外です。

別のファイルを指定する例：

```sh
# Vulkan GLSL のフラグメントシェーダー
./tools/analyze-mali-shader.sh vulkan path/to/shader.frag
# OpenGL ES のフラグメントシェーダー
./tools/analyze-mali-shader.sh gles path/to/my_shader.frag
```

後者は `malioc --core Mali-G715 --opengles --fragment path/to/my_shader.frag` に相当します。`my_shader.frag` は例であり、実際のファイルに置き換えてください。`MALIOC`、`GLSLANG_VALIDATOR` で実行ファイルのパスを指定できます。出力先は既定で `build/malioc/report.txt`（`MALIOC_REPORT_DIR` で変更可能）です。入力の SHA-256、API、コア名、コンパイラーの出力を記録し、コンパイルや解析が失敗すればエラー終了します。生成物は Git に含めません。

## GitHub Actions と組み合わせる

`Mali シェーダー解析` ワークフローを手動実行できます。GitHub ホストランナーには `malioc` が含まれないため、公式ツールと `glslangValidator` をインストールし、サービスの PATH から両方を実行できる、信頼できる Linux x64 のセルフホストランナーに `malioc` ラベルを付けてください。ワークフローがデフォルトブランチに存在し、該当ランナーがオンラインである必要があります。セルフホストランナーは信頼できるコードだけに使用してください。

Actions の実行ログと成果物 `mali-g715-shader-report` で結果を確認できます。別途 `ARM 変換エミュレーター描画確認` を実行すれば、エミュレーターによる OpenGL ES の基本描画も検査できます。両方が成功しても、Pixel 9a 上のゲームの動作保証にはなりません。
