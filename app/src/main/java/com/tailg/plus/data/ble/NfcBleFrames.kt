/**
 * Port of `lib/ble/nfc_ble_frames.dart` (tailg-ble-app) → package `com.tailg.plus.data.ble`.
 *
 * Official NFC/key frames from `TailgBleConfig` (standard stack writeData path).
 *
 * Evidence — `com.tailg.run.intelligence.tlink_ble.TailgBleConfig`:
 *   HEADER_SEND_ADD_USER_KEY="85094A4105" · HEADER_SEND_ADD_USER_KEY_BLE="85064A4109" ·
 *   HEADER_SEND_NFC_ADD_MODE="85054A320202" · HEADER_SEND_NFC_CHECK="85044A3201" ·
 *   HEADER_SEND_NFC_DEL="85054A320502" · HEADER_SEND_NFC_FAC_SET="85054A320412"
 * and `TLinkBleManager` addUserKey tails:
 *   "010103842000DE"/"000103842000DE" (phone key) · "011003789ABCDE"/"001003789ABCDE" (BLE key).
 * `HEADER_SEND_CUSHION_SET_BODY` (TailgBleUtils) is the "000000000000" filler.
 */
package com.tailg.plus.data.ble

/** Official NFC/key frames from `TailgBleConfig` (standard stack writeData path). */
object OfficialNfcBleFrames {
  const val headerAddUserKey = "85094A4105"
  const val headerAddUserKeyBle = "85064A4109"
  const val headerNfcAddMode = "85054A320202"
  const val headerNfcCheck = "85044A3201"
  const val headerNfcDel = "85054A320502"
  const val headerNfcFacSet = "85054A320412"
  /** TailgBleUtils.HEADER_SEND_CUSHION_SET_BODY fallback. */
  const val cushionSetBody = "000000000000"

  /**
   * Phone/card key add (keyType 1 = phone, 2 = BLE key).
   * Port of Dart `addUserKeyHex`.
   */
  fun addUserKeyHex(keyType: Int, type: String): String {
    if (keyType == 1) {
      val tail = if (type == "1") "010103842000DE" else "000103842000DE"
      return "$headerAddUserKey$tail"
    }
    val tail = if (type == "1") "011003789ABCDE" else "001003789ABCDE"
    return "$headerAddUserKeyBle$tail"
  }

  /** Port of Dart `checkNfcHex`. */
  fun checkNfcHex(index: String): String =
    "$headerNfcCheck$index" + "3456789ABCDE"

  /** Port of Dart `delNfcHex`. */
  fun delNfcHex(index: String): String = "$headerNfcDel$index$cushionSetBody"

  /** Port of Dart `addCardHex`. */
  fun addCardHex(index: String): String =
    "$headerNfcAddMode$index$cushionSetBody"

  /** Port of Dart `toBytes`. */
  fun toBytes(hex: String): ByteArray = hexToBytes(hex)
}
