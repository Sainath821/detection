package com.example.edgevision.gl

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES 2.0 renderer for EdgeVision
 * Renders processed camera frames to screen
 */
class EdgeVisionRenderer(private val context: Context) : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "EdgeVisionRenderer"
        // Square dimensions to match camera output
        private const val TEXTURE_WIDTH = 1088
        private const val TEXTURE_HEIGHT = 1088
    }

    private var shaderProgram: ShaderProgram? = null
    private var textureManager: TextureManager? = null
    private var currentFrameData: ByteArray? = null
    private val frameLock = Any()
    private var isRendering = false

    // FPS tracking
    private var frameStartTime = 0L
    private var frameCount = 0
    private var fps = 0.0
    private var droppedFrames = 0

    // Vertex coordinates (full screen quad)
    private val vertexCoords = floatArrayOf(
        -1.0f,  1.0f,  // Top left
        -1.0f, -1.0f,  // Bottom left
         1.0f,  1.0f,  // Top right
         1.0f, -1.0f   // Bottom right
    )

    // Texture coordinates (rotated 90 degrees clockwise for portrait mode)
    // Rotate 90 degrees clockwise to fix inverted orientation
    private val textureCoords = floatArrayOf(
        0.0f, 1.0f,  // Top left
        1.0f, 1.0f,  // Bottom left
        0.0f, 0.0f,  // Top right
        1.0f, 0.0f   // Bottom right
    )

    private var vertexBuffer: FloatBuffer
    private var textureBuffer: FloatBuffer

    init {
        // Initialize vertex buffer
        vertexBuffer = ByteBuffer.allocateDirect(vertexCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertexCoords)
                position(0)
            }

        // Initialize texture coordinate buffer
        textureBuffer = ByteBuffer.allocateDirect(textureCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(textureCoords)
                position(0)
            }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, "onSurfaceCreated")

        // Set clear color (black background)
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        // Enable blending for transparency
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        // Initialize shader program
        shaderProgram = ShaderProgram(context).apply {
            if (!loadShaders("shaders/vertex.glsl", "shaders/fragment.glsl")) {
                Log.e(TAG, "Failed to load shaders")
                return
            }
            if (!linkProgram()) {
                Log.e(TAG, "Failed to link shader program")
                return
            }
        }

        // Initialize texture manager
        textureManager = TextureManager().apply {
            createTexture(TEXTURE_WIDTH, TEXTURE_HEIGHT)
        }

        Log.i(TAG, "OpenGL ES 2.0 context, shaders, and textures initialized successfully")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceChanged: ${width}x${height}")

        // Set viewport to match surface dimensions
        GLES20.glViewport(0, 0, width, height)

        // Note: We're using a simple orthographic projection (normalized device coordinates)
        // The vertex shader receives coordinates in range [-1, 1] which maps directly to screen
        // No projection matrix needed for full-screen quad rendering
        Log.i(TAG, "Viewport configured for ${width}x${height}")
    }

    override fun onDrawFrame(gl: GL10?) {
        isRendering = true

        // Track FPS
        val currentTime = System.currentTimeMillis()
        if (frameStartTime == 0L) {
            frameStartTime = currentTime
        }
        frameCount++

        if (currentTime - frameStartTime >= 1000) {
            fps = frameCount / ((currentTime - frameStartTime) / 1000.0)
            if (droppedFrames > 0) {
                Log.i(TAG, "Rendering FPS: %.1f, Dropped: %d frames".format(fps, droppedFrames))
                droppedFrames = 0
            } else {
                Log.i(TAG, "Rendering FPS: %.1f".format(fps))
            }
            frameCount = 0
            frameStartTime = currentTime
        }

        // Clear screen
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // Update texture with latest frame data
        synchronized(frameLock) {
            currentFrameData?.let { frameData ->
                textureManager?.updateTexture(frameData, TEXTURE_WIDTH, TEXTURE_HEIGHT)
            }
        }

        // Use shader program
        shaderProgram?.use()

        // Get attribute locations
        val positionHandle = shaderProgram?.getAttribLocation("aPosition") ?: return
        val texCoordHandle = shaderProgram?.getAttribLocation("aTexCoord") ?: return
        val textureHandle = shaderProgram?.getUniformLocation("uTexture") ?: return

        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        // Set vertex position data
        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(
            positionHandle,
            2,  // 2 components per vertex (x, y)
            GLES20.GL_FLOAT,
            false,
            0,
            vertexBuffer
        )

        // Set texture coordinate data
        textureBuffer.position(0)
        GLES20.glVertexAttribPointer(
            texCoordHandle,
            2,  // 2 components per texture coord (u, v)
            GLES20.GL_FLOAT,
            false,
            0,
            textureBuffer
        )

        // Bind texture
        textureManager?.bind()
        GLES20.glUniform1i(textureHandle, 0)

        // Draw quad
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)

        // Unbind texture
        textureManager?.unbind()

        isRendering = false
    }

    /**
     * Update frame data from processing thread
     */
    fun updateFrame(frameData: ByteArray) {
        // Drop frame if renderer is busy
        if (isRendering) {
            droppedFrames++
            return
        }

        synchronized(frameLock) {
            currentFrameData = frameData
        }
    }

    /**
     * Get current FPS
     */
    fun getFPS(): Double = fps

    /**
     * Capture current frame as bitmap
     */
    fun captureCurrentFrame(callback: (Bitmap?) -> Unit) {
        synchronized(frameLock) {
            currentFrameData?.let { frameData ->
                try {
                    // Create bitmap from grayscale data
                    val bitmap = Bitmap.createBitmap(TEXTURE_WIDTH, TEXTURE_HEIGHT, Bitmap.Config.ARGB_8888)
                    val pixels = IntArray(TEXTURE_WIDTH * TEXTURE_HEIGHT)

                    // Convert grayscale to ARGB
                    for (i in frameData.indices) {
                        val gray = frameData[i].toInt() and 0xFF
                        pixels[i] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
                    }

                    bitmap.setPixels(pixels, 0, TEXTURE_WIDTH, 0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT)
                    callback(bitmap)
                } catch (e: Exception) {
                    Log.e(TAG, "Error capturing frame", e)
                    callback(null)
                }
            } ?: callback(null)
        }
    }
}
