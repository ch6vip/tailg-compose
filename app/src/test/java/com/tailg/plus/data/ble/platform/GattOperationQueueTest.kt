/**
 * Tests for [GattOperationQueue] — the serialized, priority-ordered GATT
 * operation queue extracted from `ConnectionManager.kt`.
 *
 * Uses `kotlinx-coroutines-test` (StandardTestDispatcher) so the queue's
 * internal `scope.launch` drain runs on the test scheduler and is driven
 * deterministically with `runCurrent()` / `advanceUntilIdle()`.
 */
package com.tailg.plus.data.ble.platform

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GattOperationQueueTest {

  @Test
  fun serializesOperationsAndReturnsValues() = runTest {
    val queue = GattOperationQueue(this)
    val seen = mutableListOf<String>()

    val a = launch { seen.add(queue.run(GattOperationPriority.NORMAL) { "a" }) }
    val b = launch { seen.add(queue.run(GattOperationPriority.NORMAL) { "b" }) }
    testScheduler.advanceUntilIdle()

    assertEquals(listOf("a", "b"), seen)
    a.join()
    b.join()
  }

  @Test
  fun runsHighPriorityBeforeQueuedNormalAndLow() = runTest {
    val order = mutableListOf<String>()
    val queue = GattOperationQueue(this)
    val gate = CompletableDeferred<Unit>()

    // A NORMAL op that blocks on the gate keeps the queue occupied.
    launch { order.add(queue.run(GattOperationPriority.NORMAL) { gate.await(); "normal" }) }
    testScheduler.runCurrent()

    // While the NORMAL op is active (blocked), enqueue LOW then HIGH.
    launch { order.add(queue.run(GattOperationPriority.LOW) { "low" }) }
    launch { order.add(queue.run(GattOperationPriority.HIGH) { "high" }) }
    testScheduler.runCurrent()

    // Release the gate: the queue should pick HIGH (priority) before LOW.
    gate.complete(Unit)
    testScheduler.advanceUntilIdle()

    assertEquals(listOf("normal", "high", "low"), order)
  }
}