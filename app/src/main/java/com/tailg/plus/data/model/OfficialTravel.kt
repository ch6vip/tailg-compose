package com.tailg.plus.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.tailg.plus.util.formatCompactDecimalText
import com.tailg.plus.util.formatTravelMileageMetersText
import com.tailg.plus.util.parseTravelMileageMeters
import com.tailg.plus.util.travelMetersToKm

/**
 * Port of `lib/models/official_vehicle.dart` — part 2/3: travel day / record /
 * point DTOs plus the shared top-level helpers
 * [sumTravelMileageKm], [sumTravelDurationSeconds], [formatCompactDuration].
 *
 * Wire DTOs → Moshi adapters (canonical key names). [OfficialTravelDay.records]
 * maps the wire key `deviceTravelDtoList`. The `fromJson` companions keep the
 * Dart `_clean` normalization (empty / `--` / `"null"` → `""`).
 *
 * Display labels reuse the ported `lib/services/display_number_formatter.dart`
 * functions ([formatTravelMileageMetersText], [formatCompactDecimalText]).
 * `durationSeconds` stays [Int] like the Dart `int`; absurd hour values could
 * overflow 32-bit, but real payloads never reach that range.
 */
@JsonClass(generateAdapter = true)
data class OfficialTravelDay(
    @Json(ignore = true) val raw: Map<String, Any?> = emptyMap(),
    val sec: String = "",
    val hours: String = "",
    val min: String = "",
    val travelDate: String = "",
    val totalTime: String = "",
    @Json(name = "deviceTravelDtoList") val records: List<OfficialTravelRecord> = emptyList(),
    val days: String = "",
    val totalMileage: String = "",
) {
    val hasData: Boolean
        get() = travelDate.isNotEmpty() || totalTime.isNotEmpty() ||
            totalMileage.isNotEmpty() || records.isNotEmpty()

    companion object {
        fun fromJson(json: Map<String, Any?>): OfficialTravelDay = OfficialTravelDay(
            raw = stringKeyedMap(json),
            sec = clean(json["sec"]) ?: "",
            hours = clean(json["hours"]) ?: "",
            min = clean(json["min"]) ?: "",
            travelDate = clean(json["travelDate"]) ?: "",
            totalTime = clean(json["totalTime"]) ?: "",
            records = travelRecords(json["deviceTravelDtoList"]),
            days = clean(json["days"]) ?: "",
            totalMileage = clean(json["totalMileage"]) ?: "",
        )
    }
}

@JsonClass(generateAdapter = true)
data class OfficialTravelRecord(
    @Json(ignore = true) val raw: Map<String, Any?> = emptyMap(),
    val hours: String = "",
    val carName: String = "",
    val averageSpeed: String = "",
    val deviceTravelId: String = "",
    val sec: String = "",
    val min: String = "",
    val travelDate: String = "",
    val imei: String = "",
    val days: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val mileage: String = "",
    val frame: String = "",
    val maxSpeed: String = "",
) {
    val durationLabel: String
        get() {
            val parts = mutableListOf<String>()
            if (hours.isNotEmpty() && hours != "0") {
                parts.add("${hours}h")
            }
            if (min.isNotEmpty() && min != "0") {
                parts.add("${min}m")
            }
            if (sec.isNotEmpty() && sec != "0") {
                parts.add("${sec}s")
            }
            if (parts.isNotEmpty()) {
                return parts.joinToString(" ")
            }
            if (startTime.isNotEmpty() && endTime.isNotEmpty()) {
                return "$startTime - $endTime"
            }
            return "待读取"
        }

    /** Official travel `mileage` is meters → display via [formatTravelMileageMetersText]. */
    val mileageLabel: String
        get() = if (mileage.isEmpty()) "待读取" else formatTravelMileageMetersText(mileage)

    val averageSpeedLabel: String
        get() = if (averageSpeed.isEmpty()) "待读取" else "${formatCompactDecimalText(averageSpeed)}km/h"

    val maxSpeedLabel: String
        get() = if (maxSpeed.isEmpty()) "待读取" else "${formatCompactDecimalText(maxSpeed)}km/h"

    /** Raw travel mileage in meters (official `deviceTravel` unit). */
    val mileageMeters: Double get() = parseTravelMileageMeters(mileage)

    /** Travel mileage converted to km (`meters / 1000`). */
    val mileageKm: Double get() = travelMetersToKm(mileageMeters)

    /** Duration from hours/min/sec fields; non-numeric parts count as 0. */
    val durationSeconds: Int
        get() = (hours.toIntOrNull() ?: 0) * 3600 +
            (min.toIntOrNull() ?: 0) * 60 +
            (sec.toIntOrNull() ?: 0)

    companion object {
        fun fromJson(json: Map<String, Any?>): OfficialTravelRecord = OfficialTravelRecord(
            raw = stringKeyedMap(json),
            hours = clean(json["hours"]) ?: "",
            carName = clean(json["carName"]) ?: "",
            averageSpeed = clean(json["averageSpeed"]) ?: "",
            deviceTravelId = clean(json["deviceTravelId"]) ?: "",
            sec = clean(json["sec"]) ?: "",
            min = clean(json["min"]) ?: "",
            travelDate = clean(json["travelDate"]) ?: "",
            imei = clean(json["imei"]) ?: "",
            days = clean(json["days"]) ?: "",
            startTime = clean(json["startTime"]) ?: "",
            endTime = clean(json["endTime"]) ?: "",
            mileage = clean(json["mileage"]) ?: "",
            frame = clean(json["frame"]) ?: "",
            maxSpeed = clean(json["maxSpeed"]) ?: "",
        )
    }
}

fun sumTravelMileageKm(records: Iterable<OfficialTravelRecord>): Double =
    records.fold(0.0) { sum, record -> sum + record.mileageKm }

fun sumTravelDurationSeconds(records: Iterable<OfficialTravelRecord>): Int =
    records.fold(0) { sum, record -> sum + record.durationSeconds }

/**
 * Compact `2h30m` / `30m` duration label used by travel and ride stats.
 * When [emptyWhenZero] is true, zero/negative totals render as `''`
 * (travel day cards prefer blank over `0m`).
 */
fun formatCompactDuration(seconds: Int, emptyWhenZero: Boolean = false): String {
    if (seconds <= 0) return if (emptyWhenZero) "" else "0m"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    if (hours > 0) return "${hours}h${minutes}m"
    return "${minutes}m"
}

@JsonClass(generateAdapter = true)
data class OfficialTravelPoint(
    @Json(ignore = true) val raw: Map<String, Any?> = emptyMap(),
    val lng: String = "",
    val heading: String = "",
    val starsNum: String = "",
    val lat: String = "",
    val reportTime: String = "",
    val speed: String = "",
) {
    val latitude: Double? get() = lat.toDoubleOrNull()
    val longitude: Double? get() = lng.toDoubleOrNull()

    val hasCoordinate: Boolean get() = latitude != null && longitude != null

    companion object {
        fun fromJson(json: Map<String, Any?>): OfficialTravelPoint = OfficialTravelPoint(
            raw = stringKeyedMap(json),
            lng = clean(json["lng"]) ?: "",
            heading = clean(json["heading"]) ?: "",
            starsNum = clean(json["starsNum"]) ?: "",
            lat = clean(json["lat"]) ?: "",
            reportTime = clean(json["reportTime"]) ?: "",
            speed = clean(json["speed"]) ?: "",
        )
    }
}

private fun travelRecords(value: Any?): List<OfficialTravelRecord> =
    parsePersistedMapList(value).map { OfficialTravelRecord.fromJson(it) }

private fun clean(value: Any?): String? = cleanTextOrNull(value)
