# 無料ゲームの固定デモ比較

## ゲームと方法

無料の [Freedoom 0.13.0](https://github.com/freedoom/freedoom/releases/tag/v0.13.0) Phase 2 と、オープンソースの [Chocolate Doom 3.1.1](https://github.com/chocolate-doom/chocolate-doom/releases/tag/chocolate-doom-3.1.1) Windows x86-64 版を使い、付属の `DEMO1`（MAP03、単一プレイヤー）を `-timedemo` で再生した。ゲームと WAD の公式配布物の SHA-256 を固定して検証し、DXVK 2.6.1 の配布物はバージョン固定・ハッシュ記録を行った（DXVK のハッシュは独立した公式チェックサムとの照合はしていない）。外部取得物は runner の一時領域だけに置き、既存 APK・ゲームデータには触れていない。

Chocolate Doom はゲーム画面を主に CPU で描き、SDL2 の Direct3D 9 レンダラーで画面表示する。Direct3D 9 の実ゲーム表示経路の限定的な比較であって、Direct3D 11 や GPU の3Dゲーム負荷の比較ではない。

x86-64 Linux・Wine、ソフトウェア Vulkan lavapipe／OpenGL llvmpipe、Xorg dummy 画面を使った。各方式に独立した一時 Wine prefix を用意し、ウォームアップを1回ずつ行い、Wine の DLL ロードログで DXVK の native `d3d9.dll` と WineD3D の builtin `d3d9.dll` を確認した。SDL が要求した `direct3d`（D3D9）を選んだことを各回確認し、フォールバックは失敗とした。試行は DXVK→WineD3D と WineD3D→DXVK を交互に入れ替え、各4回行った。

画面検証のため同じ SDL2 診断用プロキシを両方式で使い、120回目の `SDL_RenderPresent` 直前に画面全体を読み戻した。各回のサイズと、32色以上あることを検査し、基準フレームに対する RGB の平均絶対差が1以下、差が8を超える成分が0.5%以下であることを確認する。検査用の読み戻し1回は測定時間に含まれる。プロキシはベンチマーク用であり、ゲームや GameNative の既存インストールには適用しない。画像一致はこのフレームに限り、全フレームの描画、正解画像との一致やすべての効果の正確性を保証しない。実行 43547232661 の縮小画像はゲーム内の部屋・武器・HUD が表示されていることを目視した。

## 結果

[実行 43547232661](https://github.com/Python121212/My-GameNative-v1/actions/runs/43547232661) は両方式で同じ 2664 gametics のデモを完走し、固定フレームの RGB は全8回で完全一致した。timedemo は終了時に意図的に `I_Error` を使うため終了コード255を返す。結果行・コード・フレーム・DLL を検証し、他の異常終了とは区別した。

| 方式 | 1回目 | 2回目 | 3回目 | 4回目 | 平均 |
|---|---:|---:|---:|---:|---:|
| DXVK | 200.948 | 201.382 | 201.382 | 201.818 | 201.383 |
| WineD3D | 262.648 | 262.648 | 263.390 | 263.390 | 263.019 |

値はゲーム内 timedemo の FPS（gametics と35 Hz 単位の realtics から算出）で、ディスプレイの実表示 FPS やフレーム時間分布ではない。各回の正確な値、環境、チェックサム、ログ、元サイズの画像は Actions の `freedoom-comparison` 成果物に保存され、保存期間は14日。固定フレームの RGB SHA-256 は `2342af2ac6da4d8b3048b9268e4ec6a58c73c4215076824656640d4c8c124cbe`。

このソフトウェア環境のこのゲームでは WineD3D の平均が高かったが、一般的な優劣、Pixel 9a・GameNative・実 GPU での性能や省電力を示さない。別の共有 runner の [実行 43546286188](https://github.com/Python121212/My-GameNative-v1/actions/runs/43546286188) は DXVK 平均135.533、WineD3D 平均168.087 で、runner 間の数値の大きな差があり、異なる実行の絶対値を直接比較できない。FEX／Box64 は使っておらず、温度・共有 GPU メモリ・実機描画は未計測。WineD3D のデフォルトを変更していない。
