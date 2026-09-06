package com.tailg.plus.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkAvailabilityServiceTest {
    @Test
    fun losingPreviousNetworkDoesNotDisableCurrentConnection() = runTest {
        val wifi = mockk<Network>()
        val cellular = mockk<Network>()
        var active: Network? = wifi
        val capabilities = mockk<NetworkCapabilities>(relaxed = true)
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        val callback = slot<ConnectivityManager.NetworkCallback>()
        val manager = mockk<ConnectivityManager>(relaxed = true)
        every { manager.activeNetwork } answers { active }
        every { manager.getNetworkCapabilities(any()) } returns capabilities
        every { manager.registerDefaultNetworkCallback(capture(callback)) } just Runs
        val context = mockk<Context>()
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns manager
        val states = mutableListOf<Boolean>()
        backgroundScope.launch { NetworkAvailabilityService(context).changes.collect { states += it } }
        testScheduler.runCurrent()

        callback.captured.onAvailable(wifi)
        callback.captured.onCapabilitiesChanged(wifi, capabilities)
        active = cellular
        callback.captured.onAvailable(cellular)
        callback.captured.onCapabilitiesChanged(cellular, capabilities)
        callback.captured.onLost(wifi)
        callback.captured.onCapabilitiesChanged(wifi, mockk(relaxed = true))
        testScheduler.runCurrent()
        assertEquals(listOf(true), states)

        active = null
        callback.captured.onLost(cellular)
        testScheduler.runCurrent()
        assertEquals(listOf(true, false), states)
    }

    @Test
    fun unavailableConnectivityServiceStillEmitsFailOpenState() = runTest {
        val manager = mockk<ConnectivityManager>()
        every { manager.activeNetwork } throws SecurityException("unavailable")
        every { manager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>()) } just Runs
        val context = mockk<Context>()
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns manager
        val states = mutableListOf<Boolean>()
        backgroundScope.launch { NetworkAvailabilityService(context).changes.collect { states += it } }
        testScheduler.runCurrent()
        assertEquals(listOf(true), states)
    }
}
