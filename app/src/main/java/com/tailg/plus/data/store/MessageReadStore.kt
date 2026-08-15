package com.tailg.plus.data.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tailg.plus.data.model.OfficialCloudMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

private val Context.messageReadStoreDataStore by preferencesDataStore(name = "message_read_store")

/**
 * Port of `lib/services/message_read_store.dart`.
 *
 * Local read/hidden state for official cloud messages, shared by the message
 * center and the mine-page bell badge. Dart `SharedPreferences` string lists →
 * DataStore Preferences string sets; key names match the Dart constants
 * (`vehicle_message_read_ids` / `vehicle_message_hidden_ids`). Ids are stored
 * sorted, like the Dart `_sortedIds`.
 *
 * Deviations:
 * - Dart `ValueNotifier<int> unreadCount` → [unreadCount] (`StateFlow<Int>`),
 *   collected by the badge composables.
 * - Dart `getStringList` ↔ Kotlin `Set<String>`; ordering is irrelevant because
 *   both sides sort on write and treat the ids as a set.
 */
class MessageReadStore(
    private val context: Context,
) {

    companion object {
        /** Dart `MessageReadStore.prefReadIds`. */
        const val PREF_READ_IDS = "vehicle_message_read_ids"

        /** Dart `MessageReadStore.prefHiddenIds`. */
        const val PREF_HIDDEN_IDS = "vehicle_message_hidden_ids"

        private val KEY_READ_IDS = stringSetPreferencesKey(PREF_READ_IDS)
        private val KEY_HIDDEN_IDS = stringSetPreferencesKey(PREF_HIDDEN_IDS)
    }

    private val _unreadCount = MutableStateFlow(0)

    /** Dart `unreadCount` notifier. */
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _readIds = mutableSetOf<String>()
    private val _hiddenIds = mutableSetOf<String>()
    private var loaded = false

    /** Dart `readIds`: immutable snapshot. */
    val readIds: Set<String> get() = _readIds.toSet()

    /** Dart `hiddenIds`: immutable snapshot. */
    val hiddenIds: Set<String> get() = _hiddenIds.toSet()

    /** Dart `ensureLoaded()`: idempotent one-time load. */
    suspend fun ensureLoaded() {
        if (loaded) return
        val prefs = context.messageReadStoreDataStore.data.first()
        _readIds.clear()
        _readIds.addAll(prefs[KEY_READ_IDS] ?: emptySet())
        _hiddenIds.clear()
        _hiddenIds.addAll(prefs[KEY_HIDDEN_IDS] ?: emptySet())
        loaded = true
    }

    /** Dart `persist()`: write both sets, sorted. */
    suspend fun persist() {
        context.messageReadStoreDataStore.edit { prefs ->
            prefs[KEY_READ_IDS] = sortedIds(_readIds).toSet()
            prefs[KEY_HIDDEN_IDS] = sortedIds(_hiddenIds).toSet()
        }
    }

    /** Dart `_sortedIds`. */
    private fun sortedIds(ids: Set<String>): List<String> = ids.sorted()

    /** Dart `replaceState`. */
    suspend fun replaceState(readIds: Set<String>, hiddenIds: Set<String>) {
        ensureLoaded()
        _readIds.clear()
        _readIds.addAll(readIds)
        _hiddenIds.clear()
        _hiddenIds.addAll(hiddenIds)
        persist()
    }

    /** Dart `markRead`: only persists when the set actually grew. */
    suspend fun markRead(ids: Iterable<String>) {
        ensureLoaded()
        val before = _readIds.size
        _readIds.addAll(ids)
        if (_readIds.size != before) {
            persist()
        }
    }

    /** Dart `hideAndRead`. */
    suspend fun hideAndRead(ids: Iterable<String>) {
        ensureLoaded()
        _hiddenIds.addAll(ids)
        _readIds.addAll(ids)
        persist()
    }

    /**
     * Dart `syncFromCloudMessages`: recompute the badge from the latest cloud
     * message lists, ignoring hidden ids.
     */
    suspend fun syncFromCloudMessages(
        vehicleMessages: List<OfficialCloudMessage>,
        systemMessages: List<OfficialCloudMessage>,
    ) {
        ensureLoaded()
        val visibleIds = buildSet {
            for (message in vehicleMessages) {
                if (message.id !in _hiddenIds) add(message.id)
            }
            for (message in systemMessages) {
                if (message.id !in _hiddenIds) add(message.id)
            }
        }
        val next = visibleIds.count { it !in _readIds }
        if (_unreadCount.value != next) {
            _unreadCount.value = next
        }
    }

    /**
     * Dart `setUnreadCount`: force the badge without wiping read history
     * (used when lists are empty); negative counts clamp to zero.
     */
    fun setUnreadCount(count: Int) {
        val next = if (count < 0) 0 else count
        if (_unreadCount.value != next) {
            _unreadCount.value = next
        }
    }

    /** Dart `resetForTest`. */
    fun resetForTest() {
        _readIds.clear()
        _hiddenIds.clear()
        _unreadCount.value = 0
        loaded = false
    }
}
