/**
 * Port of `lib/services/ble_connection_snapshot_guard.dart` (tailg-ble-app).
 *
 * Guards auto-connect / induction decisions against a stale BLE snapshot:
 * a "ready" target only counts if the manager and device we are looking at
 * are the *same instances* the connection started with (Dart `identical` →
 * Kotlin reference equality `===`), and the device id still matches the
 * expected one. Otherwise an old callback may resurrect a connection that
 * the user already abandoned.
 */
package com.tailg.plus.service

import android.bluetooth.BluetoothDevice
import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.data.ble.platform.ConnectionState

/** Dart `BleConnectionSnapshotGuard` — pure function seam, no state. */
class BleConnectionSnapshotGuard {
  fun allowsReadyTarget(
    startManager: ConnectionManager?,
    currentManager: ConnectionManager?,
    startDevice: BluetoothDevice?,
    currentDevice: BluetoothDevice?,
    currentDeviceId: String?,
    expectedDeviceId: String,
    currentState: ConnectionState,
  ): Boolean {
    return currentState == ConnectionState.READY &&
      expectedDeviceId.isNotEmpty() &&
      startManager != null &&
      startDevice != null &&
      startManager === currentManager &&
      startDevice === currentDevice &&
      currentDeviceId == expectedDeviceId
  }
}
