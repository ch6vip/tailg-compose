/**
 * Port-validation tests for `com.tailg.plus.service.InductionModeService`
 * (Dart → Kotlin).
 *
 * `tailg-ble-app/test/induction_mode_service_test.dart` does NOT exist
 * upstream, so the pure-logic vectors mirror the closest related Dart suite
 * (`test/qgj_proximity_test.dart` — model-type routing) plus service-flow
 * coverage derived directly from the Dart source. All collaborators
 * (`ConnectionManager`, `ManualModeService`, `OfficialCloudService`,
 * `InductionForegroundServiceBridge`, `InductionPrefs`) are mockk mocks and
 * `kotlinx-coroutines-test` `runTest` drives the service scope; every test
 * disposes the service so no coroutine outlives the test body.
 */
package com.tailg.plus.service

import com.tailg.plus.data.ble.QgjCommandIds
import com.tailg.plus.data.ble.QgjResponse
import com.tailg.plus.data.ble.TLINK_HID_OPEN_AFTER_BOND_PLAIN
import com.tailg.plus.data.ble.buildQgjHidPayload
import com.tailg.plus.data.ble.buildQgjProximityDistancePayload
import com.tailg.plus.data.ble.buildQgjProximityStatusPayload
import com.tailg.plus.data.ble.defaultMaxDistanceM
import com.tailg.plus.data.ble.defaultMinDistanceM
import com.tailg.plus.data.ble.defaultRssiA
import com.tailg.plus.data.ble.defaultRssiFactor
import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.data.ble.platform.ConnectionState
import com.tailg.plus.data.ble.platform.ProtocolType
import com.tailg.plus.data.cloud.OfficialCloudService
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InductionModeServiceTest {

  private fun qgjResponse(cmdId: Int, payload: List<Int>, success: Boolean = true): QgjResponse =
    QgjResponse(
      cmdId = cmdId,
      payload = ByteArray(payload.size) { payload[it].toByte() },
      success = success,
    )

  private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

  /** ConnectionManager mocked to a protocol-logged-in QGJ link. */
  private fun qgjReadyCm(): ConnectionManager {
    val cm = mockk<ConnectionManager>(relaxed = true)
    every { cm.isProtocolLoggedIn } returns true
    every { cm.protocol } returns ProtocolType.QGJ
    every { cm.stateFlow } returns MutableStateFlow(ConnectionState.READY)
    return cm
  }

  private fun TestScope.buildService(
    cm: ConnectionManager,
    manual: ManualModeService? = null,
    cloud: OfficialCloudService? = null,
    prefs: InductionPrefs? = null,
    bridge: InductionForegroundServiceBridge? = null,
  ): InductionModeService = InductionModeService(
    cm = cm,
    context = mockk(relaxed = true),
    manual = manual,
    log = mockk(relaxed = true),
    foregroundService = bridge ?: mockk(relaxed = true),
    cloud = cloud,
    prefs = prefs ?: mockk(relaxed = true),
    externalScope = this,
  )

  // --- model-type routing (mirror of qgj_proximity_test.dart group) ---------

  @Test
  fun `stackForModelType routes official model types`() {
    assertEquals(InductionStack.QGJ, InductionModeService.stackForModelType(8))
    assertEquals(InductionStack.QGJ, InductionModeService.stackForModelType(283))
    assertEquals(InductionStack.TLINK, InductionModeService.stackForModelType(3))
    assertEquals(InductionStack.TLINK, InductionModeService.stackForModelType(10))
    assertEquals(InductionStack.TLINK, InductionModeService.stackForModelType(14))
    assertEquals(InductionStack.TLINK, InductionModeService.stackForModelType(401))
    assertEquals(InductionStack.TLINK, InductionModeService.stackForModelType(928))
    assertEquals(InductionStack.TLINK, InductionModeService.stackForModelType(2103))
    assertEquals(InductionStack.TLINK, InductionModeService.stackForModelType(2201))
    // unsupported control models still get the TLink induction path.
    assertEquals(InductionStack.TLINK, InductionModeService.stackForModelType(1501))
    assertEquals(InductionStack.TLINK, InductionModeService.stackForModelType(1601))
    assertEquals(InductionStack.TLINK, InductionModeService.stackForModelType(1701))
    assertEquals(InductionStack.RSSI, InductionModeService.stackForModelType(1))
    assertEquals(InductionStack.NONE, InductionModeService.stackForModelType(2))
    assertEquals(InductionStack.NONE, InductionModeService.stackForModelType(null))
    assertEquals(InductionStack.NONE, InductionModeService.stackForModelType(999))
  }

  @Test
  fun `resolveStack falls back to live protocol when model unknown`() = runTest {
    val cm = mockk<ConnectionManager>(relaxed = true)
    every { cm.protocol } returns ProtocolType.TLINK
    every { cm.stateFlow } returns MutableStateFlow(ConnectionState.DISCONNECTED)
    val service = buildService(cm)
    assertEquals(InductionStack.TLINK, service.resolveStack(null))
    assertEquals(InductionStack.QGJ, service.resolveStack(8))
    service.dispose()
  }

  // --- snapshot semantics (Dart InductionModeSnapshot) ----------------------

  @Test
  fun `snapshot unlockSelection reflects stack and enabled`() {
    assertEquals(false, InductionModeSnapshot.EMPTY.unlockSelection)
    assertEquals(
      true,
      InductionModeSnapshot(
        stack = InductionStack.QGJ,
        enabled = true,
        distance = null,
        busy = false,
        bleReady = true,
      ).unlockSelection,
    )
    assertNull(
      InductionModeSnapshot(
        stack = InductionStack.TLINK,
        enabled = null,
        distance = null,
        busy = false,
        bleReady = true,
      ).unlockSelection,
    )
  }

  @Test
  fun `snapshot copyWith keeps Dart sentinel semantics`() {
    val base = InductionModeSnapshot(
      stack = InductionStack.QGJ,
      enabled = true,
      distance = 5,
      busy = true,
      bleReady = true,
      lastError = "err",
    )
    // A null argument keeps the current value (Dart `?? this.enabled`).
    assertEquals(true, base.copyWith(enabled = null).enabled)
    assertEquals("err", base.copyWith(lastError = null).lastError)
    // Explicit clear flags null the field out.
    assertNull(base.copyWith(clearEnabled = true).enabled)
    assertNull(base.copyWith(clearError = true).lastError)
    assertEquals(5, base.copyWith(clearError = true).distance)
  }

  @Test
  fun `rssiCalibration fromMap tolerates key casing and types`() {
    assertEquals(defaultRssiA, RssiCalibration.fromMap(null).rssiA, 0.0)
    assertEquals(defaultRssiFactor, RssiCalibration.fromMap(emptyMap()).rssiFactor, 0.0)
    val camel = RssiCalibration.fromMap(
      mapOf(
        "rssiA" to 60.0,
        "rssiFactor" to "5.0",
        "minRssiDistance" to 1,
        "maxRssiDistance" to 4.0,
      ),
    )
    assertEquals(60.0, camel.rssiA, 0.0)
    assertEquals(5.0, camel.rssiFactor, 0.0)
    assertEquals(1.0, camel.minDistanceM, 0.0)
    assertEquals(4.0, camel.maxDistanceM, 0.0)
    val pascal = RssiCalibration.fromMap(
      mapOf("RssiA" to 55.0, "RssiFactor" to 4.0, "MinRssiDistance" to 2.5, "MaxRssiDistance" to 3.5),
    )
    assertEquals(55.0, pascal.rssiA, 0.0)
    assertEquals(4.0, pascal.rssiFactor, 0.0)
    val bad = RssiCalibration.fromMap(mapOf("rssiA" to "not-a-number"))
    assertEquals(defaultRssiA, bad.rssiA, 0.0)
  }

  // --- gates ----------------------------------------------------------------

  @Test
  fun `enable rejected when ble not logged in`() = runTest {
    val cm = mockk<ConnectionManager>(relaxed = true)
    every { cm.isProtocolLoggedIn } returns false
    every { cm.protocol } returns ProtocolType.QGJ
    every { cm.stateFlow } returns MutableStateFlow(ConnectionState.CONNECTED)
    val service = buildService(cm)
    service.bindVehicle(modelType = 8, carId = "c1", vehicleRaw = null)
    runCurrent()

    val ok = service.setEnabled(true)

    assertFalse(ok)
    assertEquals("请先连接车辆蓝牙并完成协议登录", service.snapshot.lastError)
    service.dispose()
  }

  @Test
  fun `enable rejected when manual mode is on`() = runTest {
    val cm = qgjReadyCm()
    val manual = mockk<ManualModeService>(relaxed = true)
    every { manual.enabled } returns true
    coEvery { manual.setEnabled(false) } just Runs
    val service = buildService(cm, manual = manual)
    service.bindVehicle(modelType = 8, carId = "c1", vehicleRaw = null)
    runCurrent()

    val ok = service.setEnabled(true)

    assertFalse(ok)
    assertEquals("已开启手动模式，无法开关感应解锁", service.snapshot.lastError)
    coVerify { manual.setEnabled(false) }
    coVerify(exactly = 0) { cm.sendQgjCommand(QgjCommandIds.proximityStatusSet, any()) }
    service.dispose()
  }

  // --- QGJ path -------------------------------------------------------------

  @Test
  fun `qgj enable sends proximity then HID then bond and persists`() = runTest {
    val cm = qgjReadyCm()
    val proximityPayloads = mutableListOf<Int>()
    coEvery { cm.sendQgjCommand(QgjCommandIds.proximityStatusSet, any()) } answers {
      proximityPayloads.add(secondArg<ByteArray>().first().toInt() and 0xFF)
      qgjResponse(QgjCommandIds.proximityStatusSet, emptyList())
    }
    coEvery { cm.sendQgjCommand(QgjCommandIds.hidStatusSet, any()) } returns
      qgjResponse(QgjCommandIds.hidStatusSet, emptyList())
    coEvery { cm.createBond(quiet = true) } returns true
    val prefs = mockk<InductionPrefs>(relaxed = true)
    coEvery { prefs.loadBoolean(any(), any()) } returns false
    val service = buildService(cm, prefs = prefs)

    service.bindVehicle(modelType = 8, carId = "c1", vehicleRaw = null)
    runCurrent()
    val ok = service.setEnabled(true)

    assertTrue(ok)
    assertEquals(listOf(1), proximityPayloads)
    coVerify {
      cm.sendQgjCommand(QgjCommandIds.proximityStatusSet, match { it.contentEquals(bytes(1)) })
    }
    coVerify {
      cm.sendQgjCommand(QgjCommandIds.hidStatusSet, match { it.contentEquals(bytes(1)) })
    }
    coVerify { cm.createBond(quiet = true) }
    coVerify { prefs.saveBoolean("induction_enabled_c1", true) }
    assertEquals(true, service.snapshot.enabled)
    assertEquals(false, service.snapshot.busy)
    assertEquals(false, service.snapshot.bondIncomplete)
    service.dispose()
  }

  @Test
  fun `qgj enable rolls back proximity when HID fails`() = runTest {
    val cm = qgjReadyCm()
    coEvery { cm.sendQgjCommand(QgjCommandIds.proximityStatusSet, any()) } returns
      qgjResponse(QgjCommandIds.proximityStatusSet, emptyList())
    coEvery { cm.sendQgjCommand(QgjCommandIds.hidStatusSet, any()) } returns
      qgjResponse(QgjCommandIds.hidStatusSet, emptyList(), success = false)
    val prefs = mockk<InductionPrefs>(relaxed = true)
    coEvery { prefs.loadBoolean(any(), any()) } returns false
    val service = buildService(cm, prefs = prefs)

    service.bindVehicle(modelType = 8, carId = "c1", vehicleRaw = null)
    runCurrent()
    val ok = service.setEnabled(true)

    assertFalse(ok)
    assertEquals("车辆未确认开启蓝牙感应配对", service.snapshot.lastError)
    coVerify(exactly = 2) { cm.sendQgjCommand(QgjCommandIds.proximityStatusSet, any()) }
    coVerify {
      cm.sendQgjCommand(QgjCommandIds.proximityStatusSet, match { it.contentEquals(bytes(0)) })
    }
    coVerify(exactly = 0) { prefs.saveBoolean(any(), any()) }
    service.dispose()
  }

  @Test
  fun `qgj disable sends close and keeps warning when bond removal fails`() = runTest {
    val cm = qgjReadyCm()
    coEvery { cm.sendQgjCommand(QgjCommandIds.proximityStatusSet, any()) } returns
      qgjResponse(QgjCommandIds.proximityStatusSet, emptyList())
    coEvery { cm.sendQgjCommand(QgjCommandIds.hidStatusSet, any()) } returns
      qgjResponse(QgjCommandIds.hidStatusSet, emptyList())
    coEvery { cm.removeBond(quiet = true) } returns false
    val prefs = mockk<InductionPrefs>(relaxed = true)
    coEvery { prefs.loadBoolean(any(), any()) } returns false
    val service = buildService(cm, prefs = prefs)

    service.bindVehicle(modelType = 8, carId = "c1", vehicleRaw = null)
    runCurrent()
    val ok = service.setEnabled(false)

    assertTrue(ok)
    assertEquals("车辆感应已关闭，但系统蓝牙配对未能移除", service.snapshot.lastError)
    coVerify {
      cm.sendQgjCommand(QgjCommandIds.proximityStatusSet, match { it.contentEquals(bytes(0)) })
    }
    coVerify {
      cm.sendQgjCommand(QgjCommandIds.hidStatusSet, match { it.contentEquals(bytes(0)) })
    }
    coVerify { prefs.saveBoolean("induction_enabled_c1", false) }
    service.dispose()
  }

  // --- TLink path -----------------------------------------------------------

  @Test
  fun `tlink enable opens mode then bonds then writes hid`() = runTest {
    val cm = mockk<ConnectionManager>(relaxed = true)
    every { cm.isProtocolLoggedIn } returns true
    every { cm.protocol } returns ProtocolType.TLINK
    every { cm.stateFlow } returns MutableStateFlow(ConnectionState.READY)
    coEvery { cm.openTlinkInduction() } returns true
    coEvery { cm.createBond(quiet = true) } returns true
    coEvery { cm.writeStandardHex(TLINK_HID_OPEN_AFTER_BOND_PLAIN) } returns true
    val prefs = mockk<InductionPrefs>(relaxed = true)
    coEvery { prefs.loadBoolean(any(), any()) } returns false
    val service = buildService(cm, prefs = prefs)

    service.bindVehicle(modelType = 3, carId = "c1", vehicleRaw = null)
    runCurrent()
    val ok = service.setEnabled(true)

    assertTrue(ok)
    coVerify { cm.openTlinkInduction() }
    coVerify { cm.createBond(quiet = true) }
    coVerify { cm.writeStandardHex(TLINK_HID_OPEN_AFTER_BOND_PLAIN) }
    coVerify { prefs.saveBoolean("induction_enabled_c1", true) }
    assertEquals(false, service.snapshot.bondIncomplete)
    service.dispose()
  }

  @Test
  fun `tlink enable rolls back when hid write fails`() = runTest {
    val cm = mockk<ConnectionManager>(relaxed = true)
    every { cm.isProtocolLoggedIn } returns true
    every { cm.protocol } returns ProtocolType.TLINK
    every { cm.stateFlow } returns MutableStateFlow(ConnectionState.READY)
    coEvery { cm.openTlinkInduction() } returns true
    coEvery { cm.createBond(quiet = true) } returns true
    coEvery { cm.writeStandardHex(TLINK_HID_OPEN_AFTER_BOND_PLAIN) } returns false
    coEvery { cm.closeTlinkInduction() } returns true
    coEvery { cm.removeBond(quiet = true) } returns true
    val prefs = mockk<InductionPrefs>(relaxed = true)
    coEvery { prefs.loadBoolean(any(), any()) } returns false
    val service = buildService(cm, prefs = prefs)

    service.bindVehicle(modelType = 3, carId = "c1", vehicleRaw = null)
    runCurrent()
    val ok = service.setEnabled(true)

    assertFalse(ok)
    assertEquals("车辆感应已开启，但蓝牙感应配对写入失败", service.snapshot.lastError)
    coVerify { cm.closeTlinkInduction() }
    coVerify { cm.removeBond(quiet = true) }
    coVerify(exactly = 0) { prefs.saveBoolean(any(), any()) }
    service.dispose()
  }

  @Test
  fun `tlink refresh timeout publishes retry error`() = runTest {
    val cm = mockk<ConnectionManager>(relaxed = true)
    every { cm.isProtocolLoggedIn } returns true
    every { cm.protocol } returns ProtocolType.TLINK
    every { cm.stateFlow } returns MutableStateFlow(ConnectionState.READY)
    coEvery { cm.checkTlinkInduction() } returns null
    val service = buildService(cm)

    service.bindVehicle(modelType = 3, carId = "c1", vehicleRaw = null)
    runCurrent()

    assertEquals("读取感应状态超时，请重试", service.snapshot.lastError)
    assertEquals(false, service.snapshot.busy)
    assertEquals(true, service.snapshot.bleReady)
    service.dispose()
  }

  // --- RSSI path ------------------------------------------------------------

  @Test
  fun `rssi enable for KKS syncs cloud hid and starts loop`() = runTest {
    val cm = mockk<ConnectionManager>(relaxed = true)
    every { cm.isProtocolLoggedIn } returns true
    every { cm.protocol } returns ProtocolType.KKS
    every { cm.stateFlow } returns MutableStateFlow(ConnectionState.READY)
    val cloud = mockk<OfficialCloudService>(relaxed = true)
    coEvery { cloud.setKksHidEnabled(any()) } just Runs
    val bridge = mockk<InductionForegroundServiceBridge>(relaxed = true)
    every { bridge.supportsBackgroundRssi } returns true
    coEvery { bridge.start(any()) } returns true
    val prefs = mockk<InductionPrefs>(relaxed = true)
    coEvery { prefs.loadBoolean(any(), any()) } returns false
    val service = buildService(cm, cloud = cloud, prefs = prefs, bridge = bridge)

    service.bindVehicle(modelType = 1, carId = "k1", vehicleRaw = null)
    runCurrent()
    val ok = service.setEnabled(true)
    runCurrent()

    assertTrue(ok)
    coVerify { cloud.setKksHidEnabled(true) }
    coVerify { prefs.saveBoolean("induction_enabled_k1", true) }
    coVerify { bridge.start("k1") }
    assertEquals(true, service.snapshot.enabled)
    service.dispose()
  }

  @Test
  fun `rssi disable stops loop and syncs cloud off`() = runTest {
    val cm = mockk<ConnectionManager>(relaxed = true)
    every { cm.isProtocolLoggedIn } returns true
    every { cm.protocol } returns ProtocolType.KKS
    every { cm.stateFlow } returns MutableStateFlow(ConnectionState.READY)
    val cloud = mockk<OfficialCloudService>(relaxed = true)
    coEvery { cloud.setKksHidEnabled(any()) } just Runs
    val bridge = mockk<InductionForegroundServiceBridge>(relaxed = true)
    every { bridge.supportsBackgroundRssi } returns true
    coEvery { bridge.start(any()) } returns true
    val prefs = mockk<InductionPrefs>(relaxed = true)
    coEvery { prefs.loadBoolean(any(), any()) } returns false
    val service = buildService(cm, cloud = cloud, prefs = prefs, bridge = bridge)

    service.bindVehicle(modelType = 1, carId = "k1", vehicleRaw = null)
    runCurrent()
    assertTrue(service.setEnabled(true))
    runCurrent()
    val ok = service.setEnabled(false)
    runCurrent()

    assertTrue(ok)
    coVerify { cloud.setKksHidEnabled(false) }
    coVerify { bridge.stop() }
    coVerify { prefs.saveBoolean("induction_enabled_k1", false) }
    assertEquals(false, service.snapshot.enabled)
    service.dispose()
  }

  // --- distance -------------------------------------------------------------

  @Test
  fun `setDistance clamps to max and persists`() = runTest {
    val cm = qgjReadyCm()
    coEvery { cm.sendQgjCommand(QgjCommandIds.proximityDistanceSet, any()) } returns
      qgjResponse(QgjCommandIds.proximityDistanceSet, emptyList())
    val prefs = mockk<InductionPrefs>(relaxed = true)
    coEvery { prefs.loadBoolean(any(), any()) } returns false
    val service = buildService(cm, prefs = prefs)

    service.bindVehicle(modelType = 8, carId = "c1", vehicleRaw = null)
    runCurrent()
    val ok = service.setDistance(999)

    assertTrue(ok)
    assertEquals(InductionModeService.MAX_DISTANCE_LEVEL, service.snapshot.distance)
    coVerify { prefs.saveInt("induction_distance_c1", InductionModeService.MAX_DISTANCE_LEVEL) }
    coVerify {
      cm.sendQgjCommand(
        QgjCommandIds.proximityDistanceSet,
        match {
          it.contentEquals(buildQgjProximityDistancePayload(InductionModeService.MAX_DISTANCE_LEVEL))
        },
      )
    }
    service.dispose()
  }
}
