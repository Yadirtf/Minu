package com.daniel.reproductor.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Indica si hay conexión a internet, para mostrar solo lo descargado cuando no la hay. */
class NetworkMonitor(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    private val _isOnline = MutableStateFlow(currentlyOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _isOnline.value = caps.hasInternet()
            }

            override fun onLost(network: Network) {
                _isOnline.value = false
            }
        })
    }

    private fun currentlyOnline(): Boolean =
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasInternet() == true

    private fun NetworkCapabilities.hasInternet() =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
