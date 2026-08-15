package com.tailg.plus.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Port of `lib/services/network_availability_service.dart`.
 *
 * Fast link-state probe matching the official app's pre-command network gate.
 * Connectivity errors fail open: the MQTT/HTTP layers still provide the
 * authoritative transport error, while a plugin failure must not disable BLE.
 */
class NetworkAvailabilityService(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** True when the device has any usable network link. Fails open. */
    suspend fun checkNow(fallback: Boolean = true): Boolean = try {
        hasNetwork(connectivityManager.activeNetwork)
    } catch (_: Exception) {
        fallback
    }

    /** Emits the link state on changes (fail-open on registration errors). */
    val changes: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(hasNetwork(network))
            }

            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                trySend(hasNetwork(capabilities))
            }
        }
        try {
            val request = android.net.NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, callback)
            trySend(hasNetwork(connectivityManager.activeNetwork))
        } catch (_: Exception) {
            // Keep last known state when registration is unavailable.
        }
        awaitClose { runCatching { connectivityManager.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()

    private fun hasNetwork(network: Network?): Boolean {
        if (network == null) return false
        return hasNetwork(connectivityManager.getNetworkCapabilities(network))
    }

    private fun hasNetwork(capabilities: NetworkCapabilities?): Boolean =
        capabilities != null &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
}
