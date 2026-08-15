/**
 * Port-validation tests for `com.tailg.plus.service.FirmwareOtaService`.
 *
 * Mirrors `tailg-ble-app/test/p3_ota_nfc_depth_test.dart`:
 * - `OfficialNfcBleFrames` header-prefix group (already ported in
 *   `com.tailg.plus.data.ble.NfcBleFrames`; asserted here to keep the Dart
 *   test file mirrored 1:1).
 * - `FirmwareOtaService` e2e pipeline: query + download override + chunk
 *   transfer; missing downloader fails before BLE writes; not-LOGIN gate.
 *
 * The cloud service and connection manager are mocked (mockk); the OTA write
 * hooks bypass BLE entirely, so no device or Robolectric is needed.
 */
package com.tailg.plus.service

import com.tailg.plus.data.ble.OfficialNfcBleFrames
import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.cloud.OfficialCloudState
import com.tailg.plus.data.model.OfficialVehicle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirmwareOtaServiceTest {

  // --- OfficialNfcBleFrames (mirror of the Dart P3-6 group) ----------------

  @Test
  fun nfcFrames_buildOfficialHeaderPrefixes() {
    assertTrue(OfficialNfcBleFrames.addUserKeyHex(keyType = 1, type = "1").startsWith(OfficialNfcBleFrames.headerAddUserKey))
    assertTrue(OfficialNfcBleFrames.checkNfcHex("01").startsWith(OfficialNfcBleFrames.headerNfcCheck))
    assertTrue(OfficialNfcBleFrames.delNfcHex("02").startsWith(OfficialNfcBleFrames.headerNfcDel))
    assertTrue(OfficialNfcBleFrames.addCardHex("03").startsWith(OfficialNfcBleFrames.headerNfcAddMode))
    assertEquals(2, OfficialNfcBleFrames.toBytes("8504").size)
  }

  // --- FirmwareOtaService e2e pipeline (mirror of the Dart P3-5 group) -----

  private fun otaVehicle(carId: String, imei: String): OfficialVehicle =
    OfficialVehicle.fromJson(
      mapOf(
        "carId" to carId,
        "carNickName" to "OTA车",
        "imei" to imei,
        "imeiGps" to imei,
      ),
    )

  private fun cloudSelecting(vehicle: OfficialVehicle): OfficialCloudService {
    val cloud = mockk<OfficialCloudService>()
    every { cloud.currentState } returns
      OfficialCloudState.initial().copyWith(
        token = "t",
        vehicles = listOf(vehicle),
        selectedVehicleKey = vehicle.key,
      )
    return cloud
  }

  @Test
  fun queryPlusDownloadOverridePlusChunkTransferCompletes() = runTest {
    val vehicle = otaVehicle("ota-1", "860000000000999")
    val cloud = cloudSelecting(vehicle)
    coEvery { cloud.getFirmVersion(imei = any()) } returns mapOf(
      "version" to "1.0.0-demo",
      "url" to "https://example.invalid/fw.bin",
    )

    val manager = mockk<ConnectionManager>()
    every { manager.isProtocolLoggedIn } returns true

    val orders = mutableListOf<List<Int>>()
    val chunks = mutableListOf<List<Int>>()
    val ota = FirmwareOtaService(cloud = cloud, connectionManager = manager)
    ota.downloadOverride = { url -> ByteArray(400) { i -> (i and 0xFF).toByte() } }
    ota.writeOrderOverride = { order ->
      orders.add(order)
      true
    }
    ota.writeChunkOverride = { chunk ->
      chunks.add(chunk)
      true
    }

    val progress = ota.run(chunkSize = 100).toList()
    assertEquals(FirmwareOtaPhase.COMPLETED, progress.last().phase)
    assertTrue(orders.isNotEmpty())
    assertEquals(4, chunks.size)
    assertEquals(400, chunks.sumOf { it.size })
  }

  @Test
  fun missingProductionDownloaderFailsBeforeBleWrites() = runTest {
    val vehicle = otaVehicle("ota-safe", "8602")
    val cloud = cloudSelecting(vehicle)
    coEvery { cloud.getFirmVersion(imei = any()) } returns mapOf("url" to "https://invalid/fw")

    val manager = mockk<ConnectionManager>()
    every { manager.isProtocolLoggedIn } returns true

    var writes = 0
    val ota = FirmwareOtaService(cloud = cloud, connectionManager = manager)
    ota.writeOrderOverride = { order ->
      writes += 1
      true
    }
    ota.writeChunkOverride = { chunk ->
      writes += 1
      true
    }

    val progress = ota.run().toList()
    assertEquals(FirmwareOtaPhase.FAILED, progress.last().phase)
    assertTrue(progress.last().message.contains("未配置 downloadOverride"))
    assertEquals(0, writes)
  }

  @Test
  fun failsWhenNotLogin() = runTest {
    val vehicle = otaVehicle("ota-2", "8601")
    val cloud = cloudSelecting(vehicle)
    coEvery { cloud.getFirmVersion(imei = any()) } returns mapOf("url" to "x")

    val manager = mockk<ConnectionManager>()
    every { manager.isProtocolLoggedIn } returns false

    val ota = FirmwareOtaService(cloud = cloud, connectionManager = manager)
    ota.downloadOverride = { url -> ByteArray(16) }

    val progress = ota.run().toList()
    assertEquals(FirmwareOtaPhase.FAILED, progress.last().phase)
    assertTrue(progress.last().message.contains("协议登录"))
  }
}
