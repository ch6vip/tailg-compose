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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
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
    currentCoroutineContext().ensureActive()
    scope.coroutineContext.ensureActive()
    @Suppress("UNCHECKED_CAST")
    val queued = QueuedGattOperation(operation, priority) as QueuedGattOperation<Any?>
    synchronized(queueLock) {
      pendingByPriority[priority]?.addLast(queued)
    }
    drain()
    return try {
      @Suppress("UNCHECKED_CAST")
      (queued.deferred as CompletableDeferred<T>).await()
    } catch (e: CancellationException) {
      // Cancelling an await does not cancel an independent CompletableDeferred.
      // Remove the write too, otherwise a timed-out command can execute later.
      synchronized(queueLock) {
        pendingByPriority[priority]?.remove(queued)
        queued.deferred.cancel(e)
        queued.job?.cancel(e)
      }
      throw e
    }
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
          val queued = synchronized(queueLock) {
            takeNext()?.also { activeOperation = it }
          } ?: break
          try {
            supervisorScope {
              // A child per operation lets cancellation stop the active write
              // without cancelling the drain or unrelated queued operations.
              val task = async(start = CoroutineStart.LAZY) {
                withTimeout(BleTimings.gattOperationTimeout) { queued.operation() }
              }
              synchronized(queueLock) {
                queued.job = task
                if (queued.deferred.isCompleted) task.cancel()
              }
              try {
                task.start()
                queued.deferred.complete(task.await())
              } catch (e: CancellationException) {
                queued.deferred.completeExceptionally(e)
                currentCoroutineContext().ensureActive()
              } catch (e: Exception) {
                queued.deferred.completeExceptionally(e)
              }
            }
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
        if (scope.isActive && hasPending()) drain()
      }
    }.invokeOnCompletion { cause ->
      // Also covers a launch into a scope that was cancelled before it started.
      if (cause is CancellationException && !scope.isActive) completePending(cause)
    }
  }

  /** Fail every queued + active operation (Dart `_completePendingGattOperations`). */
  fun completePending(error: Throwable) {
    synchronized(queueLock) {
      val active = activeOperation
      if (active != null && !active.deferred.isCompleted) {
        active.deferred.completeExceptionally(error)
      }
      active?.job?.cancel(CancellationException("GATT operation aborted", error))
      for (queue in pendingByPriority.values) {
        for (queued in queue) {
          if (!queued.deferred.isCompleted) queued.deferred.completeExceptionally(error)
        }
        queue.clear()
      }
    }
  }
}
