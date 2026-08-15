/**
 * Port-validation tests for `com.tailg.plus.data.mqtt.OfficialMqttStatusPayload`
 * (Dart → Kotlin MQTT payload parser).
 *
 * Vectors lifted verbatim from `tailg-ble-app/test/official_mqtt_payload_test.dart`.
 */
package com.tailg.plus.data.mqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialMqttStatusPayloadTest {

  @Test
  fun parsesAccAndDefenceStatusFields() {
    val payload = OfficialMqttStatusPayload.tryParse(
      """{"imei":"860","ACC":"1","defenceStatus":"0","muteStatus":0}""",
    )!!

    assertEquals("1", payload.acc)
    assertEquals("0", payload.defenceStatus)
    assertEquals(1, payload.accInt)
    assertEquals(0, payload.defenceStatusInt)
    assertTrue(payload.hasVehicleState)
  }

  @Test
  fun confirmsPendingCommandsLikeControlFragment() {
    val startOk = OfficialMqttStatusPayload(acc = "1", defenceStatus = "0")
    val lockOk = OfficialMqttStatusPayload(acc = "0", defenceStatus = "1")
    val unlockOk = OfficialMqttStatusPayload(acc = "0", defenceStatus = "0")

    assertTrue(startOk.confirmsCommand("start"))
    assertFalse(startOk.confirmsCommand("stop"))
    assertTrue(lockOk.confirmsCommand("lock"))
    assertTrue(unlockOk.confirmsCommand("unlock"))
  }

  @Test
  fun returnsNullForNonJsonPayloads() {
    assertNull(OfficialMqttStatusPayload.tryParse("not-json"))
    assertNull(OfficialMqttStatusPayload.tryParse(""))
    assertNull(OfficialMqttStatusPayload.tryParse("  "))
    assertNull(OfficialMqttStatusPayload.tryParse("[]"))
  }

  @Test
  fun parsesOfficialControlErrorsAndExposesPolicyState() {
    val moving = OfficialMqttStatusPayload.tryParse(
      """{"accErrorStatus":4,"defenceErrorStatus":0}""",
    )!!
    val keyed = OfficialMqttStatusPayload.tryParse("""{"accErrorStatus":8}""")!!
    val notPoweredOff = OfficialMqttStatusPayload.tryParse(
      """{"defenceErrorStatus":3,"bikeSetSourceValue":3}""",
    )!!

    assertTrue(moving.isMoving)
    assertEquals("车辆行驶中，请勿操作", moving.controlErrorMessage("start"))
    assertTrue(keyed.isKeyStarted)
    assertEquals("您已使用钥匙启动车辆，当前不支持此操作", keyed.controlErrorMessage("stop"))
    assertTrue(notPoweredOff.isNotPoweredOff)
    assertEquals("车辆未断电，请勿操作", notPoweredOff.controlErrorMessage("lock"))
  }

  @Test
  fun mapsStartStopFailuresAndDoesNotLeakToOtherCommands() {
    for (code in listOf(5, 6, 7, 20)) {
      val payload = OfficialMqttStatusPayload(accErrorStatus = code)
      assertEquals("车辆启动失败", payload.controlErrorMessage("start"))
      assertEquals("车辆熄火失败", payload.controlErrorMessage("stop"))
      assertNull(payload.controlErrorMessage("lock"))
    }
  }
}
