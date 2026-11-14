package app.gamenative

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.gamenative.utils.FexCodeMaps
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Android 上の GameNative 側マップ判定だけを検査する。FEXCore DLL は実行しない。 */
@RunWith(AndroidJUnit4::class)
class FexCoreIntegrationSmokeTest {
    @Test
    fun codeMapParserAndPackGate() {
        val base = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "fex-gate-test-" + System.nanoTime())
        assertTrue(base.mkdirs())
        try {
            assertFalse(FexCodeMaps.packSupportsCodeCaches(base))
            val compiler = File(base, "system32/FEXOfflineCompiler64.exe")
            assertTrue(compiler.parentFile!!.mkdirs())
            compiler.writeBytes(byteArrayOf(0x4d, 0x5a))
            assertTrue(FexCodeMaps.packSupportsCodeCaches(base))

            val data = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
                .putLong(-1L).putInt(-17).putLong(0x1234L)
                .putLong(0x1234L).putInt(0x20).array()
            val map = File(base, "sample.exe-0000000000001234.1.bin").apply { writeBytes(data) }
            val info = FexCodeMaps.readInfo(map)!!
            assertEquals("sample.exe", info.mainExe)
            assertEquals(64, info.bitness)
            assertEquals(1, info.entries)
            map.writeBytes(data.copyOf(data.size - 1))
            assertNull(FexCodeMaps.readInfo(map))
        } finally {
            // 自分のテスト専用ディレクトリのみを削除する。ゲームデータには触れない。
            base.deleteRecursively()
        }
    }
}
