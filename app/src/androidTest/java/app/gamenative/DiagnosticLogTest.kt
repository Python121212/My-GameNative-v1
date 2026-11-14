package app.gamenative

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.gamenative.utils.DiagnosticLog
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticLogTest {
    @Test fun recordsFourTimestampedCategoriesAndExports() {
        val original = InstrumentationRegistry.getInstrumentation().targetContext
        val base = File(original.cacheDir, "diagnostic-test-${System.nanoTime()}")
        assertTrue(base.mkdirs())
        val context = object : ContextWrapper(original) { override fun getFilesDir(): File = base }
        val old = DiagnosticLog.enabled(context)
        var sessionId: String? = null
        try {
            DiagnosticLog.setEnabled(context, false)
            DiagnosticLog.start(context)
            val empty = ByteArrayOutputStream()
            assertFalse(DiagnosticLog.exportLatest(context, empty))
            DiagnosticLog.setEnabled(context, true)
            sessionId = DiagnosticLog.start(context)
            assertNotNull(sessionId)
            assertFalse(DiagnosticLog.deleteRecords(context))
            DiagnosticLog.sample(context, 42.5f)
            DiagnosticLog.event(DiagnosticLog.LaunchEvent.ENV_READY)
            DiagnosticLog.stop(sessionId!!)
            val bytes = ByteArrayOutputStream()
            assertTrue(DiagnosticLog.exportLatest(context, bytes))
            val entries = mutableMapOf<String, String>()
            ZipInputStream(bytes.toByteArray().inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                }
            }
            assertEquals(setOf("temperature.txt", "fps.txt", "network.txt", "launch.txt"), entries.keys)
            val timestamp = Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} (?:Z|[+-]\\d{2}:\\d{2}) ")
            entries.values.forEach { text ->
                assertTrue(text.isNotBlank())
                text.lineSequence().filter { it.isNotBlank() }.forEach { assertTrue(timestamp.containsMatchIn(it)) }
            }
            assertTrue(entries.getValue("fps.txt").contains("42.5"))
            assertTrue(entries.getValue("launch.txt").contains("起動環境の初期化完了"))
            assertFalse(entries.getValue("network.txt").contains("http"))
            assertTrue(DiagnosticLog.deleteRecords(context))
            assertFalse(DiagnosticLog.exportLatest(context, ByteArrayOutputStream()))
        } finally {
            sessionId?.let { DiagnosticLog.stop(it) }
            DiagnosticLog.setEnabled(context, old)
            base.deleteRecursively()
        }
    }
}
