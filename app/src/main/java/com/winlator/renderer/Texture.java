package com.winlator.renderer;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;

// import com.winlator.XrActivity;
import com.winlator.xserver.Drawable;

import java.nio.ByteBuffer;

public class Texture {
    protected int textureId = 0;
    private int wrapS = GLES20.GL_CLAMP_TO_EDGE;
    private int wrapT = GLES20.GL_CLAMP_TO_EDGE;
    private int magFilter = GLES20.GL_LINEAR;
    private int minFilter = GLES20.GL_LINEAR;
    protected int format = GLES11Ext.GL_BGRA;
    protected byte unpackAlignment = 4;
    protected boolean needsUpdate = true;
    private int framebufferCopyWidth = 0;
    private int framebufferCopyHeight = 0;

    protected void generateTextureId() {
        int[] textureIds = new int[1];
        GLES20.glGenTextures(1, textureIds, 0);
        this.textureId = textureIds[0];
    }

    protected void setTextureParameters() {
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, this.wrapS);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, this.wrapT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, this.magFilter);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, this.minFilter);
    }

    public void setFilters(int minFilter, int magFilter) {
        // テクスチャ生成時に保存済みの設定は適用されるため、同じ値の再送は不要。
        if (this.minFilter == minFilter && this.magFilter == magFilter) return;
        this.minFilter = minFilter;
        this.magFilter = magFilter;
        if (textureId > 0) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            setTextureParameters();
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        }
    }

    public void allocateTexture(short width, short height, ByteBuffer data) {
        framebufferCopyWidth = 0;
        framebufferCopyHeight = 0;
        generateTextureId();

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, unpackAlignment);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);

        if (data != null) {
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, format, width, height, 0, format, GLES20.GL_UNSIGNED_BYTE, data);
        }
        setTextureParameters();
        GLES20.glBindTexture(3553, 0);
    }

    public void setNeedsUpdate(boolean needsUpdate) {
        this.needsUpdate = needsUpdate;
    }

    public void updateFromDrawable(Drawable drawable) {
        ByteBuffer data = drawable.getData();
        if (data == null) return;

        if (!isAllocated()) {
            allocateTexture(drawable.width, drawable.height, data);
        }
        else if (needsUpdate) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, drawable.width, drawable.height, format, GLES20.GL_UNSIGNED_BYTE, data);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            needsUpdate = false;
        }
    }

    public boolean isAllocated() {
        return textureId > 0;
    }

    public int getTextureId() {
        return textureId;
    }

    public void copyFromFramebuffer(int framebuffer, short width, short height) {
        if (!isAllocated()) {
            allocateTexture(width, height, null);
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        if (framebufferCopyWidth == width && framebufferCopyHeight == height) {
            updateFramebufferCopy(width, height);
        }
        else {
            defineFramebufferCopy(width, height);
            framebufferCopyWidth = width;
            framebufferCopyHeight = height;
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    // 同一サイズならストレージの再定義を避け、内容だけをコピーする。
    protected void defineFramebufferCopy(short width, short height) {
        GLES20.glCopyTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 0, 0, width, height, 0);
    }

    protected void updateFramebufferCopy(short width, short height) {
        GLES20.glCopyTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
    }

    public void invalidate() {
        framebufferCopyWidth = 0;
        framebufferCopyHeight = 0;
        textureId = 0;
        needsUpdate = true;
    }

    public void destroy() {
        framebufferCopyWidth = 0;
        framebufferCopyHeight = 0;
        if (textureId > 0) {
            int[] textureIds = new int[]{textureId};
            GLES20.glDeleteTextures(textureIds.length, textureIds, 0);
            textureId = 0;
        }
    }
}
