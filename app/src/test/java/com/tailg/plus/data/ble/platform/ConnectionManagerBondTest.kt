package com.tailg.plus.data.ble.platform

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConnectionManagerBondTest {
    private val receiver = slot<BroadcastReceiver>()
    private val context = mockk<Context>(relaxed = true).apply {
        every { registerReceiver(capture(receiver), any<IntentFilter>(), any<Int>()) } returns null
        every { unregisterReceiver(any()) } just Runs
    }
    private val device = mockk<BluetoothDevice>().apply {
        every { address } returns "AA:BB:CC:DD:EE:01"
        every { bondState } returns BluetoothDevice.BOND_NONE
        every { createBond() } returns true
    }

    private fun setDevice(manager: ConnectionManager) {
        ConnectionManager::class.java.getDeclaredField("_device").apply { isAccessible = true }.set(manager, device)
    }

    private fun bondEvent(changed: BluetoothDevice, state: Int = BluetoothDevice.BOND_BONDED) =
        Intent(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            .putExtra(BluetoothDevice.EXTRA_DEVICE, changed)
            .putExtra(BluetoothDevice.EXTRA_BOND_STATE, state)

    @Test
    fun pairingAnotherDeviceDoesNotCompleteVehiclePairing() = runTest {
        val manager = ConnectionManager(context, externalScope = backgroundScope)
        setDevice(manager)
        val pairing = async { manager.createBond() }
        testScheduler.runCurrent()
        val other = mockk<BluetoothDevice> { every { address } returns "AA:BB:CC:DD:EE:02" }
        receiver.captured.onReceive(context, bondEvent(other))
        testScheduler.runCurrent()
        assertFalse(pairing.isCompleted)

        receiver.captured.onReceive(context, bondEvent(device))
        // A queued duplicate broadcast must also be harmless after completion.
        receiver.captured.onReceive(context, bondEvent(device))
        assertTrue(pairing.await())
        verify(exactly = 1) { context.unregisterReceiver(receiver.captured) }
        verify { context.registerReceiver(any(), any<IntentFilter>(), Context.RECEIVER_EXPORTED) }
    }

    @Test
    fun immediatePairingResponseIsObserved() = runTest {
        val manager = ConnectionManager(context, externalScope = backgroundScope)
        setDevice(manager)
        every { device.createBond() } answers {
            assertTrue("Receiver must be registered before createBond", receiver.isCaptured)
            receiver.captured.onReceive(context, bondEvent(device))
            true
        }

        assertTrue(manager.createBond())
        verify(exactly = 1) { context.unregisterReceiver(receiver.captured) }
    }

    @Test
    fun declinedPairingReleasesReceiverImmediately() = runTest {
        val manager = ConnectionManager(context, externalScope = backgroundScope)
        setDevice(manager)
        every { device.createBond() } returns false

        assertFalse(manager.createBond())
        verify(exactly = 1) { context.unregisterReceiver(receiver.captured) }
    }

    @Test
    fun cancelledPairingReleasesReceiverAndDoesNotReturnAFailureResult() = runTest {
        val manager = ConnectionManager(context, externalScope = backgroundScope)
        setDevice(manager)
        var returned = false
        val pairing = async {
            manager.createBond()
            returned = true
        }
        testScheduler.runCurrent()
        pairing.cancelAndJoin()

        assertFalse(returned)
        verify(exactly = 1) { context.unregisterReceiver(receiver.captured) }
    }
}
