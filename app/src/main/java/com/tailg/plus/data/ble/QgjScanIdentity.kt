/**
 * Port of `lib/ble/qgj_scan_identity.dart` (tailg-ble-app) → package `com.tailg.plus.data.ble`.
 *
 * Mirrors the official `com.kuyi.blesdk.scan.ScannedDevice` manufacturer decoder for
 * ECU devices:
 * - 8-byte manufacturer payload: `bootMode = (byte0 >> 5) & 0x03`, identity MAC = bytes 2..7.
 *   BOOT_MODE_NORMAL=0 / BOOT_MODE_BINDING=1 / BOOT_MODE_OTA=2 (ScannedDevice constants).
 * - 6-byte payload: whole payload is the identity MAC, boot mode 0.
 * - Harmony devices (service-data UUID containing `fdee`, see
 *   `ScannedDevice.i = ParcelUuid.fromString("0000fdee-…")`) carry no identity MAC.
 * - Radio-address fallback (`ScannedDevice.getIdentityMac()`, lines 146–153):
 *   `getAddress().replace(":", "").toUpperCase()`.
 *
 * This file is pure Kotlin byte logic (no Android API): the BLE scan layer (future
 * connection-manager port) feeds [manufacturerPayloads] / service UUIDs into
 * [parseQgjScanIdentity] or [parseQgjManufacturerPayloads].
 */
package com.tailg.plus.data.ble

/** Port of Dart `class QgjScanIdentity`. */
data class QgjScanIdentity(
  val identityMac: String?,
  val bootMode: Int,
  val harmony: Boolean,
  /** When true, [identityMac] came from the radio-address fallback. */
  val fromRadioAddress: Boolean = false,
)

/**
 * Kotlin adaptation of Dart `parseQgjScanIdentity(AdvertisementData)` — replaces the
 * flutter_blue_plus entry point. [serviceUuids] are the advertisement's service-data
 * UUIDs (string form); harmony is detected by a `fdee` substring, like Dart.
 */
fun parseQgjScanIdentity(
  manufacturerPayloads: Iterable<ByteArray>,
  serviceUuids: Iterable<String>,
): QgjScanIdentity {
  val isHarmony = serviceUuids.any { isHarmonyServiceUuid(it) }
  return parseQgjManufacturerPayloads(manufacturerPayloads, harmony = isHarmony)
}

/** Dart `uuid.toString().toLowerCase().contains('fdee')`. */
fun isHarmonyServiceUuid(uuid: String): Boolean = uuid.lowercase().contains("fdee")

/**
 * Port of Dart `parseQgjManufacturerPayloads`.
 * FBP manufacturer values intentionally exclude the two-byte company id.
 */
fun parseQgjManufacturerPayloads(
  payloads: Iterable<ByteArray>,
  harmony: Boolean,
): QgjScanIdentity {
  if (harmony) {
    return QgjScanIdentity(identityMac = null, bootMode = 0, harmony = true)
  }

  for (data in payloads) {
    if (data.size == 8) {
      val bootMode = ((data[0].toInt() and 0xFF) shr 5) and 0x03
      return QgjScanIdentity(
        identityMac = compactMac(data.copyOfRange(2, 8)),
        bootMode = bootMode,
        harmony = false,
      )
    }
    if (data.size == 6) {
      return QgjScanIdentity(
        identityMac = compactMac(data),
        bootMode = 0,
        harmony = false,
      )
    }
  }
  return QgjScanIdentity(identityMac = null, bootMode = 0, harmony = false)
}

/**
 * Port of Dart `identityWithRadioFallback` — official `ScannedDevice.getIdentityMac()`
 * fallback: when manufacturer data has no identity MAC, use the BLE radio address
 * (colons stripped, uppercase).
 */
fun identityWithRadioFallback(
  parsed: QgjScanIdentity,
  radioAddress: String,
): QgjScanIdentity {
  if (!parsed.identityMac.isNullOrEmpty()) {
    return parsed
  }
  val compact = radioAddress.replace(Regex("[^0-9a-fA-F]"), "").uppercase()
  if (compact.isEmpty()) return parsed
  return QgjScanIdentity(
    identityMac = compact,
    bootMode = parsed.bootMode,
    harmony = parsed.harmony,
    fromRadioAddress = true,
  )
}

/** Dart `_compactMac`: lowercase 2-hex per byte, joined, uppercased. */
private fun compactMac(bytes: ByteArray): String =
  bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }.uppercase()
