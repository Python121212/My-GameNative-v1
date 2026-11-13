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

/** 実際の EGL コンテキストで設定の再送とテクスチャ再作成を確認する。 */
@RunWith(AndroidJUnit4::class)
class TextureFilterOptimizationTest {
    private class CountingTexture : Texture() {
        var parameterUpdates = 0

        override fun setTextureParameters() {
            parameterUpdates++
            super.setTextureParameters()
        }
    }

    @Test
    fun unchangedFiltersAreNotResentAndRecreatedTexturesKeepTheirSettings() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        assertNotEquals(EGL14.EGL_NO_DISPLAY, display)
        val version = IntArray(2)
        assertTrue(EGL14.eglInitialize(display, version, 0, version, 1))
        var context = EGL14.EGL_NO_CONTEXT
        var surface = EGL14.EGL_NO_SURFACE
        val texture = CountingTexture()
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

            texture.setFilters(GLES20.GL_NEAREST, GLES20.GL_NEAREST)
            assertEquals(0, texture.parameterUpdates)
            texture.allocateTexture(16, 16, null)
            assertEquals(1, texture.parameterUpdates)
            texture.setFilters(GLES20.GL_NEAREST, GLES20.GL_NEAREST)
            texture.setFilters(GLES20.GL_NEAREST, GLES20.GL_NEAREST)
            assertEquals("同じフィルターは再送されない", 1, texture.parameterUpdates)
            assertFilter(texture, GLES20.GL_NEAREST)

            texture.setFilters(GLES20.GL_LINEAR, GLES20.GL_LINEAR)
            assertEquals("変更されたフィルターは送信される", 2, texture.parameterUpdates)
            assertFilter(texture, GLES20.GL_LINEAR)
            texture.destroy()
            texture.setFilters(GLES20.GL_LINEAR, GLES20.GL_LINEAR)
            texture.allocateTexture(16, 16, null)
            assertEquals("テクスチャ再生成時にフィルターが再適用される", 3, texture.parameterUpdates)
            assertFilter(texture, GLES20.GL_LINEAR)
            assertEquals(GLES20.GL_NO_ERROR, GLES20.glGetError())
        } finally {
            texture.destroy()
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }

    private fun assertFilter(texture: Texture, expected: Int) {
        val result = IntArray(1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture.getTextureId())
        GLES20.glGetTexParameteriv(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, result, 0)
        assertEquals(expected, result[0])
        GLES20.glGetTexParameteriv(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, result, 0)
        assertEquals(expected, result[0])
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }
}
