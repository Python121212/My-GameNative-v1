package app.gamenative

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.gamenative.utils.StandaloneBenchmarkImporter
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StandaloneBenchmarkImporterTest {
    @Test fun standaloneCopyRejectsInvalidInputsAndNeverOverwrites() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "benchmark-copy-${System.nanoTime()}")
        assertTrue(root.mkdir())
        try {
            val source = ByteArray(128)
            source[0] = 'M'.code.toByte(); source[1] = 'Z'.code.toByte()
            source[0x3c] = 64
            source[64] = 'P'.code.toByte(); source[65] = 'E'.code.toByte()
            source[68] = 0x64.toByte(); source[69] = 0x86.toByte()
            val first = StandaloneBenchmarkImporter.copyToNewFolder(root, "../demo.exe", ByteArrayInputStream(source))
            val second = StandaloneBenchmarkImporter.copyToNewFolder(root, "demo.exe", ByteArrayInputStream(source))
            assertNotEquals(first, second)
            assertArrayEquals(source, File(first, "demo.exe").readBytes())
            assertArrayEquals(source, File(second, "demo.exe").readBytes())
            assertTrue(runCatching { StandaloneBenchmarkImporter.copyToNewFolder(root, "x.exe", ByteArrayInputStream(byteArrayOf(1))) }.isFailure)
            assertTrue(runCatching { StandaloneBenchmarkImporter.copyToNewFolder(root, "x.apk", ByteArrayInputStream(source)) }.isFailure)
            assertEquals(2, root.listFiles()?.size)
        } finally { root.deleteRecursively() }
    }
}
