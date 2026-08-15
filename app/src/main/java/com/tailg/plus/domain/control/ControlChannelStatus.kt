package com.tailg.plus.domain.control

import com.tailg.plus.data.ble.platform.ConnectionState

// 待解析引用 (ported by the MQTT subagent, not yet landed):
// Dart `lib/services/official_mqtt_service.dart` `OfficialMqttLinkState`
// → `com.tailg.plus.data.mqtt.OfficialMqttLinkState` with entries
// DISCONNECTED / CONNECTING / CONNECTED.
import com.tailg.plus.data.mqtt.OfficialMqttLinkState

/**
 * Port of `lib/services/control_channel_status.dart`.
 *
 * Canonical top-bar channel copy for 爱车 (README / PLAN P0-C3).
 *
 * Four primary states:
 * - `BLE 直连` — will use BLE after LOGIN
 * - `MQTT 远程` — MQTT session live for remote control
 * - `MQTT 连接中` — MQTT preconnect/ensure in flight
 * - `云端待命` — cloud path available, MQTT not yet live
 *
 * Extra diagnostics (still single source): `MQTT 待重连` · `蓝牙连接中` · `不可用`.
 */
enum class ControlTopBarChannelKind {
  BLE_DIRECT,
  BLE_CONNECTING,
  MQTT_REMOTE,
  MQTT_CONNECTING,
  MQTT_RETRY,
  CLOUD_STANDBY,
  UNAVAILABLE,
}

data class ControlTopBarChannel(
  val kind: ControlTopBarChannelKind,
  val label: String,
) {
  val isActive: Boolean
    get() =
      kind == ControlTopBarChannelKind.BLE_DIRECT ||
        kind == ControlTopBarChannelKind.MQTT_REMOTE

  companion object {
    /** Single truth source for 爱车 top-bar channel text + activity. */
    fun resolve(
      availability: ControlChannelAvailability,
      bleState: ConnectionState,
      bleProtocolLoggedIn: Boolean,
      mqttLinkState: OfficialMqttLinkState,
      mqttPreconnectInFlight: Boolean,
      mqttLastPreconnectError: String?,
    ): ControlTopBarChannel {
      // BLE LOGIN path wins when resolver will actually send BLE.
      if (availability.willUseBle && bleProtocolLoggedIn) {
        return ControlTopBarChannel(
          kind = ControlTopBarChannelKind.BLE_DIRECT,
          label = "BLE 直连",
        )
      }

      // GATT up / handshake — not LOGIN yet (P0-A3: do not claim ready).
      if (bleState == ConnectionState.CONNECTING ||
        bleState == ConnectionState.CONNECTED ||
        bleState == ConnectionState.RECONNECTING
      ) {
        // If cloud is actively usable we still prefer showing remote readiness,
        // but while intentionally near-field linking, surface BLE progress.
        if (!availability.canUseCloud ||
          availability.channel == OfficialControlChannel.BLE
        ) {
          return ControlTopBarChannel(
            kind = ControlTopBarChannelKind.BLE_CONNECTING,
            label = "蓝牙连接中",
          )
        }
      }

      val mqttConnected = mqttLinkState == OfficialMqttLinkState.CONNECTED
      if (availability.canUseCloud && mqttConnected) {
        return ControlTopBarChannel(
          kind = ControlTopBarChannelKind.MQTT_REMOTE,
          label = "MQTT 远程",
        )
      }

      if (availability.canUseCloud &&
        (mqttLinkState == OfficialMqttLinkState.CONNECTING ||
          mqttPreconnectInFlight)
      ) {
        return ControlTopBarChannel(
          kind = ControlTopBarChannelKind.MQTT_CONNECTING,
          label = "MQTT 连接中",
        )
      }

      val preErr = mqttLastPreconnectError?.trim().orEmpty()
      if (preErr.isNotEmpty() && availability.canUseCloud) {
        return ControlTopBarChannel(
          kind = ControlTopBarChannelKind.MQTT_RETRY,
          label = "MQTT 待重连",
        )
      }

      if (availability.canUseCloud) {
        return ControlTopBarChannel(
          kind = ControlTopBarChannelKind.CLOUD_STANDBY,
          label = "云端待命",
        )
      }

      // Fall back to resolver disabled/effective label — never invent a fifth primary.
      val disabled = availability.disabledReason.trim()
      if (disabled.isNotEmpty()) {
        return ControlTopBarChannel(
          kind = ControlTopBarChannelKind.UNAVAILABLE,
          label = disabled,
        )
      }
      val effective = availability.effectiveChannelLabel.trim()
      return ControlTopBarChannel(
        kind = ControlTopBarChannelKind.UNAVAILABLE,
        label = if (effective.isEmpty()) "不可用" else effective,
      )
    }
  }
}
