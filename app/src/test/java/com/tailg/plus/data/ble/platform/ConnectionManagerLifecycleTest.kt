package com.tailg.plus.data.ble.platform

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tailg.plus.data.ble.BleTimings
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ConnectionManagerLifecycleTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun device(gatt: BluetoothGatt) = mockk<BluetoothDevice> {
        every { connectGatt(any(), any(), any(), any<Int>()) } returns gatt
    }

    @Test
    fun disconnectStopsAnInitialAttemptWithoutRetryingOrRestoringTheDevice() = runTest {
        val gatt = mockk<BluetoothGatt>(relaxed = true)
        val device = device(gatt)
        val manager = ConnectionManager(context, externalScope = backgroundScope)
        val connecting = async { runCatching { manager.connect(device) } }
        testScheduler.runCurrent()
        assertEquals(ConnectionState.CONNECTING, manager.state)

        manager.disconnect()
        assertTrue(connecting.await().isFailure)
        testScheduler.runCurrent()

        verify(exactly = 1) { device.connectGatt(any(), any(), any(), any<Int>()) }
        assertEquals(ConnectionState.DISCONNECTED, manager.state)
        assertNull(manager.device)
        assertNull(manager.token)
    }

    @Test
    fun connectionTimeoutUsesTheRetryBudget() = runTest {
        val gatt = mockk<BluetoothGatt>(relaxed = true)
        val device = device(gatt)
        val manager = ConnectionManager(context, externalScope = backgroundScope)

        assertTrue(runCatching { manager.connect(device) }.isFailure)

        verify(exactly = 3) { device.connectGatt(any(), any(), any(), any<Int>()) }
        assertEquals(ConnectionState.DISCONNECTED, manager.state)
    }

    @Test
    fun callerCancellationDoesNotRetryTheConnection() = runTest {
        val gatt = mockk<BluetoothGatt>(relaxed = true)
        val device = device(gatt)
        val manager = ConnectionManager(context, externalScope = backgroundScope)
        val connecting = async { manager.connect(device) }
        testScheduler.runCurrent()
        connecting.cancelAndJoin()

        verify(exactly = 1) { device.connectGatt(any(), any(), any(), any<Int>()) }
        assertEquals(ConnectionState.DISCONNECTED, manager.state)
        assertNull(manager.device)
    }

    @Test
    fun aFailedInitialGattDoesNotDisableTheNextHandshakeWatchdog() = runTest {
        val failedGatt = mockk<BluetoothGatt>(relaxed = true)
        val connectedGatt = mockk<BluetoothGatt>(relaxed = true)
        var attempts = 0
        val device = mockk<BluetoothDevice> {
            every { connectGatt(any(), any(), any(), any<Int>()) } answers {
                val callback = thirdArg<BluetoothGattCallback>()
                if (++attempts == 1) {
                    callback.onConnectionStateChange(failedGatt, 133, BluetoothProfile.STATE_DISCONNECTED)
                    failedGatt
                } else {
                    every { connectedGatt.services } returns emptyList()
                    every { connectedGatt.discoverServices() } answers {
                        callback.onServicesDiscovered(connectedGatt, BluetoothGatt.GATT_SUCCESS)
                        true
                    }
                    callback.onConnectionStateChange(connectedGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
                    connectedGatt
                }
            }
        }
        val manager = ConnectionManager(context, externalScope = backgroundScope)
        manager.connect(device)
        assertEquals(ConnectionState.CONNECTED, manager.state)

        advanceTimeBy(BleTimings.readyHandshakeTimeout.inWholeMilliseconds)
        testScheduler.runCurrent()

        assertEquals(2, attempts)
        assertEquals(ConnectionState.DISCONNECTED, manager.state)
        verify(exactly = 1) { connectedGatt.close() }
    }

    @Test
    fun initialGattFailuresDoNotStartACompetingAutoReconnectLoop() = runTest {
        var attempts = 0
        val device = mockk<BluetoothDevice> {
            every { connectGatt(any(), any(), any(), any<Int>()) } answers {
                attempts++
                val gatt = mockk<BluetoothGatt>(relaxed = true)
                thirdArg<BluetoothGattCallback>().onConnectionStateChange(gatt, 133, BluetoothProfile.STATE_DISCONNECTED)
                gatt
            }
        }
        val manager = ConnectionManager(context, externalScope = backgroundScope)
        assertTrue(runCatching { manager.connect(device) }.isFailure)
        advanceTimeBy(60_000)
        testScheduler.runCurrent()

        assertEquals(3, attempts)
        assertEquals(ConnectionState.DISCONNECTED, manager.state)
    }

    @Test
    fun anUnexpectedDisconnectAfterLoginStartsANewReconnectCycle() = runTest {
        val oldGatt = mockk<BluetoothGatt>(relaxed = true)
        val newGatt = mockk<BluetoothGatt>(relaxed = true)
        val device = device(newGatt)
        val manager = ConnectionManager(context, externalScope = backgroundScope)
        field("_gatt").set(manager, oldGatt)
        field("_device").set(manager, device)
        field("_protocol").set(manager, ProtocolType.KKS)
        ConnectionManager::class.java.getDeclaredMethod("markProtocolLoggedIn", String::class.java)
            .apply { isAccessible = true }.invoke(manager, "11111111")
        val callback = field("gattCallback").get(manager) as BluetoothGattCallback

        callback.onConnectionStateChange(oldGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        testScheduler.runCurrent()
        assertEquals(ConnectionState.RECONNECTING, manager.state)
        advanceTimeBy(3500)
        testScheduler.runCurrent()

        verify(exactly = 1) { device.connectGatt(any(), any(), any(), any<Int>()) }
        verify(exactly = 1) { oldGatt.close() }
        manager.disconnect()
        verify(exactly = 1) { newGatt.close() }
    }

    @Test
    fun handshakeWatchdogReleasesTheGattConnection() = runTest {
        val gatt = mockk<BluetoothGatt>(relaxed = true)
        val manager = ConnectionManager(context, externalScope = backgroundScope)
        field("_gatt").set(manager, gatt)
        ConnectionManager::class.java.getDeclaredMethod("setState", ConnectionState::class.java)
            .apply { isAccessible = true }.invoke(manager, ConnectionState.CONNECTED)

        testScheduler.runCurrent()
        advanceTimeBy(BleTimings.readyHandshakeTimeout.inWholeMilliseconds)
        testScheduler.runCurrent()

        assertEquals(ConnectionState.DISCONNECTED, manager.state)
        verify(exactly = 1) { gatt.disconnect() }
        verify(exactly = 1) { gatt.close() }
    }

    @Test
    fun reconnectRetriesServiceDiscoveryFailuresUntilItsBudgetIsExhausted() = runTest {
        val oldGatt = mockk<BluetoothGatt>(relaxed = true)
        val attempts = mutableListOf<BluetoothGatt>()
        val device = mockk<BluetoothDevice> {
            every { connectGatt(any(), any(), any(), any<Int>()) } answers {
                val gatt = mockk<BluetoothGatt>(relaxed = true) {
                    every { discoverServices() } returns false
                }
                attempts += gatt
                thirdArg<BluetoothGattCallback>().onConnectionStateChange(
                    gatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED,
                )
                gatt
            }
        }
        val manager = ConnectionManager(context, externalScope = backgroundScope)
        field("_gatt").set(manager, oldGatt)
        field("_device").set(manager, device)
        field("_protocol").set(manager, ProtocolType.KKS)
        ConnectionManager::class.java.getDeclaredMethod("markProtocolLoggedIn", String::class.java)
            .apply { isAccessible = true }.invoke(manager, "11111111")
        val callback = field("gattCallback").get(manager) as BluetoothGattCallback

        callback.onConnectionStateChange(oldGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        advanceTimeBy(300_000)
        testScheduler.runCurrent()

        assertEquals(8, attempts.size)
        assertEquals(ConnectionState.DISCONNECTED, manager.state)
        assertNull(manager.token)
        attempts.forEach { gatt -> verify(exactly = 1) { gatt.close() } }
    }

    @Test
    fun reconnectDoesNotTreatServiceDiscoveryAsProtocolLogin() = runTest {
        val (manager, attempts) = reconnectWithoutLogin(backgroundScope)

        advanceTimeBy(300_000)
        testScheduler.runCurrent()

        assertEquals(8, attempts.size)
        assertEquals(ConnectionState.DISCONNECTED, manager.state)
        assertNull(manager.token)
        attempts.forEach { gatt -> verify(exactly = 1) { gatt.close() } }
    }

    @Test
    fun disconnectWhileReconnectWaitsForLoginStopsFurtherAttempts() = runTest {
        val (manager, attempts) = reconnectWithoutLogin(backgroundScope)
        advanceTimeBy(4_000)
        testScheduler.runCurrent()
        assertEquals(ConnectionState.CONNECTED, manager.state)

        manager.disconnect()
        advanceTimeBy(300_000)
        testScheduler.runCurrent()

        assertEquals(1, attempts.size)
        assertEquals(ConnectionState.DISCONNECTED, manager.state)
        assertNull(manager.device)
        verify(exactly = 1) { attempts.single().close() }
    }

    @Test
    fun successfulReconnectStopsRetryingAndALaterDisconnectCanReconnectAgain() = runTest {
        val (manager, attempts) = reconnectWithoutLogin(backgroundScope)
        advanceTimeBy(4_000)
        testScheduler.runCurrent()
        ConnectionManager::class.java.getDeclaredMethod("markProtocolLoggedIn", String::class.java)
            .apply { isAccessible = true }.invoke(manager, "22222222")
        advanceTimeBy(40_000)
        testScheduler.runCurrent()

        assertEquals(ConnectionState.READY, manager.state)
        assertEquals(1, attempts.size)
        verify(exactly = 0) { attempts.single().close() }

        val callback = field("gattCallback").get(manager) as BluetoothGattCallback
        callback.onConnectionStateChange(attempts.single(), BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        advanceTimeBy(4_000)
        testScheduler.runCurrent()
        assertEquals(2, attempts.size)
        assertEquals(ConnectionState.CONNECTED, manager.state)
        manager.disconnect()
    }

    private fun reconnectWithoutLogin(scope: CoroutineScope): Pair<ConnectionManager, MutableList<BluetoothGatt>> {
        val oldGatt = mockk<BluetoothGatt>(relaxed = true)
        val attempts = mutableListOf<BluetoothGatt>()
        val device = mockk<BluetoothDevice> {
            every { connectGatt(any(), any(), any(), any<Int>()) } answers {
                val callback = thirdArg<BluetoothGattCallback>()
                val gatt = mockk<BluetoothGatt>(relaxed = true)
                every { gatt.services } returns emptyList()
                every { gatt.discoverServices() } answers {
                    callback.onServicesDiscovered(gatt, BluetoothGatt.GATT_SUCCESS)
                    true
                }
                attempts += gatt
                callback.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
                gatt
            }
        }
        val manager = ConnectionManager(context, externalScope = scope)
        field("_gatt").set(manager, oldGatt)
        field("_device").set(manager, device)
        field("_protocol").set(manager, ProtocolType.KKS)
        ConnectionManager::class.java.getDeclaredMethod("markProtocolLoggedIn", String::class.java)
            .apply { isAccessible = true }.invoke(manager, "11111111")
        val callback = field("gattCallback").get(manager) as BluetoothGattCallback
        callback.onConnectionStateChange(oldGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        return manager to attempts
    }

    private fun field(name: String) = ConnectionManager::class.java.getDeclaredField(name).apply { isAccessible = true }
}
