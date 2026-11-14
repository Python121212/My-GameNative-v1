package app.gamenative.utils

import android.content.Context
import java.io.File
import java.io.IOException
import kotlin.random.Random

/** 自動生成したデータだけを使い、ゲームファイルには触れない。 */
object CompressionSelfTest {
    fun run(context: Context): String {
        val base = File(context.cacheDir, "compression-test-${java.util.UUID.randomUUID()}")
        if (!base.mkdir()) throw IOException("試験用ディレクトリを作成できません")
        try {
            val data = ByteArray(700_123) { i -> ((i / 4096 + i % 97) % 251).toByte() }
            val source = File(base, "input.bin")
            val packed = File(base, "test.gnc")
            source.writeBytes(data)
            val start = System.nanoTime()
            ChunkedAssetFile.create(source, packed)
            ChunkedAssetFile.open(packed).use { reader ->
                check(reader.originalSize == data.size.toLong())
                check(reader.read(0, 0).isEmpty())
                val random = Random(19)
                repeat(50) {
                    val offset = random.nextInt(data.size - 4096)
                    check(reader.read(offset.toLong(), 4096).contentEquals(data.copyOfRange(offset, offset + 4096)))
                }
                val offset = ChunkedAssetFile.BLOCK_SIZE - 13
                check(reader.read(offset.toLong(), 64).contentEquals(data.copyOfRange(offset, offset + 64)))
            }
            val ms = (System.nanoTime() - start) / 1_000_000
            return "自動生成データ: ${data.size} バイト → ${packed.length()} バイト。50回の部分読み込みと境界を照合、処理時間 ${ms} ms。ゲームの圧縮率や性能ではありません。"
        } finally {
            // この関数で新規作成した試験用ディレクトリのみ。
            base.deleteRecursively()
        }
    }
}
