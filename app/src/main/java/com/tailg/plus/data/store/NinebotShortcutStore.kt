package com.tailg.plus.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tailg.plus.data.model.NinebotShortcutLayout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.ninebotShortcutDataStore by preferencesDataStore(name = "ninebot_shortcuts")

/** Local layout preferences are isolated by the selected vehicle's stable key. */
class NinebotShortcutStore(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.ninebotShortcutDataStore)

    fun observe(vehicleKey: String): Flow<NinebotShortcutLayout> {
        val key = preferenceKey(vehicleKey)
        return dataStore.data.map { NinebotShortcutLayout.decode(it[key]) }.distinctUntilChanged()
    }

    suspend fun save(vehicleKey: String, layout: NinebotShortcutLayout) {
        val key = preferenceKey(vehicleKey)
        dataStore.edit { it[key] = NinebotShortcutLayout.decode(layout.encode()).encode() }
    }

    private fun preferenceKey(vehicleKey: String): Preferences.Key<String> {
        require(vehicleKey.isNotBlank()) { "A selected vehicle is required for shortcut preferences" }
        return stringPreferencesKey("layout_$vehicleKey")
    }
}
