/**
 * Port-validation tests for `com.tailg.plus.data.mqtt.OfficialMqttService`.
 *
 * MQTT transport is never exercised here: `liveConnectEnabled = false` skips
 * all socket work, and command sends go through `publishCommandOverride`, so
 * the P4-1/P4-2 semantics (pending command, MQTT status ACK, HTTP fallback,
 * preconnect gate) are verified hermetically. Vectors mirror
 * `tailg-ble-app/test/official_mqtt_send_command_test.dart`.
 */
package com.tailg.plus.data.mqtt

import com.tailg.plus.data.model.CommandCode
import com.tailg.plus.data.model.OfficialVehicle
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.net.SocketException
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OfficialMqttServiceTest {

  private lateinit var cloud: OfficialMqttCloudGateway

  @Before
  fun setUp() {
    OfficialMqttService.liveConnectEnabled = false
    cloud = mockk()
    every { cloud.stateChanges } returns MutableSharedFlow()
    every { cloud.signedIn } returns true
    every { cloud.userId } returns "u1"
    every { cloud.applyMqttVehicleStatus(any(), any()) } just Runs
    coEvery { cloud.sendCommand(any()) } returns "success"
    coEvery { cloud.refreshVehicles(any(), any()) } just Runs
  }

  @After
  fun tearDown() {
    OfficialMqttService.liveConnectEnabled = true
  }

  private fun vehicle(imei: String) = OfficialVehicle(
    imei = imei,
    carId = "car-$imei",
    carNickName = "MQTT车",
    modelType = 8,
    isGps = 1,
  )

  // --- formatConnectError -------------------------------------------------

  @Test
  fun formatConnectErrorIncludesSocketExceptionMessage() {
    val raw = OfficialMqttService.formatConnectError(SocketException("Connection timed out"))
    assertTrue(raw.contains("SocketException"))
    assertTrue(raw.contains("Connection timed out"))
  }

  @Test
  fun formatConnectErrorIncludesTimeoutExceptionMessage() {
    val raw = OfficialMqttService.formatConnectError(TimeoutException("connect"))
    assertTrue(raw.contains("TimeoutException"))
    assertTrue(raw.contains("connect"))
  }

  @Test
  fun formatConnectErrorFallsBackToTypeForUnknown() {
    assertEquals(
      "IllegalStateException: boom",
      OfficialMqttService.formatConnectError(IllegalStateException("boom")),
    )
  }

  // --- preconnect gate ----------------------------------------------------

  @Test
  fun preconnectSkipsSocketsWithoutLeavingRetryWork() = runBlocking {
    val mqtt = OfficialMqttService(defaultCloud = cloud)
    try {
      mqtt.preconnect(vehicle = vehicle("860000000000099"), userId = "u-live-off")

      assertFalse(mqtt.preconnectInFlight)
      assertFalse(mqtt.isConnected)
      assertEquals(OfficialRemoteErrorMessages.BROKER_UNREACHABLE, mqtt.lastPreconnectError)
      assertTrue(mqtt.lastPreconnectRawError!!.contains("live connect disabled"))
    } finally {
      mqtt.resetForTest()
    }
  }

  // --- sendCommandPreferMqtt (P4-2) ---------------------------------------

  @Test
  fun returnsMqttSuccessWhenPublishOverrideSucceeds() = runBlocking {
    val mqtt = OfficialMqttService(defaultCloud = cloud)
    try {
      every { cloud.selectedVehicle } returns vehicle("860000000000001")
      mqtt.publishCommandOverride = { _, _, _ -> }

      val result = mqtt.sendCommandPreferMqtt(CommandCode.LOCK, cloud)

      assertEquals("mqtt:success", result)
      assertEquals(OfficialRemoteSendPath.MQTT, mqtt.lastSendPath)
      assertEquals("lock", mqtt.pendingCommandApiName)
      coVerify(exactly = 0) { cloud.sendCommand(any()) }
    } finally {
      mqtt.resetForTest()
    }
  }

  @Test
  fun fallsBackToHttpWhenPublishFails() = runBlocking {
    val mqtt = OfficialMqttService(defaultCloud = cloud)
    try {
      every { cloud.selectedVehicle } returns vehicle("860000000000001")
      mqtt.publishCommandOverride = { _, _, _ ->
        throw IllegalStateException("mock broker down")
      }

      val result = mqtt.sendCommandPreferMqtt(CommandCode.UNLOCK, cloud)

      assertEquals("http:success", result)
      assertEquals(OfficialRemoteSendPath.HTTP, mqtt.lastSendPath)
      assertNull(mqtt.pendingCommandApiName)
      coVerify { cloud.sendCommand(CommandCode.UNLOCK) }
    } finally {
      mqtt.resetForTest()
    }
  }

  @Test
  fun recordsOfficialCommandErrorsWithoutTreatingThemAsAck() = runBlocking {
    val mqtt = OfficialMqttService(defaultCloud = cloud)
    try {
      every { cloud.selectedVehicle } returns vehicle("860000000000001")
      mqtt.publishCommandOverride = { _, _, _ -> }

      mqtt.sendCommandPreferMqtt(CommandCode.LOCK, cloud)
      mqtt.handleStatusPayload(
        """{"imei":"860000000000001","defenceErrorStatus":3,"bikeSetSourceValue":3}""",
      )

      assertEquals("lock", mqtt.pendingCommandApiName)
      assertEquals("车辆未断电，请勿操作", mqtt.pendingCommandError)
      verify(exactly = 0) { cloud.applyMqttVehicleStatus(any(), any()) }
    } finally {
      mqtt.resetForTest()
    }
  }

  @Test
  fun clearsPendingOnMqttStatusAckAndAppliesVehicleState() = runBlocking {
    val mqtt = OfficialMqttService(defaultCloud = cloud)
    try {
      every { cloud.selectedVehicle } returns vehicle("860000000000001")
      mqtt.publishCommandOverride = { _, _, _ -> }

      mqtt.sendCommandPreferMqtt(CommandCode.LOCK, cloud)
      assertEquals("lock", mqtt.pendingCommandApiName)

      mqtt.handleStatusPayload(
        """{"imei":"860000000000001","ACC":"0","defenceStatus":"1"}""",
      )

      assertNull(mqtt.pendingCommandApiName)
      assertNull(mqtt.pendingCommandError)
      verify { cloud.applyMqttVehicleStatus(0, 1) }
    } finally {
      mqtt.resetForTest()
    }
  }

  @Test
  fun ignoresStatusPayloadsBelongingToAnotherVehicle() = runBlocking {
    val mqtt = OfficialMqttService(defaultCloud = cloud)
    try {
      every { cloud.selectedVehicle } returns vehicle("860000000000001")
      mqtt.publishCommandOverride = { _, _, _ -> }

      mqtt.sendCommandPreferMqtt(CommandCode.LOCK, cloud)
      mqtt.handleStatusPayload("""{"imei":"another-imei","defenceStatus":"1"}""")

      assertEquals("lock", mqtt.pendingCommandApiName)
      verify(exactly = 0) { cloud.applyMqttVehicleStatus(any(), any()) }
    } finally {
      mqtt.resetForTest()
    }
  }
}
