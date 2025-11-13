package com.example.edgevision.websocket

import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.nio.ByteBuffer

/**
 * WebSocket server for streaming processed camera frames
 */
class FrameWebSocketServer(port: Int) : WebSocketServer(InetSocketAddress(port)) {

    companion object {
        private const val TAG = "FrameWebSocketServer"
        const val DEFAULT_PORT = 8888
    }

    private val connectedClients = mutableSetOf<WebSocket>()
    var onClientCountChanged: ((Int) -> Unit)? = null

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        connectedClients.add(conn)
        val clientAddress = conn.remoteSocketAddress.toString()
        Log.i(TAG, "New client connected: $clientAddress")
        Log.i(TAG, "Total connected clients: ${connectedClients.size}")
        onClientCountChanged?.invoke(connectedClients.size)
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        connectedClients.remove(conn)
        val clientAddress = conn.remoteSocketAddress.toString()
        Log.i(TAG, "Client disconnected: $clientAddress (code: $code, reason: $reason)")
        Log.i(TAG, "Total connected clients: ${connectedClients.size}")
        onClientCountChanged?.invoke(connectedClients.size)
    }

    override fun onMessage(conn: WebSocket, message: String) {
        // Echo message back to client (for testing)
        Log.d(TAG, "Received message from ${conn.remoteSocketAddress}: $message")
        conn.send("Echo: $message")
    }

    override fun onMessage(conn: WebSocket, message: ByteBuffer) {
        Log.d(TAG, "Received binary message from ${conn.remoteSocketAddress}: ${message.remaining()} bytes")
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        val clientAddress = conn?.remoteSocketAddress?.toString() ?: "unknown"
        Log.e(TAG, "WebSocket error for client $clientAddress", ex)
    }

    override fun onStart() {
        Log.i(TAG, "WebSocket server started on port $port")
        connectionLostTimeout = 30 // 30 seconds timeout
        isReuseAddr = true
    }

    /**
     * Broadcast frame message to all connected clients
     */
    fun broadcastFrame(frameMessage: FrameMessage) {
        if (connectedClients.isEmpty()) {
            return
        }

        val json = frameMessage.toJson()
        val sentCount = connectedClients.count { client ->
            try {
                if (client.isOpen) {
                    client.send(json)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending frame to client ${client.remoteSocketAddress}", e)
                false
            }
        }

        if (sentCount > 0) {
            Log.d(TAG, "Broadcasted frame to $sentCount client(s), size: ${frameMessage.frameSize} bytes")
        }
    }

    /**
     * Get number of connected clients
     */
    fun getClientCount(): Int = connectedClients.size

    /**
     * Check if server has any connected clients
     */
    fun hasClients(): Boolean = connectedClients.isNotEmpty()

    /**
     * Disconnect all clients and stop server
     */
    fun shutdown() {
        Log.i(TAG, "Shutting down WebSocket server...")
        try {
            connectedClients.forEach { client ->
                try {
                    client.close(1000, "Server shutting down")
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing client connection", e)
                }
            }
            connectedClients.clear()
            stop(1000)
            Log.i(TAG, "WebSocket server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown", e)
        }
    }
}
