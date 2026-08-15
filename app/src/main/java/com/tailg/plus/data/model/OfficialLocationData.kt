package com.tailg.plus.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Port of `lib/models/official_location_data.dart`.
 *
 * Wire DTOs → Moshi adapters (canonical key names); the `fromJson` companions
 * keep the Dart fallback semantics (`fenceRadius`/`range`).
 * `OfficialFenceData.radiusLabel` uses [formatDistanceMeters] (ported from
 * `lib/services/display_number_formatter.dart`).
 */
@JsonClass(generateAdapter = true)
data class OfficialVehicleLocation(
    @Json(ignore = true) val raw: Map<String, Any?> = emptyMap(),
    val extendId: String = "",
    val bleConnectTime: String = "",
    val bleConnectLat: String = "",
    val bleConnectLng: String = "",
    val carId: String = "",
    val bleConnectAddress: String = "",
) {
    val hasData: Boolean
        get() = bleConnectLat.isNotEmpty() || bleConnectLng.isNotEmpty() ||
            bleConnectAddress.isNotEmpty() || bleConnectTime.isNotEmpty()

    val latitude: Double? get() = parsePersistedDouble(bleConnectLat)
    val longitude: Double? get() = parsePersistedDouble(bleConnectLng)

    companion object {
        fun fromJson(json: Map<String, Any?>): OfficialVehicleLocation = OfficialVehicleLocation(
            raw = stringKeyedMap(json),
            extendId = clean(json["extendId"]) ?: "",
            bleConnectTime = clean(json["bleConnectTime"]) ?: "",
            bleConnectLat = clean(json["bleConnectLat"]) ?: "",
            bleConnectLng = clean(json["bleConnectLng"]) ?: "",
            carId = clean(json["carId"]) ?: "",
            bleConnectAddress = clean(json["bleConnectAddress"]) ?: "",
        )
    }
}

@JsonClass(generateAdapter = true)
data class OfficialFenceData(
    @Json(ignore = true) val raw: Map<String, Any?> = emptyMap(),
    val fenceRadius: String = "",
    val fenceRadiusMax: String = "",
    val fenceRadiusMin: String = "",
    val fenceSwitch: String = "",
    val fenceTimeFr: String = "",
    val fenceTimeTo: String = "",
) {
    val hasData: Boolean
        get() = fenceRadius.isNotEmpty() || fenceRadiusMax.isNotEmpty() ||
            fenceRadiusMin.isNotEmpty() || fenceSwitch.isNotEmpty() ||
            fenceTimeFr.isNotEmpty() || fenceTimeTo.isNotEmpty()

    val enabled: Boolean get() = fenceSwitch == "1" || fenceSwitch.lowercase() == "true"

    val statusLabel: String
        get() {
            if (fenceSwitch.isEmpty()) return "待读取"
            return if (enabled) "已开启" else "已关闭"
        }

    val radiusLabel: String
        get() {
            val meters = radiusMeters
            if (meters == null) return if (fenceRadius.isEmpty()) "待读取" else fenceRadius
            return formatDistanceMeters(meters)
        }

    val radiusMeters: Double?
        get() {
            if (fenceRadius.isEmpty()) return null
            val value = fenceRadius.toDoubleOrNull() ?: return null
            return value * 100.0
        }

    val timeLabel: String
        get() {
            if (fenceTimeFr.isEmpty() && fenceTimeTo.isEmpty()) return "待读取"
            return "${if (fenceTimeFr.isEmpty()) "--" else fenceTimeFr} - " +
                "${if (fenceTimeTo.isEmpty()) "--" else fenceTimeTo}"
        }

    companion object {
        fun fromJson(json: Map<String, Any?>): OfficialFenceData = OfficialFenceData(
            raw = stringKeyedMap(json),
            fenceRadius = clean(json["fenceRadius"] ?: json["range"]) ?: "",
            fenceRadiusMax = clean(json["fenceRadiusMax"]) ?: "",
            fenceRadiusMin = clean(json["fenceRadiusMin"]) ?: "",
            fenceSwitch = clean(json["fenceSwitch"]) ?: "",
            fenceTimeFr = clean(json["fenceTimeFr"]) ?: "",
            fenceTimeTo = clean(json["fenceTimeTo"]) ?: "",
        )
    }
}

private fun clean(value: Any?): String? {
    if (value == null) return null
    val text = value.toString().trim()
    if (text.isEmpty() || text == "--" || text.lowercase() == "null") return null
    return text
}
