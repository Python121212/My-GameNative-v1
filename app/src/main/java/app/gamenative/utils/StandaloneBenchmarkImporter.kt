package app.gamenative.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Copies a user-selected standalone EXE into a new, isolated custom-game folder. */
object StandaloneBenchmarkImporter {
    private const val MAX_BYTES = 128L * 1024 * 1024

    suspend fun importAndRegister(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: throw IOException("ファイル名を取得できません")
        val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("ファイルを開けません")
        val dir = stream.use { copyToNewFolder(File(CustomGameScanner.importRootPath), name, it) }
        // Once copied, keep the folder even if registration or launch fails; never delete user data.
        val item = CustomGameScanner.createLibraryItemFromFolder(dir.absolutePath)
            ?: throw IOException("ライブラリへ登録できません")
        if (item.gameSource != app.gamenative.data.GameSource.CUSTOM_GAME) throw IOException("テスト用フォルダとして登録できません")
        item.appId
    }

    /** Internal helper allows validation to be tested without accessing a document provider. */
    internal suspend fun copyToNewFolder(root: File, name: String, input: InputStream): File {
        require(name.endsWith(".exe", ignoreCase = true)) { "Windows の .exe を選択してください" }
        if (!root.isDirectory && !root.mkdirs()) throw IOException("保存先を作成できません")
        val dir = Files.createTempDirectory(root.toPath(), "Benchmark-").toFile()
        try {
            // Use only a safe base name, not paths provided by the document provider.
            val base = name.substringAfterLast('/').substringAfterLast('\\')
                .replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(120)
            val target = File(dir, base.ifEmpty { "benchmark.exe" })
            var count = 0L
            target.outputStream().use { out ->
                val buffer = ByteArray(65536)
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (n == 0) continue
                    count += n
                    if (count > MAX_BYTES) throw IOException("128 MiB を超えるファイルは選択できません")
                    out.write(buffer, 0, n)
                }
            }
            RandomAccessFile(target, "r").use { file ->
                if (file.length() < 64 || file.readUnsignedShort() != 0x4d5a) throw IOException("Windows の実行形式ではありません")
                file.seek(0x3c)
                val offset = Integer.reverseBytes(file.readInt()).toLong() and 0xffffffffL
                if (offset > file.length() - 6) throw IOException("実行ファイルが不完全です")
                file.seek(offset)
                if (file.readInt() != 0x50450000) throw IOException("PE 形式ではありません")
                val machine = java.lang.Short.reverseBytes(file.readShort()).toInt() and 0xffff
                if (machine != 0x14c && machine != 0x8664) throw IOException("x86 または x64 の EXE を選択してください")
            }
            return dir
        } catch (e: Throwable) {
            // This directory was created by this invocation and cannot contain existing games.
            dir.deleteRecursively()
            throw e
        }
    }
}
