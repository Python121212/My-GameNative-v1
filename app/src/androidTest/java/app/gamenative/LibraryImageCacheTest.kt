package app.gamenative

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.gamenative.utils.LibraryImageCache
import coil.ImageLoader
import coil.memory.MemoryCache
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryImageCacheTest {
    @Test fun hiddenLibraryReleasesOnlyMemoryCacheAndRotationRetainsIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cache = MemoryCache.Builder(context).maxSizeBytes(1024 * 1024).build()
        val loader = ImageLoader.Builder(context).memoryCache(cache).build()
        try {
            val key = MemoryCache.Key("library-cache-test")
            val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            cache[key] = MemoryCache.Value(bitmap)
            assertNotNull(cache[key])
            LibraryImageCache.onActivityStopped(loader, true)
            assertNotNull("構成変更時はキャッシュを保持", cache[key])
            LibraryImageCache.onActivityStopped(loader, false)
            assertNull("UI 非表示時はメモリキャッシュを解放", cache[key])
            assertFalse("既存の画像は破棄しない", bitmap.isRecycled)
            LibraryImageCache.onActivityStopped(null, false)
        } finally { loader.shutdown() }
    }
}
