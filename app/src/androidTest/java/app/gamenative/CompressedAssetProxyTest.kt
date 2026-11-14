package app.gamenative

import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.gamenative.utils.ChunkedAssetFile
import app.gamenative.utils.CompressedAssetProxy
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompressedAssetProxyTest {
    @Test fun proxySupportsNativePreadAndReadOnlyAndCacheTrim() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "proxy-test-${System.nanoTime()}")
        assertTrue(dir.mkdir())
        try {
            val bytes = ByteArray(300_001) { (it % 251).toByte() }
            val source = File(dir, "source").apply { writeBytes(bytes) }
            val packed = File(dir, "packed")
            ChunkedAssetFile.create(source, packed)
            CompressedAssetProxy.open(context, packed).use { proxy ->
                val fd = proxy.descriptor.fileDescriptor
                assertEquals(bytes.size.toLong(), Os.fstat(fd).st_size)
                val buffer = ByteArray(64)
                assertEquals(64, Os.pread(fd, buffer, 0, buffer.size, 262_130))
                assertArrayEquals(bytes.copyOfRange(262_130, 262_194), buffer)
                ChunkedAssetFile.trimOpenCaches()
                assertEquals(1, Os.pread(fd, buffer, 0, 1, bytes.size.toLong() - 1))
                assertEquals(bytes.last(), buffer[0])
                assertEquals(0, Os.pread(fd, buffer, 0, 1, bytes.size.toLong()))
                assertTrue(runCatching { Os.write(fd, byteArrayOf(1), 0, 1) }.isFailure)
                java.io.FileInputStream(Os.dup(fd)).use { input ->
                    val mapped = input.channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, bytes.size.toLong())
                    assertEquals(bytes[262_143], mapped.get(262_143))
                    assertEquals(bytes[262_144], mapped.get(262_144))
                    assertEquals(bytes.last(), mapped.get(bytes.lastIndex))
                }
            }
            assertArrayEquals(bytes, source.readBytes())
        } finally { dir.deleteRecursively() }
    }
}
