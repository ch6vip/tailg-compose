package com.tailg.plus.util

import com.tailg.plus.data.preferences.DistanceUnitPreference
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Port of `lib/services/display_number_formatter.dart` (top-level functions).
 *
 * Formatting semantic notes (Dart `num.toStringAsFixed` ↔ Java `String.format`):
 * - Dart `toStringAsFixed` is locale-independent and always uses `.`; we pin
 *   `Locale.US` for the same guarantee.
 * - Dart `toStringAsFixed` rounds to the closest decimal representation
 *   (double-conversion, tie handling unspecified by the Dart docs). Java
 *   `Formatter` rounds HALF_UP. The two agree except on exact `.5` ties at
 *   the last kept digit, which binary doubles rarely produce for real
 *   vehicle metrics — treated as an accepted, documented divergence.
 * - Dart switches to exponential notation for |value| >= 1e21; Java `%f`
 *   always prints plain decimal. Unreachable for mileage/energy values.
 */
/** Dart `toStringAsFixed(digits)` — fixed-point, locale-independent '.', HALF_UP. */
fun formatFixed(value: Double, fractionDigits: Int): String =
    String.format(Locale.US, "%.${fractionDigits}f", value)

/** `formatCompactDecimal(value, {fractionDigits: 1})`: drop a trailing `.0`. */
fun formatCompactDecimal(value: Double, fractionDigits: Int = 1): String {
    val fixed = formatFixed(value, fractionDigits)
    if (fractionDigits > 0 && fixed.endsWith(".0")) {
        return fixed.substring(0, fixed.length - 2)
    }
    return fixed
}

/** `formatCompactDecimalText`: returns [value] unchanged when not numeric. */
fun formatCompactDecimalText(value: String, fractionDigits: Int = 1): String {
    // Dart double.tryParse accepts leading/trailing whitespace and exponent
    // notation; toDoubleOrNull matches the exponent form but not whitespace,
    // so trim first. Non-numeric input returns the original string untouched.
    val parsed = value.trim().toDoubleOrNull() ?: return value
    return formatCompactDecimal(parsed, fractionDigits = fractionDigits)
}

/** `formatDistanceMeters`: meters below 1 km, compact km otherwise. */
fun formatDistanceMeters(
    meters: Double,
    unit: DistanceUnitPreference = DistanceUnitPreference.Metric,
): String {
    if (unit == DistanceUnitPreference.Imperial) {
        val feet = meters * FEET_PER_METER
        if (feet >= FEET_PER_MILE) {
            return formatCompactDecimal(feet / FEET_PER_MILE) + "mi"
        }
        return formatFixed(feet, 0) + "ft"
    }
    if (meters >= METERS_PER_KILOMETER) {
        return formatCompactDecimal(meters / METERS_PER_KILOMETER) + "km"
    }
    return formatFixed(meters, 0) + "m"
}

fun formatDistanceKilometers(
    kilometers: Double,
    unit: DistanceUnitPreference = DistanceUnitPreference.Metric,
    fractionDigits: Int = 1,
): String {
    return "${formatDistanceKilometersValue(kilometers, unit, fractionDigits)} ${distanceUnitSuffix(unit)}"
}

fun formatDistanceKilometersValue(
    kilometers: Double,
    unit: DistanceUnitPreference = DistanceUnitPreference.Metric,
    fractionDigits: Int = 1,
): String {
    val converted = if (unit == DistanceUnitPreference.Imperial) {
        kilometers * MILES_PER_KILOMETER
    } else {
        kilometers
    }
    return formatCompactDecimal(converted, fractionDigits)
}

fun formatDistanceKilometersText(
    raw: String?,
    unit: DistanceUnitPreference = DistanceUnitPreference.Metric,
    fractionDigits: Int = 1,
    missing: String = "--",
): String {
    val value = parseDisplayNumber(raw) ?: return missing
    return formatDistanceKilometers(value, unit, fractionDigits)
}

fun formatSpeedKilometersPerHour(
    kilometersPerHour: Double,
    unit: DistanceUnitPreference = DistanceUnitPreference.Metric,
    fractionDigits: Int = 1,
): String {
    val converted = if (unit == DistanceUnitPreference.Imperial) {
        kilometersPerHour * MILES_PER_KILOMETER
    } else {
        kilometersPerHour
    }
    return "${formatCompactDecimal(converted, fractionDigits)}${speedUnitSuffix(unit)}"
}

fun formatSpeedKilometersPerHourValue(
    raw: String?,
    unit: DistanceUnitPreference = DistanceUnitPreference.Metric,
    fractionDigits: Int = 1,
    missing: String = "--",
): String {
    val value = parseDisplayNumber(raw) ?: return missing
    val converted = if (unit == DistanceUnitPreference.Imperial) {
        value * MILES_PER_KILOMETER
    } else {
        value
    }
    return formatCompactDecimal(converted, fractionDigits)
}

fun distanceUnitSuffix(unit: DistanceUnitPreference): String =
    if (unit == DistanceUnitPreference.Imperial) "mi" else "km"

fun speedUnitSuffix(unit: DistanceUnitPreference): String =
    if (unit == DistanceUnitPreference.Imperial) "mph" else "km/h"

private val DISPLAY_NUMBER_PATTERN = Regex("[-+]?\\d+(?:\\.\\d+)?")

private fun parseDisplayNumber(raw: String?): Double? {
    val text = raw?.trim() ?: return null
    if (text.isEmpty() || text == "--" || text.equals("null", ignoreCase = true)) return null
    return DISPLAY_NUMBER_PATTERN.find(text)?.value?.toDoubleOrNull()
}

/**
 * `parseTravelMileageMeters`: official `deviceTravel`/`totalMileage` payloads
 * are meters. Take the integer part before `.`, parse the first `-?\d+` run,
 * return its absolute value; fall back to the whole text, then `0`.
 */
fun parseTravelMileageMeters(raw: String?): Double {
    val text = raw?.trim() ?: ""
    if (text.isEmpty()) return 0.0
    val head = text.substringBefore('.').trim()
    val match = TRAVEL_HEAD_NUMBER.find(head)
    if (match != null) {
        return match.value.toDoubleOrNull()?.let { abs(it) } ?: 0.0
    }
    return text.toDoubleOrNull()?.let { abs(it) } ?: 0.0
}

private val TRAVEL_HEAD_NUMBER = Regex("""-?\d+""")

/** `travelMetersToKm`: official always divides travel mileage by 1000. */
fun travelMetersToKm(meters: Double): Double = meters / 1000.0

/**
 * `formatTravelMileageMeters`:
 * - [alwaysKm] false (list): `<1000m` → `500m`, else `57.29km`
 * - [alwaysKm] true (ride-stats style): always km, decimals truncated down.
 */
fun formatTravelMileageMeters(
    meters: Double,
    alwaysKm: Boolean = false,
    unit: DistanceUnitPreference = DistanceUnitPreference.Metric,
): String {
    if (meters.isNaN() || meters.isInfinite()) return "--"
    val intMeters = abs(meters).toLong() // truncate toward zero
    if (unit == DistanceUnitPreference.Imperial) {
        val feet = abs(meters) * FEET_PER_METER
        if (!alwaysKm && feet < FEET_PER_MILE) {
            return "${feet.toLong()}ft"
        }
        return formatDecimalDown(feet / FEET_PER_MILE, fractionDigits = 2) + "mi"
    }
    if (!alwaysKm && intMeters < 1000) {
        return "${intMeters}m"
    }
    return formatDecimalDown(intMeters / 1000.0, fractionDigits = 2) + "km"
}

/** `formatTravelMileageMetersText`: blank input returns an empty string. */
fun formatTravelMileageMetersText(
    raw: String?,
    alwaysKm: Boolean = false,
    unit: DistanceUnitPreference = DistanceUnitPreference.Metric,
): String {
    val text = raw?.trim() ?: ""
    if (text.isEmpty()) return ""
    return formatTravelMileageMeters(
        parseTravelMileageMeters(text),
        alwaysKm = alwaysKm,
        unit = unit,
    )
}

private const val METERS_PER_KILOMETER = 1000.0
private const val FEET_PER_METER = 3.280839895013123
private const val FEET_PER_MILE = 5280.0
private const val MILES_PER_KILOMETER = 0.621371192237334

/**
 * `formatDecimalDown`: truncate toward zero at [fractionDigits] decimals,
 * then strip trailing zeros / the trailing dot for compact UI labels.
 *
 * `fractionDigits` is expected to be small (1-2); the Dart original also
 * overflows its integer factor for very large digit counts.
 */
fun formatDecimalDown(value: Double, fractionDigits: Int = 2): String {
    if (fractionDigits <= 0) return value.toLong().toString()
    var factor = 1
    repeat(fractionDigits) { factor *= 10 }
    val scaled = value * factor
    val truncated = if (value < 0.0) ceil(scaled) else floor(scaled)
    val fixed = formatFixed(truncated / factor, fractionDigits)
    if (!fixed.contains('.')) return fixed
    var trimmed = fixed
    while (trimmed.contains('.') && trimmed.endsWith("0")) {
        trimmed = trimmed.dropLast(1)
    }
    if (trimmed.endsWith(".")) {
        trimmed = trimmed.dropLast(1)
    }
    return trimmed
}
