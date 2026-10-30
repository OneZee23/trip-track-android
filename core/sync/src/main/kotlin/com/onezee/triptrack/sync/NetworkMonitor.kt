package com.onezee.triptrack.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// iOS: NetworkMonitor.swift (NWPath snapshot)
data class NetworkState(val isOffline: Boolean = false, val isOnWiFi: Boolean = false)

// iOS: NetworkMonitor.swift. Optimistic until the first real callback, as on iPhone.
class NetworkMonitor(context: Context) : AutoCloseable {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val mutableState = MutableStateFlow(NetworkState())
    val state = mutableState.asStateFlow()
    private var started = false
    @Volatile private var currentNetwork: Network? = null
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { currentNetwork = network }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (network == currentNetwork) mutableState.value = fromCapabilities(capabilities)
        }
        override fun onLost(network: Network) {
            if (network == currentNetwork) { currentNetwork = null; mutableState.value = NetworkState(isOffline = true) }
        }
        override fun onUnavailable() { mutableState.value = NetworkState(isOffline = true) }
    }
    @Synchronized fun start() {
        if (started) return
        currentNetwork = connectivity.activeNetwork
        val capabilities = currentNetwork?.let(connectivity::getNetworkCapabilities)
        mutableState.value = capabilities?.let(::fromCapabilities) ?: NetworkState(isOffline = true)
        connectivity.registerDefaultNetworkCallback(callback)
        started = true
    }
    @Synchronized override fun close() {
        if (!started) return
        connectivity.unregisterNetworkCallback(callback)
        started = false
    }
    companion object {
        internal fun fromCapabilities(capabilities: NetworkCapabilities): NetworkState {
            val online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            return NetworkState(!online, online && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
        }
    }
}
