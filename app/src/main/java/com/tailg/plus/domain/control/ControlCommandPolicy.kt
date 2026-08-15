package com.tailg.plus.domain.control

import com.tailg.plus.data.model.CommandCode

/**
 * Port of `lib/services/control_command_policy.dart`.
 *
 * Evaluates whether a command may be executed given durable local vehicle
 * state. MQTT error fields are command responses, not durable vehicle state —
 * they are evaluated by the MQTT status payload layer against the pending
 * command, not here.
 */
data class ControlCommandPolicyResult(
  val allowed: Boolean,
  val disabledReason: String?,
) {
  companion object {
    fun allowed(): ControlCommandPolicyResult = ControlCommandPolicyResult(
      allowed = true,
      disabledReason = null,
    )

    fun denied(reason: String): ControlCommandPolicyResult = ControlCommandPolicyResult(
      allowed = false,
      disabledReason = reason,
    )
  }
}

object ControlCommandPolicy {
  const val POWER_ON_FIND_DISABLED_REASON = "车辆已上电，不能寻车"
  const val VEHICLE_MOVING_DISABLED_REASON = "车辆行驶中，请勿操作"
  const val KEY_STARTED_DISABLED_REASON = "您已使用钥匙启动车辆，当前不支持此操作"
  const val NOT_POWERED_OFF_DISABLED_REASON = "车辆未断电，请勿操作"

  fun evaluate(
    command: CommandCode,
    isPowerOn: Boolean,
  ): ControlCommandPolicyResult {
    if (command == CommandCode.FIND && isPowerOn) {
      return ControlCommandPolicyResult.denied(POWER_ON_FIND_DISABLED_REASON)
    }
    return ControlCommandPolicyResult.allowed()
  }
}
