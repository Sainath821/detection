package com.example.edgevision.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

/**
 * Utility functions for network operations
 */
object NetworkUtils {
    private const val TAG = "NetworkUtils"

    /**
     * Get the device's IP address on the local network
     * @return IP address as string, or null if not connected
     */
    fun getLocalIpAddress(context: Context): String? {
        try {
            // Try WiFi first
            val wifiIp = getWifiIpAddress(context)
            if (wifiIp != null) {
                Log.d(TAG, "WiFi IP address: $wifiIp")
                return wifiIp
            }

            // Fallback to network interfaces
            val networkInterfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in networkInterfaces) {
                val inetAddresses = Collections.list(networkInterface.inetAddresses)
                for (inetAddress in inetAddresses) {
                    if (!inetAddress.isLoopbackAddress && inetAddress is InetAddress) {
                        val address = inetAddress.hostAddress
                        // Check if it's IPv4
                        if (address != null && address.indexOf(':') < 0) {
                            Log.d(TAG, "Network interface IP: $address")
                            return address
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP address", e)
        }
        return null
    }

    /**
     * Get WiFi IP address
     */
    private fun getWifiIpAddress(context: Context): String? {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            val ipInt = wifiInfo?.ipAddress ?: return null

            // Convert int to IP address string
            return String.format(
                "%d.%d.%d.%d",
                ipInt and 0xff,
                (ipInt shr 8) and 0xff,
                (ipInt shr 16) and 0xff,
                (ipInt shr 24) and 0xff
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting WiFi IP", e)
            return null
        }
    }

    /**
     * Check if device is connected to a network
     */
    fun isNetworkConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Check if device is connected to WiFi
     */
    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Get WebSocket URL for the device
     */
    fun getWebSocketUrl(context: Context, port: Int = 8888): String? {
        val ip = getLocalIpAddress(context)
        return if (ip != null) {
            "ws://$ip:$port"
        } else {
            null
        }
    }
}
