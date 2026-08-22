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

import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.cloud.OfficialCloudState
import com.tailg.plus.data.cloud.OfficialRemoteErrorMessages
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OfficialMqttServiceTest {

  private lateinit var cloud: OfficialCloudService

  @Before
  fun setUp() {
    OfficialMqttService.liveConnectEnabled = false
    cloud = mockk()
    every { cloud.stateFlow } returns MutableStateFlow(OfficialCloudState.initial())
    every { cloud.currentState } returns OfficialCloudState.initial()
    every { cloud.applyMqttVehicleStatus(any(), any()) } just Runs
    coEvery { cloud.sendCommand(any()) } returns "success"
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

  /** Signed-in cloud snapshot selecting [vehicle]. */
  private fun signedInState(vehicle: OfficialVehicle): OfficialCloudState =
    OfficialCloudState.initial().copyWith(
      token = "tok",
      userId = "u1",
      vehicles = listOf(vehicle),
      selectedVehicleKey = vehicle.key,
    )

  private fun bindSignedIn(mqtt: OfficialMqttService, imei: String) {
    val state = signedInState(vehicle(imei))
    every { cloud.stateFlow } returns MutableStateFlow(state)
    every { cloud.currentState } returns state
    // Fresh values so the launched cloud collector sees the signed-in state too.
  }

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
      bindSignedIn(mqtt, "860000000000001")
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
      bindSignedIn(mqtt, "860000000000001")
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
      bindSignedIn(mqtt, "860000000000001")
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
      bindSignedIn(mqtt, "860000000000001")
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
      bindSignedIn(mqtt, "860000000000001")
      mqtt.publishCommandOverride = { _, _, _ -> }

      mqtt.sendCommandPreferMqtt(CommandCode.LOCK, cloud)
      mqtt.handleStatusPayload("""{"imei":"another-imei","defenceStatus":"1"}""")

      assertEquals("lock", mqtt.pendingCommandApiName)
      verify(exactly = 0) { cloud.applyMqttVehicleStatus(any(), any()) }
    } finally {
      mqtt.resetForTest()
    }
  }

  // --- push-driven confirmation signal ------------------------------------

  @Test
  fun emitsStatusPayloadEventsForCurrentVehicleOnly() = runBlocking {
    val mqtt = OfficialMqttService(defaultCloud = cloud)
    try {
      bindSignedIn(mqtt, "860000000000001")
      mqtt.publishCommandOverride = { _, _, _ -> }

      mqtt.sendCommandPreferMqtt(CommandCode.LOCK, cloud)
      // Another vehicle's push is filtered before any state application…
      mqtt.handleStatusPayload("""{"imei":"another-imei","defenceStatus":"1"}""")
      // …the current vehicle's push applies state and wakes confirmation waiters.
      mqtt.handleStatusPayload("""{"imei":"860000000000001","ACC":"0","defenceStatus":"1"}""")

      // replay = 1: the waiter that subscribes after the push still sees it
      // (closes the check-then-subscribe gap in the confirmation loop).
      val payload = mqtt.statusPayloadEvents.first()
      assertEquals("0", payload.acc)
      assertEquals("1", payload.defenceStatus)
    } finally {
      mqtt.resetForTest()
    }
  }

  @Test
  fun emitsStatusPayloadEventsWithoutVehicleState() = runBlocking {
    val mqtt = OfficialMqttService(defaultCloud = cloud)
    try {
      bindSignedIn(mqtt, "860000000000001")
      mqtt.publishCommandOverride = { _, _, _ -> }

      mqtt.sendCommandPreferMqtt(CommandCode.FIND, cloud)
      // Error-only payloads still wake waiters (pending-error bookkeeping ran).
      mqtt.handleStatusPayload(
        """{"imei":"860000000000001","defenceErrorStatus":3,"bikeSetSourceValue":3}""",
      )

      val payload = mqtt.statusPayloadEvents.first()
      assertEquals(3, payload.defenceErrorStatus)
      verify(exactly = 0) { cloud.applyMqttVehicleStatus(any(), any()) }
    } finally {
      mqtt.resetForTest()
    }
  }
}
