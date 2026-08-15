/**
 * Port-validation tests for `com.tailg.plus.domain.control.ControlCommandExecutor`
 * (Dart → Kotlin). Vectors lifted verbatim from `tailg-ble-app/test/control_command_executor_branches_test.dart`.
 */
package com.tailg.plus.domain.control

import com.tailg.plus.data.model.CommandCode
import com.tailg.plus.data.model.OfficialVehicle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlCommandExecutorBranchesTest {

  private class FakeCloudState(
    override val signedIn: Boolean,
    override val selectedVehicle: OfficialVehicle?,
  ) : ControlCloudState {
    override fun linkedLocalVehicleId(officialVehicleKey: String): String? = null
  }

  private fun availability(
    channel: OfficialControlChannel,
    bleReady: Boolean = false,
    signedIn: Boolean = true,
    withVehicle: Boolean = true,
  ): ControlChannelAvailability {
    val vehicle = OfficialVehicle.fromJson(
      mapOf(
        "carId" to "c1",
        "carNickName" to "车",
        "modelType" to 3,
        "isGps" to 1,
      ),
    )
    val state = FakeCloudState(
      signedIn = signedIn,
      selectedVehicle = if (withVehicle) vehicle else null,
    )
    return ControlChannelResolver.resolve(
      cloudState = state,
      bleReady = bleReady,
      channel = channel,
    )
  }

  @Test
  fun `BLE branch when forced ble and ready`() = runTest {
    val calls = mutableListOf<String>()
    val executor = ControlCommandExecutor(
      sendBleCommand = { cmd ->
        calls.add("ble:${cmd.name}")
        true
      },
      sendCloudCommand = {
        calls.add("cloud")
        "ok"
      },
    )
    val result = executor.send(
      command = CommandCode.LOCK,
      availability = availability(
        channel = OfficialControlChannel.BLE,
        bleReady = true,
      ),
    )
    assertTrue(result.success)
    assertEquals(ControlCommandTransport.BLE, result.transport)
    assertEquals(listOf("ble:LOCK"), calls)
  }

  @Test
  fun `cloud branch when forced cloud`() = runTest {
    val calls = mutableListOf<String>()
    val executor = ControlCommandExecutor(
      sendBleCommand = {
        calls.add("ble")
        true
      },
      sendCloudCommand = { cmd ->
        calls.add("cloud:${cmd.name}")
        "success"
      },
    )
    val result = executor.send(
      command = CommandCode.POWER_ON,
      availability = availability(
        channel = OfficialControlChannel.OFFICIAL_CLOUD,
      ),
    )
    assertTrue(result.success)
    assertEquals(ControlCommandTransport.OFFICIAL_CLOUD, result.transport)
    assertEquals(listOf("cloud:POWER_ON"), calls)
  }

  @Test
  fun `unavailable when neither path ready`() = runTest {
    val executor = ControlCommandExecutor(
      sendBleCommand = { true },
      sendCloudCommand = { "ok" },
    )
    val result = executor.send(
      command = CommandCode.FIND,
      availability = availability(
        channel = OfficialControlChannel.AUTOMATIC,
        bleReady = false,
        signedIn = false,
        withVehicle = false,
      ),
    )
    assertFalse(result.success)
    assertEquals(ControlCommandTransport.UNAVAILABLE, result.transport)
  }

  @Test
  fun `BLE preflight failure prevents the command write`() = runTest {
    val sent = mutableListOf<CommandCode>()
    val executor = ControlCommandExecutor(
      beforeBleCommand = { command ->
        if (command == CommandCode.OPEN_SEAT) "当前车辆固件不支持开坐垫" else null
      },
      sendBleCommand = { command ->
        sent.add(command)
        true
      },
      sendCloudCommand = { "ok" },
    )

    val result = executor.send(
      command = CommandCode.OPEN_SEAT,
      availability = availability(
        channel = OfficialControlChannel.BLE,
        bleReady = true,
      ),
    )

    assertFalse(result.success)
    assertEquals(ControlCommandTransport.BLE, result.transport)
    assertEquals("当前车辆固件不支持开坐垫", result.failureMessage)
    assertTrue(sent.isEmpty())
  }
}
