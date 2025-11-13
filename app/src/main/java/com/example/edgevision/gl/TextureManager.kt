package com.example.edgevision.gl

import android.opengl.GLES20
import android.util.Log
import java.nio.ByteBuffer

/**
 * Manages OpenGL textures for frame rendering
 */
class TextureManager {

    companion object {
        private const val TAG = "TextureManager"
    }

    private var textureId: Int = 0
    private var textureWidth: Int = 0
    private var textureHeight: Int = 0

    /**
     * Create OpenGL texture
     */
    fun createTexture(width: Int, height: Int): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)

        if (textures[0] == 0) {
            Log.e(TAG, "Failed to generate texture")
            return 0
        }

        textureId = textures[0]
        textureWidth = width
        textureHeight = height

        // Bind texture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        // Set texture parameters
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        // Allocate texture memory
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_LUMINANCE,
            width,
            height,
            0,
            GLES20.GL_LUMINANCE,
            GLES20.GL_UNSIGNED_BYTE,
            null
        )

        // Unbind
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        Log.i(TAG, "Texture created: ${width}x${height}, ID: $textureId")
        return textureId
    }

    /**
     * Update texture with new frame data
     */
    fun updateTexture(frameData: ByteArray, width: Int, height: Int) {
        if (textureId == 0) {
            Log.e(TAG, "Texture not initialized")
            return
        }

        if (width != textureWidth || height != textureHeight) {
            Log.w(TAG, "Frame size mismatch: expected ${textureWidth}x${textureHeight}, got ${width}x${height}")
            return
        }

        // Bind texture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        // Create ByteBuffer from frame data
        val buffer = ByteBuffer.allocateDirect(frameData.size)
        buffer.put(frameData)
        buffer.position(0)

        // Update texture data
        GLES20.glTexSubImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            0,
            0,
            width,
            height,
            GLES20.GL_LUMINANCE,
            GLES20.GL_UNSIGNED_BYTE,
            buffer
        )

        // Unbind
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    /**
     * Bind texture for rendering
     */
    fun bind() {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
    }

    /**
     * Unbind texture
     */
    fun unbind() {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    /**
     * Get texture ID
     */
    fun getTextureId(): Int = textureId

    /**
     * Release texture resources
     */
    fun release() {
        if (textureId != 0) {
            val textures = intArrayOf(textureId)
            GLES20.glDeleteTextures(1, textures, 0)
            textureId = 0
            Log.d(TAG, "Texture released")
        }
    }
}
