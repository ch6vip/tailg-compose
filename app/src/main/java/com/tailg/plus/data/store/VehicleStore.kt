package com.tailg.plus.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
 * - A mutation mutex serializes memory changes together with their disk writes.
 * - Dart `dispose()` closes the stream controller; a `StateFlow` cannot be
 *   closed, so there is nothing to dispose and the method is omitted.
 */
class VehicleStore(
    context: Context,
    private val logService: LogService = LogService(),
    clock: () -> Instant = { Instant.now() },
    private val dataStore: DataStore<Preferences> = context.vehicleStoreDataStore,
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
    private val _defaultVehicleFlow = MutableStateFlow<VehicleProfile?>(null)

    private data class Snapshot(val vehicles: List<VehicleProfile>, val defaultVehicleId: String?) {
        val defaultVehicle: VehicleProfile?
            get() = vehicles.firstOrNull { it.id == defaultVehicleId } ?: vehicles.firstOrNull()
    }

    /**
     * Immutable snapshot of [_vehicles] for lock-free readers. Every mutation
     * happens under [mutationMutex] and publishes a fresh copy here; readers
     * (UI thread `vehicles`/`defaultVehicle`, StateFlow emissions) grab the
     * reference without ever touching the mutable list — no
     * ConcurrentModificationException possible.
     */
    @Volatile
    private var snapshot = Snapshot(emptyList(), null)

    /** Dart `vehiclesStream`: snapshot emissions after load and after every save. */
    val vehiclesFlow: StateFlow<List<VehicleProfile>> = _vehiclesFlow.asStateFlow()
    val defaultVehicleFlow: StateFlow<VehicleProfile?> = _defaultVehicleFlow.asStateFlow()

    private var _defaultVehicleId: String? = null
    @Volatile private var _initialized = false
    private val initMutex = Mutex()

    /**
     * Serializes the in-memory read-modify-write cycles (upsert/rename/remove/
     * setDefault). DataStore only serializes the disk write — without this
     * mutex two concurrent `upsert`s can both miss the same id and append
     * duplicate profiles.
     */
    private val mutationMutex = Mutex()

    /** Dart `vehicles`: immutable snapshot of the current list. */
    val vehicles: List<VehicleProfile> get() = snapshot.vehicles

    /** Dart `defaultVehicleId`. */
    val defaultVehicleId: String? get() = snapshot.defaultVehicleId

    /** Dart `defaultVehicle`: first vehicle when no default is set. */
    val defaultVehicle: VehicleProfile?
        get() = snapshot.defaultVehicle

    /**
     * Dart `init()`: idempotent one-time load. The [Mutex] mirrors the Dart
     * `_initializing` future guard, so concurrent callers wait for the load
     * instead of running it twice.
     */
    suspend fun init() {
        if (_initialized) return
        initMutex.withLock {
            if (_initialized) return
            load()
        }
    }

    /** Dart `resetForTest({clock})`. */
    fun resetForTest(clock: (() -> Instant)? = null) {
        _vehicles.clear()
        snapshot = Snapshot(emptyList(), null)
        _defaultVehicleId = null
        _initialized = false
        this.clock = clock ?: { Instant.now() }
        _vehiclesFlow.value = emptyList()
        _defaultVehicleFlow.value = null
    }

    private suspend fun load() {
        val prefs = withDataStoreReadTimeout { dataStore.data.first() }
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
    ): VehicleProfile = mutationMutex.withLock {
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
        profile
    }

    /** Dart `rename`. */
    suspend fun rename(id: String, name: String, savedAt: Instant? = null): Unit = mutationMutex.withLock {
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
    suspend fun updateLastLocation(
        id: String,
        location: VehicleLocation,
        savedAt: Instant? = null,
    ): Unit = mutationMutex.withLock {
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
    suspend fun setDefault(id: String): Unit = mutationMutex.withLock {
        init()
        val normalizedId = normalizeId(id) ?: return
        if (_vehicles.none { it.id == normalizedId }) return
        _defaultVehicleId = normalizedId
        save()
    }

    /** Dart `remove`. */
    suspend fun remove(id: String): Unit = mutationMutex.withLock {
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
                    "got ${decoded?.let { it::class.simpleName } ?: "Null"}",
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
                    (item?.let { it::class.simpleName } ?: "Null"),
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
     * Persist before publishing. Restore the committed state on failure so
     * failed mutations cannot leak into a later successful save.
     */
    private suspend fun save() {
        try {
            withContext(NonCancellable) {
                persistVehicleProfiles()
                emit()
            }
        } catch (e: Exception) {
            _vehicles.clear()
            _vehicles.addAll(snapshot.vehicles)
            _defaultVehicleId = snapshot.defaultVehicleId
            if (e is CancellationException) throw e
            logService.operation(
                "VehicleStore",
                detail = "Save failed: $e",
                level = LogLevel.ERROR,
            )
            throw e
        }
    }

    private suspend fun persistVehicleProfiles() {
        val defaultVehicleId = _defaultVehicleId
        dataStore.edit { prefs ->
            prefs[KEY_VEHICLES] = StoreJson.encode(_vehicles.map { it.toJson() })
            if (defaultVehicleId == null) {
                prefs.remove(KEY_DEFAULT_VEHICLE_ID)
            } else {
                prefs[KEY_DEFAULT_VEHICLE_ID] = defaultVehicleId
            }
        }
    }

    private fun emit() {
        // Publish a fresh immutable copy; every later mutation replaces the
        // reference rather than mutating in place.
        val committed = Snapshot(_vehicles.toList(), _defaultVehicleId)
        snapshot = committed
        _vehiclesFlow.value = committed.vehicles
        _defaultVehicleFlow.value = committed.defaultVehicle
    }
}
