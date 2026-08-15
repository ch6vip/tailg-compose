package com.tailg.plus.data.model

import java.time.Instant

/**
 * Port of `lib/models/vehicle_location.dart`.
 *
 * Persisted locally (embedded in `VehicleProfile` JSON) → no Moshi adapter.
 * `DateTime` → `java.time.Instant`; [coordinateText] reuses
 * [formatCoordinateText] from `lib/models/geo_coordinate.dart`.
 */
data class VehicleLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double,
    val recordedAt: Instant,
) {
    val coordinateText: String get() = formatCoordinateText(latitude, longitude)

    fun toJson(): Map<String, Any?> = linkedMapOf(
        "latitude" to latitude,
        "longitude" to longitude,
        "accuracy" to accuracy,
        "recordedAt" to recordedAt.toString(),
    )

    companion object {
        fun fromJson(
            json: Map<String, Any?>,
            fallbackRecordedAt: Instant? = null,
        ): VehicleLocation = VehicleLocation(
            latitude = parsePersistedDouble(json["latitude"]) ?: 0.0,
            longitude = parsePersistedDouble(json["longitude"]) ?: 0.0,
            accuracy = parsePersistedDouble(json["accuracy"]) ?: 0.0,
            recordedAt = parsePersistedDateOr(json["recordedAt"], fallbackRecordedAt),
        )
    }
}
