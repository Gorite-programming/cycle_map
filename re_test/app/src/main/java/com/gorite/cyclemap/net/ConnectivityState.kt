package com.gorite.cyclemap.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface Link {
    data object Offline : Link
    data object OnlineMetered : Link
    data object OnlineUnmetered : Link
}

interface ConnectivityObserver {
    val link: StateFlow<Link>
}

class ConnectivityState(
    private val context: Context
) : ConnectivityObserver {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _link = MutableStateFlow<Link>(Link.Offline)
    override val link: StateFlow<Link> = _link.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateNetworkState()
        }

        override fun onLost(network: Network) {
            updateNetworkState()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            updateNetworkState()
        }
    }

    init {
        register()
    }

    private fun register() {
        val cm = connectivityManager ?: return
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, callback)
            updateNetworkState()
        } catch (_: Exception) {
            _link.value = Link.Offline
        }
    }

    fun unregister() {
        try {
            connectivityManager?.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
            // Ignore unregister errors on tear down
        }
    }

    fun updateNetworkState() {
        val cm = connectivityManager
        if (cm == null) {
            _link.value = Link.Offline
            return
        }

        val activeNetwork = cm.activeNetwork
        if (activeNetwork == null) {
            _link.value = Link.Offline
            return
        }

        val caps = cm.getNetworkCapabilities(activeNetwork)
        if (caps == null) {
            _link.value = Link.Offline
            return
        }

        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        if (!hasInternet || !isValidated) {
            // 到達性(NET_CAPABILITY_VALIDATED)が確認できない場合は安全側に倒して Offline 扱い
            _link.value = Link.Offline
            return
        }

        val isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        _link.value = if (isMetered) Link.OnlineMetered else Link.OnlineUnmetered
    }
}
