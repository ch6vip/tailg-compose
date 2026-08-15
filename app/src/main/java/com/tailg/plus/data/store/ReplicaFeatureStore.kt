package com.tailg.plus.data.store

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tailg.plus.data.model.FenceConfig
import com.tailg.plus.data.model.NfcKeyRecord
import com.tailg.plus.data.model.ShareMemberRecord
import com.tailg.plus.data.model.parsePersistedMap
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first

private val Context.replicaFeatureStoreDataStore by preferencesDataStore(name = "replica_feature_store")

/**
 * Port of `lib/services/replica_feature_store.dart`.
 *
 * Local replica features: NFC keys, geofence config and share members,
 * persisted as JSON (Dart `SharedPreferences` strings) → DataStore Preferences
 * strings. Key names match the Dart constants (`replica_nfc_keys` /
 * `replica_fence_config` / `replica_share_members`); JSON payloads keep the
 * Dart shape via [StoreJson] and the `toJson()`/`fromJson` of the models in
 * `com.tailg.plus.data.model`.
 *
 * [makeId] preserves the Dart `'<epoch microseconds>_<incrementing counter>'`
 * semantics; the clock is constructor-injected for deterministic tests.
 *
 * Deviations:
 * - Dart singleton → plain class with constructor-injected [Context],
 *   [LogService] and clock (DI creates the single shared instance).
 * - `SharedPreferences` write/read → `suspend` DataStore calls; every public
 *   mutator/loader is `suspend`.
 */
class ReplicaFeatureStore(
    private val context: Context,
    private val logService: LogService = LogService(),
    clock: () -> Instant = { Instant.now() },
) {

    companion object {
        /** Dart `ReplicaFeatureStore._prefNfcKeys`. */
        const val PREF_NFC_KEYS = "replica_nfc_keys"

        /** Dart `ReplicaFeatureStore._prefFenceConfig`. */
        const val PREF_FENCE_CONFIG = "replica_fence_config"

        /** Dart `ReplicaFeatureStore._prefShareMembers`. */
        const val PREF_SHARE_MEMBERS = "replica_share_members"

        private val KEY_NFC_KEYS = stringPreferencesKey(PREF_NFC_KEYS)
        private val KEY_FENCE_CONFIG = stringPreferencesKey(PREF_FENCE_CONFIG)
        private val KEY_SHARE_MEMBERS = stringPreferencesKey(PREF_SHARE_MEMBERS)
    }

    private var clock: () -> Instant = clock

    /** Dart `_idCounter`: reset by [resetForTest], never persisted. */
    private var idCounter = 0

    /** Dart `resetForTest({clock})`. */
    fun resetForTest(clock: (() -> Instant)? = null) {
        this.clock = clock ?: { Instant.now() }
        idCounter = 0
    }

    private fun logWarning(message: String, error: Any) {
        logService.operation(message, detail = error.toString(), level = LogLevel.WARNING)
    }

    // --- NFC keys ---

    /** Dart `loadNfcKeys`. */
    suspend fun loadNfcKeys(): List<NfcKeyRecord> {
        val raw = context.replicaFeatureStoreDataStore.data.first()[KEY_NFC_KEYS]
        return decodeList(raw) { json, fallbackNow ->
            NfcKeyRecord.fromJson(json, fallbackNow = fallbackNow)
        }
    }

    /** Dart `saveNfcKeys`. */
    suspend fun saveNfcKeys(records: List<NfcKeyRecord>) {
        saveList(KEY_NFC_KEYS, records.map { it.toJson() })
    }

    /** Dart `createNfcKey`. */
    fun createNfcKey(name: String, type: String): NfcKeyRecord {
        val now = clock()
        return NfcKeyRecord(
            id = makeId(now = now),
            name = name,
            type = type,
            createdAt = now,
        )
    }

    // --- geofence config ---

    /** Dart `loadFenceConfig`: `null` when absent or undecodable. */
    suspend fun loadFenceConfig(): FenceConfig? {
        val raw = context.replicaFeatureStoreDataStore.data.first()[KEY_FENCE_CONFIG]
        val decoded = decodeMap(raw)
        if (decoded == null) return null
        return FenceConfig.fromJson(decoded, fallbackNow = clock())
    }

    /** Dart `saveFenceConfig`. */
    suspend fun saveFenceConfig(config: FenceConfig) {
        context.replicaFeatureStoreDataStore.edit { prefs ->
            prefs[KEY_FENCE_CONFIG] = StoreJson.encode(config.toJson())
        }
    }

    /** Dart `createFenceConfig`. */
    fun createFenceConfig(
        enabled: Boolean,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
    ): FenceConfig = FenceConfig(
        enabled = enabled,
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radiusMeters,
        updatedAt = clock(),
    )

    // --- share members ---

    /** Dart `loadShareMembers`. */
    suspend fun loadShareMembers(): List<ShareMemberRecord> {
        val raw = context.replicaFeatureStoreDataStore.data.first()[KEY_SHARE_MEMBERS]
        return decodeList(raw) { json, fallbackNow ->
            ShareMemberRecord.fromJson(json, fallbackNow = fallbackNow)
        }
    }

    /** Dart `saveShareMembers`. */
    suspend fun saveShareMembers(records: List<ShareMemberRecord>) {
        saveList(KEY_SHARE_MEMBERS, records.map { it.toJson() })
    }

    /** Dart `createShareMember`. */
    fun createShareMember(name: String, phone: String): ShareMemberRecord {
        val now = clock()
        return ShareMemberRecord(
            id = makeId(now = now),
            name = name,
            phone = phone,
            createdAt = now,
        )
    }

    /**
     * Dart `makeId`: `<epochMicroseconds>_<idCounter>` where the counter is
     * incremented per call and the timestamp comes from [now] or the clock.
     * `Instant` → epoch microseconds truncates nanoseconds, matching Dart
     * `DateTime.microsecondsSinceEpoch`.
     */
    fun makeId(now: Instant? = null): String {
        idCounter++
        val instant = now ?: clock()
        val micros = ChronoUnit.MICROS.between(Instant.EPOCH, instant)
        return "${micros}_$idCounter"
    }

    // --- decoding (Dart `_decodeList` / `_decodeMap` family) ---

    private fun <T> decodeList(
        raw: String?,
        decode: (Map<String, Any?>, Instant) -> T,
    ): List<T> {
        val decoded = decodeJson(raw, "ReplicaFeatureStore: JSON decode failed")
        if (decoded == null) return emptyList()
        if (decoded !is List<*>) {
            logWarning(
                "ReplicaFeatureStore: expected list payload",
                decoded::class.qualifiedName ?: decoded::class.simpleName ?: "unknown",
            )
            return emptyList()
        }
        val records = mutableListOf<T>()
        val fallbackNow = clock()
        for (item in decoded) {
            decodeListItem(item, decode, fallbackNow)?.let { records.add(it) }
        }
        return records
    }

    private fun <T> decodeListItem(
        item: Any?,
        decode: (Map<String, Any?>, Instant) -> T,
        fallbackNow: Instant,
    ): T? {
        if (item !is Map<*, *>) {
            logWarning(
                "ReplicaFeatureStore: skipped list item with type",
                item?.let { it::class.qualifiedName } ?: "null",
            )
            return null
        }
        return try {
            val payload = parsePersistedMap(item)
            if (payload == null) null else decode(payload, fallbackNow)
        } catch (e: Exception) {
            logWarning("ReplicaFeatureStore: decode list item failed", e)
            null
        }
    }

    private fun decodeMap(raw: String?): Map<String, Any?>? {
        val decoded = decodeJson(raw, "ReplicaFeatureStore: decode map failed")
        return decodeMapPayload(decoded)
    }

    private fun decodeMapPayload(decoded: Any?): Map<String, Any?>? {
        if (decoded == null) return null
        val payload = parsePersistedMap(decoded)
        if (payload != null) return payload
        logWarning(
            "ReplicaFeatureStore: expected map payload",
            decoded::class.qualifiedName ?: decoded::class.simpleName ?: "unknown",
        )
        return null
    }

    private fun decodeJson(raw: String?, errorMessage: String): Any? {
        if (raw.isNullOrEmpty()) return null
        return try {
            StoreJson.decode(raw)
        } catch (e: Exception) {
            logWarning(errorMessage, e)
            null
        }
    }

    // --- persistence (Dart `_saveList`) ---

    private suspend fun saveList(key: Preferences.Key<String>, records: List<Map<String, Any?>>) {
        context.replicaFeatureStoreDataStore.edit { prefs ->
            prefs[key] = StoreJson.encode(records)
        }
    }
}
