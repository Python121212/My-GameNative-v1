# Direct3D 10 の基本動作確認

[実行 43560874159](https://github.com/Python121212/My-GameNative-v1/actions/runs/43560874159) で、Windows x86-64 向けの小さな Direct3D 10 テストプログラムを Wine 上で実行した。DXVK 2.6.1 と WineD3D を隔離された別々の prefix／runner でそれぞれ4回検査した。外部ゲームではなく合成試験であり、FPS の測定や性能比較は行っていない。

| 方式 | 4回の結果 | 実際にロードされたライブラリ |
|---|---|---|
| DXVK | 4回とも成功 | DXVK `d3d10core.dll`・`d3d11.dll`・`dxgi.dll`（native） |
| WineD3D | 4回とも成功 | Wine の同名 DLL（builtin） |

各回で `D3D10CreateDeviceAndSwapChain`、頂点・ピクセルシェーダーのコンパイルと生成、頂点バッファによる赤い三角形の描画、背景のクリア、8回の `Present` の成功を確認した。最初と最後のフレームは Present 前のバックバッファを読み戻し、三角形内の赤いピクセルと背景の黒いピクセルを検査した。Present の戻り値を確認したが、画面に最終的に表示された内容のスクリーンショットは検査していない。描画全体やすべての API の正確性を証明するものではない。

環境は GitHub Actions の x86-64 Linux、Wine、Xorg dummy、DXVK 側は Vulkan lavapipe、WineD3D 側は OpenGL llvmpipe のソフトウェア描画。**Android／GameNative／Pixel 9a 実機での Direct3D 10 動作は未確認**。実際の D3D10 ゲーム、テクスチャ、複雑なシェーダー、リサイズや互換性・速度・発熱は検査していない。

テストは `tools/direct3d10-test/` と `.github/workflows/direct3d10-smoke.yml` にある。既存の Wine prefix、APK、ゲームデータ、独自互換レイヤーは変更していない。ログは各実行の成果物に14日間保存する。
