/**
 * Port of `lib/ble/rssi_distance.dart` (tailg-ble-app) → package `com.tailg.plus.data.ble`.
 *
 * Official `BleConnectService.judgeDeviceDistance` RSSI → metres model
 * (`E:\ctf-aaa\tlddc\3.5.9\sources\com\tailg\run\intelligence\ble\BleConnectService.java`):
 *
 * ```
 * distance = 10 ^ ( (|avgRssi| - rssiA) / (rssiFactor * 10) )
 * ```
 *
 * Evidence (field initialisers, BleConnectService lines 61–64):
 *   mRssiA = 52.1949 · mRssiFactor = 4.6241 · mMinRssiDistance = 2.0 · mMaxRssiDistance = 3.0
 * and the estimator (line 613): `Math.pow(10.0, (Math.abs(jLongValue / 10) - mRssiA) / (mRssiFactor * 10.0))`
 * where `jLongValue` is the sum of the rolling 10-sample window
 * (`mRssiList.size() == 10`, poll `Thread.sleep(200)` → [RSSI_POLL_INTERVAL]).
 *
 * Classification thresholds mirror the service: `dPow <= mMinRssiDistance` → approach
 * (unlock/power-on), `dPow >= mMaxRssiDistance` → leave (lock/power-off).
 */
package com.tailg.plus.data.ble

import kotlin.math.abs
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Dart `defaultRssiA` = 52.1949. */
const val defaultRssiA = 52.1949

/** Dart `defaultRssiFactor` = 4.6241. */
const val defaultRssiFactor = 4.6241

/** Dart `defaultMinDistanceM` = 2.0 (approach → unlock). */
const val defaultMinDistanceM = 2.0

/** Dart `defaultMaxDistanceM` = 3.0 (leave → lock). */
const val defaultMaxDistanceM = 3.0

/** Dart `rssiSampleWindow` = 10 — rolling window size used by the official service. */
const val rssiSampleWindow = 10

/** Dart `rssiPollInterval` — poll interval matching `Thread.sleep(200)` in `startReadRssi`. */
val rssiPollInterval: Duration = 200.milliseconds

/**
 * Port of Dart `estimateDistanceFromRssiSamples` — log-distance path-loss model.
 * Throws [IllegalArgumentException] on an empty sample list, like Dart.
 */
fun estimateDistanceFromRssiSamples(
  rssiSamples: List<Int>,
  rssiA: Double = defaultRssiA,
  rssiFactor: Double = defaultRssiFactor,
): Double {
  if (rssiSamples.isEmpty()) {
    throw IllegalArgumentException("rssiSamples must not be empty")
  }
  val avg = rssiSamples.sum().toDouble() / rssiSamples.size
  val absAvg = abs(avg)
  return 10.0.pow((absAvg - rssiA) / (rssiFactor * 10.0))
}

/** Port of Dart `enum RssiProximityAction`. */
enum class RssiProximityAction {
  /** distance ≤ min → unlock / power on */
  approachUnlock,

  /** distance ≥ max → lock / power off */
  leaveLock,

  /** between thresholds — hold */
  hold,
}

/** Port of Dart `classifyDistance`. */
fun classifyDistance(
  distanceM: Double,
  minDistanceM: Double = defaultMinDistanceM,
  maxDistanceM: Double = defaultMaxDistanceM,
): RssiProximityAction {
  if (distanceM <= minDistanceM) return RssiProximityAction.approachUnlock
  if (distanceM >= maxDistanceM) return RssiProximityAction.leaveLock
  return RssiProximityAction.hold
}

/** Port of Dart `enum RssiTaskState` — official task latch (`mRssiTaskState`). */
enum class RssiTaskState { idle, pending, unlocked, poweredOn, poweredOff, locked }

/** Port of Dart `enum RssiProximityStep` — individual BLE operations. */
enum class RssiProximityStep { unlock, powerOn, powerOff, lock }

/**
 * Port of Dart `pendingRssiSteps` — only the operations still required for the
 * requested transition. Keeping intermediate states lets a failed power or lock
 * command be retried without pretending the whole transition succeeded.
 */
fun pendingRssiSteps(
  action: RssiProximityAction,
  state: RssiTaskState,
): List<RssiProximityStep> {
  return when (action) {
    RssiProximityAction.approachUnlock -> when (state) {
      RssiTaskState.idle, RssiTaskState.poweredOff, RssiTaskState.locked ->
        listOf(RssiProximityStep.unlock, RssiProximityStep.powerOn)
      RssiTaskState.unlocked -> listOf(RssiProximityStep.powerOn)
      RssiTaskState.pending, RssiTaskState.poweredOn -> emptyList()
    }
    RssiProximityAction.leaveLock -> when (state) {
      RssiTaskState.idle, RssiTaskState.unlocked, RssiTaskState.poweredOn ->
        listOf(RssiProximityStep.powerOff, RssiProximityStep.lock)
      RssiTaskState.poweredOff -> listOf(RssiProximityStep.lock)
      RssiTaskState.pending, RssiTaskState.locked -> emptyList()
    }
    RssiProximityAction.hold -> emptyList()
  }
}

/** Port of Dart `confirmedRssiState` — advance the latch on success only. */
fun confirmedRssiState(
  current: RssiTaskState,
  step: RssiProximityStep,
  success: Boolean,
): RssiTaskState {
  if (!success) return current
  return when (step) {
    RssiProximityStep.unlock -> RssiTaskState.unlocked
    RssiProximityStep.powerOn -> RssiTaskState.poweredOn
    RssiProximityStep.powerOff -> RssiTaskState.poweredOff
    RssiProximityStep.lock -> RssiTaskState.locked
  }
}

/** Port of Dart `shouldFireRssiAction`. */
fun shouldFireRssiAction(action: RssiProximityAction, state: RssiTaskState): Boolean =
  pendingRssiSteps(action, state).isNotEmpty()
