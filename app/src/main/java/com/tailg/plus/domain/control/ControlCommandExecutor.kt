package com.tailg.plus.domain.control

import com.tailg.plus.data.model.CommandCode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Port of `lib/services/control_command_executor.dart`.
 *
 * Pure state machine that picks the transport from [ControlChannelAvailability]
 * and executes the command through injected platform senders. The platform
 * services (BLE ConnectionManager / MQTT OfficialMqttService / cloud HTTP) are
 * ported by sibling agents; here they are constructor-injected lambdas so this
 * class stays unit-testable without a device.
 */
typealias BleCommandSender = suspend (CommandCode) -> Boolean
typealias BleCommandPreflight = suspend (CommandCode) -> String?
typealias CloudCommandSender = suspend (CommandCode) -> String
typealias CommandErrorMessage = (Throwable) -> String

class ControlCommandExecutor(
  val sendBleCommand: BleCommandSender? = null,
  val beforeBleCommand: BleCommandPreflight? = null,
  val sendCloudCommand: CloudCommandSender,
  val errorMessage: CommandErrorMessage = OfficialRemoteErrorMessages::describe,
  val bleTimeout: Duration = 15.seconds,
  val cloudTimeout: Duration = 20.seconds,
) {
  suspend fun send(
    command: CommandCode,
    availability: ControlChannelAvailability,
  ): ControlCommandResult = when (availability.channel) {
    OfficialControlChannel.BLE -> {
      if (!availability.canUseBle) unavailable(command, availability)
      else sendBle(command)
    }
    OfficialControlChannel.OFFICIAL_CLOUD -> {
      if (!availability.canUseCloud) {
        unavailable(command, availability)
      } else {
        sendCloud(command)
      }
    }
    OfficialControlChannel.AUTOMATIC -> {
      if (availability.canUseBle) sendBle(command)
      else if (availability.canUseCloud) sendCloud(command)
      else unavailable(command, availability)
    }
  }

  private suspend fun sendBle(command: CommandCode): ControlCommandResult {
    val sender = sendBleCommand
    if (sender == null) {
      return ControlCommandResult.failure(
        command,
        transport = ControlCommandTransport.BLE,
        message = "BLE 通道未配置",
      )
    }
    return try {
      val preflight = beforeBleCommand
      if (preflight != null) {
        val failure = try {
          withTimeout(bleTimeout) { preflight(command) }
        } catch (e: TimeoutCancellationException) {
          return ControlCommandResult.failure(
            command,
            transport = ControlCommandTransport.BLE,
            message = "BLE preflight timed out",
          )
        }
        if (failure != null && failure.trim().isNotEmpty()) {
          return ControlCommandResult.failure(
            command,
            transport = ControlCommandTransport.BLE,
            message = failure.trim(),
          )
        }
      }
      val success = try {
        withTimeout(bleTimeout) { sender(command) }
      } catch (e: TimeoutCancellationException) {
        return ControlCommandResult.failure(
          command,
          transport = ControlCommandTransport.BLE,
          message = "BLE command timed out",
        )
      }
      if (success) return ControlCommandResult.bleSuccess(command)
      ControlCommandResult.failure(
        command,
        transport = ControlCommandTransport.BLE,
        message = "${command.label}失败",
      )
    } catch (e: CancellationException) {
      // TimeoutCancellationException is handled above; rethrow real
      // cooperative cancellation so structured concurrency is preserved.
      throw e
    } catch (e: Exception) {
      ControlCommandResult.failure(
        command,
        transport = ControlCommandTransport.BLE,
        message = errorMessage(e),
      )
    }
  }

  private suspend fun sendCloud(command: CommandCode): ControlCommandResult {
    return try {
      val message = try {
        withTimeout(cloudTimeout) { sendCloudCommand(command) }
      } catch (e: TimeoutCancellationException) {
        return ControlCommandResult.failure(
          command,
          transport = ControlCommandTransport.OFFICIAL_CLOUD,
          message = OfficialRemoteErrorMessages.NETWORK_UNAVAILABLE,
        )
      }
      ControlCommandResult.cloudSuccess(command, message = message)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      ControlCommandResult.failure(
        command,
        transport = ControlCommandTransport.OFFICIAL_CLOUD,
        message = errorMessage(e),
      )
    }
  }

  private fun unavailable(
    command: CommandCode,
    availability: ControlChannelAvailability,
  ): ControlCommandResult {
    return ControlCommandResult.unavailable(
      command,
      availability.disabledReason,
    )
  }
}
