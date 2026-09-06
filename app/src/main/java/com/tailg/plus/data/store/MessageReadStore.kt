package com.tailg.plus.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tailg.plus.data.model.OfficialCloudMessage
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val Context.messageReadStoreDataStore by preferencesDataStore(name = "message_read_store")

data class MessageReadState(
    val readIds: Set<String> = emptySet(),
    val hiddenIds: Set<String> = emptySet(),
)

/** Shared read history and badge state for the message center and profile page. */
class MessageReadStore(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.messageReadStoreDataStore)

    companion object {
        const val PREF_READ_IDS = "vehicle_message_read_ids"
        const val PREF_HIDDEN_IDS = "vehicle_message_hidden_ids"
        private val KEY_READ_IDS = stringSetPreferencesKey(PREF_READ_IDS)
        private val KEY_HIDDEN_IDS = stringSetPreferencesKey(PREF_HIDDEN_IDS)
    }

    private val mutex = Mutex()
    private var loaded = false
    private var messageIds: Set<String> = emptySet()
    private val _state = MutableStateFlow(MessageReadState())
    val stateFlow: StateFlow<MessageReadState> = _state.asStateFlow()
    val readIds: Set<String> get() = _state.value.readIds
    val hiddenIds: Set<String> get() = _state.value.hiddenIds
    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    suspend fun ensureLoaded() = mutex.withLock { loadLocked() }

    private suspend fun loadLocked() {
        if (loaded) return
        val prefs = withDataStoreReadTimeout { dataStore.data.first() }
        _state.value = MessageReadState(
            readIds = prefs[KEY_READ_IDS]?.toSet() ?: emptySet(),
            hiddenIds = prefs[KEY_HIDDEN_IDS]?.toSet() ?: emptySet(),
        )
        loaded = true
        updateCount()
    }

    suspend fun persist() = update { it }

    suspend fun replaceState(readIds: Set<String>, hiddenIds: Set<String>) = update {
        MessageReadState(readIds.toSet(), hiddenIds.toSet())
    }

    suspend fun markRead(ids: Iterable<String>) = update { it.copy(readIds = it.readIds + ids) }

    suspend fun hideAndRead(ids: Iterable<String>) {
        val snapshot = ids.toSet()
        update { it.copy(readIds = it.readIds + snapshot, hiddenIds = it.hiddenIds + snapshot) }
    }

    private suspend fun update(transform: (MessageReadState) -> MessageReadState) = mutex.withLock {
        loadLocked()
        val next = transform(_state.value)
        // Complete the disk write and publish its snapshot even when a page is closed.
        withContext(NonCancellable) {
            dataStore.edit { prefs ->
                prefs[KEY_READ_IDS] = next.readIds
                prefs[KEY_HIDDEN_IDS] = next.hiddenIds
            }
            _state.value = next
            updateCount()
        }
    }

    suspend fun syncFromCloudMessages(
        vehicleMessages: List<OfficialCloudMessage>,
        systemMessages: List<OfficialCloudMessage>,
    ) = mutex.withLock {
        loadLocked()
        messageIds = (vehicleMessages + systemMessages).mapTo(mutableSetOf()) { it.id }
        updateCount()
    }

    private fun updateCount() {
        val snapshot = _state.value
        _unreadCount.value = messageIds.count { it !in snapshot.readIds && it !in snapshot.hiddenIds }
    }

    fun setUnreadCount(count: Int) {
        _unreadCount.value = count.coerceAtLeast(0)
    }

    fun resetForTest() {
        _state.value = MessageReadState()
        _unreadCount.value = 0
        messageIds = emptySet()
        loaded = false
    }
}
