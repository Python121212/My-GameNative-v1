package app.gamenative.ui.screen.xserver

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.gamenative.utils.DiagnosticLog
import com.winlator.widget.FrameRating
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** FPS は FrameRating の表示フレーム値。実際に新しいフレームがある期間のみ記録する。 */
@Composable
internal fun DiagnosticSession(context: Context, sessionKey: String, rating: () -> FrameRating?) {
    LaunchedEffect(sessionKey) {
        if (!DiagnosticLog.enabled(context)) return@LaunchedEffect
        val sessionId = withContext(Dispatchers.IO) { DiagnosticLog.start(context) } ?: return@LaunchedEffect
        var previousFrames: Long? = null
        try {
            while (isActive) {
                val current = rating()
                val frames = current?.totalFrames
                val fps = if (current != null && frames != null && previousFrames != null && frames > previousFrames) current.currentFPS else null
                previousFrames = frames
                withContext(Dispatchers.IO) { DiagnosticLog.sample(context, fps) }
                delay(5000)
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { DiagnosticLog.stop(sessionId) }
        }
    }
}
