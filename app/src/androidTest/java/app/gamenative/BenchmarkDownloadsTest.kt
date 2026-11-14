package app.gamenative

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.gamenative.utils.BenchmarkDownload
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BenchmarkDownloadsTest {
    @Test fun pinnedBrowserLinksAndDescriptionsAreAvailable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (item in BenchmarkDownload.values()) {
            val intent = item.browserIntent()
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
            assertEquals("https", intent.data?.scheme)
            assertEquals("github.com", intent.data?.host)
            assertTrue(intent.data?.path?.contains("/releases/download/") == true)
        }
        assertTrue(context.getString(R.string.benchmark_cpu_detail).contains("7-Zip"))
        assertTrue(context.getString(R.string.benchmark_graphics_detail).contains("DX11"))
    }
}
