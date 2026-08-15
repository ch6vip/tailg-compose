/**
 * Key-value persistence seam for induction-mode settings.
 *
 * Dart used `SharedPreferences` with dynamic per-vehicle keys
 * (`induction_enabled_<carId|modelType|default>`,
 * `induction_distance_<carId|modelType|default>`). Per CONVENTIONS.md the
 * Kotlin line uses DataStore Preferences; the key strings are kept identical
 * so a future migration can map values 1:1. The interface keeps the service
 * logic pure-JVM testable (mock the seam instead of Android storage).
 */
package com.tailg.plus.service

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** Dedicated DataStore file for induction-mode settings (separate from app prefs). */
private val Context.inductionDataStore by preferencesDataStore(name = "induction_mode")

/** Dart `SharedPreferences` surface used by the induction + manual-mode services. */
interface InductionPrefs {
  suspend fun loadBoolean(key: String, default: Boolean): Boolean

  suspend fun saveBoolean(key: String, value: Boolean)

  suspend fun loadInt(key: String, default: Int): Int

  suspend fun saveInt(key: String, value: Int)

  suspend fun loadString(key: String, default: String): String

  suspend fun saveString(key: String, value: String)
}

/** DataStore-backed [InductionPrefs]. */
class DataStoreInductionPrefs(private val context: Context) : InductionPrefs {

  override suspend fun loadBoolean(key: String, default: Boolean): Boolean =
    context.inductionDataStore.data.first()[booleanPreferencesKey(key)] ?: default

  override suspend fun saveBoolean(key: String, value: Boolean) {
    context.inductionDataStore.edit { it[booleanPreferencesKey(key)] = value }
  }

  override suspend fun loadInt(key: String, default: Int): Int =
    context.inductionDataStore.data.first()[intPreferencesKey(key)] ?: default

  override suspend fun saveInt(key: String, value: Int) {
    context.inductionDataStore.edit { it[intPreferencesKey(key)] = value }
  }

  override suspend fun loadString(key: String, default: String): String =
    context.inductionDataStore.data.first()[stringPreferencesKey(key)] ?: default

  override suspend fun saveString(key: String, value: String) {
    context.inductionDataStore.edit { it[stringPreferencesKey(key)] = value }
  }
}
