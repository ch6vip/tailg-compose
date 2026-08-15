package com.tailg.plus.domain.control

/**
 * Port of `lib/services/official_control_route.dart`.
 *
 * Official control-path routing extracted from decompiled
 * `ControlFragment.lock()` / `start()` / `find()` + `ControlTypeUtil`
 * (3.5.9). This encodes **transport selection only** (BLE vs remote).
 * Official remote is MQTT (project: OfficialMqttService.sendCommandPreferMqtt
 * with HTTP `app/device/cmd` (star-suffix) fallback). **When** to choose remote vs BLE
 * follows the official decision tree (`modelType` / `isGps` / BLE LOGIN).
 *
 * Pure Kotlin: no Android imports, fully unit-testable.
 */
enum class OfficialBleStackKind {
  /** TLinkBleManager / BleHandler path (KKS, C39, BB, GPS combo…). */
  STANDARD,

  /** TLinkBleManagerQgj path (modelType 8 / 283). */
  QGJ,

  /** Remote-only models (e.g. YJ) never use local BLE for control. */
  NONE,
}

enum class OfficialControlTransportChoice {
  /** Local BLE after protocol LOGIN (`LoginStatus.LOGIN`). */
  BLE,

  /** Remote control (official MQTT; HTTP cmd as fallback). */
  CLOUD,

  /** No usable path. */
  UNAVAILABLE,
}

data class OfficialControlRouteDecision(
  val transport: OfficialControlTransportChoice,
  val bleStack: OfficialBleStackKind,
  val reason: String,
) {
  val usesBle: Boolean get() = transport == OfficialControlTransportChoice.BLE
  val usesCloud: Boolean get() = transport == OfficialControlTransportChoice.CLOUD
  val isUnavailable: Boolean get() = transport == OfficialControlTransportChoice.UNAVAILABLE
}

/** Pure routing table matching official `ControlFragment` control keys. */
object OfficialControlRoute {
  /** QGJ model types (`ControlTypeUtil.isQgj` + 283 variant in ControlFragment). */
  val qgjModelTypes: Set<Int> = setOf(8, 283)

  /** C39 family. */
  val c39ModelTypes: Set<Int> = setOf(10, 14)

  /**
   * GPS combo models that fall back to remote when BLE is not LOGIN,
   * without an extra `isGps == 1` gate (see lock cases 401/928/2103/2201).
   */
  val gpsComboModelTypes: Set<Int> = setOf(401, 928, 2103, 2201)

  /**
   * Model types for which ControlFragment has no implementation for the
   * primary control actions (`case 1501/1601/1701: break;` in lock/start).
   * Keep them unavailable instead of guessing a transport and sending a
   * command the official app would ignore.
   */
  val unsupportedControlModelTypes: Set<Int> = setOf(1501, 1601, 1701)

  /** Known model types handled by the official control fragment. */
  val supportedControlModelTypes: Set<Int> =
    setOf(1, 2, 3, 8, 10, 14, 283, 401, 928, 2103, 2201)

  /**
   * Resolve which transport official control would take for a bound vehicle.
   *
   * [bleReady] **must** mean official `LoginStatus.LOGIN` (or
   * `bleIsConnectedField` for KKS modelType 1). Feed
   * `ConnectionManager.isProtocolLoggedIn` — not mere GATT
   * `ConnectionState.connected` / raw `ready` without credential.
   * [networkReady] corresponds to `NetworkUtils.isConnected()`.
   * [cloudSessionReady] is our stand-in for "can talk to remote backend"
   * (signed-in + selected vehicle). MQTT connect is ensured at send time via
   * OfficialMqttService.ensureConnected.
   */
  fun resolve(
    bindingCar: Boolean,
    modelType: Int?,
    isGps: Int?,
    bleReady: Boolean,
    networkReady: Boolean = true,
    cloudSessionReady: Boolean = false,
  ): OfficialControlRouteDecision {
    if (!bindingCar) {
      return OfficialControlRouteDecision(
        transport = OfficialControlTransportChoice.UNAVAILABLE,
        bleStack = OfficialBleStackKind.NONE,
        reason = "未绑定车辆",
      )
    }

    val cloudReady = networkReady && cloudSessionReady
    val type = modelType ?: -1

    if (!supportedControlModelTypes.contains(type) ||
      unsupportedControlModelTypes.contains(type)
    ) {
      return OfficialControlRouteDecision(
        transport = OfficialControlTransportChoice.UNAVAILABLE,
        bleStack = OfficialBleStackKind.NONE,
        reason = "当前车型暂不支持控车",
      )
    }

    // --- modelType 1: KKS ---
    // if (bleIsConnected) BLE else MQTT
    if (type == 1) {
      if (bleReady) {
        return OfficialControlRouteDecision(
          transport = OfficialControlTransportChoice.BLE,
          bleStack = OfficialBleStackKind.STANDARD,
          reason = "",
        )
      }
      if (cloudReady) {
        return OfficialControlRouteDecision(
          transport = OfficialControlTransportChoice.CLOUD,
          bleStack = OfficialBleStackKind.STANDARD,
          reason = "",
        )
      }
      return OfficialControlRouteDecision(
        transport = OfficialControlTransportChoice.UNAVAILABLE,
        bleStack = OfficialBleStackKind.STANDARD,
        reason = if (!networkReady) "手机网络未连接" else "请先登录官方账号并选择车辆",
      )
    }

    // --- modelType 2: YJ — cloud only ---
    if (type == 2) {
      if (cloudReady) {
        return OfficialControlRouteDecision(
          transport = OfficialControlTransportChoice.CLOUD,
          bleStack = OfficialBleStackKind.NONE,
          reason = "",
        )
      }
      return OfficialControlRouteDecision(
        transport = OfficialControlTransportChoice.UNAVAILABLE,
        bleStack = OfficialBleStackKind.NONE,
        reason = if (!networkReady) "手机网络未连接" else "请先登录官方账号并选择车辆",
      )
    }

    // --- modelType 8 / 283: QGJ ---
    // if (isGps == 1 && bleConnectStatusQgj != LOGIN) → MQTT
    // else require BLE LOGIN → QGJ local
    if (qgjModelTypes.contains(type)) {
      return hybridIsGpsGate(
        isGps = isGps,
        bleReady = bleReady,
        cloudReady = cloudReady,
        networkReady = networkReady,
        bleStack = OfficialBleStackKind.QGJ,
      )
    }

    // --- modelType 10 / 14: C39 ---
    // same gate with standard BLE status
    if (c39ModelTypes.contains(type)) {
      return hybridIsGpsGate(
        isGps = isGps,
        bleReady = bleReady,
        cloudReady = cloudReady,
        networkReady = networkReady,
        bleStack = OfficialBleStackKind.STANDARD,
      )
    }

    // --- modelType 401 / 928 / 2103 / 2201 ---
    // if (ble != LOGIN) → MQTT else BLE (no isGps gate)
    if (gpsComboModelTypes.contains(type)) {
      if (bleReady) {
        return OfficialControlRouteDecision(
          transport = OfficialControlTransportChoice.BLE,
          bleStack = OfficialBleStackKind.STANDARD,
          reason = "",
        )
      }
      if (cloudReady) {
        return OfficialControlRouteDecision(
          transport = OfficialControlTransportChoice.CLOUD,
          bleStack = OfficialBleStackKind.STANDARD,
          reason = "",
        )
      }
      return OfficialControlRouteDecision(
        transport = OfficialControlTransportChoice.UNAVAILABLE,
        bleStack = OfficialBleStackKind.STANDARD,
        reason = if (!networkReady) "手机网络未连接" else "请先登录官方账号并选择车辆",
      )
    }

    // --- modelType 3 (BB) ---
    return hybridIsGpsGate(
      isGps = isGps,
      bleReady = bleReady,
      cloudReady = cloudReady,
      networkReady = networkReady,
      bleStack = OfficialBleStackKind.STANDARD,
    )
  }

  /**
   * Official pattern used by QGJ / C39 / BB:
   * `isGps == 1 && ble != LOGIN` → remote; else require BLE LOGIN.
   */
  private fun hybridIsGpsGate(
    isGps: Int?,
    bleReady: Boolean,
    cloudReady: Boolean,
    networkReady: Boolean,
    bleStack: OfficialBleStackKind,
  ): OfficialControlRouteDecision {
    if (isGps == 1 && !bleReady) {
      if (cloudReady) {
        return OfficialControlRouteDecision(
          transport = OfficialControlTransportChoice.CLOUD,
          bleStack = bleStack,
          reason = "",
        )
      }
      return OfficialControlRouteDecision(
        transport = OfficialControlTransportChoice.UNAVAILABLE,
        bleStack = bleStack,
        reason = if (!networkReady) "手机网络未连接" else "请先登录官方账号并选择车辆",
      )
    }

    if (bleReady) {
      return OfficialControlRouteDecision(
        transport = OfficialControlTransportChoice.BLE,
        bleStack = bleStack,
        reason = "",
      )
    }

    return OfficialControlRouteDecision(
      transport = OfficialControlTransportChoice.UNAVAILABLE,
      bleStack = bleStack,
      reason = "蓝牙未连接",
    )
  }
}
