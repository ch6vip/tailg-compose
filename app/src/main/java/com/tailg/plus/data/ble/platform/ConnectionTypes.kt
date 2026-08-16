package com.tailg.plus.data.ble.platform

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import kotlinx.coroutines.CompletableDeferred

/**
 * Port of Dart `enum ProtocolType`.
 */
enum class ProtocolType { KKS, TLINK, QGJ, UNKNOWN }

/**
 * Port of Dart `enum ConnectionState` (see `ConnectionManager` KDoc for
 * LoginStatus mapping).
 */
enum class ConnectionState {
  DISCONNECTED,
  CONNECTING,
  RECONNECTING,

  /** GATT connected; token / QGJ login not yet confirmed. */
  CONNECTED,

  /** Handshake success path; combined with token → official LOGIN. */
  READY;

  /**
   * Port of Dart `ConnectionStateLabel.label`.
   *
   * NOTE: display labels are kept as string constants here (not string
   * resources) because the enum is used from BLE / service layers without a
   * Context; UI layers that need localized text should map this to a
   * resource. See `strings.xml` extraction follow-up.
   */
  val label: String
    get() = when (this) {
      DISCONNECTED -> "未连接"
      CONNECTING -> "连接中"
      CONNECTED -> "已连接"
      READY -> "已连接"
      RECONNECTING -> "正在重连"
    }
}

/** Port of Dart `enum GattOperationPriority`. */
enum class GattOperationPriority { HIGH, NORMAL, LOW }

/**
 * Port of Dart `_QueuedGattOperation<T>` — one queued GATT operation with its
 * completion deferred. The Dart `Completer` maps to [CompletableDeferred].
 */
internal class QueuedGattOperation<T>(
  val operation: suspend () -> T,
  val priority: GattOperationPriority,
  val deferred: CompletableDeferred<T> = CompletableDeferred(),
)

/** Internal event carried from the `BluetoothGattCallback` to the event loop. */
internal sealed interface GattEvent {
  data class ConnectionStateChanged(val status: Int, val newState: Int) : GattEvent
  data class ServicesDiscovered(val status: Int) : GattEvent
  data class CharacteristicRead(
    val characteristic: BluetoothGattCharacteristic,
    val status: Int,
    val value: ByteArray,
  ) : GattEvent
  data class CharacteristicWrite(
    val characteristic: BluetoothGattCharacteristic,
    val status: Int,
  ) : GattEvent
  data class DescriptorWrite(val descriptor: BluetoothGattDescriptor, val status: Int) : GattEvent
  data class CharacteristicChanged(
    /**
     * Null on API 33+ when the framework fires the characteristic-less
     * `onCharacteristicChanged(gatt, value)` overload; consumers must match
     * on the value payload in that case.
     */
    val characteristic: BluetoothGattCharacteristic?,
    val value: ByteArray,
  ) : GattEvent
  data class MtuChanged(val mtu: Int, val status: Int) : GattEvent
  data class ReadRemoteRssi(val rssi: Int, val status: Int) : GattEvent
}

/**
 * Thrown for failed GATT operations; [status] is the Android `BluetoothGatt`
 * status code.
 */
internal class GattException(val status: Int, message: String) : Exception(message)
