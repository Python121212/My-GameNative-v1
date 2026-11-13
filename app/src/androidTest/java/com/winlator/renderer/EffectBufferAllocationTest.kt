package com.winlator.renderer

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** エフェクトの数とサイズの変更時に必要な中間バッファだけを確保する。 */
@RunWith(AndroidJUnit4::class)
class EffectBufferAllocationTest {
    @Test
    fun allocatesAndReleasesOnlyRequiredIntermediateBuffers() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        assertNotEquals(EGL14.EGL_NO_DISPLAY, display)
        val version = IntArray(2)
        assertTrue(EGL14.eglInitialize(display, version, 0, version, 1))
        var context = EGL14.EGL_NO_CONTEXT
        var surface = EGL14.EGL_NO_SURFACE
        val read = RenderTarget()
        val write = RenderTarget()
        try {
            val attrs = intArrayOf(
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            assertTrue(EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, count, 0))
            assertTrue(count[0] > 0)
            val config = requireNotNull(configs[0])
            context = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
            )
            assertNotEquals(EGL14.EGL_NO_CONTEXT, context)
            surface = EGL14.eglCreatePbufferSurface(
                display, config,
                intArrayOf(EGL14.EGL_WIDTH, 16, EGL14.EGL_HEIGHT, 16, EGL14.EGL_NONE), 0,
            )
            assertNotEquals(EGL14.EGL_NO_SURFACE, surface)
            assertTrue(EGL14.eglMakeCurrent(display, surface, surface, context))

            EffectComposer.prepareIntermediateBuffers(read, write, 16, 16, 1)
            assertEquals(0, read.getFramebuffer())
            assertEquals(0, write.getFramebuffer())
            EffectComposer.prepareIntermediateBuffers(read, write, 16, 16, 2)
            assertTrue(GLES20.glIsFramebuffer(read.getFramebuffer()))
            assertEquals(0, write.getFramebuffer())
            val readId = read.getFramebuffer()
            EffectComposer.prepareIntermediateBuffers(read, write, 16, 16, 3)
            assertEquals(readId, read.getFramebuffer())
            assertTrue(GLES20.glIsFramebuffer(write.getFramebuffer()))
            EffectComposer.prepareIntermediateBuffers(read, write, 32, 24, 3)
            assertEquals(32, read.getWidth())
            assertEquals(24, write.getHeight())
            assertTrue(GLES20.glIsFramebuffer(read.getFramebuffer()))
            assertTrue(GLES20.glIsFramebuffer(write.getFramebuffer()))
            val oldWrite = write.getFramebuffer()
            EffectComposer.prepareIntermediateBuffers(read, write, 32, 24, 2)
            assertEquals(0, write.getFramebuffer())
            assertTrue(!GLES20.glIsFramebuffer(oldWrite))
            val oldRead = read.getFramebuffer()
            EffectComposer.prepareIntermediateBuffers(read, write, 32, 24, 1)
            assertEquals(0, read.getFramebuffer())
            assertTrue(!GLES20.glIsFramebuffer(oldRead))
            EffectComposer.prepareIntermediateBuffers(read, write, 32, 24, 3)
            assertTrue(GLES20.glIsFramebuffer(read.getFramebuffer()))
            assertTrue(GLES20.glIsFramebuffer(write.getFramebuffer()))
            assertEquals(GLES20.GL_NO_ERROR, GLES20.glGetError())
        } finally {
            read.destroy()
            write.destroy()
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }
}
