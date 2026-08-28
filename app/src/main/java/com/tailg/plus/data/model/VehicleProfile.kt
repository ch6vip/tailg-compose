package com.tailg.plus.data.model

import androidx.compose.runtime.Immutable
import java.time.Instant

/**
 * Port of `lib/models/vehicle_profile.dart`.
 *
 * Persisted locally (not a wire DTO), so no Moshi adapter annotation.
 * `DateTime` fields map to `java.time.Instant`; JSON serialization uses
 * `Instant.toString()` (ISO-8601 UTC, may omit `.000` milliseconds vs Dart's
 * `toIso8601String()`, which always emits them — parseable either way).
 */
enum class VehicleProtocol(val value: String, val label: String) {
    AUTO("auto", "自动识别"),
    STANDARD("standard", "Standard"),
    QGJ("qgj", "QGJ");

    companion object {
        /** Matches Dart `VehicleProtocol.fromValue`: unknown → `auto`. */
        fun fromValue(value: String?): VehicleProtocol =
            entries.firstOrNull { it.value == value } ?: AUTO
    }
}

@Immutable
data class VehicleProfile(
    val id: String,
    val name: String,
    val protocol: VehicleProtocol,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastConnectedAt: Instant? = null,
    val lastLocation: VehicleLocation? = null,
) {
    val displayName: String get() = parsePersistedStringOr(name, "未命名车辆")

    fun copyWith(
        name: String? = null,
        protocol: VehicleProtocol? = null,
        updatedAt: Instant? = null,
        lastConnectedAt: Instant? = null,
        lastLocation: VehicleLocation? = null,
    ): VehicleProfile = VehicleProfile(
        id = id,
        name = name ?: this.name,
        protocol = protocol ?: this.protocol,
        createdAt = createdAt,
        updatedAt = updatedAt ?: this.updatedAt,
        lastConnectedAt = lastConnectedAt ?: this.lastConnectedAt,
        lastLocation = lastLocation ?: this.lastLocation,
    )

    fun toJson(): Map<String, Any?> = linkedMapOf(
        "id" to id,
        "name" to name,
        "protocol" to protocol.value,
        "createdAt" to createdAt.toString(),
        "updatedAt" to updatedAt.toString(),
        "lastConnectedAt" to lastConnectedAt?.toString(),
        "lastLocation" to lastLocation?.toJson(),
    )

    companion object {
        fun fromJson(
            json: Map<String, Any?>,
            fallbackNow: Instant? = null,
            clock: () -> Instant = { Instant.now() },
        ): VehicleProfile {
            val now = fallbackNow ?: clock()
            return VehicleProfile(
                id = parsePersistedString(json["id"]),
                name = parsePersistedString(json["name"]),
                protocol = VehicleProtocol.fromValue(parsePersistedString(json["protocol"])),
                createdAt = parsePersistedDateOr(json["createdAt"], now),
                updatedAt = parsePersistedDateOr(json["updatedAt"], now),
                lastConnectedAt = parsePersistedDate(json["lastConnectedAt"]),
                lastLocation = vehicleLocation(json["lastLocation"], fallbackNow = now),
            )
        }

        private fun vehicleLocation(value: Any?, fallbackNow: Instant?): VehicleLocation? {
            val json = parsePersistedMap(value)
            return json?.let { VehicleLocation.fromJson(it, fallbackRecordedAt = fallbackNow) }
        }
    }
}
