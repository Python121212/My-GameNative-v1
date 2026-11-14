package app.gamenative

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.gamenative.utils.ChunkedAssetFile
import app.gamenative.utils.CompressionSelfTest
import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChunkedAssetFileTest {
    @Test fun randomAccessAndDamageAreCheckedWithoutOverwritingOriginal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(CompressionSelfTest.run(context).contains("照合"))
        val dir = File(context.cacheDir, "chunk-test-${System.nanoTime()}")
        assertTrue(dir.mkdir())
        try {
            val input = File(dir, "source")
            val packed = File(dir, "packed")
            val contents = ByteArray(300_000) { (it % 127).toByte() }
            input.writeBytes(contents)
            ChunkedAssetFile.create(input, packed)
            assertThrows(Exception::class.java) { ChunkedAssetFile.create(input, packed) }
            assertArrayEquals(contents, input.readBytes())
            ChunkedAssetFile.open(packed).use { file ->
                assertArrayEquals(contents.copyOfRange(262140, 262160), file.read(262140, 20))
                assertThrows(IllegalArgumentException::class.java) { file.read(-1, 3) }
                assertThrows(IllegalArgumentException::class.java) { file.read(299999, 2) }
            }
            RandomAccessFile(packed, "rw").use { file ->
                file.seek(28) // 先頭ブロックの CRC を破損させ、入力は変更しない。
                val value = file.readInt()
                file.seek(28)
                file.writeInt(value xor 1)
            }
            ChunkedAssetFile.open(packed).use { assertThrows(Exception::class.java) { it.read(0, 1) } }
            RandomAccessFile(packed, "rw").use { file -> file.setLength(file.length() - 3) }
            assertThrows(Exception::class.java) { ChunkedAssetFile.open(packed) }
            val empty = File(dir, "empty").apply { writeBytes(byteArrayOf()) }
            val emptyPacked = File(dir, "empty-packed")
            ChunkedAssetFile.create(empty, emptyPacked)
            ChunkedAssetFile.open(emptyPacked).use { assertEquals(0L, it.originalSize); assertTrue(it.read(0, 0).isEmpty()) }
        } finally { dir.deleteRecursively() }
    }
}
