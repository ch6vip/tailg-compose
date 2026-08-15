package com.tailg.plus.util

import java.time.Duration
import java.time.LocalDateTime

/**
 * Port of `lib/services/display_time_formatter.dart` (top-level functions).
 *
 * Dart `DateTime` (local wall clock) maps to `java.time.LocalDateTime`: the
 * Dart source never touches UTC or zones here, so no conversions are needed.
 * All outputs are zero-padded like Dart `_twoDigits` and involve no locale.
 */
private fun twoDigits(value: Int): String = value.toString().padStart(2, '0')

/** `formatDateText`: `yyyy-MM-dd`. */
fun formatDateText(time: LocalDateTime): String =
    "${time.year}-${twoDigits(time.monthValue)}-${twoDigits(time.dayOfMonth)}"

/**
 * `normalizeOfficialDateKey`: normalize travel/date payloads to a `yyyy-MM-dd`
 * day key. Accepts `yyyy-MM-dd`, `yyyy/MM/dd` and longer timestamps; blank
 * input returns an empty string. Only the first 10 chars are kept.
 */
fun normalizeOfficialDateKey(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val datePart = if (trimmed.length >= 10) trimmed.substring(0, 10) else trimmed
    return datePart.replace('/', '-')
}

/** `formatDateMinuteText`: `yyyy-MM-dd HH:mm`. */
fun formatDateMinuteText(time: LocalDateTime): String =
    "${formatDateText(time)} ${formatHourMinuteText(time.hour, time.minute)}"

/** `formatMonthText`: `yyyy-MM`. */
fun formatMonthText(time: LocalDateTime): String =
    "${time.year}-${twoDigits(time.monthValue)}"

/**
 * `parseMonthText`: strict `yyyy-MM`. Returns the first day of the month, or
 * null for malformed / out-of-range input (month outside 1..12).
 */
fun parseMonthText(value: String): LocalDateTime? {
    val parts = value.trim().split('-')
    if (parts.size != 2) return null
    // Dart int.tryParse accepts leading/trailing whitespace in each part.
    val year = parts[0].trim().toIntOrNull() ?: return null
    val month = parts[1].trim().toIntOrNull() ?: return null
    if (month < 1 || month > 12) return null
    return LocalDateTime.of(year, month, 1, 0, 0)
}

/** `shiftMonthText`: shift a `yyyy-MM` value by [delta] months (null on invalid). */
fun shiftMonthText(
    month: String,
    delta: Int,
    clock: () -> LocalDateTime = { LocalDateTime.now() },
): String? {
    val current = parseMonthText(month) ?: return null
    return shiftMonthDate(current, delta, clock)
}

/**
 * `shiftMonthDate`: same bounds as [shiftMonthText] for an already-parsed
 * month. Advancing past the current calendar month is blocked for
 * travel/stats navigation (future months are not allowed).
 */
fun shiftMonthDate(
    current: LocalDateTime,
    delta: Int,
    clock: () -> LocalDateTime = { LocalDateTime.now() },
): String? {
    val next = current.plusMonths(delta.toLong())
    if (delta > 0) {
        val now = clock()
        val currentMonthStart = LocalDateTime.of(now.year, now.monthValue, 1, 0, 0)
        if (next.isAfter(currentMonthStart)) return null
    }
    return formatMonthText(next)
}

/** `formatMonthDayMinuteText`: `MM/dd HH:mm`. */
fun formatMonthDayMinuteText(time: LocalDateTime): String =
    "${twoDigits(time.monthValue)}/${twoDigits(time.dayOfMonth)} " +
        formatHourMinuteText(time.hour, time.minute)

/** `formatHourMinuteText`: padded `HH:mm` for pickers / compact clocks. */
fun formatHourMinuteText(hour: Int, minute: Int): String =
    "${twoDigits(hour)}:${twoDigits(minute)}"

/** `formatLogClockTime`: `HH:mm:ss`. */
fun formatLogClockTime(time: LocalDateTime): String =
    "${formatHourMinuteText(time.hour, time.minute)}:${twoDigits(time.second)}"

/**
 * `formatRelativeSyncText`: human-readable sync age for the control page.
 * Null → `尚未同步`; else seconds/minutes/hours, or the absolute
 * `MM/dd HH:mm 同步` once older than a day.
 */
fun formatRelativeSyncText(
    time: LocalDateTime?,
    clock: () -> LocalDateTime = { LocalDateTime.now() },
): String {
    if (time == null) return "尚未同步"
    val now = clock()
    val seconds = Duration.between(time, now).toSeconds()
    if (seconds < 15) return "刚刚同步"
    if (seconds < 60) return "${seconds}秒前同步"
    val minutes = Duration.between(time, now).toMinutes()
    if (minutes < 60) return "${minutes}分钟前同步"
    val hours = Duration.between(time, now).toHours()
    if (hours < 24) return "${hours}小时前同步"
    return "${formatMonthDayMinuteText(time)} 同步"
}
