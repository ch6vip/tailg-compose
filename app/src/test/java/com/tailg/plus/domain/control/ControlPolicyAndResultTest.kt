/**
 * Port-validation tests for `com.tailg.plus.domain.control` policy/result/operator
 * logic (Dart → Kotlin). Covers `control_command_policy.dart`,
 * `control_command_result.dart` and `official_car_operator_policy.dart`.
 */
package com.tailg.plus.domain.control

import com.tailg.plus.data.model.CommandCode
import com.tailg.plus.data.model.OfficialVehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlCommandPolicyTest {

  @Test
  fun `find is denied while the vehicle is powered on`() {
    val denied = ControlCommandPolicy.evaluate(
      command = CommandCode.FIND,
      isPowerOn = true,
    )
    assertFalse(denied.allowed)
    assertEquals(
      ControlCommandPolicy.POWER_ON_FIND_DISABLED_REASON,
      denied.disabledReason,
    )
  }

  @Test
  fun `find is allowed while the vehicle is powered off`() {
    val allowed = ControlCommandPolicy.evaluate(
      command = CommandCode.FIND,
      isPowerOn = false,
    )
    assertTrue(allowed.allowed)
    assertNull(allowed.disabledReason)
  }

  @Test
  fun `other commands are not gated by power state`() {
    for (command in listOf(CommandCode.LOCK, CommandCode.UNLOCK, CommandCode.OPEN_SEAT, CommandCode.POWER_ON)) {
      assertTrue(
        "command $command",
        ControlCommandPolicy.evaluate(command = command, isPowerOn = true).allowed,
      )
    }
  }
}

class ControlCommandResultTest {

  @Test
  fun `cloud success strips channel tags and maps success body to label`() {
    val mqtt = ControlCommandResult.cloudSuccess(
      command = CommandCode.LOCK,
      message = "mqtt:success",
    )
    assertTrue(mqtt.success)
    assertEquals(ControlCommandTransport.OFFICIAL_CLOUD, mqtt.transport)
    assertEquals("设防已完成", mqtt.successMessage)

    val http = ControlCommandResult.cloudSuccess(
      command = CommandCode.FIND,
      message = "http:OK",
    )
    assertEquals("寻车已完成", http.successMessage)

    val plain = ControlCommandResult.cloudSuccess(
      command = CommandCode.POWER_ON,
      message = "success",
    )
    assertEquals("启动已完成", plain.successMessage)
  }

  @Test
  fun `cloud success keeps non-success body verbatim`() {
    val result = ControlCommandResult.cloudSuccess(
      command = CommandCode.LOCK,
      message = "http:设备离线",
    )
    assertEquals("设备离线", result.successMessage)
  }

  @Test
  fun `empty message normalizes to success`() {
    val result = ControlCommandResult.cloudSuccess(
      command = CommandCode.UNLOCK,
      message = "   ",
    )
    assertEquals("解锁已完成", result.successMessage)
  }

  @Test
  fun `shouldRefreshBikeState only for BLE success`() {
    assertTrue(ControlCommandResult.bleSuccess(CommandCode.LOCK).shouldRefreshBikeState)
    assertFalse(
      ControlCommandResult.cloudSuccess(CommandCode.LOCK, message = "ok").shouldRefreshBikeState,
    )
    assertFalse(
      ControlCommandResult.unavailable(CommandCode.LOCK, "x").shouldRefreshBikeState,
    )
  }
}

class OfficialCarOperatorPolicyTest {

  private fun vehicle(
    carId: String = "car-1",
    modelType: Int? = 3,
    shareCarFlag: Boolean = false,
  ): OfficialVehicle = OfficialVehicle.fromJson(
    mapOf(
      "carId" to carId,
      "modelType" to modelType,
      "shareCarFlag" to shareCarFlag,
    ),
  )

  @Test
  fun `KKS and YJ always track operator flag on power on and off`() {
    for (type in listOf(1, 2)) {
      val on = OfficialCarOperatorPolicy.updateFor(
        command = CommandCode.POWER_ON,
        vehicle = vehicle(modelType = type),
      )
      val off = OfficialCarOperatorPolicy.updateFor(
        command = CommandCode.POWER_OFF,
        vehicle = vehicle(modelType = type),
      )
      assertEquals(OfficialCarOperatorUpdate("car-1", "1"), on)
      assertEquals(OfficialCarOperatorUpdate("car-1", "0"), off)
    }
  }

  @Test
  fun `shared cars on other families track powerOn only`() {
    for (type in listOf(3, 8, 10, 14, 283, 401, 928, 2103, 2201)) {
      val on = OfficialCarOperatorPolicy.updateFor(
        command = CommandCode.POWER_ON,
        vehicle = vehicle(modelType = type, shareCarFlag = true),
      )
      assertEquals("type $type", OfficialCarOperatorUpdate("car-1", "1"), on)
      val off = OfficialCarOperatorPolicy.updateFor(
        command = CommandCode.POWER_OFF,
        vehicle = vehicle(modelType = type, shareCarFlag = true),
      )
      assertNull("type $type powerOff", off)
    }
  }

  @Test
  fun `non-shared cars on other families are not tracked`() {
    val update = OfficialCarOperatorPolicy.updateFor(
      command = CommandCode.POWER_ON,
      vehicle = vehicle(modelType = 3, shareCarFlag = false),
    )
    assertNull(update)
  }

  @Test
  fun `empty carId is ignored`() {
    val update = OfficialCarOperatorPolicy.updateFor(
      command = CommandCode.POWER_ON,
      vehicle = vehicle(carId = "   "),
    )
    assertNull(update)
  }
}
