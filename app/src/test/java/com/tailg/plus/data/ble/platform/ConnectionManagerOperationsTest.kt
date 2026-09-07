package com.tailg.plus.data.ble.platform

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import androidx.test.core.app.ApplicationProvider
import com.tailg.plus.data.ble.BleUuids
import com.tailg.plus.data.ble.RidingMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ConnectionManagerOperationsTest {
  private val inductionCalls: List<Pair<String, suspend (ConnectionManager) -> Any?>> = listOf(
    "_tlinkInductionStatusDeferred" to { it.checkTlinkInduction() },
    "_tlinkInductionSetDeferred" to { it.openTlinkInduction() },
    "_tlinkInductionSetDeferred" to { it.closeTlinkInduction() },
    "_tlinkProximityDistanceDeferred" to { it.setTlinkInductionDistance(10) },
  )

  @Test
  fun inductionWriteFailuresReleaseTheAcknowledgementSlot() = runTest {
    for ((slot, call) in inductionCalls) {
      val gatt = mockk<BluetoothGatt>()
      every { gatt.writeCharacteristic(any(), any(), any()) } throws IllegalStateException("write refused")
      val manager = readyManager(backgroundScope, gatt)

      assertTrue(runCatching { call(manager) }.isFailure)
      assertNull((field(slot).get(manager) as AtomicDeferred<*>).get())
    }
  }

  @Test
  fun inductionCancellationDuringWriteReleasesTheAcknowledgementSlot() = runTest {
    for ((slot, call) in inductionCalls) {
      val gatt = mockk<BluetoothGatt>()
      every { gatt.writeCharacteristic(any(), any(), any()) } returns 0
      val manager = readyManager(backgroundScope, gatt)
      val request = async { call(manager) }
      testScheduler.runCurrent()

      request.cancelAndJoin()

      assertNull((field(slot).get(manager) as AtomicDeferred<*>).get())
    }
  }

  @Test
  fun timedOutRssiReadRetiresTheGattBeforeAnotherReadCanUseIt() = runTest {
    val gatt = mockk<BluetoothGatt>(relaxed = true)
    every { gatt.readRemoteRssi() } returns true
    val manager = readyManager(backgroundScope, gatt)
    val read = async { manager.readRemoteRssi() }
    testScheduler.runCurrent()
    testScheduler.advanceTimeBy(5_000)
    testScheduler.runCurrent()

    assertNull(read.await())
    assertNull(field("_gatt").get(manager))
    assertEquals(ConnectionState.DISCONNECTED, manager.state)
    assertNull(manager.readRemoteRssi())
    verify(exactly = 1) { gatt.readRemoteRssi() }
    verify(exactly = 1) { gatt.close() }
  }

  @Test
  fun cancelledReadCannotDonateItsLateCallbackToTheNextConnection() = runTest {
    val oldGatt = mockk<BluetoothGatt>(relaxed = true)
    every { oldGatt.readCharacteristic(any()) } returns true
    val manager = readyManager(backgroundScope, oldGatt)
    val characteristic = BluetoothGattCharacteristic(UUID.fromString(BleUuids.feb3), 0, 0)
    field("_feb3Char").set(manager, characteristic)
    val first = async { manager.readFeb3() }
    testScheduler.runCurrent()
    first.cancelAndJoin()
    testScheduler.runCurrent()
    assertNull(field("_gatt").get(manager))

    val newGatt = mockk<BluetoothGatt>(relaxed = true)
    every { newGatt.readCharacteristic(any()) } returns true
    field("_gatt").set(manager, newGatt)
    field("_feb3Char").set(manager, characteristic)
    @Suppress("UNCHECKED_CAST")
    val state = field("_state").get(manager) as MutableStateFlow<ConnectionState>
    state.value = ConnectionState.READY
    val second = async { manager.readFeb3() }
    testScheduler.runCurrent()
    val callback = field("gattCallback").get(manager) as BluetoothGattCallback
    callback.onCharacteristicRead(oldGatt, characteristic, byteArrayOf(1), BluetoothGatt.GATT_SUCCESS)
    testScheduler.runCurrent()
    assertFalse(second.isCompleted)
    callback.onCharacteristicRead(newGatt, characteristic, byteArrayOf(2), BluetoothGatt.GATT_SUCCESS)
    assertEquals(listOf<Byte>(2), second.await()?.toList())
  }

  @Test
  fun ridingModeRequiresAValidReadbackMatchingTheRequestedMode() = runTest {
    val responses = listOf(
      byteArrayOf() to false,
      byteArrayOf(0, 7, 0, 2, 0, 1, 0) to false,
      byteArrayOf(0, 7, 0, 2, 0, 3, 0) to true,
    )
    for ((readback, expected) in responses) {
      val gatt = mockk<BluetoothGatt>()
      val manager = readyManager(backgroundScope, gatt)
      val fcc1 = BluetoothGattCharacteristic(UUID.fromString(BleUuids.fcc1), 0, 0)
      field("_fcc1Char").set(manager, fcc1)
      val callback = field("gattCallback").get(manager) as BluetoothGattCallback
      var reads = 0
      every { gatt.readCharacteristic(fcc1) } answers {
        val data = if (reads++ == 0) byteArrayOf(0, 7, 0, 2, 0, 2, 0) else readback
        callback.onCharacteristicRead(gatt, fcc1, data, BluetoothGatt.GATT_SUCCESS)
        true
      }
      every { gatt.writeCharacteristic(fcc1, any(), any()) } answers {
        callback.onCharacteristicWrite(gatt, fcc1, BluetoothGatt.GATT_SUCCESS)
        0
      }

      assertEquals(expected, manager.setRidingMode(RidingMode.sport))
      val actual = when {
        readback.isEmpty() -> RidingMode.standard
        expected -> RidingMode.sport
        else -> RidingMode.eco
      }
      assertEquals(actual, manager.ridingModeFlow.value)
    }
  }

  private fun readyManager(scope: CoroutineScope, gatt: BluetoothGatt): ConnectionManager {
    val manager = ConnectionManager(ApplicationProvider.getApplicationContext(), externalScope = scope)
    field("_gatt").set(manager, gatt)
    field("_protocol").set(manager, ProtocolType.TLINK)
    field("_protocolLoggedIn").set(manager, true)
    field("_token").set(manager, "11111111")
    field("_writeChar").set(manager, BluetoothGattCharacteristic(UUID.fromString(BleUuids.writeChar), 0, 0))
    @Suppress("UNCHECKED_CAST")
    val state = field("_state").get(manager) as MutableStateFlow<ConnectionState>
    state.value = ConnectionState.READY
    return manager
  }

  private fun field(name: String) = ConnectionManager::class.java.getDeclaredField(name).apply { isAccessible = true }
}
