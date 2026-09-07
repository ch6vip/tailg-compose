package com.tailg.plus.data.ble.platform

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import com.tailg.plus.data.ble.BikeState
import com.tailg.plus.data.ble.BleUuids
import com.tailg.plus.data.ble.ModelType
import com.tailg.plus.data.ble.aesEcbEncrypt
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConnectionManagerCallbackTest {
  @Test
  fun firstVoltagePublishesAndDebouncingStillRetainsLatestSample() = runTest {
    val manager = ConnectionManager(ApplicationProvider.getApplicationContext(), externalScope = backgroundScope)
    val publish = ConnectionManager::class.java.getDeclaredMethod("publishBikeState", BikeState::class.java)
      .apply { isAccessible = true }
    val unknown = BikeState(isLocked = true, isPowerOn = false)
    publish.invoke(manager, unknown)
    publish.invoke(manager, unknown.copy(voltage = 48.0))
    assertEquals(48.0, manager.bikeStateFlow.value?.voltage)
    publish.invoke(manager, unknown.copy(voltage = 48.1))
    assertEquals(48.0, manager.bikeStateFlow.value?.voltage)
    assertEquals(48.1, manager.latestBikeState?.voltage)
  }

  @Test
  fun callbacksFromReplacedGattCannotCompleteCurrentRead() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = ConnectionManager(context, externalScope = backgroundScope)
    val oldGatt = mockk<BluetoothGatt>()
    val newGatt = mockk<BluetoothGatt>()
    val callbackField = ConnectionManager::class.java.getDeclaredField("gattCallback")
      .apply { isAccessible = true }
    val callback = callbackField.get(manager) as BluetoothGattCallback
    val gattField = ConnectionManager::class.java.getDeclaredField("_gatt")
      .apply { isAccessible = true }
    val rssiField = ConnectionManager::class.java.getDeclaredField("_rssiDeferred")
      .apply { isAccessible = true }
    val currentRead = CompletableDeferred<Int>()

    gattField.set(manager, oldGatt)
    callback.onReadRemoteRssi(oldGatt, -10, BluetoothGatt.GATT_SUCCESS)
    // The old callback was queued before the switch but is handled afterward.
    gattField.set(manager, newGatt)
    rssiField.set(manager, currentRead)
    callback.onReadRemoteRssi(oldGatt, -20, BluetoothGatt.GATT_SUCCESS)
    testScheduler.runCurrent()
    assertFalse(currentRead.isCompleted)

    callback.onReadRemoteRssi(newGatt, -70, BluetoothGatt.GATT_SUCCESS)
    testScheduler.runCurrent()
    assertEquals(-70, currentRead.await())
  }

  @Test
  fun tlinkLoginCannotCrossAConnectionSwitchDuringResponseDispatch() = runTest {
    val manager = ConnectionManager(ApplicationProvider.getApplicationContext(), externalScope = backgroundScope)
    val oldGatt = mockk<BluetoothGatt>()
    val newGatt = mockk<BluetoothGatt>()
    val notify = BluetoothGattCharacteristic(UUID.fromString(BleUuids.notifyChar), 0, 0)
    field("_gatt").set(manager, oldGatt)
    field("_protocol").set(manager, ProtocolType.TLINK)
    field("_notifyChar").set(manager, notify)
    field("_token").set(manager, "11111111")
    val callback = field("gattCallback").get(manager) as BluetoothGattCallback

    callback.onCharacteristicChanged(oldGatt, notify, aesEcbEncrypt(ModelType.KKS.aesKey, "8503B51101".padEnd(32, '0')))
    // Switch after the event-loop dispatch, before any extra response coroutine.
    backgroundScope.launch {
      field("_gatt").set(manager, newGatt)
      field("_token").set(manager, "22222222")
      field("_protocolLoggedIn").set(manager, false)
    }
    testScheduler.runCurrent()

    assertFalse(manager.isProtocolLoggedIn)
    assertEquals("22222222", manager.token)
  }

  @Test
  fun tlinkAcknowledgementStaysWithTheRequestPresentAtDispatch() = runTest {
    val manager = ConnectionManager(ApplicationProvider.getApplicationContext(), externalScope = backgroundScope)
    val oldGatt = mockk<BluetoothGatt>()
    val newGatt = mockk<BluetoothGatt>()
    val notify = BluetoothGattCharacteristic(UUID.fromString(BleUuids.notifyChar), 0, 0)
    field("_gatt").set(manager, oldGatt)
    field("_protocol").set(manager, ProtocolType.TLINK)
    field("_notifyChar").set(manager, notify)
    @Suppress("UNCHECKED_CAST")
    val slot = field("_tlinkInductionSetDeferred").get(manager) as AtomicDeferred<Boolean>
    val oldAck = CompletableDeferred<Boolean>()
    val newAck = CompletableDeferred<Boolean>()
    slot.set(oldAck)
    val callback = field("gattCallback").get(manager) as BluetoothGattCallback

    callback.onCharacteristicChanged(oldGatt, notify, aesEcbEncrypt(ModelType.KKS.aesKey, "8504B5330201".padEnd(32, '0')))
    backgroundScope.launch {
      field("_gatt").set(manager, newGatt)
      slot.getAndSet(newAck)
    }
    testScheduler.runCurrent()

    assertTrue(oldAck.isCompleted)
    assertEquals(true, oldAck.await())
    assertFalse(newAck.isCompleted)
  }

  private fun field(name: String) = ConnectionManager::class.java.getDeclaredField(name).apply { isAccessible = true }
}
