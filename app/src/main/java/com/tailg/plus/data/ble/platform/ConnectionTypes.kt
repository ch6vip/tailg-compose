package com.tailg.plus.data.ble.platform

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import java.util.concurrent.atomic.AtomicReference
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

/**
 * Thread-safe holder for a nullable [CompletableDeferred] using [AtomicReference],
 * replacing the `@Volatile var` + `===` pattern that had a race window between
 * `complete()` and `remove()`.
 */
internal class AtomicDeferred<T>(
  private val ref: AtomicReference<CompletableDeferred<T>?> = AtomicReference(null),
) {
  /** Atomically set the deferred if none is currently held. */
  fun set(deferred: CompletableDeferred<T>): Boolean =
    ref.compareAndSet(null, deferred)

  /** Atomically replace the held deferred, returning the previous one. */
  fun getAndSet(deferred: CompletableDeferred<T>?): CompletableDeferred<T>? =
    ref.getAndSet(deferred)

  /** Atomically clear and return the current deferred. */
  fun clear(): CompletableDeferred<T>? = ref.getAndSet(null)

  /** Atomically complete the current deferred with [value] and clear it. */
  fun complete(value: T) {
    val d = ref.getAndSet(null)
    if (d != null && !d.isCompleted) d.complete(value)
  }

  /** Atomically complete the current deferred exceptionally and clear it. */
  fun completeExceptionally(error: Throwable) {
    val d = ref.getAndSet(null)
    if (d != null && !d.isCompleted) d.completeExceptionally(error)
  }

  /** Complete with [value] only if the held reference matches [expected]. */
  fun completeIfSame(expected: CompletableDeferred<T>?, value: T) {
    if (expected == null) return
    ref.compareAndSet(expected, null)
    if (!expected.isCompleted) expected.complete(value)
  }

  /** Atomically clear only if the current value is [expected]. */
  fun compareAndSet(expected: CompletableDeferred<T>?, new: CompletableDeferred<T>?): Boolean =
    ref.compareAndSet(expected, new)

  fun get(): CompletableDeferred<T>? = ref.get()
}

/** Internal event carried from the `BluetoothGattCallback` to the event loop. */
internal sealed interface GattEvent {
  /**
   * [gatt] identifies the emitting [BluetoothGatt] instance so the event loop
   * can drop late events from a superseded connection (a stale DISCONNECTED
   * must not fail a freshly started connect attempt).
   */
  data class ConnectionStateChanged(
    val gatt: BluetoothGatt?,
    val status: Int,
    val newState: Int,
  ) : GattEvent
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
