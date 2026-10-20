package com.pinakes.app.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the device currently has a network that can carry traffic, from the system default
 * network callback (singleton, Hilt-provided).
 *
 * "A request failed" is not enough to know the app is offline: a screen loaded online keeps
 * showing loan buttons and availability after airplane mode is switched on. Screens combine
 * [isOnline] with their own request outcome through [com.pinakes.app.ui.common.OfflinePolicy].
 *
 * Only `NET_CAPABILITY_INTERNET` is required, not `VALIDATED`: a self-hosted instance on a LAN
 * (or the emulator host at 10.0.2.2) is reachable on networks the system never validates.
 */
open class ConnectivityObserver(context: Context?) {

    private val manager = context?.applicationContext?.getSystemService(ConnectivityManager::class.java)
    private val _isOnline = MutableStateFlow(currentlyOnline())
    open val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        runCatching {
            manager?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isOnline.value = manager.getNetworkCapabilities(network)?.let(::usable) ?: true
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    _isOnline.value = usable(capabilities)
                }

                override fun onLost(network: Network) {
                    _isOnline.value = false
                }

                override fun onUnavailable() {
                    _isOnline.value = false
                }
            })
        }
    }

    private fun currentlyOnline(): Boolean {
        val m = manager ?: return true
        val caps = m.activeNetwork?.let { m.getNetworkCapabilities(it) } ?: return false
        return usable(caps)
    }

    private fun usable(caps: NetworkCapabilities): Boolean = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

    /** For tests: a fixed state that never changes on its own. */
    class Fixed(online: Boolean) : ConnectivityObserver(null) {
        private val state = MutableStateFlow(online)
        override val isOnline: StateFlow<Boolean> = state.asStateFlow()
        fun set(online: Boolean) { state.value = online }

        /** How many collectors listen right now (lets tests catch leaked collectors). */
        val subscribers: Int get() = state.subscriptionCount.value
    }
}
