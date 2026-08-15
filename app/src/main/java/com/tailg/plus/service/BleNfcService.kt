/**
 * Port of `lib/services/ble_nfc_service.dart` (tailg-ble-app) → package
 * `com.tailg.plus.service`.
 *
 * P3-6 true NFC path: official BLE `writeData` frames after LOGIN. Frames come
 * from `com.tailg.plus.data.ble.OfficialNfcBleFrames` (port of
 * `lib/ble/nfc_ble_frames.dart`); the actual write goes through
 * [ConnectionManager.writeStandardHex] (official standard-stack path).
 */
package com.tailg.plus.service

import com.tailg.plus.data.ble.OfficialNfcBleFrames
import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.data.ble.platform.ProtocolType
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService

/** P3-6 true NFC path: official BLE writeData frames after LOGIN. */
class BleNfcService(
  val connectionManager: ConnectionManager,
  logService: LogService? = null,
) {
  private val log: LogService = logService ?: LogService()

  /**
   * Dart `canWriteOfficialNfc`: standard-stack LOGIN with a KKS or TLink
   * protocol (official `writeData` path); QGJ is not supported.
   */
  val canWriteOfficialNfc: Boolean
    get() = connectionManager.isProtocolLoggedIn &&
      (connectionManager.protocol == ProtocolType.KKS ||
        connectionManager.protocol == ProtocolType.TLINK)

  /** Dart `addUserKey({keyType, type})`. */
  suspend fun addUserKey(keyType: Int, type: String): Boolean =
    write(
      OfficialNfcBleFrames.addUserKeyHex(keyType = keyType, type = type),
      label = "addUserKey",
    )

  /** Dart `addCard(index)`. */
  suspend fun addCard(index: String): Boolean =
    write(OfficialNfcBleFrames.addCardHex(index), label = "addCard")

  /** Dart `checkNfc(index)`. */
  suspend fun checkNfc(index: String): Boolean =
    write(OfficialNfcBleFrames.checkNfcHex(index), label = "checkNfc")

  /** Dart `delNfc(index)`. */
  suspend fun delNfc(index: String): Boolean =
    write(OfficialNfcBleFrames.delNfcHex(index), label = "delNfc")

  /** Dart `_write(hex, {label})`: gate, write, log the outcome. */
  private suspend fun write(hex: String, label: String): Boolean {
    if (!canWriteOfficialNfc) {
      log.operation(
        "官方 NFC 写入跳过（需 standard LOGIN）",
        detail = label,
        level = LogLevel.WARNING,
      )
      return false
    }
    val ok = connectionManager.writeStandardHex(hex)
    log.operation(
      if (ok) "官方 NFC 指令已发送: $label" else "官方 NFC 指令失败: $label",
      detail = hex,
      level = if (ok) LogLevel.INFO else LogLevel.WARNING,
    )
    return ok
  }
}
