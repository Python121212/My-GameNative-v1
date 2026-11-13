<div align="center">

# GameNative

**Steam、Epic、GOG で所有している PC ゲームを、クラウドセーブとともに Android 端末でプレイできます。**

<a href="https://trendshift.io/repositories/14497" target="_blank"><img src="https://trendshift.io/api/badge/repositories/14497" alt="utkarshdalal%2FGameNative | Trendshift" style="width: 250px; height: 55px;" width="250" height="55"/></a>

<a href="https://www.star-history.com/utkarshdalal/gamenative">
 <picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/badge?repo=utkarshdalal/GameNative&theme=dark" />
  <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/badge?repo=utkarshdalal/GameNative" />
  <img alt="Star History Rank" src="https://api.star-history.com/badge?repo=utkarshdalal/GameNative" />
 </picture>
</a>

[![GitHub Release](https://img.shields.io/github/v/release/utkarshdalal/GameNative?style=flat-square&logo=github&label=latest)](https://github.com/utkarshdalal/GameNative/releases/latest)
[![GitHub stars](https://img.shields.io/github/stars/utkarshdalal/GameNative?style=flat-square&logo=github&color=ffd700)](https://github.com/utkarshdalal/GameNative/stargazers)
[![Discord](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fdiscord.com%2Fapi%2Fv9%2Finvites%2F2hKv4VfZfE%3Fwith_counts%3Dtrue&query=%24.approximate_member_count&style=flat-square&logo=discord&logoColor=white&label=discord&color=5865F2&suffix=%20members)](https://discord.gg/2hKv4VfZfE)
[![License](https://img.shields.io/badge/license-GPL%203.0-blue?style=flat-square)](https://github.com/utkarshdalal/GameNative/blob/master/LICENSE)
[![Ko-fi](https://img.shields.io/badge/ko--fi-support-FF5E5B?style=flat-square&logo=ko-fi&logoColor=white)](https://ko-fi.com/gamenative)

[**ダウンロード**](https://github.com/utkarshdalal/GameNative/releases/download/v1.4.0/gamenative-v1.4.0.apk) · [**Discord**](https://discord.gg/2hKv4VfZfE) · [**Ko-fi で支援**](https://ko-fi.com/gamenative)

<video src="https://github.com/user-attachments/assets/95b5397b-908a-44ef-a10a-dac7723580b0" autoplay loop muted playsinline width="100%"></video>

</div>

---

GameNative は Steam、Epic、GOG のライブラリにある PC ゲームをストリーミングなしで Android 上で直接実行できます。セーブデータはクラウドに同期されるため、PC で中断した続きをスマートフォンで遊べます。

開発はまだ初期段階です。すべてのゲームが動作するわけではなく、快適に遊ぶには調整が必要なものもあります。コミュニティが動作する設定を見つけて共有しており、それらは自動的に適用されます。ゲームの動作報告は https://gamenative.app/compatibility で確認できます。

## 主な機能

- Steam、Epic、GOG、Amazon で実際に所有しているゲームのプレイ
- PC とスマートフォン間で引き継げるクラウドセーブ
- 動作確認済みの設定を自動適用し、多くのゲームを調整なしでプレイ
- コントローラーとタッチ操作、カスタム操作エディター、画面上の HUD に対応
- Steam の DLC、ワークショップ、ブランチに対応
- ゲームの起動に困ったときは Discord でサポート

## デモ

[TechDweeb](https://www.youtube.com/@TechDweeb) が Android 携帯ゲーム機での GameNative の設定方法を数分で紹介しています。

<div align="center">

<a href="https://youtu.be/QqIChmAu2_A?si=Ha6xzTQXZA2H8HUN&t=53" target="_blank"><img src="https://github.com/user-attachments/assets/6957e3a1-34ac-41f5-b558-0f1868dbf3d4" alt="Youtube Video" /></a>

</div>

## 使い方

1. [こちら](https://downloads.gamenative.app/releases/1.4.0/gamenative-v1.4.0.apk)から最新リリースをダウンロードします
2. Android 端末に APK をインストールします
3. Steam アカウントにログインします
4. ゲームをインストールします
5. プレイを押して楽しみましょう

## サポート

サポートを受ける最も速い方法は [Discord サーバー](https://discord.gg/2hKv4VfZfE)です。3万5千人以上が参加しており、通常は誰かが対応できます。

GitHub の Issue は自動的にクローズされるため、**作成せず**、Discord で相談してください。

プロジェクトを支援したい場合は [Ko-fi](https://ko-fi.com/gamenative) をご利用ください。

## 貢献

貢献したい場合は、[Discord](https://discord.gg/2hKv4VfZfE) の **#development** チャンネルに参加できるよう連絡するか、そこでスレッドを作成してください。現在募集している作業は [Trello ボード](https://trello.com/b/vGRkFoAM/open-source-board)に掲載されています。

### 動作しないゲームの修正

実際の利用データに基づき、GameNative で動作しないゲームの一覧を毎晩 [GameNative/gamenative-compat](https://github.com/GameNative/gamenative-compat) に公開しています。各ゲームの[未解決 Issue](https://github.com/GameNative/gamenative-compat/issues?q=is%3Aopen+label%3Adoes-not-work) には、影響を受けたプレイヤー数、報告内容、ゲーム画面が描画されたかどうかなどが記載されています。

修正を担当する場合：

1. 一覧からゲームを選んで Issue を開き、他の人が取り組んでいないかコメントを確認してから、担当する旨をコメントします。
2. 動作しない原因を調べます。修正先は主にこのリポジトリ、[proton-wine](https://github.com/GameNative/proton-wine)、[FEX](https://github.com/GameNative/FEX)、GPU ドライバーラッパー、またはゲームごとの設定です。
3. 適切なリポジトリにプルリクエストを作成し、ゲームの Issue にそのリンクをコメントします。修正がリリースされてプレイヤーがゲームを起動できるようになると、Issue は自動的にクローズされます。

動作しないゲームの修正のためにキーを提供したい場合は、[support@gamenative.app](mailto:support@gamenative.app) にメールしてください。

### ビルド

通常、遊ぶだけなら上記のリリースを入手すれば十分です。この手順は貢献者向けです。

1. 通常の Android Studio プロジェクトと同様にビルドします。困ったら Discord で質問してください。
2. **SteamGridDB API キー（任意）：** カスタムゲームの画像を取得するには、`local.properties` にキーを追加します。
   ```properties
   STEAMGRIDDB_API_KEY=your_api_key_here
   ```
   キーは [SteamGridDB の設定](https://www.steamgriddb.com/profile/preferences)から取得できます。キーがなくても画像の取得以外は動作します。

## 分析とプライバシー

GameNative は匿名の分析に [PostHog](https://posthog.com) を使用します。名前、メールアドレス、IP アドレス、端末識別子などの個人情報は収集しません。

ゲームの互換性を改善するために**常に収集される情報**：
- ゲームの起動・終了イベント（ゲーム名、ストア、セッション時間、平均 FPS、コンテナ設定）
- ゲームのインストール、キャンセル、アンインストールのイベント

これにより、動作するゲーム、その動作品質、次の利用者に自動適用する設定を判断します。個人を特定することはできません。

**任意の情報**は *設定 → 情報 → 利用状況の分析* で切り替えられます。
- 機能の利用状況（画面キーボード、コントローラー、HUD、操作エディター）
- ログインの成功・失敗イベント
- おすすめ機能への操作
- アプリのライフサイクルイベント（フォアグラウンド／バックグラウンド）
- クラウド同期イベント

詳細は[プライバシーポリシー](PrivacyPolicy/README.md)をご覧ください。

## 支援者

[CodeRabbit](https://coderabbit.link/gnative) をはじめとする [Ko-fi スポンサー](https://ko-fi.com/gamenative)と [GitHub スポンサー](https://github.com/sponsors/utkarshdalal?preview=true)の皆さまに感謝します。

[![Star History Chart](https://star-history.dera.page/svg?repos=utkarshdalal/GameNative&type=Date&theme=dark)](https://star-history.dera.page/#utkarshdalal/GameNative&Date)

## ライセンス

[GPL 3.0](https://github.com/utkarshdalal/GameNative/blob/master/LICENSE).

アプリに同梱されたサードパーティ製・プロプライエタリなコンポーネントの帰属表示、コピーレフトのソース提供、および通知については [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES) を参照してください。

---

**免責事項：** このソフトウェアは、合法的に所有しているゲームをプレイするためのものです。海賊行為やその他の違法行為に使用しないでください。不正使用についてメンテナーは責任を負いません。
