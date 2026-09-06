package com.tailg.plus.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.util.concurrent.atomic.AtomicReference
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
        val currentNetwork = AtomicReference<Network?>(null)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                currentNetwork.set(network)
                // Capabilities arrive next; querying them here can return a stale snapshot.
            }

            override fun onLost(network: Network) {
                if (currentNetwork.compareAndSet(network, null)) {
                    trySend(runCatching {
                        val active = connectivityManager.activeNetwork?.takeUnless { it == network }
                        currentNetwork.compareAndSet(null, active)
                        hasNetwork(active)
                    }.getOrDefault(true))
                }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                if (currentNetwork.get() == network) trySend(hasNetwork(capabilities))
            }
        }
        try {
            currentNetwork.set(connectivityManager.activeNetwork)
            connectivityManager.registerDefaultNetworkCallback(callback)
            trySend(hasNetwork(connectivityManager.activeNetwork))
        } catch (_: Exception) {
            trySend(checkNow(fallback = true))
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
