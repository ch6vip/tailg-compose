package com.tailg.plus.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Port of `lib/models/persistence_value.dart` (top-level helpers).
 *
 * These helpers normalize values that survive persistence (DataStore / JSON
 * files) back into typed Kotlin values, exactly like the Dart originals:
 *
 * - Dart `Map<String, dynamic>`  ↔ Kotlin `Map<String, Any?>`
 * - Dart `DateTime`              ↔ Kotlin `java.time.Instant`
 * - Dart `num`                   ↔ Kotlin `Number` (`Int`/`Double`/`Long`/…)
 *
 * Deviation notes:
 * - [parsePersistedMap] throws [IllegalArgumentException] (Dart: `FormatException`)
 *   when a map key is not a [String]; the message text is kept in Chinese-free
 *   English to match the original intent ("Persisted map keys must be strings").
 * - [parseDateTimeLenient] treats naive (timezone-less) date-times as UTC,
 *   whereas Dart's `DateTime.tryParse` treats them as device-local time. This is
 *   deterministic and safe for round-tripping ISO-8601 strings produced by
 *   `Instant.toString()`; callers that need local wall-clock semantics must pass
 *   their zone as `defaultZone` (see `OfficialCloudMessage`).
 */
/**
 * Render a dynamic scalar the way Dart's `jsonDecode` + `toString()` would:
 * JSON integers stay integers. Moshi's plain `Any` adapter parses every JSON
 * number as [Double], and Kotlin renders doubles ≥ 1e7 in scientific notation
 * ("1.71234567895E7") — sending such a string back as carId/uid makes the
 * official endpoints answer 400. Integral doubles are therefore rendered
 * losslessly as integer strings (exact up to 2^53, which covers every id the
 * API returns); fractional doubles keep their normal rendering.
 */
fun parsePersistedString(value: Any?): String = renderScalar(value)?.trim() ?: ""

/** Scalar renderer shared with parsers that do raw `.toString()` on JSON maps. */
internal fun renderScalar(value: Any?): String? = when (value) {
    null -> null
    is Double -> renderFiniteDouble(value)
    is Float -> renderFiniteDouble(value.toDouble())
    else -> value.toString()
}

private fun renderFiniteDouble(value: Double): String {
    // Integral values (the common id case: JSON `171234567895`) render as
    // integers — exact within the 2^53 double mantissa.
    if (value == Math.floor(value) && Math.abs(value) <= 9007199254740992.0) {
        return value.toLong().toString()
    }
    // Dart prints doubles ≥ 1e7 in plain decimal ("17123456.7895") while
    // Java/Kotlin switches to scientific notation at 1e7
    // ("1.71234567895E7") — the official endpoints accept only the former.
    // Expand Java's shortest-round-trip form to plain decimal, preserving
    // the exact digits Dart would have sent.
    val text = value.toString()
    val eIndex = text.indexOf('E')
    if (eIndex <= 0) return text
    val exponent = text.substring(eIndex + 1).toInt()
    if (exponent < 0) return text // tiny magnitudes never occur as ids
    var mantissa = text.substring(0, eIndex)
    var negative = false
    if (mantissa.startsWith("-")) {
        negative = true
        mantissa = mantissa.substring(1)
    }
    val digits = StringBuilder()
    var pointPos = -1
    for (char in mantissa) {
        if (char == '.') {
            pointPos = digits.length
            continue
        }
        digits.append(char)
    }
    if (pointPos < 0) pointPos = digits.length
    pointPos += exponent
    val out = StringBuilder()
    if (negative) out.append('-')
    when {
        pointPos >= digits.length -> {
            out.append(digits)
            repeat(pointPos - digits.length) { out.append('0') }
        }
        pointPos <= 0 -> {
            out.append("0.")
            repeat(-pointPos) { out.append('0') }
            out.append(digits)
        }
        else -> out.append(digits, 0, pointPos).append('.').append(digits, pointPos, digits.length)
    }
    return out.toString()
}

fun parsePersistedStringOr(value: Any?, fallback: String): String {
    val parsed = parsePersistedString(value)
    return if (parsed.isEmpty()) fallback else parsed
}

fun parsePersistedStringList(value: Any?): List<String> {
    val strings = mutableListOf<String>()
    for (item in persistedListItems(value)) {
        if (item is String) strings.add(item)
    }
    return strings
}

fun parsePersistedMap(value: Any?): Map<String, Any?>? {
    if (value !is Map<*, *>) return null
    val parsed = linkedMapOf<String, Any?>()
    for ((key, entryValue) in value.entries) {
        if (key !is String) {
            throw IllegalArgumentException("Persisted map keys must be strings, got: $key")
        }
        parsed[key] = entryValue
    }
    return parsed
}

fun parsePersistedMapList(value: Any?): List<Map<String, Any?>> {
    val maps = mutableListOf<Map<String, Any?>>()
    for (item in persistedListItems(value)) {
        val parsed = parsePersistedMap(item)
        if (parsed != null) maps.add(parsed)
    }
    return maps
}

private fun persistedListItems(value: Any?): List<Any?> =
    if (value !is List<*>) emptyList() else value.toList()

fun parsePersistedDouble(value: Any?): Double? {
    if (value is Number) return value.toDouble()
    if (value is String) return value.trim().toDoubleOrNull()
    return null
}

fun parsePersistedInt(value: Any?): Int? {
    if (value is Number) return value.toInt()
    if (value is String) return value.trim().toIntOrNull()
    return null
}

fun parsePersistedBool(value: Any?): Boolean {
    if (value is Boolean) return value
    if (value is Number) return value.toDouble() != 0.0
    if (value is String) {
        val normalized = value.trim().lowercase()
        return normalized == "true" || normalized == "1" || normalized == "yes"
    }
    return false
}

fun parsePersistedDate(value: Any?): Instant? {
    if (value == null) return null
    return parseDateTimeLenient(value.toString())
}

fun parsePersistedDateOr(
    value: Any?,
    fallback: Instant?,
    clock: () -> Instant = { Instant.now() },
): Instant = parsePersistedDate(value) ?: fallback ?: clock()

/**
 * Lenient ISO-8601 parse mirroring Dart's `DateTime.tryParse`, which accepts a
 * space separator (`"2021-01-01 10:00:00"`), date-only values, and offset
 * suffixes. Naive persisted values default to UTC; wire parsers can supply
 * the device zone to preserve local wall-clock semantics.
 */
internal fun parseDateTimeLenient(text: String, defaultZone: ZoneId = ZoneOffset.UTC): Instant? {
    val normalized = text.trim().replaceFirst(" ", "T")
    try {
        return Instant.parse(normalized)
    } catch (_: Exception) {
        // fall through to the next format
    }
    try {
        return OffsetDateTime.parse(normalized).toInstant()
    } catch (_: Exception) {
        // fall through
    }
    try {
        return LocalDateTime.parse(normalized).atZone(defaultZone).toInstant()
    } catch (_: Exception) {
        // fall through
    }
    try {
        return LocalDate.parse(normalized).atStartOfDay(defaultZone).toInstant()
    } catch (_: Exception) {
        // fall through
    }
    return null
}

/**
 * Dart `Map<String, dynamic>.unmodifiable(...)` equivalent: re-keys a raw map
 * to `String` keys (throwing on non-string keys, like the Dart helper) and
 * returns an immutable copy.
 */
internal fun stringKeyedMap(value: Map<*, *>): Map<String, Any?> {
    val parsed = parsePersistedMap(value)
        ?: throw IllegalArgumentException("Persisted map keys must be strings")
    return parsed.toMap()
}
