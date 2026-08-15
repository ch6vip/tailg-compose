/**
 * Port-validation tests for `com.tailg.plus.service.DiagnosticExportService`.
 *
 * Mirrors `tailg-ble-app/test/diagnostic_export_service_test.dart`:
 * injected report time, evicted-log-count heading, selected official vehicle
 * details, and cloud error redaction.
 *
 * The cloud service and vehicle store are mocked (mockk) so the report is
 * deterministic without persistence or network; the MQTT section reads the
 * default-constructed `OfficialMqttService` initial state (no sockets opened).
 */
package com.tailg.plus.service

import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.cloud.OfficialCloudState
import com.tailg.plus.data.model.OfficialBatteryInfo
import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.data.store.VehicleStore
import com.tailg.plus.log.LogService
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticExportServiceTest {

  private val generatedAt: LocalDateTime = LocalDateTime.of(2026, 6, 1, 8, 30)

  private fun store(): VehicleStore {
    val store = mockk<VehicleStore>()
    every { store.defaultVehicle } returns null
    return store
  }

  private fun cloud(state: OfficialCloudState): OfficialCloudService {
    val cloud = mockk<OfficialCloudService>()
    every { cloud.currentState } returns state
    every { cloud.lastRequest } returns null
    return cloud
  }

  private fun service(
    log: LogService,
    cloud: OfficialCloudService,
  ): DiagnosticExportService = DiagnosticExportService(
    logService = log,
    vehicleStore = store(),
    officialCloudService = cloud,
    clock = { generatedAt },
  )

  @Test
  fun usesInjectedReportTime() {
    val report = service(LogService(), cloud(OfficialCloudState.initial())).buildReport(emptyList())
    val lines = report.split("\n")
    assertEquals("# Tailg Diagnostic Report", lines[0])
    // Dart `DateTime(2026,6,1,8,30).toIso8601String()` = "2026-06-01T08:30:00.000";
    // the port formats ISO_LOCAL_DATE_TIME (no millis suffix).
    assertEquals("Generated: 2026-06-01T08:30:00", lines[1])
  }

  @Test
  fun includesEvictedLogCountInHeading() {
    val log = LogService()
    for (index in 0 until 2001) {
      log.operation("entry $index")
    }
    val lines = service(log, cloud(OfficialCloudState.initial())).buildReport(log.all).split("\n")
    assertTrue(lines.contains("## Logs (2000) [1 older entries evicted]"))
  }

  @Test
  fun includesSelectedOfficialVehicleDetails() {
    val vehicle = OfficialVehicle.fromJson(
      mapOf<String, Any?>(
        "carId" to "official-car-123456",
        "carNickName" to "通勤车",
        "defenceStatus" to 1,
        "acc" to 1,
        "electricQuantity" to 87,
        "voltage" to 52.4,
        "modelType" to 3,
        "imei" to "123456789012345",
        "imeiGps" to "987654321098765",
        "btname" to "TAILG-BLE",
        "btmac" to "AA:BB:CC:DD:EE:FF",
        "mac" to "AABBCCDDEEFF",
        "passwordInfo" to mapOf("main" to 123456, "children" to listOf(654321)),
        "latitude" to "31.2304",
        "longitude" to "121.4737",
      ),
    )
    val state = OfficialCloudState.initial().copyWith(
      initialized = true,
      token = "token",
      phone = "18800001111",
      vehicles = listOf(vehicle),
      selectedVehicleKey = vehicle.key,
      localVehicleLinks = mapOf(vehicle.key to "AA:BB:CC:DD:EE:FF"),
      batteryInfo = OfficialBatteryInfo.fromJson(
        mapOf(
          "dumpEnergyPercentLabel" to "86%",
          "voltage" to "52.3",
          "temperature" to "31.2",
        ),
      ),
    )

    val lines = service(LogService(), cloud(state)).buildReport(emptyList()).split("\n")

    assertTrue(lines.contains("Selected vehicle: 通勤车"))
    assertTrue(lines.contains("Selected key: off***456"))
    assertTrue(lines.contains("Linked local vehicle: AA:***:FF"))
    assertTrue(lines.contains("Online: false"))
    assertTrue(lines.contains("Defence: 已设防"))
    assertTrue(lines.contains("ACC: 车辆已启动"))
    assertTrue(lines.contains("Official vehicle battery: 87%"))
    assertTrue(lines.contains("Official vehicle voltage: 52.4V"))
    assertTrue(lines.contains("ModelType: 3"))
    assertTrue(lines.contains("Command IMEI: 987***765"))
    assertTrue(lines.contains("BT name: TAILG-BLE"))
    assertTrue(lines.contains("BT MAC: AA:***:FF"))
    assertTrue(lines.contains("Raw mac field: AAB***EFF"))
    assertTrue(lines.contains("Raw btmac field: AA:***:FF"))
    assertTrue(lines.contains("BLE identity MAC: AAB***EFF"))
    assertTrue(lines.contains("BLE stack: tlink"))
    assertTrue(lines.contains("passwordInfo key: present"))
    assertTrue(lines.contains("passwordInfo.main: present"))
    assertTrue(lines.contains("mainBlePassword: present"))
    assertTrue(lines.contains("childBlePasswords: 1"))
    assertTrue(lines.contains("Location: present (hidden)"))
    assertTrue(lines.contains("Official battery detail: 86%"))
    assertTrue(lines.contains("Official battery detail voltage: 52.3V"))
    assertTrue(lines.contains("Official battery detail temperature: 31.2C"))
  }

  @Test
  fun redactsOfficialCloudErrorDetails() {
    val state = OfficialCloudState.initial().copyWith(
      initialized = true,
      token = "token",
      error = "sync failed token=abcdef123456 userId=user-secret password=qgj-secret",
    )
    val report = service(LogService(), cloud(state)).buildReport(emptyList())

    assertTrue(
      report.contains(
        "Error: sync failed token=abc***456 userId=use***ret password=qgj***ret",
      ),
    )
    assertFalse(report.contains("abcdef123456"))
    assertFalse(report.contains("user-secret"))
    assertFalse(report.contains("qgj-secret"))
  }
}
