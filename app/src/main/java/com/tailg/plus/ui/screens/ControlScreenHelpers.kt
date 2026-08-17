package com.tailg.plus.ui.screens

import com.tailg.plus.data.ble.BikeState
import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.data.ble.platform.ConnectionState
import com.tailg.plus.data.cloud.OfficialCloudState
import com.tailg.plus.data.model.BatterySnapshot
import com.tailg.plus.data.model.CommandCode
import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.domain.control.ControlCloudState
import com.tailg.plus.ui.components.OfficialBleChipState
import com.tailg.plus.util.formatCompactDecimal
import com.tailg.plus.util.formatDecimalDown

/**
 * Pure state/formatting helpers extracted from [ControlScreen].
 *
 * These functions touch no Compose state and no Android resources, so they
 * stay unit-testable and keep the screen composable lean.
 */

internal fun currentPowerState(
  bleBikeState: BikeState?,
  cloudVehicle: OfficialVehicle?,
): Boolean? {
  if (bleBikeState != null) return bleBikeState.isPowerOn
  val acc = cloudVehicle?.acc
  return acc?.let { it == 1 }
}

internal fun currentLockState(
  bleBikeState: BikeState?,
  cloudVehicle: OfficialVehicle?,
): Boolean? {
  if (bleBikeState != null) return bleBikeState.isLocked
  val defence = cloudVehicle?.defenceStatus
  return defence?.let { it == 1 }
}

internal fun officialBleChipState(
  vehicle: OfficialVehicle?,
  connectionManager: ConnectionManager,
  bleState: ConnectionState,
  busy: Boolean,
): OfficialBleChipState {
  if (vehicle == null) return OfficialBleChipState.Hidden
  if (connectionManager.isProtocolLoggedIn) return OfficialBleChipState.Connected
  if (bleState == ConnectionState.CONNECTING ||
    bleState == ConnectionState.CONNECTED ||
    bleState == ConnectionState.RECONNECTING
  ) {
    return OfficialBleChipState.Connecting
  }
  return OfficialBleChipState.ClickToConnect
}

private val NON_DIGIT_PATTERN = Regex("[^\\d.]")

internal fun rangeLabel(battery: BatterySnapshot): String {
  val remaining = battery.remainingMileage?.trim()
  if (!remaining.isNullOrEmpty()) {
    val cleaned = remaining.replace(NON_DIGIT_PATTERN, "")
    val parsed = cleaned.toDoubleOrNull()
    if (parsed != null) return "${formatCompactDecimal(parsed)} km"
    return if (remaining.contains("km")) remaining else "$remaining km"
  }
  val estimated = battery.estimatedRangeKm
  if (estimated != null) return "${formatCompactDecimal(estimated)} km"
  return "--"
}

internal fun todayRideLabel(cloudState: OfficialCloudState): String {
  val direct = cloudState.todayRideMileage.trim()
  if (direct.isNotEmpty()) {
    val cleaned = direct.replace(NON_DIGIT_PATTERN, "")
    val parsed = cleaned.toDoubleOrNull()
    if (parsed != null) return "${formatCompactDecimal(parsed)} km"
    return if (direct.lowercase().contains("km")) direct else "$direct km"
  }
  return "--"
}

internal fun totalMileageLabel(vehicle: OfficialVehicle?): String {
  val m = vehicle?.mileage
  if (m != null && m > 0) return "${formatCompactDecimal(m)} km"
  return "--"
}

internal fun lastRideVisuals(cloudState: OfficialCloudState): Pair<String, String> {
  var latest: com.tailg.plus.data.model.OfficialTravelRecord? = null
  for (day in cloudState.travelDays) {
    for (record in day.records) {
      if (latest == null || record.startTime.compareTo(latest.startTime) > 0) {
        latest = record
      }
    }
  }
  if (latest == null) return "--" to "--"
  val distKm = latest.mileageKm
  val mins = (latest.durationSeconds / 60.0).toInt()
  val dist = "${formatDecimalDown(distKm, fractionDigits = 1)} km"
  val dur = if (mins > 0) "$mins min" else latest.durationLabel
  return dist to dur
}

internal fun successTitle(command: CommandCode, titles: Map<CommandCode, String>, format: String): String =
  titles[command] ?: format.format(command.label)

internal fun successSubtitle(command: CommandCode, subtitles: Map<CommandCode, String>): String =
  subtitles[command] ?: command.label

internal fun failureMessage(command: CommandCode, detail: String?, format: String, detailFormat: String): String {
  val text = detail?.trim() ?: ""
  if (text.isEmpty()) return format.format(command.label)
  if (text.contains(command.label)) return text
  return detailFormat.format(command.label, text)
}

/** Dart `_unconfirmedMessage` — cloud publish landed but the vehicle never confirmed. */
internal fun unconfirmedMessage(command: CommandCode, titles: Map<CommandCode, String>, format: String): String =
  titles[command] ?: format.format(command.label)

internal fun OfficialCloudState.asControlCloudState(): ControlCloudState = object : ControlCloudState {
  override val signedIn: Boolean get() = this@asControlCloudState.signedIn
  override val selectedVehicle: OfficialVehicle? get() = this@asControlCloudState.selectedVehicle
  override fun linkedLocalVehicleId(officialVehicleKey: String): String? =
    this@asControlCloudState.linkedLocalVehicleId(officialVehicleKey)
}

internal fun CommandCode.toBleCommandCode(): com.tailg.plus.data.ble.CommandCode =
  when (this) {
    CommandCode.LOCK -> com.tailg.plus.data.ble.CommandCode.lock
    CommandCode.UNLOCK -> com.tailg.plus.data.ble.CommandCode.unlock
    CommandCode.OPEN_SEAT -> com.tailg.plus.data.ble.CommandCode.openSeat
    CommandCode.POWER_ON -> com.tailg.plus.data.ble.CommandCode.powerOn
    CommandCode.POWER_OFF -> com.tailg.plus.data.ble.CommandCode.powerOff
    CommandCode.FIND -> com.tailg.plus.data.ble.CommandCode.find
    CommandCode.READ_STATE -> com.tailg.plus.data.ble.CommandCode.readState
    CommandCode.READ_ANTI_THEFT -> com.tailg.plus.data.ble.CommandCode.readAntiTheft
  }
