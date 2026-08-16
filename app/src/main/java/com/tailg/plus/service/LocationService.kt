package com.tailg.plus.service

import android.annotation.SuppressLint

import android.content.Context
import android.location.Location
import androidx.activity.ComponentActivity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.tailg.plus.data.model.VehicleLocation
import com.tailg.plus.data.store.VehicleStore
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService
import com.tailg.plus.permission.AppPermissionService
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/** Dart `LocationCaptureException` — carries the user-facing message. */
class LocationCaptureException(message: String) : Exception(message) {
    override fun toString(): String = message ?: "LocationCaptureException"
}

/** Raw platform position — Dart `Position` from the geolocator plugin. */
data class GeoPosition(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double,
)

/** Injected position source — Dart `Geolocator.getCurrentPosition`. */
fun interface LocationProvider {
    suspend fun getCurrentPosition(): GeoPosition
}

/**
 * Port of `lib/services/location_service.dart` (LocationService).
 *
 * Capture + record a vehicle's last location with a 60 s silent-capture
 * throttle. Dart singleton → plain constructor-injected class (DI creates the
 * shared instance, per `CONVENTIONS.md`).
 *
 * Platform mapping:
 * - Dart `AppPermissionService().ensureLocationPermission(...)` →
 *   [AppPermissionService.ensureLocationPermission]. The geolocator plugin
 *   hides the Android activity plumbing; here the caller must supply the
 *   [ComponentActivity] when [requestPermission] is true (check-only silent
 *   captures may pass null).
 * - Dart `Geolocator.getCurrentPosition` (high accuracy, 8 s time limit) →
 *   [LocationProvider] (default [FusedLocationProvider] over
 *   play-services-location); inject a fake provider in tests.
 * - Dart `DateTime` clock → `() -> Instant` (matches `VehicleLocation` /
 *   `VehicleStore`), and `Duration` → `java.time.Duration` for the throttle.
 */
class LocationService(
    private val context: Context,
    private val vehicleStore: VehicleStore,
    private val permissionService: AppPermissionService = AppPermissionService(context),
    private val logService: LogService = LogService(),
    clock: () -> Instant = { Instant.now() },
    private val locationProvider: LocationProvider = FusedLocationProvider(context),
) {

    companion object {
        /** Dart `silentCaptureThrottle = Duration(seconds: 60)`. */
        val SILENT_CAPTURE_THROTTLE: Duration = Duration.ofSeconds(60)
    }

    private var clock: () -> Instant = clock
    private val _lastSilentCaptures = mutableMapOf<String, Instant>()

    /** Dart `resetForTest({clock})`: clears the throttle map and replaces the clock. */
    fun resetForTest(clock: (() -> Instant)? = null) {
        _lastSilentCaptures.clear()
        this.clock = clock ?: { Instant.now() }
    }

    /**
     * Dart `captureCurrentLocation({requestPermission})`. Throws
     * [LocationCaptureException] when permission is unavailable. [activity]
     * is required when [requestPermission] is true.
     */
    suspend fun captureCurrentLocation(
        requestPermission: Boolean = false,
        activity: ComponentActivity? = null,
    ): VehicleLocation {
        val permission = permissionService.ensureLocationPermission(activity, requestPermission)
        if (!permission.granted) {
            throw LocationCaptureException(permission.message ?: "定位权限不可用")
        }
        val position = locationProvider.getCurrentPosition()
        return VehicleLocation(
            latitude = position.latitude,
            longitude = position.longitude,
            accuracy = position.accuracy,
            recordedAt = clock(),
        )
    }

    /**
     * Dart `recordVehicleLocation(vehicleId, {requestPermission})`.
     *
     * Silent captures (requestPermission = false) swallow capture failures and
     * return null; user-triggered captures rethrow.
     */
    suspend fun recordVehicleLocation(
        vehicleId: String,
        requestPermission: Boolean = false,
        activity: ComponentActivity? = null,
    ): VehicleLocation? {
        val normalizedId = vehicleId.trim()
        if (normalizedId.isEmpty()) return null
        val store = vehicleStore
        try {
            store.init()
            throttledLocation(store, normalizedId, requestPermission)?.let { return it }
            val location = captureCurrentLocation(
                requestPermission = requestPermission,
                activity = activity,
            )
            store.updateLastLocation(normalizedId, location)
            if (!requestPermission) {
                _lastSilentCaptures[normalizedId] = location.recordedAt
            }
            logService.operation(
                "记录车辆位置",
                detail = "$normalizedId ${location.coordinateText}",
                level = LogLevel.INFO,
            )
            return location
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logService.operation("记录车辆位置失败", detail = e.toString(), level = LogLevel.DEBUG)
            if (requestPermission) throw e
            return null
        }
    }

    private fun throttledLocation(
        store: VehicleStore,
        vehicleId: String,
        requestPermission: Boolean,
    ): VehicleLocation? {
        if (requestPermission) return null

        val now = clock()
        val lastCapture = _lastSilentCaptures[vehicleId]
        if (lastCapture != null &&
            now.isBefore(lastCapture.plus(SILENT_CAPTURE_THROTTLE))
        ) {
            return cachedLocation(store, vehicleId)
        }

        val cached = cachedLocation(store, vehicleId)
        if (cached != null &&
            now.isBefore(cached.recordedAt.plus(SILENT_CAPTURE_THROTTLE))
        ) {
            _lastSilentCaptures[vehicleId] = cached.recordedAt
            logService.operation("记录车辆位置已节流", detail = vehicleId, level = LogLevel.DEBUG)
            return cached
        }

        return null
    }

    private fun cachedLocation(store: VehicleStore, vehicleId: String): VehicleLocation? {
        for (vehicle in store.vehicles) {
            if (vehicle.id == vehicleId) return vehicle.lastLocation
        }
        return null
    }

    /** Dart `recordDefaultVehicleLocation({requestPermission})`. */
    suspend fun recordDefaultVehicleLocation(
        requestPermission: Boolean = false,
        activity: ComponentActivity? = null,
    ): VehicleLocation? {
        val store = vehicleStore
        store.init()
        val vehicle = store.defaultVehicle ?: return null
        return recordVehicleLocation(
            vehicle.id,
            requestPermission = requestPermission,
            activity = activity,
        )
    }
}

/**
 * play-services-location backed [LocationProvider]: Dart
 * `Geolocator.getCurrentPosition` with `LocationAccuracy.high` and
 * `timeLimit: Duration(seconds: 8)` → `Priority.PRIORITY_HIGH_ACCURACY`
 * bounded by [timeout]. The 8 s expiry surfaces as a [TimeoutException]
 * (Dart's geolocator reports the time limit as an error, which
 * `recordVehicleLocation` swallows for silent captures).
 */
private class FusedLocationProvider(
    private val context: Context,
    private val timeout: kotlin.time.Duration = 8.seconds,
) : LocationProvider {
    @SuppressLint("MissingPermission")
    override suspend fun getCurrentPosition(): GeoPosition {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val location = withTimeoutOrNull(timeout) {
            suspendCancellableCoroutine<Location?> { cont ->
                val task = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                task.addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                task.addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
                task.addOnCanceledListener { if (cont.isActive) cont.resumeWithException(CancellationException("Location task canceled")) }
                // Google Play Services Task has no cancel(); the timeout or
                // addOnCanceledListener above handles cleanup.
            }
        } ?: throw TimeoutException("定位超时: ${timeout.inWholeSeconds}s 内未获取到位置")
        return GeoPosition(
            latitude = location?.latitude ?: 0.0,
            longitude = location?.longitude ?: 0.0,
            accuracy = (location?.accuracy ?: 0.0f).toDouble(),
        )
    }
}
