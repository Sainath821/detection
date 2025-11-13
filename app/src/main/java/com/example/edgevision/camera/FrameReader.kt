package com.example.edgevision.camera

import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size

class FrameReader(size: Size) {

    private val imageReader: ImageReader
    private val frameBufferQueue = FrameBufferQueue(capacity = 3)
    private val handlerThread: HandlerThread
    private val handler: Handler

    var onFrameAvailable: (FrameBufferQueue.FrameBuffer) -> Unit = {}

    companion object {
        private const val TAG = "FrameReader"
        private const val MAX_IMAGES = 3
    }

    init {
        // Create background thread for image processing
        handlerThread = HandlerThread("FrameReaderThread").apply { start() }
        handler = Handler(handlerThread.looper)

        imageReader = ImageReader.newInstance(
            size.width,
            size.height,
            ImageFormat.YUV_420_888,
            MAX_IMAGES
        )

        imageReader.setOnImageAvailableListener({ reader ->
            var image: Image? = null
            try {
                // Use acquireLatestImage to drop old frames automatically
                image = reader.acquireLatestImage()

                if (image != null) {
                    // Add to buffer queue
                    if (frameBufferQueue.enqueue(image)) {
                        // Dequeue and process
                        frameBufferQueue.dequeue()?.let { buffer ->
                            try {
                                onFrameAvailable(buffer)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in frame callback", e)
                            } finally {
                                buffer.recycle()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing frame", e)
            } finally {
                // Always close image to prevent memory leaks
                image?.close()
            }
        }, handler)

        Log.d(TAG, "FrameReader initialized: ${size.width}x${size.height}")
    }

    fun getImageReader(): ImageReader = imageReader

    fun getBufferQueue(): FrameBufferQueue = frameBufferQueue

    fun close() {
        frameBufferQueue.stop()
        imageReader.close()
        handlerThread.quitSafely()
        try {
            handlerThread.join()
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping handler thread", e)
        }
        Log.d(TAG, "FrameReader closed")
    }
}
