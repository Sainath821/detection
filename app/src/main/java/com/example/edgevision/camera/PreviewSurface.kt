package com.example.edgevision.camera

import android.graphics.SurfaceTexture
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView

class PreviewSurface(
    private val textureView: TextureView,
    private val previewSize: Size
) {

    private var surface: Surface? = null
    var onSurfaceReady: (Surface) -> Unit = {}

    companion object {
        private const val TAG = "PreviewSurface"
    }

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(
            surfaceTexture: SurfaceTexture,
            width: Int,
            height: Int
        ) {
            Log.d(TAG, "Surface texture available: ${width}x${height}")
            setupSurface(surfaceTexture)
        }

        override fun onSurfaceTextureSizeChanged(
            surfaceTexture: SurfaceTexture,
            width: Int,
            height: Int
        ) {
            Log.d(TAG, "Surface texture size changed: ${width}x${height}")
        }

        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
            Log.d(TAG, "Surface texture destroyed")
            releaseSurface()
            return true
        }

        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
            // Frame updated on texture
        }
    }

    init {
        textureView.surfaceTextureListener = surfaceTextureListener

        // If surface texture is already available
        if (textureView.isAvailable) {
            textureView.surfaceTexture?.let { setupSurface(it) }
        }
    }

    private fun setupSurface(surfaceTexture: SurfaceTexture) {
        // Configure the size of default buffer
        surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)

        // Create Surface from SurfaceTexture
        surface = Surface(surfaceTexture)

        Log.d(TAG, "Surface created with size: ${previewSize.width}x${previewSize.height}")

        surface?.let { onSurfaceReady(it) }
    }

    fun getSurface(): Surface? = surface

    private fun releaseSurface() {
        surface?.release()
        surface = null
    }

    fun release() {
        releaseSurface()
        textureView.surfaceTextureListener = null
    }
}
