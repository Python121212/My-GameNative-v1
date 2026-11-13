package app.gamenative

import android.opengl.EGL14
import android.opengl.GLES20
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 実機の OpenGL ES 描画を確認する簡易テスト。ゲームの描画互換性を保証するものではない。 */
@RunWith(AndroidJUnit4::class)
class GraphicsDeviceSmokeTest {
    @Test
    fun canRenderAndReadBackOnDevice() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        assertNotEquals("EGL ディスプレイを取得できません", EGL14.EGL_NO_DISPLAY, display)
        val version = IntArray(2)
        assertTrue("EGL を初期化できません", EGL14.eglInitialize(display, version, 0, version, 1))
        var context = EGL14.EGL_NO_CONTEXT
        var surface = EGL14.EGL_NO_SURFACE
        try {
            val attributes = intArrayOf(
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val count = IntArray(1)
            assertTrue("EGL 設定を選択できません", EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0))
            assertTrue("対応する EGL 設定がありません", count[0] > 0)
            val config = requireNotNull(configs[0])
            context = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
            )
            assertNotEquals("OpenGL ES コンテキストを作成できません", EGL14.EGL_NO_CONTEXT, context)
            surface = EGL14.eglCreatePbufferSurface(
                display, config,
                intArrayOf(EGL14.EGL_WIDTH, 16, EGL14.EGL_HEIGHT, 16, EGL14.EGL_NONE), 0,
            )
            assertNotEquals("描画先を作成できません", EGL14.EGL_NO_SURFACE, surface)
            assertTrue("EGL コンテキストを有効にできません", EGL14.eglMakeCurrent(display, surface, surface, context))

            Log.i(
                TAG,
                "端末=${Build.MANUFACTURER} ${Build.MODEL}, Android=${Build.VERSION.RELEASE}, " +
                    "GPU=${GLES20.glGetString(GLES20.GL_RENDERER)}, " +
                    "ベンダー=${GLES20.glGetString(GLES20.GL_VENDOR)}, " +
                    "OpenGL=${GLES20.glGetString(GLES20.GL_VERSION)}",
            )
            GLES20.glViewport(0, 0, 16, 16)
            GLES20.glClearColor(1f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            val pixel = ByteBuffer.allocateDirect(4)
            GLES20.glReadPixels(8, 8, 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixel)
            assertEquals("OpenGL エラー", GLES20.GL_NO_ERROR, GLES20.glGetError())
            assertTrue("赤が描画されていません", (pixel.get(0).toInt() and 0xff) >= 250)
            assertTrue("緑が不正です", (pixel.get(1).toInt() and 0xff) <= 5)
            assertTrue("青が不正です", (pixel.get(2).toInt() and 0xff) <= 5)
            assertTrue("アルファが不正です", (pixel.get(3).toInt() and 0xff) >= 250)
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }

    companion object {
        private const val TAG = "GraphicsDeviceSmokeTest"
    }
}
