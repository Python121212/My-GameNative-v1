package app.gamenative.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** ユーザーが有効にしたゲーム画面の観測だけを、アプリ内に保存する。 */
object DiagnosticLog {
    private const val PREFS = "diagnostic_log"
    private const val LIMIT = 1024 * 1024L
    private val names = listOf("temperature.txt", "fps.txt", "network.txt", "launch.txt")
    private var session: File? = null

    fun enabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", false)
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("enabled", enabled).apply()
    }

    private fun stamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss XXX", Locale.ROOT).format(Date())
    private fun root(context: Context) = File(context.filesDir, "diagnostic-records")

    @Synchronized fun start(context: Context) {
        if (!enabled(context)) return
        try {
            val parent = root(context)
            if (!parent.exists() && !parent.mkdirs()) return
            val dir = File(parent, "session-${System.currentTimeMillis()}-${java.util.UUID.randomUUID()}")
            if (!dir.mkdir()) return
            session = dir
            names.forEach { append(it, "記録開始") }
            append("launch.txt", "ゲーム画面を開きました。ゲームの起動成功は未確認")
        } catch (_: Exception) { session = null }
    }

    @Synchronized private fun append(name: String, message: String) {
        val dir = session ?: return
        if (name !in names) return
        try {
            val file = File(dir, name)
            val line = "${stamp()} $message\n"
            if (file.length() + line.toByteArray(Charsets.UTF_8).size <= LIMIT) file.appendText(line)
        } catch (_: Exception) { /* 記録失敗でゲームを止めない */ }
    }

    /** 固定のイベント名のみ受け付ける。エラー本文やパスを保存しない。 */
    fun event(event: LaunchEvent) = append("launch.txt", event.label)
    enum class LaunchEvent(val label: String) {
        ENV_READY("起動環境の初期化完了。ゲーム起動成功は未確認"),
        SETUP_FAILED("起動環境の初期化失敗"),
        GUEST_EXIT_OK("ゲストプロセス終了: ステータス0"),
        GUEST_EXIT_ERROR("ゲストプロセス異常終了。詳細は未記録"),
    }

    fun sample(context: Context, fps: Float?) {
        try {
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val raw = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
            val temp = if (raw in -400..1000) "${raw / 10.0} °C" else "取得不可"
            append("temperature.txt", "バッテリー温度=$temp（CPU/GPU温度ではありません）")
        } catch (_: Exception) { append("temperature.txt", "バッテリー温度=取得不可") }
        val fpsText = fps?.takeIf { it.isFinite() && it >= 0 }?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "取得不可"
        append("fps.txt", "表示フレーム推定FPS=$fpsText（ゲーム内部FPSとは限りません）")
        try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = manager.getNetworkCapabilities(manager.activeNetwork)
            val transport = when {
                caps == null -> "接続なしまたは取得不可"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "モバイル"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "有線"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                else -> "その他"
            }
            val verified = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            append("network.txt", "Androidの接続=$transport インターネット検証済み=$verified（Steam接続の成否ではありません）")
        } catch (_: Exception) { append("network.txt", "Androidの接続=取得不可") }
    }

    @Synchronized fun stop() {
        append("launch.txt", "ゲーム画面の記録終了。正常終了を保証しません")
        session = null
    }

    /** 最新セッションの4種類のみを出力。外部への送信はしない。 */
    @Synchronized fun exportLatest(context: Context, output: OutputStream): Boolean {
        val dir = root(context).listFiles()?.filter { it.isDirectory && it.name.startsWith("session-") }
            ?.maxByOrNull { it.name.substringAfter("session-").substringBefore('-').toLongOrNull() ?: 0L }
            ?: return false
        ZipOutputStream(output).use { zip ->
            for (name in names) {
                val file = File(dir, name)
                zip.putNextEntry(ZipEntry(name))
                if (file.isFile) file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return true
    }
}
