/**
 * Port of `lib/services/firmware_ota_service.dart` (tailg-ble-app) → package
 * `com.tailg.plus.service`.
 *
 * P3-5 experimental OTA pipeline: query official firm version → download →
 * BLE chunked transfer. The Dart `async*` generator becomes a cold
 * [kotlinx.coroutines.flow.Flow]: every collector re-runs the whole pipeline,
 * exactly like a Dart stream listener. [FirmwareOtaProgress] emissions mirror
 * the Dart `yield` points 1:1, including phase, fraction and message text.
 *
 * Download / write test hooks keep the Dart semantics: [downloadOverride]
 * must be set in tests (production refuses to fetch real firmware), and the
 * write overrides bypass the [ConnectionManager] BLE path.
 */
package com.tailg.plus.service

import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.log.LogService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Port of Dart `enum FirmwareOtaPhase { idle, querying, downloading, transferring, completed, failed }`. */
enum class FirmwareOtaPhase { IDLE, QUERYING, DOWNLOADING, TRANSFERRING, COMPLETED, FAILED }

/** Port of Dart `class FirmwareOtaProgress` (immutable progress snapshot). */
data class FirmwareOtaProgress(
  val phase: FirmwareOtaPhase,
  val fraction: Double,
  val message: String,
)

/**
 * P3-5 experimental OTA: query firm version → download → BLE chunks.
 * Port of `lib/services/firmware_ota_service.dart`.
 *
 * Dart `Stream` → cold [Flow] via [run]; `Uint8List` → [ByteArray];
 * `Future<bool> Function(List<int>)` write hooks → suspend lambdas taking
 * `List<Int>` (the [ConnectionManager] API itself consumes `ByteArray`).
 */
class FirmwareOtaService(
  val cloud: OfficialCloudService,
  val connectionManager: ConnectionManager,
  logService: LogService? = null,
) {
  private val log: LogService = logService ?: LogService()

  /** Dart `downloadOverride`: injected downloader; null refuses real downloads. */
  var downloadOverride: (suspend (String) -> ByteArray)? = null

  /** Dart `writeOrderOverride`: intercepts the OTA order write (3-byte header). */
  var writeOrderOverride: (suspend (List<Int>) -> Boolean)? = null

  /** Dart `writeChunkOverride`: intercepts each OTA file chunk write. */
  var writeChunkOverride: (suspend (List<Int>) -> Boolean)? = null

  companion object {
    /** Dart `static const defaultChunkSize = 180`. */
    const val DEFAULT_CHUNK_SIZE = 180
  }

  /** Dart `run({vehicle, chunkSize}) async*` → cold [Flow] of progress events. */
  fun run(
    vehicle: OfficialVehicle? = null,
    chunkSize: Int = DEFAULT_CHUNK_SIZE,
  ): Flow<FirmwareOtaProgress> = flow {
    val selected = vehicle ?: cloud.currentState.selectedVehicle
    if (selected == null) {
      emit(FirmwareOtaProgress(FirmwareOtaPhase.FAILED, 0.0, "未选择车辆"))
      return@flow
    }
    val imei = selected.commandImei
    if (imei.isEmpty()) {
      emit(FirmwareOtaProgress(FirmwareOtaPhase.FAILED, 0.0, "车辆缺少 IMEI"))
      return@flow
    }

    emit(FirmwareOtaProgress(FirmwareOtaPhase.QUERYING, 0.05, "查询官方固件版本…"))

    val firmInfo: Map<String, Any?>
    try {
      firmInfo = cloud.getFirmVersion(imei = imei)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      emit(FirmwareOtaProgress(FirmwareOtaPhase.FAILED, 0.05, "固件查询失败: $e"))
      return@flow
    }

    val url = (firmInfo["url"] ?: firmInfo["fileUrl"] ?: firmInfo["downUrl"] ?: "")
      .toString()
      .trim()

    if (url.isEmpty()) {
      emit(
        FirmwareOtaProgress(
          FirmwareOtaPhase.FAILED,
          0.1,
          "未查到可下载固件（version=${firmInfo["version"] ?: firmInfo["firmVersion"] ?: "-"}）",
        ),
      )
      return@flow
    }

    emit(FirmwareOtaProgress(FirmwareOtaPhase.DOWNLOADING, 0.15, "下载固件…"))
    val bytes: ByteArray
    try {
      bytes = download(url)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      emit(FirmwareOtaProgress(FirmwareOtaPhase.FAILED, 0.15, "固件下载失败: $e"))
      return@flow
    }

    if (!connectionManager.isProtocolLoggedIn) {
      emit(FirmwareOtaProgress(FirmwareOtaPhase.FAILED, 0.25, "请先 BLE 协议登录后再传输固件"))
      return@flow
    }

    emit(
      FirmwareOtaProgress(
        FirmwareOtaPhase.TRANSFERRING,
        0.3,
        "开始 BLE 分片传输 (${bytes.size} bytes)…",
      ),
    )

    val order = listOf(0x01, (bytes.size shr 8) and 0xFF, bytes.size and 0xFF)
    val orderOverride = writeOrderOverride
    val orderOk = if (orderOverride != null) {
      orderOverride(order)
    } else {
      connectionManager.writeOtaOrder(
        byteArrayOf(
          0x01.toByte(),
          ((bytes.size shr 8) and 0xFF).toByte(),
          (bytes.size and 0xFF).toByte(),
        ),
      )
    }
    if (!orderOk) {
      emit(
        FirmwareOtaProgress(
          FirmwareOtaPhase.FAILED,
          0.3,
          "OTA order 写入失败（7000 特征不可用？）",
        ),
      )
      return@flow
    }

    val total = bytes.size
    var offset = 0
    var index = 0
    while (offset < total) {
      val end = if (offset + chunkSize > total) total else offset + chunkSize
      val chunk = bytes.copyOfRange(offset, end)
      val chunkOverride = writeChunkOverride
      val ok = if (chunkOverride != null) {
        chunkOverride(chunk.map { it.toInt() })
      } else {
        connectionManager.writeOtaFileChunk(chunk)
      }
      if (!ok) {
        emit(
          FirmwareOtaProgress(
            FirmwareOtaPhase.FAILED,
            0.3 + 0.65 * (offset.toDouble() / total.toDouble()),
            "OTA 分片 $index 写入失败",
          ),
        )
        return@flow
      }
      offset = end
      index += 1
      emit(
        FirmwareOtaProgress(
          FirmwareOtaPhase.TRANSFERRING,
          0.3 + 0.65 * (offset.toDouble() / total.toDouble()),
          "已传输 $offset / $total",
        ),
      )
    }

    log.operation("OTA 分片传输完成", detail = "chunks=$index bytes=$total")
    emit(FirmwareOtaProgress(FirmwareOtaPhase.COMPLETED, 1.0, "OTA 传输完成，请等待车辆重启/校验"))
  }

  /**
   * Dart `_download`: production has no real downloader — an override is
   * mandatory, otherwise [UnsupportedOperationException] (Dart
   * `UnsupportedError`) is thrown before any BLE write happens.
   */
  private suspend fun download(url: String): ByteArray {
    val override = downloadOverride
    if (override != null) return override(url)
    throw UnsupportedOperationException("未配置 downloadOverride，拒绝在此环境拉真实固件: $url")
  }
}
