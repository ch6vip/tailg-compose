/**
 * Port-validation tests for `com.tailg.plus.domain.control.OfficialControlRoute`
 * (Dart → Kotlin). Vectors lifted verbatim from `tailg-ble-app/test/official_control_route_test.dart`,
 * which itself encodes the decompiled `ControlFragment.lock()/start()` + `ControlTypeUtil` table.
 */
package com.tailg.plus.domain.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialControlRouteTest {

  @Test
  fun `modelType 1 KKS is BLE when ready else cloud`() {
    val ble = OfficialControlRoute.resolve(
      bindingCar = true,
      modelType = 1,
      isGps = 0,
      bleReady = true,
      cloudSessionReady = true,
    )
    val cloud = OfficialControlRoute.resolve(
      bindingCar = true,
      modelType = 1,
      isGps = 0,
      bleReady = false,
      cloudSessionReady = true,
    )

    assertTrue(ble.usesBle)
    assertEquals(OfficialBleStackKind.STANDARD, ble.bleStack)
    assertTrue(cloud.usesCloud)
  }

  @Test
  fun `modelType 2 YJ is cloud only`() {
    val withBle = OfficialControlRoute.resolve(
      bindingCar = true,
      modelType = 2,
      isGps = 1,
      bleReady = true,
      cloudSessionReady = true,
    )
    val noSession = OfficialControlRoute.resolve(
      bindingCar = true,
      modelType = 2,
      isGps = 1,
      bleReady = true,
      cloudSessionReady = false,
    )

    assertTrue(withBle.usesCloud)
    assertEquals(OfficialBleStackKind.NONE, withBle.bleStack)
    assertTrue(noSession.isUnavailable)
  }

  @Test
  fun `modelType 8 QGJ isGps==1 and not LOGIN goes cloud else BLE required`() {
    val remote = OfficialControlRoute.resolve(
      bindingCar = true,
      modelType = 8,
      isGps = 1,
      bleReady = false,
      cloudSessionReady = true,
    )
    val local = OfficialControlRoute.resolve(
      bindingCar = true,
      modelType = 8,
      isGps = 1,
      bleReady = true,
      cloudSessionReady = true,
    )
    val pureBleNoLogin = OfficialControlRoute.resolve(
      bindingCar = true,
      modelType = 8,
      isGps = 0,
      bleReady = false,
      cloudSessionReady = true,
    )

    assertTrue(remote.usesCloud)
    assertEquals(OfficialBleStackKind.QGJ, remote.bleStack)
    assertTrue(local.usesBle)
    assertTrue(pureBleNoLogin.isUnavailable)
    assertEquals("蓝牙未连接", pureBleNoLogin.reason)
  }

  @Test
  fun `modelType 10/14 C39 follow isGps hybrid gate with standard stack`() {
    for (type in OfficialControlRoute.c39ModelTypes) {
      val remote = OfficialControlRoute.resolve(
        bindingCar = true,
        modelType = type,
        isGps = 1,
        bleReady = false,
        cloudSessionReady = true,
      )
      val local = OfficialControlRoute.resolve(
        bindingCar = true,
        modelType = type,
        isGps = 0,
        bleReady = true,
        cloudSessionReady = true,
      )
      assertTrue("type $type remote", remote.usesCloud)
      assertEquals(OfficialBleStackKind.STANDARD, remote.bleStack)
      assertTrue("type $type local", local.usesBle)
    }
  }

  @Test
  fun `gpsCombo modelTypes fall back to cloud without isGps gate`() {
    for (type in OfficialControlRoute.gpsComboModelTypes) {
      val ble = OfficialControlRoute.resolve(
        bindingCar = true,
        modelType = type,
        isGps = 0,
        bleReady = true,
        cloudSessionReady = true,
      )
      val cloud = OfficialControlRoute.resolve(
        bindingCar = true,
        modelType = type,
        isGps = 0,
        bleReady = false,
        cloudSessionReady = true,
      )
      assertTrue("type $type BLE", ble.usesBle)
      assertTrue("type $type cloud", cloud.usesCloud)
    }
  }

  @Test
  fun `official no-op and unknown model types are unavailable`() {
    for (type in OfficialControlRoute.unsupportedControlModelTypes + 9999) {
      val decision = OfficialControlRoute.resolve(
        bindingCar = true,
        modelType = type,
        isGps = 1,
        bleReady = true,
        cloudSessionReady = true,
      )
      assertTrue("type $type", decision.isUnavailable)
    }
  }

  @Test
  fun `unbound vehicle is unavailable`() {
    val decision = OfficialControlRoute.resolve(
      bindingCar = false,
      modelType = 1,
      isGps = 1,
      bleReady = true,
      cloudSessionReady = true,
    )
    assertTrue(decision.isUnavailable)
    assertEquals("未绑定车辆", decision.reason)
  }

  @Test
  fun `network and session gates for cloud path`() {
    val noNet = OfficialControlRoute.resolve(
      bindingCar = true,
      modelType = 1,
      isGps = 0,
      bleReady = false,
      networkReady = false,
      cloudSessionReady = true,
    )
    val noSession = OfficialControlRoute.resolve(
      bindingCar = true,
      modelType = 1,
      isGps = 0,
      bleReady = false,
      networkReady = true,
      cloudSessionReady = false,
    )
    assertTrue(noNet.isUnavailable)
    assertEquals("手机网络未连接", noNet.reason)
    assertTrue(noSession.isUnavailable)
    assertTrue(noSession.reason.contains("登录"))
  }

  /** P0-C2 table-driven matrix: modelType × bleReady × network × session. */
  @Test
  fun `table-driven branches cover ControlFragment families`() {
    val cases = listOf(
      // KKS
      RouteCase(1, 0, true, true, true, expectsBle = true),
      RouteCase(1, 0, false, true, true, expectsCloud = true),
      RouteCase(1, 0, false, false, true, expectsUnavailable = true),
      // YJ cloud only
      RouteCase(2, 1, true, true, true, expectsCloud = true),
      RouteCase(2, 0, false, true, false, expectsUnavailable = true),
      // QGJ hybrid
      RouteCase(8, 1, false, true, true, expectsCloud = true),
      RouteCase(283, 1, true, true, true, expectsBle = true, stack = OfficialBleStackKind.QGJ),
      RouteCase(8, 0, false, true, true, expectsUnavailable = true),
      // C39
      RouteCase(10, 1, false, true, true, expectsCloud = true),
      RouteCase(14, 0, true, true, true, expectsBle = true),
      // GPS combo
      RouteCase(401, 0, false, true, true, expectsCloud = true),
      RouteCase(928, 0, true, true, true, expectsBle = true),
      RouteCase(2103, 1, false, true, true, expectsCloud = true),
      RouteCase(2201, 0, true, false, true, expectsBle = true),
      // default/BB hybrid
      RouteCase(3, 1, false, true, true, expectsCloud = true),
      RouteCase(3, 0, true, true, true, expectsBle = true),
      RouteCase(3, 0, false, true, true, expectsUnavailable = true),
      // no-op control family
      RouteCase(1501, 0, false, true, true, expectsUnavailable = true),
    )

    for (c in cases) {
      val d = OfficialControlRoute.resolve(
        bindingCar = true,
        modelType = c.modelType,
        isGps = c.isGps,
        bleReady = c.bleReady,
        networkReady = c.networkReady,
        cloudSessionReady = c.cloudSessionReady,
      )
      if (c.expectsBle) {
        assertTrue(c.describe, d.usesBle)
        if (c.stack != null) assertEquals(c.describe, c.stack, d.bleStack)
      } else if (c.expectsCloud) {
        assertTrue(c.describe, d.usesCloud)
      } else if (c.expectsUnavailable) {
        assertTrue(c.describe, d.isUnavailable)
      }
    }
  }

  private data class RouteCase(
    val modelType: Int,
    val isGps: Int,
    val bleReady: Boolean,
    val networkReady: Boolean,
    val cloudSessionReady: Boolean,
    val expectsBle: Boolean = false,
    val expectsCloud: Boolean = false,
    val expectsUnavailable: Boolean = false,
    val stack: OfficialBleStackKind? = null,
  ) {
    val describe: String
      get() = "type=$modelType isGps=$isGps ble=$bleReady net=$networkReady session=$cloudSessionReady"
  }
}
