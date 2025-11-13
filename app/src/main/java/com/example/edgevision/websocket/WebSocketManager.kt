package com.example.edgevision.websocket

import android.content.Context
import android.util.Log
import com.example.edgevision.utils.NetworkUtils
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages WebSocket server lifecycle and frame streaming
 */
class WebSocketManager(private val context: Context) {

    companion object {
        private const val TAG = "WebSocketManager"
        private const val DEFAULT_PORT = 8888
        private const val FRAME_THROTTLE_MS = 100 // Send frame every 100ms (10 FPS)
    }

    private var server: FrameWebSocketServer? = null
    private var isRunning = false
    private val lastFrameSentTime = AtomicLong(0)

    var onServerStateChanged: ((Boolean) -> Unit)? = null
    var onClientCountChanged: ((Int) -> Unit)? = null

    /**
     * Start the WebSocket server
     */
    fun startServer(port: Int = DEFAULT_PORT): Boolean {
        if (isRunning) {
            Log.w(TAG, "Server is already running")
            return true
        }

        try {
            Log.i(TAG, "Starting WebSocket server on port $port...")

            server = FrameWebSocketServer(port).apply {
                onClientCountChanged = { count ->
                    this@WebSocketManager.onClientCountChanged?.invoke(count)
                }
                start()
            }

            isRunning = true
            onServerStateChanged?.invoke(true)

            val wsUrl = NetworkUtils.getWebSocketUrl(context, port)
            Log.i(TAG, "WebSocket server started successfully at $wsUrl")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start WebSocket server", e)
            isRunning = false
            onServerStateChanged?.invoke(false)
            return false
        }
    }

    /**
     * Stop the WebSocket server
     */
    fun stopServer() {
        if (!isRunning) {
            Log.w(TAG, "Server is not running")
            return
        }

        try {
            Log.i(TAG, "Stopping WebSocket server...")
            server?.shutdown()
            server = null
            isRunning = false
            onServerStateChanged?.invoke(false)
            Log.i(TAG, "WebSocket server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping WebSocket server", e)
        }
    }

    /**
     * Send a frame to all connected clients with throttling
     * @param frameData Raw frame bytes
     * @param width Frame width
     * @param height Frame height
     * @param format Frame format (e.g., "Grayscale")
     * @param processingMode Processing mode (e.g., "Canny Edge Detection")
     * @param fps Current FPS
     * @return true if frame was sent, false if throttled or no clients
     */
    fun sendFrame(
        frameData: ByteArray,
        width: Int,
        height: Int,
        format: String,
        processingMode: String,
        fps: Double
    ): Boolean {
        if (!isRunning || server == null) {
            return false
        }

        if (!server!!.hasClients()) {
            return false
        }

        // Throttle frame sending
        val currentTime = System.currentTimeMillis()
        val lastSent = lastFrameSentTime.get()
        if (currentTime - lastSent < FRAME_THROTTLE_MS) {
            return false
        }

        try {
            val frameMessage = FrameMessage.create(
                width = width,
                height = height,
                format = format,
                processingMode = processingMode,
                fps = fps,
                frameData = frameData
            )

            server?.broadcastFrame(frameMessage)
            lastFrameSentTime.set(currentTime)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending frame", e)
            return false
        }
    }

    /**
     * Get server running state
     */
    fun isServerRunning(): Boolean = isRunning

    /**
     * Get number of connected clients
     */
    fun getClientCount(): Int = server?.getClientCount() ?: 0

    /**
     * Get WebSocket server URL
     */
    fun getServerUrl(port: Int = DEFAULT_PORT): String {
        return NetworkUtils.getWebSocketUrl(context, port) ?: "Not available"
    }

    /**
     * Check if WiFi is connected
     */
    fun isWifiConnected(): Boolean {
        return NetworkUtils.isWifiConnected(context)
    }
}
