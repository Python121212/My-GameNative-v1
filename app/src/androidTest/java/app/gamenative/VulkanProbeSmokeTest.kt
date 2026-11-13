package app.gamenative

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.winlator.core.GPUHelper
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Vulkan が使えない環境でも GPU の検査でプロセスが落ちないことを確認する。 */
@RunWith(AndroidJUnit4::class)
class VulkanProbeSmokeTest {
    @Test
    fun probesDeviceWithoutCrashing() {
        val version = GPUHelper.vkGetApiVersion()
        assertTrue("取得値またはフォールバックは Vulkan 1.0 以上", version >= GPUHelper.vkMakeVersion(1, 0, 0))
        GPUHelper.vkGetDeviceExtensions()
    }
}
