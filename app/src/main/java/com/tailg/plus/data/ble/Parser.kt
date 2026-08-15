/**
 * Port of `lib/ble/parser.dart` (tailg-ble-app) → package `com.tailg.plus.data.ble`.
 *
 * Standard-stack notification parser: AES-ECB-decrypt the raw GATT value, then match
 * the decrypted hex against the official reply prefixes:
 * - `TOKEN_REPLY_PREFIX = "78000000"` (TailgBleCmd) → 4-byte token at hex [8,16)
 * - `PACKAGE_REPLY_PREFIX = "780EB310"` (TailgBleCmd) → voltage = BE16 / 100 (raw 16 bytes)
 * - command/state replies: control code at hex [6,10) = commandType(2) + statusCode(2)
 *
 * Garbled frames must never throw out of the notification listener: [parseResponse]
 * mirrors Dart by catching `NumberFormatException` (FormatException),
 * `IndexOutOfBoundsException` (RangeError) and `IllegalArgumentException`
 * (ArgumentError) and returning [UnknownResponse] with the ENCRYPTED bytes hex.
 * (NumberFormatException extends IllegalArgumentException, so it is caught first.)
 */
package com.tailg.plus.data.ble

/** Dart `_tokenPrefix`. */
private const val TOKEN_PREFIX = "78000000"

/** Dart `_voltagePrefix`. */
private const val VOLTAGE_PREFIX = "780EB310"

/** Port of Dart `sealed class ParsedResponse`. */
sealed class ParsedResponse(val raw: String)

// Note: subclasses are plain classes (not data classes) — a data class would require
// every primary-constructor parameter to be `val`/`var`, and Dart's response types are
// identity-based (no value equality), so plain classes are the faithful mapping.

/** Port of Dart `TokenResponse`. */
class TokenResponse(raw: String, val token: String) : ParsedResponse(raw)

/** Port of Dart `VoltageResponse`. */
class VoltageResponse(raw: String, val voltage: Double) : ParsedResponse(raw)

/** Port of Dart `StateResponse`. */
class StateResponse(
  raw: String,
  val success: Boolean,
  val bikeState: BikeState? = null,
) : ParsedResponse(raw)

/** Port of Dart `CommandResponse`. */
class CommandResponse(
  raw: String,
  val commandType: String,
  val statusCode: String,
  val success: Boolean,
) : ParsedResponse(raw)

/** Port of Dart `UnknownResponse`. */
class UnknownResponse(raw: String) : ParsedResponse(raw)

/**
 * Port of Dart `parseResponse` — never throws; malformed frames become [UnknownResponse].
 */
fun parseResponse(keyHex: String, raw: ByteArray): ParsedResponse {
  return try {
    parseResponseInner(keyHex, raw)
  } catch (e: NumberFormatException) {
    // Malformed/garbled frames (wrong length, undecryptable, too short to slice)
    // must never throw out of the notification listener and crash the app.
    UnknownResponse(bytesToHex(raw))
  } catch (e: IndexOutOfBoundsException) {
    UnknownResponse(bytesToHex(raw))
  } catch (e: IllegalArgumentException) {
    // aesEcbDecrypt throws IllegalArgumentException for empty or non-block-aligned data.
    UnknownResponse(bytesToHex(raw))
  }
}

/** Port of Dart `_parseResponse`. */
private fun parseResponseInner(keyHex: String, raw: ByteArray): ParsedResponse {
  val hex = aesEcbDecrypt(keyHex, raw)

  if (hex.length < 10) {
    return UnknownResponse(hex)
  }

  if (hex.startsWith(TOKEN_PREFIX)) {
    // Token frame is at least 8 bytes (16 hex chars): 4-byte prefix + 4-byte
    // token. Reject short frames explicitly instead of relying on the outer
    // try/catch to swallow the RangeError from substring.
    if (hex.length < 16) {
      return UnknownResponse(hex)
    }
    val token = hex.substring(8, 16)
    return TokenResponse(hex, token)
  }

  if (hex.startsWith(VOLTAGE_PREFIX) && raw.size == 16) {
    val highByte = hex.substring(8, 10).toInt(16)
    val lowByte = hex.substring(10, 12).toInt(16)
    val voltage = ((highByte shl 8) or lowByte) / 100.0
    return VoltageResponse(hex, voltage)
  }

  // Validate frame starts with expected header before parsing as command response.
  if (!hex.startsWith("78")) {
    return UnknownResponse(hex)
  }

  val controlCode = hex.substring(6, 10)
  val commandType = controlCode.substring(0, 2)
  val statusCode = controlCode.substring(2, 4)

  if (commandType == "0C") {
    if (statusCode == "FF") {
      return StateResponse(hex, success = false)
    }
    val stateNum = statusCode.toInt(16)
    return StateResponse(
      hex,
      success = true,
      bikeState = BikeState(
        isLocked = stateNum == 1,
        isPowerOn = stateNum == 3 || stateNum == 4,
      ),
    )
  }

  return CommandResponse(
    hex,
    commandType = commandType,
    statusCode = statusCode,
    success = statusCode != "FF",
  )
}
