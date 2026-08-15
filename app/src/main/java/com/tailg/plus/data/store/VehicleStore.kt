package com.tailg.plus.data.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tailg.plus.data.model.VehicleLocation
import com.tailg.plus.data.model.VehicleProfile
import com.tailg.plus.data.model.VehicleProtocol
import com.tailg.plus.data.model.parsePersistedMap
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

private val Context.vehicleStoreDataStore by preferencesDataStore(name = "vehicle_store")

/**
 * Port of `lib/services/vehicle_store.dart`.
 *
 * Persists the vehicle profile list + the current (default) vehicle id.
 * Dart `SharedPreferences` → DataStore Preferences; the JSON payload keeps the
 * exact Dart key names (`vehicle_profiles` / `vehicle_default_id`) so the
 * stored data shape matches the Flutter original.
 *
 * Deviations:
 * - Dart singleton + `resetForTest` → plain class with constructor-injected
 *   [Context], [LogService] and clock (DI creates the single shared instance,
 *   per `CONVENTIONS.md`).
 * - Broadcast `Stream<List<VehicleProfile>>` → [vehiclesFlow] (`StateFlow`).
 * - The Dart `_saveQueue` serialization is not needed: DataStore serializes
 *   concurrent `edit` writes per instance.
 * - Dart `dispose()` closes the stream controller; a `StateFlow` cannot be
 *   closed, so there is nothing to dispose and the method is omitted.
 */
class VehicleStore(
    private val context: Context,
    private val logService: LogService = LogService(),
    clock: () -> Instant = { Instant.now() },
) {

    companion object {
        /** Dart `VehicleStore._prefVehicles`. */
        const val PREF_VEHICLES = "vehicle_profiles"

        /** Dart `VehicleStore._prefDefaultVehicleId`. */
        const val PREF_DEFAULT_VEHICLE_ID = "vehicle_default_id"

        private val KEY_VEHICLES = stringPreferencesKey(PREF_VEHICLES)
        private val KEY_DEFAULT_VEHICLE_ID = stringPreferencesKey(PREF_DEFAULT_VEHICLE_ID)

        /** Sentinel returned when the persisted payload fails to decode (Dart `_decodeFailed`). */
        private val DECODE_FAILED = Any()
    }

    private var clock: () -> Instant = clock

    private val _vehicles = mutableListOf<VehicleProfile>()
    private val _vehiclesFlow = MutableStateFlow<List<VehicleProfile>>(emptyList())

    /** Dart `vehiclesStream`: snapshot emissions after load and after every save. */
    val vehiclesFlow: StateFlow<List<VehicleProfile>> = _vehiclesFlow.asStateFlow()

    private var _defaultVehicleId: String? = null
    private var _initialized = false

    /** Dart `vehicles`: immutable snapshot of the current list. */
    val vehicles: List<VehicleProfile> get() = _vehicles.toList()

    /** Dart `defaultVehicleId`. */
    val defaultVehicleId: String? get() = _defaultVehicleId

    /** Dart `defaultVehicle`: first vehicle when no default is set. */
    val defaultVehicle: VehicleProfile?
        get() {
            if (_vehicles.isEmpty()) return null
            val id = _defaultVehicleId
            if (id == null) return _vehicles.first()
            return _vehicles.firstOrNull { it.id == id } ?: _vehicles.first()
        }

    /** Dart `init()`: idempotent one-time load. */
    suspend fun init() {
        if (_initialized) return
        load()
    }

    /** Dart `resetForTest({clock})`. */
    fun resetForTest(clock: (() -> Instant)? = null) {
        _vehicles.clear()
        _defaultVehicleId = null
        _initialized = false
        this.clock = clock ?: { Instant.now() }
        _vehiclesFlow.value = emptyList()
    }

    private suspend fun load() {
        val prefs = context.vehicleStoreDataStore.data.first()
        _defaultVehicleId = normalizeId(prefs[KEY_DEFAULT_VEHICLE_ID])
        val rawProfiles = prefs[KEY_VEHICLES]
        val decodedVehicles = decodeVehicles(rawProfiles)
        _vehicles.clear()
        _vehicles.addAll(decodedVehicles)
        normalizeDefaultVehicleId()
        // Scrub legacy BLE-era QGJ credential fields from prefs if present.
        if (rawContainsLegacyQgjCredentials(rawProfiles)) {
            persistVehicleProfiles()
        }
        _initialized = true
        emit()
    }

    /** Dart `upsert`. */
    suspend fun upsert(
        id: String,
        name: String,
        protocol: VehicleProtocol = VehicleProtocol.AUTO,
        makeDefault: Boolean = false,
        lastConnectedAt: Instant? = null,
        savedAt: Instant? = null,
    ): VehicleProfile {
        init()
        val normalizedId = normalizeId(id)
            ?: throw IllegalArgumentException("Vehicle id must not be blank: $id")
        val normalizedName = normalizeName(name)
        val now = savedAt(savedAt)
        val index = _vehicles.indexOfFirst { it.id == normalizedId }
        val profile: VehicleProfile
        if (index >= 0) {
            val current = _vehicles[index]
            profile = current.copyWith(
                name = normalizedName ?: current.name,
                protocol = protocol,
                updatedAt = now,
                lastConnectedAt = lastConnectedAt,
            )
            _vehicles[index] = profile
        } else {
            profile = VehicleProfile(
                id = normalizedId,
                name = normalizedName ?: "未命名车辆",
                protocol = protocol,
                createdAt = now,
                updatedAt = now,
                lastConnectedAt = lastConnectedAt,
            )
            _vehicles.add(profile)
        }

        if (makeDefault || _defaultVehicleId == null || _vehicles.size == 1) {
            _defaultVehicleId = normalizedId
        }

        save()
        return profile
    }

    /** Dart `rename`. */
    suspend fun rename(id: String, name: String, savedAt: Instant? = null) {
        init()
        val normalizedId = normalizeId(id) ?: return
        val normalizedName = normalizeName(name) ?: return
        val index = _vehicles.indexOfFirst { it.id == normalizedId }
        if (index < 0) return
        _vehicles[index] = _vehicles[index].copyWith(
            name = normalizedName,
            updatedAt = savedAt(savedAt),
        )
        save()
    }

    /** Dart `updateLastLocation`. */
    suspend fun updateLastLocation(id: String, location: VehicleLocation, savedAt: Instant? = null) {
        init()
        val normalizedId = normalizeId(id) ?: return
        val index = _vehicles.indexOfFirst { it.id == normalizedId }
        if (index < 0) return
        _vehicles[index] = _vehicles[index].copyWith(
            lastLocation = location,
            updatedAt = savedAt(savedAt),
        )
        save()
    }

    /** Dart `setDefault`. */
    suspend fun setDefault(id: String) {
        init()
        val normalizedId = normalizeId(id) ?: return
        if (_vehicles.none { it.id == normalizedId }) return
        _defaultVehicleId = normalizedId
        save()
    }

    /** Dart `remove`. */
    suspend fun remove(id: String) {
        init()
        val normalizedId = normalizeId(id) ?: return
        _vehicles.removeAll { it.id == normalizedId }
        if (_defaultVehicleId == normalizedId) {
            _defaultVehicleId = if (_vehicles.isEmpty()) null else _vehicles.first().id
        }
        save()
    }

    // --- decoding (Dart `_decodeVehicles` family) ---

    private fun decodeVehicles(raw: String?): List<VehicleProfile> {
        if (raw.isNullOrEmpty()) return emptyList()
        val decoded = decodeVehiclePayload(raw)
        if (decoded === DECODE_FAILED) return emptyList()
        if (decoded !is List<*>) {
            logDecodeWarning(
                "Expected persisted vehicle profiles to be a list, " +
                    "got ${decoded::class.qualifiedName ?: decoded::class.simpleName}",
            )
            return emptyList()
        }
        return decodeVehicleList(decoded)
    }

    private fun decodeVehiclePayload(raw: String): Any? = try {
        StoreJson.decode(raw)
    } catch (e: Exception) {
        logDecodeWarning("Failed to decode persisted vehicle profiles: $e")
        DECODE_FAILED
    }

    private fun decodeVehicleList(decoded: List<*>): List<VehicleProfile> {
        val vehicles = mutableListOf<VehicleProfile>()
        for (item in decoded) {
            decodeVehicle(item)?.let { vehicles.add(it) }
        }
        return vehicles
    }

    private fun decodeVehicle(item: Any?): VehicleProfile? {
        if (item !is Map<*, *>) {
            logDecodeWarning(
                "Skipped vehicle profile entry with type " +
                    (item?.let { it::class.qualifiedName } ?: "null"),
            )
            return null
        }
        return try {
            val vehicle = VehicleProfile.fromJson(decodeVehicleMap(item))
            if (vehicle.id.isEmpty()) {
                logDecodeWarning("Skipped vehicle profile with blank id")
                null
            } else {
                vehicle
            }
        } catch (e: Exception) {
            logDecodeWarning("Skipped vehicle parse error: $e")
            null
        }
    }

    /** Dart `_decodeVehicleMap`; throws [IllegalArgumentException] on non-string keys. */
    private fun decodeVehicleMap(item: Map<*, *>): Map<String, Any?> =
        parsePersistedMap(item)
            ?: throw IllegalArgumentException("Persisted map keys must be strings")

    private fun logDecodeWarning(detail: String) {
        logService.operation("VehicleStore", detail = detail, level = LogLevel.WARNING)
    }

    private fun rawContainsLegacyQgjCredentials(raw: String?): Boolean {
        if (raw.isNullOrEmpty()) return false
        return raw.contains("qgjLoginPassword") || raw.contains("qgjUserId")
    }

    private fun normalizeDefaultVehicleId() {
        if (_vehicles.isEmpty()) {
            _defaultVehicleId = null
            return
        }
        val id = _defaultVehicleId
        if (id == null || _vehicles.none { it.id == id }) {
            _defaultVehicleId = _vehicles.first().id
        }
    }

    private fun normalizeId(id: String?): String? = nonBlankTrimmed(id)

    private fun normalizeName(name: String?): String? = nonBlankTrimmed(name)

    private fun nonBlankTrimmed(value: String?): String? {
        val trimmed = value?.trim()
        return if (trimmed.isNullOrEmpty()) null else trimmed
    }

    private fun savedAt(savedAt: Instant?): Instant = savedAt ?: clock()

    // --- persistence (Dart `_save` / `_persistVehicleProfiles`) ---

    /**
     * Dart `_save`: persist then notify. Save failures are logged and isolated
     * so subsequent writes are not poisoned (Dart `catchError` semantics).
     */
    private suspend fun save() {
        try {
            persistVehicleProfiles()
            emit()
        } catch (e: Exception) {
            logService.operation(
                "VehicleStore",
                detail = "Save failed: $e",
                level = LogLevel.ERROR,
            )
        }
    }

    private suspend fun persistVehicleProfiles() {
        val defaultVehicleId = _defaultVehicleId
        context.vehicleStoreDataStore.edit { prefs ->
            prefs[KEY_VEHICLES] = StoreJson.encode(_vehicles.map { it.toJson() })
            if (defaultVehicleId == null) {
                prefs.remove(KEY_DEFAULT_VEHICLE_ID)
            } else {
                prefs[KEY_DEFAULT_VEHICLE_ID] = defaultVehicleId
            }
        }
    }

    private fun emit() {
        _vehiclesFlow.value = _vehicles.toList()
    }
}
