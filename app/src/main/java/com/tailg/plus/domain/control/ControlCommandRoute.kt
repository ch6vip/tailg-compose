package com.tailg.plus.domain.control

import com.tailg.plus.data.model.CommandCode
import com.tailg.plus.data.model.OfficialVehicle

/**
 * Port of `lib/services/control_command_route.dart`.
 *
 * Applies command-specific constraints on top of the official vehicle route.
 *
 * The official app does not expose one universal six-command transport table:
 * seat control is local-only (`ControlFragment.chair()` requires BLE LOGIN and
 * never publishes MQTT) and some model families have no implementation.
 */
object ControlCommandRoute {
  fun resolve(
    base: ControlChannelAvailability,
    command: CommandCode,
    vehicle: OfficialVehicle?,
  ): ControlChannelAvailability {
    if (vehicle == null || !base.enabled) return base

    if (command == CommandCode.OPEN_SEAT) {
      if (vehicle.isCushionLockSupported != true) {
        return disabled(base, "当前车辆不支持开坐垫")
      }

      val canUseBle = base.canUseBle
      val canUseCloud = false
      val enabled = when (base.channel) {
        OfficialControlChannel.BLE -> canUseBle
        OfficialControlChannel.OFFICIAL_CLOUD -> false
        OfficialControlChannel.AUTOMATIC -> canUseBle
      }
      val willUseBle = enabled && canUseBle
      return base.copyWith(
        canUseBle = canUseBle,
        canUseCloud = canUseCloud,
        enabled = enabled,
        willUseBle = willUseBle,
        effectiveChannelLabel = if (enabled) "BLE" else "不可用",
        cloudUnavailableReason = "开坐垫需连接蓝牙",
        disabledReason = if (enabled) "" else "开坐垫需连接蓝牙",
      )
    }

    return base
  }

  private fun disabled(
    base: ControlChannelAvailability,
    reason: String,
  ): ControlChannelAvailability {
    return base.copyWith(
      canUseBle = false,
      canUseCloud = false,
      enabled = false,
      willUseBle = false,
      effectiveChannelLabel = "不可用",
      disabledReason = reason,
    )
  }
}
