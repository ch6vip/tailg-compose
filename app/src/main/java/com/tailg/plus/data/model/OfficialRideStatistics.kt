package com.tailg.plus.data.model

import com.squareup.moshi.JsonClass
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Port of `lib/models/official_ride_statistics.dart`.
 *
 * [OfficialRidePeriod] is a plain Dart enum (no values); the display / wire
 * helpers were Dart extension members and are ported as Kotlin extension
 * properties/functions on the enum.
 *
 * `DateTime` → `Instant` mapping note: Dart's `DateTime.now()` is device-local,
 * so [OfficialRidePeriod.requestKey] and [officialIsoWeekNumber] convert the
 * [Instant] back to the system-default zone before reading year/month/day.
 */
enum class OfficialRidePeriod { DAY, WEEK, MONTH }

val OfficialRidePeriod.wireName: String
    get() = when (this) {
        OfficialRidePeriod.DAY -> "days"
        OfficialRidePeriod.WEEK -> "weeks"
        OfficialRidePeriod.MONTH -> "months"
    }

val OfficialRidePeriod.tabLabel: String
    get() = when (this) {
        OfficialRidePeriod.DAY -> "天"
        OfficialRidePeriod.WEEK -> "周"
        OfficialRidePeriod.MONTH -> "月"
    }

val OfficialRidePeriod.carbonTitle: String
    get() = when (this) {
        OfficialRidePeriod.DAY -> "日节碳量"
        OfficialRidePeriod.WEEK -> "周节碳量"
        OfficialRidePeriod.MONTH -> "月节碳量"
    }

val OfficialRidePeriod.mileageTitle: String
    get() = when (this) {
        OfficialRidePeriod.DAY -> "今日里程"
        OfficialRidePeriod.WEEK -> "本周里程"
        OfficialRidePeriod.MONTH -> "本月里程"
    }

fun OfficialRidePeriod.requestKey(now: Instant): String {
    val zoned = now.atZone(ZoneId.systemDefault())
    val year = zoned.year.toString().padStart(4, '0')
    val month = zoned.monthValue.toString().padStart(2, '0')
    return when (this) {
        OfficialRidePeriod.DAY -> "$year$month${zoned.dayOfMonth.toString().padStart(2, '0')}"
        OfficialRidePeriod.WEEK -> "$year$month${officialIsoWeekNumber(now).toString().padStart(2, '0')}"
        OfficialRidePeriod.MONTH -> "$year$month"
    }
}

/**
 * Official `app/appRiding/getRidingDetail` response payload.
 * Wire DTO → Moshi adapter; every field is kept as [String] exactly like the
 * Dart `json[key]?.toString().trim() ?? ''` parsing, and defaults to `""` so a
 * missing key yields `""` instead of a Moshi error.
 */
@JsonClass(generateAdapter = true)
data class OfficialRideStatistics(
    val avgSpeed: String = "",
    val carbonAbsorption: String = "",
    val carbonSaving: String = "",
    val dayMileage: String = "",
    val maxSpeed: String = "",
    val monthsMileage: String = "",
    val ridingCount: String = "",
    val ridingTime: String = "",
    val totalMileage: String = "",
    val weekMileage: String = "",
    val yearMileage: String = "",
) {
    fun mileageFor(period: OfficialRidePeriod): String = when (period) {
        OfficialRidePeriod.DAY -> dayMileage
        OfficialRidePeriod.WEEK -> weekMileage
        OfficialRidePeriod.MONTH -> monthsMileage
    }

    companion object {
        fun fromJson(json: Map<String, Any?>): OfficialRideStatistics {
            fun value(key: String): String = json[key]?.toString()?.trim() ?: ""
            return OfficialRideStatistics(
                avgSpeed = value("avgSpeed"),
                carbonAbsorption = value("carbonAbsorption"),
                carbonSaving = value("carbonSaving"),
                dayMileage = value("dayMileage"),
                maxSpeed = value("maxSpeed"),
                monthsMileage = value("monthsMileage"),
                ridingCount = value("ridingCount"),
                ridingTime = value("ridingTime"),
                totalMileage = value("totalMileage"),
                weekMileage = value("weekMileage"),
                yearMileage = value("yearMileage"),
            )
        }

        fun displayValue(value: String): String {
            val normalized = value.trim()
            return if (normalized.isEmpty()) "--" else normalized
        }

        /**
         * The official binding treats mileage fields as meters, drops the
         * decimal part, then converts to kilometres with two digits rounded
         * down (truncation, not rounding).
         */
        fun formatMileageKm(value: String): String {
            val normalized = value.trim()
            if (normalized.isEmpty()) return "--"
            val wholeMeters = normalized.substringBefore('.').toIntOrNull()
                ?: normalized.toDoubleOrNull()?.toInt()
            if (wholeMeters == null) return "--"
            val truncatedHundredths = wholeMeters * 100 / 1000
            return formatFixed(truncatedHundredths / 100.0, 2)
        }
    }
}

/**
 * Matches Java Calendar's Monday-first, seven-day minimum first week used by
 * the official app's `TimeUtil.getCurTimeYMW()`. Mirrors the Dart
 * [officialIsoWeekNumber] algorithm exactly (Thursday-of-week anchor).
 */
fun officialIsoWeekNumber(value: Instant): Int {
    val date = value.atZone(ZoneId.systemDefault()).toLocalDate()
    val weekThursday = date.plusDays((4 - date.dayOfWeek.value).toLong())
    val januaryFourth = LocalDate.of(weekThursday.year, 1, 4)
    val firstWeekThursday = januaryFourth.plusDays((4 - januaryFourth.dayOfWeek.value).toLong())
    return 1 + ChronoUnit.DAYS.between(firstWeekThursday, weekThursday).toInt() / 7
}
