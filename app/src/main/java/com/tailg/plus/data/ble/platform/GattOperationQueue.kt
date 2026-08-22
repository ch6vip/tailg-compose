/**
 * GATT operation queue, extracted from `ConnectionManager.kt`.
 *
 * Serializes BLE GATT operations one at a time by priority (HIGH → NORMAL →
 * LOW), mirroring the Dart `runGattOperation` priority queue. Owns the queue
 * bookkeeping (`_gattPendingByPriority` / `_activeGattOperation` /
 * `_gattRunning`) so ConnectionManager stays focused on connection / protocol
 * state rather than queue mechanics.
 */
package com.tailg.plus.data.ble.platform

import com.tailg.plus.data.ble.BleTimings
import java.util.EnumMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Serialized GATT operation queue with priority ordering (port of Dart queue). */
class GattOperationQueue(
  private val scope: CoroutineScope,
) {
  private val queueLock = Any()

  private val pendingByPriority =
    EnumMap<GattOperationPriority, ArrayDeque<QueuedGattOperation<Any?>>>(GattOperationPriority::class.java).apply {
      for (p in GattOperationPriority.entries) put(p, ArrayDeque())
    }
  @Volatile private var activeOperation: QueuedGattOperation<Any?>? = null
  @Volatile private var running = false

  /**
   * Enqueue [operation] with [priority] and suspend until it completes (or
   * times out after [BleTimings.gattOperationTimeout]). Operations run
   * strictly one at a time, HIGH → NORMAL → LOW. On failure the thrown
   * exception propagates to the caller.
   */
  suspend fun <T> run(priority: GattOperationPriority, operation: suspend () -> T): T {
    @Suppress("UNCHECKED_CAST")
    val queued = QueuedGattOperation(operation, priority) as QueuedGattOperation<Any?>
    synchronized(queueLock) {
      pendingByPriority[priority]?.addLast(queued)
    }
    drain()
    @Suppress("UNCHECKED_CAST")
    return (queued.deferred as CompletableDeferred<T>).await()
  }

  /** Port of Dart `_takeNextGattOperation` — first non-empty priority queue, FIFO. */
  private fun takeNext(): QueuedGattOperation<Any?>? {
    for (p in GattOperationPriority.entries) {
      val queue = pendingByPriority[p] ?: continue
      if (queue.isNotEmpty()) return queue.removeFirst()
    }
    return null
  }

  private fun hasPending(): Boolean = synchronized(queueLock) {
    GattOperationPriority.entries.any { pendingByPriority[it]?.isNotEmpty() == true }
  }

  /** Port of Dart `_drainGattQueue` — single consumer loop. */
  private fun drain() {
    scope.launch {
      synchronized(queueLock) {
        if (running) return@launch
        running = true
      }
      try {
        while (true) {
          val queued = synchronized(queueLock) { takeNext() } ?: break
          synchronized(queueLock) { activeOperation = queued }
          try {
            val result = withTimeout(BleTimings.gattOperationTimeout) { queued.operation() }
            if (!queued.deferred.isCompleted) queued.deferred.complete(result)
          } catch (e: TimeoutCancellationException) {
            // withTimeout fired: fail this operation, keep draining the queue.
            if (!queued.deferred.isCompleted) queued.deferred.completeExceptionally(e)
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            if (!queued.deferred.isCompleted) queued.deferred.completeExceptionally(e)
          } finally {
            synchronized(queueLock) {
              if (activeOperation === queued) activeOperation = null
            }
          }
        }
      } finally {
        synchronized(queueLock) { running = false }
        // Re-drain after the loop exits in case an item was queued between the
        // last take and `running = false`.
        if (hasPending()) drain()
      }
    }
  }

  /** Fail every queued + active operation (Dart `_completePendingGattOperations`). */
  fun completePending(error: Throwable) {
    synchronized(queueLock) {
      val active = activeOperation
      if (active != null && !active.deferred.isCompleted) {
        active.deferred.completeExceptionally(error)
      }
      activeOperation = null
      for (queue in pendingByPriority.values) {
        for (queued in queue) {
          if (!queued.deferred.isCompleted) queued.deferred.completeExceptionally(error)
        }
        queue.clear()
      }
    }
  }
}