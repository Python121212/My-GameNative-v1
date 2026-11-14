package com.winlator.renderer

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

/** フレームバッファの再利用と実際のピクセルを EGL で確認する。 */
@RunWith(AndroidJUnit4::class)
class FramebufferCopyReuseTest {
    private class CountingTexture : Texture() {
        var defines = 0
        var updates = 0
        override fun defineFramebufferCopy(width: Short, height: Short) {
            defines++
            super.defineFramebufferCopy(width, height)
        }
        override fun updateFramebufferCopy(width: Short, height: Short) {
            updates++
            super.updateFramebufferCopy(width, height)
        }
    }

    @Test fun reusesStorageAndRecreatesOnResizeOrDestroy() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        assertNotEquals(EGL14.EGL_NO_DISPLAY, display)
        assertTrue(EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0))
        var context = EGL14.EGL_NO_CONTEXT
        var surface = EGL14.EGL_NO_SURFACE
        val texture = CountingTexture()
        val framebuffer = IntArray(1)
        try {
            val attrs = intArrayOf(EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE)
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            assertTrue(EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, count, 0))
            assertTrue(count[0] > 0)
            val config = requireNotNull(configs[0])
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            surface = EGL14.eglCreatePbufferSurface(display, config,
                intArrayOf(EGL14.EGL_WIDTH, 16, EGL14.EGL_HEIGHT, 16, EGL14.EGL_NONE), 0)
            assertNotEquals(EGL14.EGL_NO_CONTEXT, context)
            assertNotEquals(EGL14.EGL_NO_SURFACE, surface)
            assertTrue(EGL14.eglMakeCurrent(display, surface, surface, context))
            GLES20.glGenFramebuffers(1, framebuffer, 0)

            fun copyAndCheck(red: Float, green: Float, size: Short) {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
                GLES20.glClearColor(red, green, 0f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                texture.copyFromFramebuffer(0, size, size)
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[0])
                GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, texture.getTextureId(), 0)
                assertEquals(GLES20.GL_FRAMEBUFFER_COMPLETE, GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER))
                val pixel = ByteBuffer.allocateDirect(4)
                GLES20.glReadPixels(0, 0, 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixel)
                assertEquals((red * 255).toInt(), pixel.get(0).toInt() and 255)
                assertEquals((green * 255).toInt(), pixel.get(1).toInt() and 255)
                assertEquals(GLES20.GL_NO_ERROR, GLES20.glGetError())
            }
            copyAndCheck(1f, 0f, 8)
            copyAndCheck(0f, 1f, 8)
            assertEquals(1, texture.defines)
            assertEquals(1, texture.updates)
            copyAndCheck(1f, 0f, 4)
            assertEquals(2, texture.defines)
            texture.destroy()
            copyAndCheck(0f, 1f, 4)
            assertEquals(3, texture.defines)
            assertEquals(1, texture.updates)
        } finally {
            texture.destroy()
            GLES20.glDeleteFramebuffers(1, framebuffer, 0)
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }
}
