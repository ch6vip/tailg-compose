/**
 * Port of `lib/ble/hex.dart` (tailg-ble-app) → package `com.tailg.plus.data.ble`.
 *
 * Byte-level semantics must match the Dart original exactly — the BLE protocol
 * frames are hex strings at every layer (AES plaintext/ciphertext, tokens,
 * command codes), so hex formatting is part of the wire protocol.
 *
 * - [bytesToHex] renders UPPERCASE contiguous hex (Dart `intToHex2`).
 * - [bytesToSpacedHex] renders lowercase space-separated hex (Dart `intToHex2Lower`).
 * - [hexToBytes] validates even length + `[0-9a-fA-F]` exactly like Dart.
 */
package com.tailg.plus.data.ble

/** Dart `RegExp(r'^[0-9a-fA-F]*$')`. */
private val HEX_PATTERN = Regex("^[0-9a-fA-F]*$")

/** Port of Dart `hexToBytes`: empty → empty array, odd length / bad chars → IllegalArgumentException. */
fun hexToBytes(hex: String): ByteArray {
  if (hex.isEmpty()) return ByteArray(0)
  if (hex.length % 2 != 0) {
    throw IllegalArgumentException("Hex string must have even length, got ${hex.length}")
  }
  if (!HEX_PATTERN.matches(hex)) {
    throw IllegalArgumentException("Hex string contains invalid characters: $hex")
  }
  val bytes = ByteArray(hex.length / 2)
  var i = 0
  while (i < hex.length) {
    bytes[i / 2] = hex.substring(i, i + 2).toInt(16).toByte()
    i += 2
  }
  return bytes
}

/** Port of Dart `bytesToHex`: UPPERCASE contiguous hex. */
fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { intToHex2(it.toInt()) }

/** Port of Dart `bytesToSpacedHex(Iterable<int>)`: lowercase, space separated. */
fun bytesToSpacedHex(bytes: Iterable<Int>): String = bytes.joinToString(" ") { intToHex2Lower(it) }

/** Port of Dart `intToHex4Lower` — NOT masked to 4 digits (Dart pads only). */
fun intToHex4Lower(n: Int): String = n.toString(16).padStart(4, '0')

/** Port of Dart `intToHex2Lower`: lowercase, masked to one byte. */
fun intToHex2Lower(n: Int): String = (n and 0xff).toString(16).padStart(2, '0')

/** Port of Dart `intToHex2`: uppercase, masked to one byte. */
fun intToHex2(n: Int): String = intToHex2Lower(n).uppercase()
