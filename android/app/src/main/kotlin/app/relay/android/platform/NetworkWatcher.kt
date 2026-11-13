package app.relay.android.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network

/**
 * Calls [onChange] when the phone's default network comes back or switches (Wi-Fi to mobile
 * data and back), so the relay socket reconnects at once rather than after its backoff.
 */
class NetworkWatcher(context: Context, private val onChange: () -> Unit) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private var current: Network? = null
    private var seenFirst = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // The first call reports the network the socket is already on.
            val changed = seenFirst && network != current
            seenFirst = true
            current = network
            if (changed) onChange()
        }

        override fun onLost(network: Network) {
            if (network == current) current = null
        }
    }

    fun start() {
        runCatching { manager?.registerDefaultNetworkCallback(callback) }
    }

    fun stop() {
        runCatching { manager?.unregisterNetworkCallback(callback) }
    }
}
