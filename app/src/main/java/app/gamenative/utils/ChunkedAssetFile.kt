package app.gamenative.utils

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/** 実験用の単一ファイル形式。Wine やゲームのファイルシステムには接続しない。 */
class ChunkedAssetFile private constructor(private val file: RandomAccessFile) : Closeable {
    private data class Block(val offset: Long, val compressed: Int, val size: Int, val crc: Int)
    private val blocks = ArrayList<Block>()
    private val cache = object : LinkedHashMap<Int, ByteArray>(3, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ByteArray>?): Boolean = size > 2
    }
    val originalSize: Long
    private val blockSize: Int

    init {
        if (file.readLong() != MAGIC) throw IOException("不正な形式")
        blockSize = file.readInt()
        originalSize = file.readLong()
        if (blockSize != BLOCK_SIZE || originalSize < 0 || originalSize > MAX_SIZE) throw IOException("不正なヘッダー")
        val count = ((originalSize + BLOCK_SIZE - 1) / BLOCK_SIZE).toInt()
        for (i in 0 until count) {
            val compressed = file.readInt()
            val size = file.readInt()
            val crc = file.readInt()
            val expected = minOf(BLOCK_SIZE.toLong(), originalSize - i.toLong() * BLOCK_SIZE).toInt()
            if (size != expected || compressed <= 0 || compressed > BLOCK_SIZE + 1024) throw IOException("不正なブロック")
            val offset = file.filePointer
            if (compressed.toLong() > file.length() - offset) throw IOException("データが途中で切れています")
            blocks.add(Block(offset, compressed, size, crc))
            file.seek(offset + compressed)
        }
        if (file.filePointer != file.length()) throw IOException("余分なデータがあります")
    }

    @Synchronized fun read(offset: Long, length: Int): ByteArray {
        require(offset >= 0 && length >= 0 && length <= MAX_READ && offset <= originalSize && length.toLong() <= originalSize - offset)
        val output = ByteArray(length)
        var cursor = offset
        var written = 0
        while (written < length) {
            val index = (cursor / BLOCK_SIZE).toInt()
            val block = cache[index] ?: load(index).also { cache[index] = it }
            val within = (cursor % BLOCK_SIZE).toInt()
            val amount = minOf(length - written, block.size - within)
            block.copyInto(output, written, within, within + amount)
            cursor += amount
            written += amount
        }
        return output
    }

    private fun load(index: Int): ByteArray {
        val block = blocks[index]
        val bytes = ByteArray(block.compressed)
        file.seek(block.offset)
        file.readFully(bytes)
        val output = ByteArrayOutputStream(block.size)
        InflaterInputStream(ByteArrayInputStream(bytes)).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n == -1) break
                if (output.size() + n > block.size) throw IOException("展開サイズが不正です")
                output.write(buffer, 0, n)
            }
        }
        val result = output.toByteArray()
        val crc = CRC32().apply { update(result) }.value.toInt()
        if (result.size != block.size || crc != block.crc) throw IOException("ブロックの検証に失敗")
        return result
    }

    /** メモリ不足時に再取得可能な展開済みブロックのみを捨てる。 */
    @Synchronized fun trimCache() { cache.clear() }

    override fun close() {
        synchronized(this) { cache.clear(); file.close() }
        synchronized(readers) { readers.remove(this) }
    }

    companion object {
        private const val MAGIC = 0x474e4348554e4b31L // GNCHUNK1
        const val BLOCK_SIZE = 256 * 1024
        const val MAX_READ = 4 * 1024 * 1024
        const val MAX_SIZE = 256L * 1024 * 1024 * 1024
        private val readers = java.util.WeakHashMap<ChunkedAssetFile, Boolean>()

        /** 稼働中の読み取り側のキャッシュだけを解放。swap/ZRAMは変更しない。 */
        fun trimOpenCaches() {
            val snapshot = synchronized(readers) { readers.keys.toList() }
            snapshot.forEach { it.trimCache() }
        }

        /** 入力は変更しない。出力は新規ファイルのみ、失敗時はこの呼び出しで作った出力だけ消す。 */
        fun create(source: File, target: File) {
            if (!source.isFile || source.length() > MAX_SIZE || source.canonicalFile == target.canonicalFile) throw IOException("入力ファイルが不正です")
            if (!target.createNewFile()) throw IOException("出力ファイルは既に存在します")
            try {
                val size = source.length()
                RandomAccessFile(target, "rw").use { out ->
                    out.writeLong(MAGIC)
                    out.writeInt(BLOCK_SIZE)
                    out.writeLong(size)
                    source.inputStream().buffered().use { input ->
                        val buf = ByteArray(BLOCK_SIZE)
                        var remaining = size
                        while (remaining > 0) {
                            val want = minOf(remaining, BLOCK_SIZE.toLong()).toInt()
                            var got = 0
                            while (got < want) {
                                val n = input.read(buf, got, want - got)
                                if (n < 0) throw IOException("入力ファイルが途中で変更されました")
                                got += n
                            }
                            val compressed = ByteArrayOutputStream()
                            DeflaterOutputStream(compressed).use { it.write(buf, 0, got) }
                            val payload = compressed.toByteArray()
                            out.writeInt(payload.size)
                            out.writeInt(got)
                            out.writeInt(CRC32().apply { update(buf, 0, got) }.value.toInt())
                            out.write(payload)
                            remaining -= got
                        }
                        if (input.read() != -1) throw IOException("入力ファイルが途中で変更されました")
                    }
                }
            } catch (e: Exception) {
                target.delete()
                throw e
            }
        }

        fun open(source: File): ChunkedAssetFile {
            val file = RandomAccessFile(source, "r")
            try {
                val reader = ChunkedAssetFile(file)
                synchronized(readers) { readers[reader] = true }
                return reader
            } catch (e: Exception) { file.close(); throw e }
        }
    }
}
