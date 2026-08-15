package com.tailg.plus.util

/**
 * Port of `lib/services/sensitive_value_masker.dart` (both classes from that
 * file live here to keep the port traceable).
 *
 * Length / substring semantics match Dart: Kotlin `String.length` and
 * `substring` operate on UTF-16 code units exactly like Dart, so mask
 * positions are identical, including for non-BMP input.
 */
object SensitiveValueMasker {

    /** Dart `compact(value, {emptyValue: '***', trim: true})`. */
    fun compact(value: String, emptyValue: String = "***", trim: Boolean = true): String {
        val text = if (trim) value.trim() else value
        if (text.isEmpty()) return emptyValue
        if (text.length <= 6) return "***"
        return text.substring(0, 3) + "***" + text.substring(text.length - 3)
    }

    /** Dart `phone(value, {minMaskLength: 7, shortValue: null, trim: false})`. */
    fun phone(
        value: String,
        minMaskLength: Int = 7,
        shortValue: String? = null,
        trim: Boolean = false,
    ): String {
        val text = if (trim) value.trim() else value
        if (text.length < minMaskLength) return shortValue ?: text
        return text.substring(0, 3) + "****" + text.substring(text.length - 4)
    }
}

/**
 * Log-line redactor. Port of `SensitiveTextRedactor` from the same Dart file.
 *
 * Regex port notes:
 * - Dart `caseSensitive: false` → `RegexOption.IGNORE_CASE`.
 * - All patterns are otherwise identical; both engines use ASCII `\b` word
 *   boundaries and support the `(?!Bearer\b)` negative lookahead.
 * - Kotlin `Regex.replace(input, transform)` iterates matches left-to-right
 *   and non-overlapping — the same contract as Dart `replaceAllMapped`.
 * - The replacement order is significant and must not be reordered.
 */
object SensitiveTextRedactor {

    /** `authorization: <value>` pairs whose value does not start with `Bearer`. */
    private val authorizationValuePattern = Regex(
        """(["']?\bauthorization\b["']?\s*[:=]\s*["']?)(?!Bearer\b)([^"'\s,&}]+)(["']?)""",
        RegexOption.IGNORE_CASE,
    )

    private val bearerTokenPattern = Regex(
        """\bBearer\s+([A-Za-z0-9._~+/=-]+)""",
        RegexOption.IGNORE_CASE,
    )

    /** `phone|token|imei|carId|uid|userId|password|frame|btmac|mac` key/value pairs. */
    private val sensitiveKeyValuePattern = Regex(
        """(["']?\b(?:phone|token|imei|carId|uid|userId|password|frame|btmac|mac)\b["']?\s*[:=]\s*["']?)([^"'\s,&}]+)(["']?)""",
        RegexOption.IGNORE_CASE,
    )

    /** Chinese mobile numbers: `1` followed by 10 digits. */
    private val phonePattern = Regex("""\b1\d{10}\b""")

    /** IMEI / serial-like runs of 14-17 digits. */
    private val imeiPattern = Regex("""\b\d{14,17}\b""")

    /** Colon- or dash-separated MAC (`AA:BB:CC:DD:EE:FF`). */
    private val macPattern = Regex("""\b(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\b""")

    /** Separator-less 12-hex-char MAC. */
    private val compactMacPattern = Regex("""\b[0-9A-Fa-f]{12}\b""")

    fun redact(value: String): String {
        return value
            .replace(bearerTokenPattern) { match ->
                "Bearer ${mask(match.groupValues[1])}"
            }
            .replace(authorizationValuePattern, ::maskGrouped)
            .replace(sensitiveKeyValuePattern, ::maskGrouped)
            .replace(phonePattern, ::maskMatch)
            .replace(imeiPattern, ::maskMatch)
            .replace(macPattern, ::maskMatch)
            .replace(compactMacPattern, ::maskMatch)
    }

    private fun maskGrouped(match: MatchResult): String =
        match.groupValues[1] + mask(match.groupValues[2]) + match.groupValues[3]

    private fun maskMatch(match: MatchResult): String = mask(match.value)

    private fun mask(value: String): String = SensitiveValueMasker.compact(value)
}
