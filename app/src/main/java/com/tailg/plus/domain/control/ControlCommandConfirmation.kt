package com.tailg.plus.domain.control

import com.tailg.plus.data.model.CommandCode

/**
 * Port of `lib/services/control_command_confirmation.dart`.
 *
 * Pure confirmation rules shared by UI and unit tests.
 *
 * Official remote path: MQTT publish success ≠ vehicle executed. Confirmation
 * requires either a MQTT status ACK that clears the pending command, or an
 * observed ACC/defence change to the expected post-command state.
 */
/** Snapshot of vehicle ACC/defence used as baseline before a command is sent. */
data class ControlCommandVehicleStateSnapshot(
  val isLocked: Boolean? = null,
  val isPowerOn: Boolean? = null,
)

data class ControlCommandConfirmationContext(
  val transport: ControlCommandTransport,
  val officialVehicleKey: String? = null,
)

data class PendingControlCommandConfirmationContext(
  val officialVehicleKey: String? = null,
) {
  fun forTransport(transport: ControlCommandTransport): ControlCommandConfirmationContext =
    ControlCommandConfirmationContext(
      transport = transport,
      officialVehicleKey = officialVehicleKey,
    )
}

class ControlCommandConfirmationGuard {
  fun allows(
    context: ControlCommandConfirmationContext,
    currentOfficialVehicleKey: String?,
  ): Boolean = when (context.transport) {
    ControlCommandTransport.BLE -> true
    ControlCommandTransport.OFFICIAL_CLOUD -> sameOfficialVehicle(
      expectedOfficialVehicleKey = context.officialVehicleKey,
      currentOfficialVehicleKey = currentOfficialVehicleKey,
    )
    ControlCommandTransport.UNAVAILABLE -> false
  }

  private fun sameOfficialVehicle(
    expectedOfficialVehicleKey: String?,
    currentOfficialVehicleKey: String?,
  ): Boolean =
    expectedOfficialVehicleKey != null &&
      expectedOfficialVehicleKey.isNotEmpty() &&
      expectedOfficialVehicleKey == currentOfficialVehicleKey
}

object ControlCommandConfirmation {
  val guard: ControlCommandConfirmationGuard = ControlCommandConfirmationGuard()

  /** lock / unlock / powerOn / powerOff need vehicle-state or MQTT ACK. */
  fun needsVehicleStateConfirmation(command: CommandCode): Boolean = when (command) {
    CommandCode.LOCK,
    CommandCode.UNLOCK,
    CommandCode.POWER_ON,
    CommandCode.POWER_OFF,
    -> true
    else -> false
  }

  /**
   * Search has no durable target state, but an MQTT search still needs the
   * current command response so official error payloads are not missed.
   */
  fun needsMqttResponse(
    command: CommandCode,
    pendingAtSend: String?,
  ): Boolean {
    val pending = pendingAtSend?.trim().orEmpty()
    return pending.isNotEmpty() && !needsVehicleStateConfirmation(command)
  }

  /** Whether [isLocked]/[isPowerOn] match the expected post-command state. */
  fun matchesExpectedState(
    command: CommandCode,
    isLocked: Boolean?,
    isPowerOn: Boolean?,
  ): Boolean = when (command) {
    CommandCode.LOCK -> isLocked == true
    CommandCode.UNLOCK -> isLocked == false
    CommandCode.POWER_ON -> isPowerOn == true
    CommandCode.POWER_OFF -> isPowerOn == false
    else -> true
  }

  /** MQTT layer confirmed when a non-empty pending command was cleared by status. */
  fun mqttPendingAcknowledged(
    pendingAtSend: String?,
    pendingNow: String?,
  ): Boolean {
    val start = pendingAtSend?.trim().orEmpty()
    if (start.isEmpty()) return false
    return pendingNow?.trim().orEmpty().isEmpty()
  }

  /**
   * Decide if a successful transport send may be shown as confirmed to the user.
   *
   * - BLE: device ACK is enough (official LOGIN path already executed locally).
   * - Cloud: MQTT publish alone is **not** enough when [needsVehicleStateConfirmation].
   *   Confirm via MQTT pending clear, or ACC/defence reaching expected **and**
   *   differing from [baseline] (avoids false success when already locked).
   */
  fun isConfirmed(
    command: CommandCode,
    transport: ControlCommandTransport,
    expectedOfficialVehicleKey: String?,
    currentOfficialVehicleKey: String?,
    baseline: ControlCommandVehicleStateSnapshot,
    current: ControlCommandVehicleStateSnapshot,
    mqttAcked: Boolean,
  ): Boolean {
    if (!guard.allows(
        context = ControlCommandConfirmationContext(
          transport = transport,
          officialVehicleKey = expectedOfficialVehicleKey,
        ),
        currentOfficialVehicleKey = currentOfficialVehicleKey,
      )
    ) {
      return false
    }

    if (transport == ControlCommandTransport.BLE) return true

    if (transport != ControlCommandTransport.OFFICIAL_CLOUD) return false

    // find / seat: no durable state signal; transport success stands.
    if (!needsVehicleStateConfirmation(command)) return true

    if (mqttAcked) return true

    val matches = matchesExpectedState(
      command = command,
      isLocked = current.isLocked,
      isPowerOn = current.isPowerOn,
    )
    if (!matches) return false

    val baselineAlreadyMatched = matchesExpectedState(
      command = command,
      isLocked = baseline.isLocked,
      isPowerOn = baseline.isPowerOn,
    )
    // Already in target state before send → only MQTT ACK can confirm.
    if (baselineAlreadyMatched) return false

    return true
  }
}
