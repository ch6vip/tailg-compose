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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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

  @Test
  fun cancelledQueuedCommandIsNeverExecuted() = runTest {
    val queue = GattOperationQueue(this)
    val gate = CompletableDeferred<Unit>()
    launch { queue.run(GattOperationPriority.NORMAL) { gate.await() } }
    testScheduler.runCurrent()

    var sent = false
    val command = launch { queue.run(GattOperationPriority.HIGH) { sent = true } }
    testScheduler.runCurrent()
    command.cancelAndJoin()
    gate.complete(Unit)
    testScheduler.advanceUntilIdle()

    assertFalse(sent)
  }

  @Test
  fun cancellingActiveCommandReleasesQueueWithoutWaitingForGattTimeout() = runTest {
    val queue = GattOperationQueue(this)
    val gate = CompletableDeferred<Unit>()
    var sent = false
    val command = launch {
      queue.run(GattOperationPriority.NORMAL) { gate.await(); sent = true }
    }
    testScheduler.runCurrent()
    command.cancelAndJoin()
    val next = async { queue.run(GattOperationPriority.NORMAL) { "next" } }
    testScheduler.runCurrent()

    assertTrue(next.isCompleted)
    assertEquals("next", next.await())
    assertFalse(sent)
    assertEquals(0L, testScheduler.currentTime)
  }

  @Test
  fun disconnectCancelsActiveWorkBeforeNewSessionCanUseQueue() = runTest {
    val queue = GattOperationQueue(this)
    val gate = CompletableDeferred<Unit>()
    var sent = false
    val oldCommand = launch {
      queue.run(GattOperationPriority.NORMAL) { gate.await(); sent = true }
    }
    testScheduler.runCurrent()

    queue.completePending(CancellationException("disconnected"))
    val next = async { queue.run(GattOperationPriority.NORMAL) { "new session" } }
    testScheduler.runCurrent()
    gate.complete(Unit)
    testScheduler.advanceUntilIdle()

    assertTrue(oldCommand.isCancelled)
    assertEquals("new session", next.await())
    assertFalse(sent)
  }
}
