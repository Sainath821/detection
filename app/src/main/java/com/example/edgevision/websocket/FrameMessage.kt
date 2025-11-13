package com.example.edgevision.websocket

import android.util.Base64
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data class representing a frame message to be sent via WebSocket
 */
data class FrameMessage(
    val timestamp: String,
    val width: Int,
    val height: Int,
    val format: String,
    val processingMode: String,
    val fps: Double,
    val frameData: String,  // Base64 encoded image data
    val frameSize: Int
) {
    companion object {
        private val gson = Gson()
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        /**
         * Create a FrameMessage from raw frame data
         */
        fun create(
            width: Int,
            height: Int,
            format: String,
            processingMode: String,
            fps: Double,
            frameData: ByteArray
        ): FrameMessage {
            val base64Data = Base64.encodeToString(frameData, Base64.NO_WRAP)
            val timestamp = dateFormat.format(Date())

            return FrameMessage(
                timestamp = timestamp,
                width = width,
                height = height,
                format = format,
                processingMode = processingMode,
                fps = fps,
                frameData = base64Data,
                frameSize = frameData.size
            )
        }

        /**
         * Parse FrameMessage from JSON string
         */
        fun fromJson(json: String): FrameMessage {
            return gson.fromJson(json, FrameMessage::class.java)
        }
    }

    /**
     * Convert FrameMessage to JSON string
     */
    fun toJson(): String {
        return gson.toJson(this)
    }

    /**
     * Decode base64 frame data back to byte array
     */
    fun decodeFrameData(): ByteArray {
        return Base64.decode(frameData, Base64.NO_WRAP)
    }
}
