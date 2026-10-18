package com.masjidclock.wfsender.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.masjidclock.wfsender.protocol.CardConnector
import com.masjidclock.wfsender.protocol.UdpConnector
import java.io.IOException
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Tracks the connected WiFi network, also when it has no internet access (spec section 5.3).
 * Sockets from [connectorFor] are bound to that network, so traffic never goes over mobile data.
 */
class CardNetwork(context: Context) {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    @Volatile
    var current: Network? = null
        private set

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            current = network
        }

        override fun onLost(network: Network) {
            if (current == network) current = null
        }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivity.registerNetworkCallback(request, callback)
    }

    fun stop() {
        runCatching { connectivity.unregisterNetworkCallback(callback) }
    }

    /** A UDP socket that is bound to [network] (for the time sync). */
    fun udpFor(network: Network) = UdpConnector {
        val socket = DatagramSocket()
        try {
            network.bindSocket(socket)
            socket
        } catch (e: IOException) {
            socket.close()
            throw e
        }
    }

    fun connectorFor(network: Network) = CardConnector { host, port, timeoutMs ->
        val socket = Socket()
        try {
            network.bindSocket(socket)
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket
        } catch (e: IOException) {
            socket.close()
            throw e
        }
    }
}
