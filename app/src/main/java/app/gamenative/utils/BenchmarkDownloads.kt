package app.gamenative.utils

import android.content.Intent
import android.net.Uri

/** User-initiated browser downloads only; no extraction, installation, or execution. */
enum class BenchmarkDownload(val url: String) {
    CPU("https://github.com/ip7z/7zip/releases/download/26.06/7z2606-x64.exe"),
    GRAPHICS("https://github.com/The412Banner/AIO-Graphics-Test/releases/download/2.2.0/AIO-Graphics-Test-64bit.exe");

    fun browserIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
}
