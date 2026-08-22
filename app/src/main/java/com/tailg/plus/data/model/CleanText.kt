package com.tailg.plus.data.model

/**
 * Shared decoder helper for official-API string fields: trim, then map blank,
 * "--" and case-insensitive "null" to null (Dart `_clean` semantics). Numbers
 * pass through via [toString], so real zero values survive.
 *
 * Previously duplicated as private `clean` / `cleanText` in
 * OfficialCloudMessage / OfficialLocationData / OfficialTravel /
 * OfficialBatteryInfo / BatterySnapshot.
 */
internal fun cleanTextOrNull(value: Any?): String? {
    if (value == null) return null
    val text = value.toString().trim()
    if (text.isEmpty() || text == "--" || text.lowercase() == "null") return null
    return text
}
