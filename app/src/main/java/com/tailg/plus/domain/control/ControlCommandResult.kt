package com.tailg.plus.domain.control

import com.tailg.plus.data.model.CommandCode

/**
 * Port of `lib/services/control_command_result.dart`.
 *
 * Terminal outcome of one control command send. The Dart class uses a private
 * constructor + factory constructors; Kotlin mirrors that with a private
 * constructor + companion factories.
 */
enum class ControlCommandTransport {
  BLE,
  OFFICIAL_CLOUD,
  UNAVAILABLE,
}

class ControlCommandResult private constructor(
  val command: CommandCode,
  val transport: ControlCommandTransport,
  val success: Boolean,
  val successMessage: String?,
  val failureMessage: String?,
) {
  /** BLE success means the device ACKed locally — refresh vehicle state. */
  val shouldRefreshBikeState: Boolean
    get() = success && transport == ControlCommandTransport.BLE

  companion object {
    fun bleSuccess(command: CommandCode): ControlCommandResult = ControlCommandResult(
      command = command,
      transport = ControlCommandTransport.BLE,
      success = true,
    )

    fun cloudSuccess(
      command: CommandCode,
      message: String,
    ): ControlCommandResult {
      val trimmedMessage = message.trim()
      val normalizedMessage = if (trimmedMessage.isEmpty()) "success" else message
      val lower = normalizedMessage.lowercase()
      // Strip channel tags from sendCommandPreferMqtt (mqtt:success / http:…).
      val body = if (normalizedMessage.contains(':')) {
        normalizedMessage.substring(normalizedMessage.indexOf(':') + 1)
      } else {
        normalizedMessage
      }
      val bodyLower = body.lowercase()
      val display = if (bodyLower == "success" || bodyLower == "ok" || lower == "success") {
        "${command.label}已完成"
      } else {
        body
      }
      return ControlCommandResult(
        command = command,
        transport = ControlCommandTransport.OFFICIAL_CLOUD,
        success = true,
        successMessage = display,
      )
    }

    fun unavailable(
      command: CommandCode,
      message: String,
    ): ControlCommandResult = ControlCommandResult(
      command = command,
      transport = ControlCommandTransport.UNAVAILABLE,
      success = false,
      failureMessage = message,
    )

    fun failure(
      command: CommandCode,
      transport: ControlCommandTransport = ControlCommandTransport.OFFICIAL_CLOUD,
      message: String,
    ): ControlCommandResult = ControlCommandResult(
      command = command,
      transport = transport,
      success = false,
      failureMessage = message,
    )
  }
}
