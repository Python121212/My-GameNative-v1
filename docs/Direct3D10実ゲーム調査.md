# Direct3D 10 実ゲームの計測準備

## 現状

**実ゲームの FPS 計測は未実施。** DXVK と WineD3D の各4回比較、同一場面の画面検査、実行時の DLL 確認も未実施。`Direct3D10動作確認.md` の FPS は合成ベンチマークであり、ゲームの値ではない。

## 候補: Death Leak

- [ゲーム作者のソース](https://github.com/jeromebyrne/Death_Leak/tree/2aa098fc77609047e025113b06efed30a38e020b) は 2D アクションゲームで DirectX 10.1 と説明し、[Steam](https://store.steampowered.com/app/995990/) では無料。Steam ページの最低要件は DirectX 11 と表示されているので、実際の配布版の使用 API は実行時にも確認する必要がある。
- 上記ソースの `Game/src/Core/Graphics.cpp` は `D3D10CreateDeviceAndSwapChain` を呼び、`IDXGISwapChain::Present` を使用している。ソース上の根拠であり、配布版の DLL 読み込みや描画成功の証拠ではない。
- ゲームループは `Game/src/program.cpp` で目標を 60 更新/秒に設定している。無改造の通常ゲーム FPS は上限の影響を受ける。上限解除して比較する場合、ゲーム速度・再現性が変化し得るため、通常動作とは別に明記する。
- GitHub リポジトリは MIT ライセンスを掲載しているが、同梱の irrKlang、Steamworks SDK、アセットなど第三者要素には別条件があり得る。ゲームや同梱物をこのリポジトリに再配布していない。
- リポジトリにはゲームの実行ファイルが見当たらず、ソースの Visual Studio プロジェクトは旧 DirectX SDK、irrKlang、Steamworks などに依存する。再ビルド・起動は未検証。

## 取得テスト

GitHub Actions [43562275855](https://github.com/Python121212/My-GameNative-v1/actions/runs/43562275855) で公式 SteamCMD を準備し、無料ゲームの匿名取得を試みた。準備ステップは成功したが取得確認ステップは失敗した。実行ログのダウンロードがこの環境で EOF となり、失敗理由やゲームファイルの取得状況は確認できていない。匿名取得が権限上不可能と断定しない。

その後、Windows 版を指定して再試行し、[43562758317](https://github.com/Python121212/My-GameNative-v1/actions/runs/43562758317) の診断ログを確認した。SteamCMD は匿名ログインに成功したが、アプリ 995990 のインストールは `ERROR! Failed to install app '995990' (Missing configuration)` で失敗した。この方法ではゲームを取得できず、原因は未確定。SteamCMD の起動成功をゲーム取得成功と扱わない。

次は正規の別の入手方法またはソースのビルドを検証する。実行可能になってから、同じゲームシーン、解像度、設定、時間とフレーム制限で各4回測定し、画像と D3D10・DXVK/WineD3D の DLL 読み込みを検査する。ソフトウェア描画の Linux/Wine 結果を Android や Pixel 9a 実機の性能として扱わない。

## 除外・保留

- [GameLibrary_DirectX10](https://github.com/TD0419/GameLibrary_DirectX10/tree/e18ba48802fcc48f441b40098e3cd1c67faed793) には `d3d10.dll` をインポートする 32-bit 実行ファイルとアセットがある。しかし作者の文書ではゲームライブラリの動作を示す操作可能なサンプルであり、独立したゲーム本編として扱わない。実行ファイルはデバッグ版 Visual C++ ランタイムにも依存し、起動・描画は未検証。明示的ライセンスも見当たらず、同梱・再配布していない。
- [Death Run](https://github.com/nmpeartree/deathrun/tree/6e0c7170d97fdef2c9ba2ffe805b3483680451ee) はリポジトリの説明に DirectX10 とあるが、実行ファイルの静的インポートは `DDRAW.dll` 等であり、D3D10 ゲームと確認できなかったため除外。
- [GarbageEngine](https://github.com/sadegh-taghavi/GarbageEngine/tree/6c7b0b8bb4c50a48be18f3a42c1e0f6d12002acd) に同梱の GravitonBall は無料だが、現行リポジトリは DirectX 11 と説明し、ソースにも D3D11 呼び出しがある。配布済みゲームが D3D10 か確認できておらず、採用しない。
- Call of Juarez DirectX 10 Benchmark はゲーム本編ではなく独立ベンチマークデモであり、今回の実ゲーム FPS の代用としない。
