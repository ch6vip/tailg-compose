package com.tailg.plus.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
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
 *   an explicit `ZoneId` conversion (see `OfficialRidePeriod.requestKey`).
 */
fun parsePersistedString(value: Any?): String = value?.toString()?.trim() ?: ""

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
 * suffixes. Naive values are assumed UTC (see file KDoc).
 */
internal fun parseDateTimeLenient(text: String): Instant? {
    try {
        return Instant.parse(text)
    } catch (_: Exception) {
        // fall through to the next format
    }
    try {
        return Instant.parse(text.replaceFirst(" ", "T"))
    } catch (_: Exception) {
        // fall through
    }
    try {
        return OffsetDateTime.parse(text).toInstant()
    } catch (_: Exception) {
        // fall through
    }
    try {
        return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC)
    } catch (_: Exception) {
        // fall through
    }
    try {
        return LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant()
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
