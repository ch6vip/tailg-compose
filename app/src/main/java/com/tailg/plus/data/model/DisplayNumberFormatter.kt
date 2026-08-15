package com.tailg.plus.data.model

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Port of `lib/services/display_number_formatter.dart` into the model package.
 *
 * `lib/models/*` (e.g. `OfficialFenceData`, `OfficialTravelRecord`) call these
 * formatters directly, so they are kept here as pure functions instead of
 * living in `data.cloud` (which would create a models → cloud dependency).
 *
 * [formatFixed] is an extra helper (not part of the Dart formatter file) that
 * emulates Dart's `double.toStringAsFixed`: round-half-to-even, trailing zeros
 * preserved (`2.5` → `"2"`, `1.25` → `"1.2"`, `12.0` → `"12.00"` for 2 digits).
 */
fun formatFixed(value: Double, fractionDigits: Int): String =
    BigDecimal.valueOf(value).setScale(fractionDigits, RoundingMode.HALF_EVEN).toPlainString()

/** Format [value] with [fractionDigits], then drop a trailing ".0" (compact). */
fun formatCompactDecimal(value: Double, fractionDigits: Int = 1): String {
    val fixed = formatFixed(value, fractionDigits)
    if (fractionDigits > 0 && fixed.endsWith(".0")) {
        return fixed.dropLast(2)
    }
    return fixed
}

/** Same as [formatCompactDecimal] for text payloads; returns [value] unchanged when not numeric. */
fun formatCompactDecimalText(value: String, fractionDigits: Int = 1): String {
    val parsed = value.toDoubleOrNull() ?: return value
    return formatCompactDecimal(parsed, fractionDigits)
}

/** Human-readable distance label: meters below 1 km, compact km otherwise. */
fun formatDistanceMeters(meters: Double): String {
    if (meters >= 1000.0) {
        return "${formatCompactDecimal(meters / 1000.0)}km"
    }
    return "${formatFixed(meters, 0)}m"
}

/**
 * Official `deviceTravel` mileage / totalMileage payloads are meters. Matches
 * official parsing: take the integer part before `.`, then convert.
 */
fun parseTravelMileageMeters(raw: String?): Double {
    val text = raw?.trim() ?: ""
    if (text.isEmpty()) return 0.0
    val head = text.substringBefore('.').trim()
    val match = Regex("-?\\d+").find(head)
    if (match != null) {
        return abs(match.value.toDoubleOrNull() ?: 0.0)
    }
    return abs(text.toDoubleOrNull() ?: 0.0)
}

/** Meters → kilometers (official always divides travel mileage by 1000). */
fun travelMetersToKm(meters: Double): Double = meters / 1000.0

/**
 * Format travel mileage like the official list/detail adapters:
 * - [alwaysKm] false (list): `<1000m` → `500m`, else `57.29km`
 * - [alwaysKm] true (ride-stats style): always km with down-rounded decimals
 */
fun formatTravelMileageMeters(meters: Double, alwaysKm: Boolean = false): String {
    val value = meters
    if (value.isNaN() || value.isInfinite()) return "--"
    val intMeters = abs(value).toInt()
    if (!alwaysKm && intMeters < 1000) {
        return "${intMeters}m"
    }
    return "${formatDecimalDown(intMeters / 1000.0, fractionDigits = 2)}km"
}

/** Parse a travel mileage text payload (meters) and format for display. */
fun formatTravelMileageMetersText(raw: String?, alwaysKm: Boolean = false): String {
    val text = raw?.trim() ?: ""
    if (text.isEmpty()) return ""
    return formatTravelMileageMeters(parseTravelMileageMeters(text), alwaysKm)
}

/**
 * Decimal format with truncate-toward-zero semantics (Dart `RoundingMode.DOWN`
 * equivalent), then strip trailing zeros / dot for compact UI labels.
 */
fun formatDecimalDown(value: Double, fractionDigits: Int = 2): String {
    if (fractionDigits <= 0) {
        return value.toLong().toString()
    }
    var factor = 1
    repeat(fractionDigits) { factor *= 10 }
    val scaled = value * factor
    val truncated = if (value < 0) ceil(scaled) else floor(scaled)
    val fixed = formatFixed(truncated / factor, fractionDigits)
    var trimmed = fixed
    while (trimmed.contains('.') && trimmed.endsWith("0")) {
        trimmed = trimmed.dropLast(1)
    }
    if (trimmed.endsWith(".")) {
        trimmed = trimmed.dropLast(1)
    }
    return trimmed
}
