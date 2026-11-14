package app.gamenative.utils

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FexCodeMapsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // Written by FEX 2611-gn-6 on a Retroid Pocket 6 for Steam's loader: 318 blocks, 4 libraries.
    private val fixtureName = "steamclient_loader_x64.exe-74ec33596a84c560.134384505043741006.bin"

    private fun fixture(): File {
        val bytes = javaClass.classLoader!!.getResourceAsStream("fexcodemaps/$fixtureName")!!.use { it.readBytes() }
        return tmp.newFile(fixtureName).apply { writeBytes(bytes) }
    }

    @Test
    fun `a pack supports caches when it ships the offline compiler`() {
        val pack = tmp.newFolder("2611-gn-6")
        assertFalse(FexCodeMaps.packSupportsCodeCaches(pack))
        assertFalse(FexCodeMaps.packSupportsCodeCaches(null))
        File(pack, "system32").mkdirs()
        File(pack, "system32/FEXOfflineCompiler64.exe").writeBytes(ByteArray(16))
        assertTrue(FexCodeMaps.packSupportsCodeCaches(pack))
    }

    @Test
    fun `reads a real map written by FEX`() {
        val info = FexCodeMaps.readInfo(fixture())!!
        assertEquals("steamclient_loader_x64.exe", info.mainExe)
        assertEquals("74ec33596a84c560", info.mainFileId)
        assertEquals(64, info.bitness)
        assertEquals(318, info.entries)
        assertEquals(4, info.libraries)
    }

    @Test
    fun `rejects an empty or truncated map`() {
        assertNull(FexCodeMaps.readInfo(tmp.newFile("winhandler.exe-48ac2ba1b68866ae.1.bin")))
        val full = fixture()
        val truncated = tmp.newFile("truncated.exe-74ec33596a84c560.2.bin")
        truncated.writeBytes(full.readBytes().copyOf(full.length().toInt() - 5))
        assertNull(FexCodeMaps.readInfo(truncated))
    }

    @Test
    fun `rejects a map without a main executable record`() {
        val buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        buf.putLong(1L).putInt(0x1000).putLong(1L).putInt(0x2000)
        val file = tmp.newFile("blocks.exe-0000000000000001.3.bin").apply { writeBytes(buf.array()) }
        assertNull(FexCodeMaps.readInfo(file))
    }

    @Test
    fun `reads a minimal 32-bit map and retains its unsigned block offset`() {
        val data = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(-1L).putInt(-18).putLong(0x1122334455667788L)
            .putLong(0x1122334455667788L).putInt(-2).array()
        val full = tmp.newFile("game32.exe-1122334455667788.1.bin").apply { writeBytes(data) }
        val info = FexCodeMaps.readInfo(full)!!
        assertEquals(32, info.bitness)
        assertEquals("1122334455667788", info.mainFileId)
        assertEquals(1, info.entries)
        val delta = tmp.newFile("game32.delta")
        assertEquals(1, FexCodeMaps.writeDelta(full, delta, emptySet()))
        assertTrue(data.contentEquals(delta.readBytes()))
    }

    @Test
    fun `rejects an unknown reserved record and a missing library terminator`() {
        val unknown = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(-1L).putInt(123).putLong(1L).putInt(3).array()
        val missingNul = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(-1L).putInt(-1).putLong(1L).putInt(0x41414141).array()
        assertNull(FexCodeMaps.readInfo(tmp.newFile("unknown.bin").apply { writeBytes(unknown) }))
        assertNull(FexCodeMaps.readInfo(tmp.newFile("bad-library.bin").apply { writeBytes(missingNul) }))
    }

    @Test
    fun `a directory named as compiler is not a compiler file`() {
        val pack = tmp.newFolder("fake-pack")
        assertTrue(File(pack, "system32/FEXOfflineCompiler64.exe").mkdirs())
        assertFalse(FexCodeMaps.packSupportsCodeCaches(pack))
    }

    @Test
    fun `a delta keeps the records and only the unsent blocks`() {
        val full = fixture()
        val first = tmp.newFile("first.exe-74ec33596a84c560.4.bin")
        assertEquals(318, FexCodeMaps.writeDelta(full, first, emptySet()))
        assertEquals(full.length(), first.length())

        // Pretend every block of the first map was sent: the next delta has the records but no blocks.
        val sent = HashSet<Long>()
        val buf = java.nio.ByteBuffer.wrap(full.readBytes()).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        while (buf.remaining() >= 12) {
            val fileId = buf.long
            val off = buf.int
            if (fileId == -1L) {
                buf.long
                if (off == -1) {
                    var end = buf.position()
                    while (buf.get(end) != 0.toByte()) end++
                    buf.position((end + 1 + 3) and 3.inv())
                }
            } else {
                sent.add(fileId * 31 + off.toLong().and(0xffffffffL))
            }
        }
        val second = tmp.newFile("second.exe-74ec33596a84c560.5.bin")
        assertEquals(0, FexCodeMaps.writeDelta(full, second, sent))
        assertEquals(full.length() - 318L * 12, second.length()) // records kept, every block gone
        assertNull(FexCodeMaps.readInfo(second)) // no blocks: not worth sending
    }

    @Test
    fun `blocks sent by one map are not sent again by an overlapping map in the same pass`() {
        val first = fixture()
        val sent = HashSet<Long>()
        val firstDelta = tmp.newFile("first.exe-74ec33596a84c560.6.bin")
        assertEquals(318, FexCodeMaps.writeDelta(first, firstDelta, sent))
        sent.addAll(FexCodeMaps.blockKeysOf(firstDelta)) // what a successful upload records

        // A second map from another process of the same session covering the same blocks.
        val overlapping = tmp.newFile("second.exe-74ec33596a84c560.7.bin").apply { writeBytes(first.readBytes()) }
        val secondDelta = tmp.newFile("second.delta")
        assertEquals(0, FexCodeMaps.writeDelta(overlapping, secondDelta, sent))
    }
}
