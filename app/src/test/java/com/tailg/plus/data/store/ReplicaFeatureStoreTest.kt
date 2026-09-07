package com.tailg.plus.data.store

import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
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
class ReplicaFeatureStoreTest {
  private fun store(data: ShortcutTestDataStore) = ReplicaFeatureStore(mockk(), dataStore = data)

  @Test
  fun failedKeyUpdateDoesNotLeakIntoTheNextSave() = runTest {
    val data = ShortcutTestDataStore()
    val store = store(data)
    val first = store.createNfcKey("first", "card")
    val second = store.createNfcKey("second", "phone")
    store.saveNfcKeys(listOf(first))
    data.failWrites = true

    assertTrue(runCatching { store.updateNfcKeys { emptyList() } }.exceptionOrNull() is IOException)
    assertEquals(listOf(first), store.loadNfcKeys())

    data.failWrites = false
    assertEquals(listOf(first, second), store.updateNfcKeys { it + second })
    assertEquals(listOf(first, second), store(data).loadNfcKeys())
  }

  @Test
  fun concurrentMemberChangesMergeAgainstTheLatestCommittedList() = runTest {
    val data = ShortcutTestDataStore()
    val firstScreen = store(data)
    val secondScreen = store(data)
    val first = firstScreen.createShareMember("first", "1")
    val second = firstScreen.createShareMember("second", "2")
    val release = CompletableDeferred<Unit>()
    data.beforeWrite = { release.await() }
    val firstSave = async { firstScreen.updateShareMembers { it + first } }
    val secondSave = async { secondScreen.updateShareMembers { it + second } }
    testScheduler.runCurrent()
    release.complete(Unit)
    firstSave.await()
    secondSave.await()

    assertEquals(listOf(first, second), firstScreen.loadShareMembers())
  }

  @Test
  fun newScreenReadWaitsForAnInProgressSave() = runTest {
    val data = ShortcutTestDataStore()
    val store = store(data)
    val record = store.createNfcKey("saved", "card")
    val release = CompletableDeferred<Unit>()
    data.beforeWrite = { release.await() }
    val save = async { store.updateNfcKeys { it + record } }
    testScheduler.runCurrent()
    val read = async { store.loadNfcKeys() }
    testScheduler.runCurrent()
    assertFalse(read.isCompleted)

    release.complete(Unit)
    save.await()
    assertEquals(listOf(record), read.await())
  }
}
