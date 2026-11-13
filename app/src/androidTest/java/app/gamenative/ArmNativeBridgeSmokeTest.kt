package app.gamenative

import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** APK に含まれる ARM64 ライブラリの読み込みを、実行中プロセス内で確認する。 */
@RunWith(AndroidJUnit4::class)
class ArmNativeBridgeSmokeTest {
    @Test
    fun loadsBundledArm64Library() {
        assertTrue("64ビットプロセスで実行してください", Process.is64Bit())
        System.loadLibrary("gnaffinity")
    }
}
