package com.tailg.plus.data.model

import java.time.Instant

/**
 * Port of `lib/models/replica_feature.dart`.
 *
 * Local replica features persisted as JSON (DataStore / files), not wire DTOs
 * → no Moshi adapter. `DateTime` fields map to `java.time.Instant`; the JSON
 * timestamps use `Instant.toString()` (may omit `.000` vs Dart
 * `toIso8601String()`, both parse back through [parsePersistedDateOr]).
 */
data class NfcKeyRecord(
    val id: String,
    val name: String,
    val type: String,
    val createdAt: Instant,
) {
    fun toJson(): Map<String, Any?> = linkedMapOf(
        "id" to id,
        "name" to name,
        "type" to type,
        "createdAt" to createdAt.toString(),
    )

    fun copyWith(name: String? = null, type: String? = null): NfcKeyRecord = NfcKeyRecord(
        id = id,
        name = name ?: this.name,
        type = type ?: this.type,
        createdAt = createdAt,
    )

    companion object {
        fun fromJson(
            json: Map<String, Any?>,
            fallbackNow: Instant? = null,
            clock: () -> Instant = { Instant.now() },
        ): NfcKeyRecord = NfcKeyRecord(
            id = parsePersistedString(json["id"]),
            name = parsePersistedStringOr(json["name"], "未命名钥匙"),
            type = parsePersistedStringOr(json["type"], "手机"),
            createdAt = parsePersistedDateOr(json["createdAt"], fallbackNow, clock),
        )
    }
}

data class FenceConfig(
    val enabled: Boolean,
    val latitude: Double?,
    val longitude: Double?,
    val radiusMeters: Int,
    val updatedAt: Instant,
) {
    fun toJson(): Map<String, Any?> = linkedMapOf(
        "enabled" to enabled,
        "latitude" to latitude,
        "longitude" to longitude,
        "radiusMeters" to radiusMeters,
        "updatedAt" to updatedAt.toString(),
    )

    companion object {
        fun fromJson(
            json: Map<String, Any?>,
            fallbackNow: Instant? = null,
            clock: () -> Instant = { Instant.now() },
        ): FenceConfig = FenceConfig(
            enabled = parsePersistedBool(json["enabled"]),
            latitude = parsePersistedDouble(json["latitude"]),
            longitude = parsePersistedDouble(json["longitude"]),
            radiusMeters = parsePersistedInt(json["radiusMeters"]) ?: 500,
            updatedAt = parsePersistedDateOr(json["updatedAt"], fallbackNow, clock),
        )
    }
}

data class ShareMemberRecord(
    val id: String,
    val name: String,
    val phone: String,
    val createdAt: Instant,
) {
    fun toJson(): Map<String, Any?> = linkedMapOf(
        "id" to id,
        "name" to name,
        "phone" to phone,
        "createdAt" to createdAt.toString(),
    )

    fun copyWith(name: String? = null, phone: String? = null): ShareMemberRecord = ShareMemberRecord(
        id = id,
        name = name ?: this.name,
        phone = phone ?: this.phone,
        createdAt = createdAt,
    )

    companion object {
        fun fromJson(
            json: Map<String, Any?>,
            fallbackNow: Instant? = null,
            clock: () -> Instant = { Instant.now() },
        ): ShareMemberRecord = ShareMemberRecord(
            id = parsePersistedString(json["id"]),
            name = parsePersistedStringOr(json["name"], "未命名成员"),
            phone = parsePersistedString(json["phone"]),
            createdAt = parsePersistedDateOr(json["createdAt"], fallbackNow, clock),
        )
    }
}
