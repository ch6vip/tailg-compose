package com.tailg.plus.data.ble.platform

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.content.Context
import com.tailg.plus.data.ble.BikeState
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
