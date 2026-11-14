package app.gamenative.utils

import android.app.ActivityManager
import android.content.Context
import java.io.File

/** 読み取りのみ。swapon、ZRAM、システム設定、ゲームプロセスを変更しない。 */
object MemoryStatus {
    fun read(context: Context): String {
        val info = ActivityManager.MemoryInfo()
        val memory = runCatching {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            manager.getMemoryInfo(info)
            "空きメモリ: ${info.availMem / 1048576} MiB / 合計 ${info.totalMem / 1048576} MiB\nメモリ不足状態: ${if (info.lowMemory) "はい" else "いいえ"}"
        }.getOrDefault("メモリ状態: 取得不可")
        val entries = runCatching {
            File("/proc/meminfo").useLines { lines ->
                lines.mapNotNull { line ->
                    val match = Regex("^(SwapTotal|SwapFree):\\s+(\\d+)\\s+kB\\s*$").matchEntire(line)
                    match?.groupValues?.let { it[1] to it[2].toLongOrNull() }
                }.toMap()
            }
        }.getOrDefault(emptyMap())
        val total = entries["SwapTotal"]
        val free = entries["SwapFree"]
        val swap = if (total != null && free != null && free <= total) {
            "スワップ合計: ${total / 1024} MiB / 使用中: ${(total - free) / 1024} MiB"
        } else "スワップ状態: 取得不可"
        return "$memory\n$swap\n合計には ZRAM 以外を含む可能性があります。アプリはシステムのスワップや ZRAM を拡張・変更していません。"
    }
}
