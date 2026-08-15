/**
 * Port-validation tests for `com.tailg.plus.domain.control.ControlCommandConfirmation`
 * (Dart → Kotlin). Vectors lifted verbatim from `tailg-ble-app/test/control_command_confirmation_test.dart`.
 */
package com.tailg.plus.domain.control

import com.tailg.plus.data.model.CommandCode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlCommandConfirmationTest {

  @Test
  fun `guard keeps cloud confirmation bound to the selected official vehicle`() {
    val guard = ControlCommandConfirmationGuard()

    assertTrue(
      guard.allows(
        context = PendingControlCommandConfirmationContext(
          officialVehicleKey = "official-1",
        ).forTransport(ControlCommandTransport.OFFICIAL_CLOUD),
        currentOfficialVehicleKey = "official-1",
      ),
    )
    assertFalse(
      guard.allows(
        context = PendingControlCommandConfirmationContext(
          officialVehicleKey = "official-1",
        ).forTransport(ControlCommandTransport.OFFICIAL_CLOUD),
        currentOfficialVehicleKey = "official-2",
      ),
    )
    assertFalse(
      guard.allows(
        context = PendingControlCommandConfirmationContext(
          officialVehicleKey = "",
        ).forTransport(ControlCommandTransport.OFFICIAL_CLOUD),
        currentOfficialVehicleKey = "official-1",
      ),
    )
  }

  @Test
  fun `guard rejects unavailable transport`() {
    val guard = ControlCommandConfirmationGuard()

    assertFalse(
      guard.allows(
        context = PendingControlCommandConfirmationContext(
          officialVehicleKey = "official-1",
        ).forTransport(ControlCommandTransport.UNAVAILABLE),
        currentOfficialVehicleKey = "official-1",
      ),
    )
  }

  @Test
  fun `needs state confirmation only for lock and power family`() {
    assertTrue(ControlCommandConfirmation.needsVehicleStateConfirmation(CommandCode.LOCK))
    assertFalse(ControlCommandConfirmation.needsVehicleStateConfirmation(CommandCode.FIND))
    assertFalse(ControlCommandConfirmation.needsVehicleStateConfirmation(CommandCode.OPEN_SEAT))
  }

  @Test
  fun `MQTT pending clear is treated as ACK`() {
    assertTrue(
      ControlCommandConfirmation.mqttPendingAcknowledged(
        pendingAtSend = "lock",
        pendingNow = null,
      ),
    )
    assertFalse(
      ControlCommandConfirmation.mqttPendingAcknowledged(
        pendingAtSend = "lock",
        pendingNow = "lock",
      ),
    )
    assertFalse(
      ControlCommandConfirmation.mqttPendingAcknowledged(
        pendingAtSend = null,
        pendingNow = null,
      ),
    )
  }

  @Test
  fun `find waits for its MQTT response so command errors are observable`() {
    assertTrue(
      ControlCommandConfirmation.needsMqttResponse(
        command = CommandCode.FIND,
        pendingAtSend = "search",
      ),
    )
    assertFalse(
      ControlCommandConfirmation.needsMqttResponse(
        command = CommandCode.FIND,
        pendingAtSend = null,
      ),
    )
    assertFalse(
      ControlCommandConfirmation.needsMqttResponse(
        command = CommandCode.LOCK,
        pendingAtSend = "lock",
      ),
    )
  }

  @Test
  fun `BLE transport success is confirmed without cloud state`() {
    val confirmed = ControlCommandConfirmation.isConfirmed(
      command = CommandCode.LOCK,
      transport = ControlCommandTransport.BLE,
      expectedOfficialVehicleKey = "v1",
      currentOfficialVehicleKey = "v1",
      baseline = ControlCommandVehicleStateSnapshot(isLocked = false),
      current = ControlCommandVehicleStateSnapshot(isLocked = false),
      mqttAcked = false,
    )
    assertTrue(confirmed)
  }

  @Test
  fun `cloud publish alone does not confirm lock when state unchanged`() {
    // Already locked before send → MQTT publish returning success must not
    // look like vehicle executed until ACK or state change.
    val confirmed = ControlCommandConfirmation.isConfirmed(
      command = CommandCode.LOCK,
      transport = ControlCommandTransport.OFFICIAL_CLOUD,
      expectedOfficialVehicleKey = "v1",
      currentOfficialVehicleKey = "v1",
      baseline = ControlCommandVehicleStateSnapshot(isLocked = true),
      current = ControlCommandVehicleStateSnapshot(isLocked = true),
      mqttAcked = false,
    )
    assertFalse(confirmed)
  }

  @Test
  fun `cloud confirms lock when defence flips after publish`() {
    val confirmed = ControlCommandConfirmation.isConfirmed(
      command = CommandCode.LOCK,
      transport = ControlCommandTransport.OFFICIAL_CLOUD,
      expectedOfficialVehicleKey = "v1",
      currentOfficialVehicleKey = "v1",
      baseline = ControlCommandVehicleStateSnapshot(isLocked = false),
      current = ControlCommandVehicleStateSnapshot(isLocked = true),
      mqttAcked = false,
    )
    assertTrue(confirmed)
  }

  @Test
  fun `cloud confirms lock on MQTT ACK even if baseline already matched`() {
    val confirmed = ControlCommandConfirmation.isConfirmed(
      command = CommandCode.LOCK,
      transport = ControlCommandTransport.OFFICIAL_CLOUD,
      expectedOfficialVehicleKey = "v1",
      currentOfficialVehicleKey = "v1",
      baseline = ControlCommandVehicleStateSnapshot(isLocked = true),
      current = ControlCommandVehicleStateSnapshot(isLocked = true),
      mqttAcked = true,
    )
    assertTrue(confirmed)
  }

  @Test
  fun `cloud rejects confirmation when selected vehicle changed`() {
    val confirmed = ControlCommandConfirmation.isConfirmed(
      command = CommandCode.POWER_ON,
      transport = ControlCommandTransport.OFFICIAL_CLOUD,
      expectedOfficialVehicleKey = "v1",
      currentOfficialVehicleKey = "v2",
      baseline = ControlCommandVehicleStateSnapshot(isPowerOn = false),
      current = ControlCommandVehicleStateSnapshot(isPowerOn = true),
      mqttAcked = true,
    )
    assertFalse(confirmed)
  }

  @Test
  fun `find command accepts cloud transport without ACC flip`() {
    val confirmed = ControlCommandConfirmation.isConfirmed(
      command = CommandCode.FIND,
      transport = ControlCommandTransport.OFFICIAL_CLOUD,
      expectedOfficialVehicleKey = "v1",
      currentOfficialVehicleKey = "v1",
      baseline = ControlCommandVehicleStateSnapshot(),
      current = ControlCommandVehicleStateSnapshot(),
      mqttAcked = false,
    )
    assertTrue(confirmed)
  }
}
