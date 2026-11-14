package app.gamenative.utils

import coil.ImageLoader

/** UI が不可視の間に再生成可能なカバー画像のメモリキャッシュを解放する。 */
object LibraryImageCache {
    fun onActivityStopped(loader: ImageLoader?, changingConfigurations: Boolean) {
        // 画面回転などの構成変更では、直後の再描画に備えて従来通り保持する。
        if (!changingConfigurations) loader?.memoryCache?.clear()
    }
}
