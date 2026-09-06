package com.tailg.plus.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.tailg.plus.data.model.OfficialCloudMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageReadStoreTest {
    private class MemoryDataStore : DataStore<Preferences> {
        private val mutex = Mutex()
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            yield()
            transform(data.value).also { data.value = it }
        }
    }

    private fun message(id: Int) = OfficialCloudMessage.vehicle(mapOf("msgId" to id))

    @Test
    fun markingAndHidingMessagesImmediatelyUpdateSharedBadge() = runTest {
        val store = MessageReadStore(MemoryDataStore())
        store.syncFromCloudMessages(listOf(message(1), message(2), message(3)), emptyList())
        assertEquals(3, store.unreadCount.value)
        store.markRead(listOf("vehicle:1"))
        assertEquals(2, store.unreadCount.value)
        store.hideAndRead(listOf("vehicle:2"))
        assertEquals(1, store.unreadCount.value)
        assertEquals(setOf("vehicle:2"), store.stateFlow.value.hiddenIds)
    }

    @Test
    fun concurrentReadAndHideOperationsPersistTheirUnion() = runTest {
        val dataStore = MemoryDataStore()
        val store = MessageReadStore(dataStore)
        store.syncFromCloudMessages((1..40).map(::message), emptyList())
        (1..40).map { id ->
            async {
                if (id % 2 == 0) store.hideAndRead(listOf("vehicle:$id"))
                else store.markRead(listOf("vehicle:$id"))
            }
        }.awaitAll()
        val restored = MessageReadStore(dataStore)
        restored.ensureLoaded()
        assertEquals((1..40).map { "vehicle:$it" }.toSet(), restored.readIds)
        assertEquals((2..40 step 2).map { "vehicle:$it" }.toSet(), restored.hiddenIds)
        assertEquals(0, store.unreadCount.value)
    }
}
