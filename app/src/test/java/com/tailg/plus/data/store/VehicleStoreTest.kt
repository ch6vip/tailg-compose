package com.tailg.plus.data.store

import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VehicleStoreTest {
  private fun store(data: ShortcutTestDataStore) = VehicleStore(mockk(), dataStore = data)

  @Test
  fun failedMutationsLeaveThePublishedAndPersistedVehicleUnchanged() = runTest {
    val data = ShortcutTestDataStore()
    val store = store(data)
    store.upsert("a", "A")
    store.upsert("b", "B")
    val original = store.vehicles
    val mutations: List<suspend () -> Unit> = listOf(
      { store.upsert("c", "C", makeDefault = true) },
      { store.rename("a", "failed rename") },
      { store.setDefault("b") },
      { store.remove("a") },
    )
    data.failWrites = true
    for (mutation in mutations) {
      assertTrue(runCatching { mutation() }.exceptionOrNull() is IOException)
      assertEquals(original, store.vehicles)
      assertEquals(original, store.vehiclesFlow.value)
      assertEquals("a", store.defaultVehicleId)
      assertEquals("a", store.defaultVehicleFlow.value?.id)
    }
    data.failWrites = false
    store.rename("b", "B updated")
    val restored = store(data).apply { init() }
    assertEquals(listOf("a", "b"), restored.vehicles.map { it.id })
    assertEquals("A", restored.vehicles.first().name)
    assertEquals("a", restored.defaultVehicleId)
  }

  @Test
  fun defaultSelectionIsPublishedOnlyAfterTheWriteCommits() = runTest {
    val data = ShortcutTestDataStore()
    val store = store(data)
    store.upsert("a", "A")
    store.upsert("b", "B")
    val finishWrite = CompletableDeferred<Unit>()
    data.beforeWrite = { finishWrite.await() }
    val change = async { store.setDefault("b") }
    testScheduler.runCurrent()

    assertEquals("a", store.defaultVehicleId)
    assertEquals("a", store.defaultVehicleFlow.value?.id)
    finishWrite.complete(Unit)
    change.await()

    assertEquals("b", store.defaultVehicleId)
    assertEquals("b", store.defaultVehicleFlow.value?.id)
    assertEquals("b", store(data).apply { init() }.defaultVehicleId)
  }
}
