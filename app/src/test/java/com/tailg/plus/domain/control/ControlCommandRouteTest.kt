/**
 * Port-validation tests for `com.tailg.plus.domain.control.ControlCommandRoute`
 * (Dart → Kotlin). Vectors lifted verbatim from `tailg-ble-app/test/control_command_route_test.dart`.
 */
package com.tailg.plus.domain.control

import com.tailg.plus.data.model.CommandCode
import com.tailg.plus.data.model.OfficialVehicle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlCommandRouteTest {

  private class FakeCloudState(
    override val selectedVehicle: OfficialVehicle?,
  ) : ControlCloudState {
    override val signedIn: Boolean = true
    override fun linkedLocalVehicleId(officialVehicleKey: String): String? = null
  }

  private fun stateFor(
    modelType: Int = 3,
    isGps: Int = 0,
    cushionSupported: Boolean = true,
  ): FakeCloudState {
    val vehicle = OfficialVehicle.fromJson(
      mapOf(
        "carId" to "route-1",
        "carNickName" to "路由测试车",
        "modelType" to modelType,
        "isGps" to isGps,
        "btmac" to "AA:BB:CC:DD:EE:FF",
        "defenceStatus" to 0,
        "acc" to 0,
        "isCushionLock" to (if (cushionSupported) 1 else 0),
      ),
    )
    return FakeCloudState(selectedVehicle = vehicle)
  }

  private fun base(
    state: FakeCloudState,
    bleReady: Boolean = true,
    networkReady: Boolean = true,
    channel: OfficialControlChannel = OfficialControlChannel.AUTOMATIC,
  ): ControlChannelAvailability {
    return ControlChannelResolver.resolve(
      cloudState = state,
      bleReady = bleReady,
      networkReady = networkReady,
      channel = channel,
    )
  }

  @Test
  fun `seat control is BLE-only and requires the official capability flag`() {
    val state = stateFor()
    val availability = ControlCommandRoute.resolve(
      base = base(state),
      command = CommandCode.OPEN_SEAT,
      vehicle = state.selectedVehicle,
    )
    assertTrue(availability.enabled)
    assertTrue(availability.willUseBle)
    assertFalse(availability.canUseCloud)

    val unsupportedState = stateFor(cushionSupported = false)
    val unsupported = ControlCommandRoute.resolve(
      base = base(unsupportedState),
      command = CommandCode.OPEN_SEAT,
      vehicle = unsupportedState.selectedVehicle,
    )
    assertFalse(unsupported.enabled)
    assertTrue(unsupported.disabledReason == "当前车辆不支持开坐垫")
  }

  @Test
  fun `seat remains unavailable on a cloud-only control path`() {
    val state = stateFor(modelType = 8, isGps = 1)
    val availability = ControlCommandRoute.resolve(
      base = base(state, bleReady = false),
      command = CommandCode.OPEN_SEAT,
      vehicle = state.selectedVehicle,
    )
    assertFalse(availability.enabled)
    assertTrue(availability.disabledReason == "开坐垫需连接蓝牙")
  }

  @Test
  fun `manual channels still obey the official vehicle route`() {
    val yj = stateFor(modelType = 2, isGps = 1)
    val ble = base(yj, channel = OfficialControlChannel.BLE)
    assertFalse(ble.enabled)
    assertFalse(ble.canUseBle)

    val nonGpsBb = stateFor(modelType = 3, isGps = 0)
    val cloud = base(
      nonGpsBb,
      bleReady = false,
      channel = OfficialControlChannel.OFFICIAL_CLOUD,
    )
    assertFalse(cloud.enabled)
    assertFalse(cloud.canUseCloud)

    val kks = stateFor(modelType = 1, isGps = 0)
    val supportedCloud = base(
      kks,
      bleReady = true,
      channel = OfficialControlChannel.OFFICIAL_CLOUD,
    )
    assertTrue(supportedCloud.enabled)
    assertTrue(supportedCloud.canUseCloud)
  }

  @Test
  fun `cloud control is disabled immediately while the phone is offline`() {
    val gps = stateFor(modelType = 8, isGps = 1)
    val offline = base(gps, bleReady = false, networkReady = false)

    assertFalse(offline.enabled)
    assertFalse(offline.canUseCloud)
    assertTrue(offline.disabledReason == "手机网络未连接")
  }
}
