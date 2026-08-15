/**
 * Port of the model section of `lib/services/induction_mode_service.dart`
 * (tailg-ble-app) → package `com.tailg.plus.service`.
 *
 * The 813-line Dart service file is split by responsibility:
 * - this file: `InductionStack`, `InductionModeSnapshot`, `RssiCalibration`
 * - `InductionModeService.kt`: the service class (facade + RSSI runtime + prefs)
 *
 * Dart → Kotlin mapping: `class X { final ... }` → `data class`; the Dart
 * `static const empty` snapshot becomes `EMPTY` (UPPER_SNAKE constant);
 * `copyWith` keeps the Dart sentinel semantics (`clearEnabled` / `clearError`
 * explicitly null out a field, a plain `null` argument keeps the current value).
 */
package com.tailg.plus.service

import com.tailg.plus.data.ble.defaultMaxDistanceM
import com.tailg.plus.data.ble.defaultMinDistanceM
import com.tailg.plus.data.ble.defaultRssiA
import com.tailg.plus.data.ble.defaultRssiFactor

/** Which official induction stack a model type uses. */
enum class InductionStack {
  /** QGJ HID + Proximity (`0x2030`-`0x2033` / `0x2140`). */
  QGJ,

  /** TLink ECU mode (`4A33` open/close + bond). */
  TLINK,

  /** Phone-side RSSI estimator (`BleConnectService`) for KKS / legacy. */
  RSSI,

  /** No local induction path (e.g. pure cloud YJ without BLE). */
  NONE,
}

/** Snapshot exposed to UI. */
data class InductionModeSnapshot(
  val stack: InductionStack,
  val enabled: Boolean?,
  val distance: Int?,
  val busy: Boolean,
  val bleReady: Boolean,
  val lastError: String? = null,

  /** ECU mode is on, but system BLE bond did not complete. */
  val bondIncomplete: Boolean = false,
) {
  /** true = induction, false = manual, null = unknown / still reading. */
  val unlockSelection: Boolean?
    get() = when {
      stack == InductionStack.NONE -> false
      enabled == null -> null
      else -> enabled
    }

  /**
   * Port of Dart `copyWith` — a `null` argument keeps the current value;
   * [clearError] / [clearEnabled] explicitly clear the respective fields.
   */
  fun copyWith(
    stack: InductionStack? = null,
    enabled: Boolean? = null,
    distance: Int? = null,
    busy: Boolean? = null,
    bleReady: Boolean? = null,
    lastError: String? = null,
    bondIncomplete: Boolean? = null,
    clearError: Boolean = false,
    clearEnabled: Boolean = false,
  ): InductionModeSnapshot = InductionModeSnapshot(
    stack = stack ?: this.stack,
    enabled = if (clearEnabled) null else (enabled ?: this.enabled),
    distance = distance ?: this.distance,
    busy = busy ?: this.busy,
    bleReady = bleReady ?: this.bleReady,
    lastError = if (clearError) null else (lastError ?: this.lastError),
    bondIncomplete = bondIncomplete ?: this.bondIncomplete,
  )

  companion object {
    /** Dart `InductionModeSnapshot.empty`. */
    val EMPTY = InductionModeSnapshot(
      stack = InductionStack.NONE,
      enabled = null,
      distance = null,
      busy = false,
      bleReady = false,
    )
  }
}

/**
 * Optional RSSI path-loss calibration (official CarControlInfoBean fields).
 * Defaults match the decompiled `BleConnectService` field initialisers
 * (`mRssiA = 52.1949` · `mRssiFactor = 4.6241` · `mMinRssiDistance = 2.0` ·
 * `mMaxRssiDistance = 3.0`).
 */
data class RssiCalibration(
  val rssiA: Double = defaultRssiA,
  val rssiFactor: Double = defaultRssiFactor,
  val minDistanceM: Double = defaultMinDistanceM,
  val maxDistanceM: Double = defaultMaxDistanceM,
) {
  companion object {
    /** Port of Dart `RssiCalibration.fromMap` — accepts camelCase and PascalCase keys. */
    fun fromMap(map: Map<String, Any?>?): RssiCalibration {
      if (map.isNullOrEmpty()) return RssiCalibration()
      fun parse(v: Any?, fallback: Double): Double = when (v) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull() ?: fallback
        else -> fallback
      }
      return RssiCalibration(
        rssiA = parse(map["rssiA"] ?: map["RssiA"], defaultRssiA),
        rssiFactor = parse(map["rssiFactor"] ?: map["RssiFactor"], defaultRssiFactor),
        minDistanceM = parse(map["minRssiDistance"] ?: map["MinRssiDistance"], defaultMinDistanceM),
        maxDistanceM = parse(map["maxRssiDistance"] ?: map["MaxRssiDistance"], defaultMaxDistanceM),
      )
    }
  }
}
