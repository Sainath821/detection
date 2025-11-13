package com.example.edgevision.camera

import android.media.Image
import android.util.Log
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

class FrameBufferQueue(private val capacity: Int = 3) {

    private val bufferQueue = ArrayBlockingQueue<FrameBuffer>(capacity)
    private var isRunning = true

    companion object {
        private const val TAG = "FrameBufferQueue"
    }

    data class FrameBuffer(
        val data: ByteArray,
        val width: Int,
        val height: Int,
        val timestamp: Long
    ) {
        fun recycle() {
            // Buffer can be reused
        }
    }

    fun enqueue(image: Image): Boolean {
        if (!isRunning) {
            Log.w(TAG, "Queue stopped, dropping frame")
            return false
        }

        try {
            // Convert Image to ByteArray
            val buffer = imageToByteArray(image)
            val frameBuffer = FrameBuffer(
                data = buffer,
                width = image.width,
                height = image.height,
                timestamp = image.timestamp
            )

            // Try to add to queue, drop oldest if full
            if (!bufferQueue.offer(frameBuffer)) {
                val dropped = bufferQueue.poll()
                dropped?.recycle()
                bufferQueue.offer(frameBuffer)
                Log.d(TAG, "Queue full, dropped oldest frame")
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error enqueuing frame", e)
            return false
        }
    }

    fun dequeue(timeout: Long = 100): FrameBuffer? {
        return try {
            bufferQueue.poll(timeout, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Dequeue interrupted", e)
            null
        }
    }

    fun clear() {
        while (bufferQueue.isNotEmpty()) {
            bufferQueue.poll()?.recycle()
        }
        Log.d(TAG, "Queue cleared")
    }

    fun stop() {
        isRunning = false
        clear()
        Log.d(TAG, "Queue stopped")
    }

    fun size(): Int = bufferQueue.size

    private fun imageToByteArray(image: Image): ByteArray {
        // Get YUV planes
        val planes = image.planes
        val yPlane = planes[0].buffer
        val uPlane = planes[1].buffer
        val vPlane = planes[2].buffer

        val ySize = yPlane.remaining()
        val uSize = uPlane.remaining()
        val vSize = vPlane.remaining()

        val data = ByteArray(ySize + uSize + vSize)

        // Copy Y plane
        yPlane.get(data, 0, ySize)
        // Copy U plane
        uPlane.get(data, ySize, uSize)
        // Copy V plane
        vPlane.get(data, ySize + uSize, vSize)

        return data
    }
}
