/**
 * Port of `lib/ble/tlink_protocol.dart` (tailg-ble-app) → package `com.tailg.plus.data.ble`.
 *
 * TLink (8500-series, standard stack) token/login/command/induction frames.
 * All plaintexts are AES-128-ECB/NoPadding blocks: command plaintexts are 24 hex
 * chars (12 bytes) + the 4-byte session token = one 16-byte block.
 *
 * Evidence — official `com.tailg.run.intelligence.tlink_ble.TLinkBleManager` +
 * `TailgBleConfig`:
 * - token request: `writeData("850000002EC97FA3518DBFE04A6F5B12")` (TLinkBleManager line 1368;
 *   `TailgBleUtils.HEADER_SEND_TOKEN_BODY = "2EC97FA3518DBFE04A6F5B12"`)
 * - login: `"850A4A11" + %08x(password) + %08x(uid) + token` (`BaseOtaActivity` line 360;
 *   `HEADER_SEND_LOGIN = "850A4A11"`)
 * - six-key commands: `85034A20/21/22/23/24/25` + `00123456789ABCDE` + token
 *   (`HEADER_SEND_LOCK/UNLOCK/START/STOP/CHAIR/FIND`, TLinkBleManager lines 2029–2069)
 * - induction open/close/check/distance: `85054A3302010056789ABCDE`,
 *   `85054A3302020056789ABCDE`, `85034A3301123456789ABCDE`,
 *   `85044A3303<level>3456789ABCDE` (TLinkBleManager lines 1883–1911)
 * - HID open after bond: `85044A3402003456789ABCDE` (line 1939)
 * - receive headers: `HEADER_RECEIVE_TOKEN="85000000"`, `HEADER_RECEIVE_LOGIN="8503B511"`,
 *   `HEADER_RECEIVE_INDUCTION_STATUS="8506B53301"`,
 *   `HEADER_RECEIVE_SET_INDUCTION_STATUS="8504B53302"`,
 *   `HEADER_RECEIVE_PROXIMITYDISTANCE_SET="8504B53303"`.
 */
package com.tailg.plus.data.ble

/** Dart `_tlinkTokenPlaintext` — 16 bytes. */
private const val TLINK_TOKEN_PLAINTEXT = "850000002EC97FA3518DBFE04A6F5B12"

/** Port of Dart `buildTLinkTokenRequest`. */
fun buildTLinkTokenRequest(keyHex: String): ByteArray = aesEcbEncrypt(keyHex, TLINK_TOKEN_PLAINTEXT)

/**
 * Port of Dart `buildTLinkLoginFrame`.
 * Plaintext: `850A4A11` + password (BE32 hex) + userId (BE32 hex) + token (16 bytes total).
 */
fun buildTLinkLoginFrame(
  keyHex: String,
  password: Int,
  userId: Int,
  token: String,
): ByteArray {
  val frame = "850A4A11" + uint32Hex(password) + uint32Hex(userId) + token
  return aesEcbEncrypt(keyHex, frame)
}

/**
 * Port of Dart `buildTLinkCommand`.
 * Plaintext: `85034Axx` + `00123456789ABCDE` + token (16 bytes).
 */
fun buildTLinkCommand(
  keyHex: String,
  command: CommandCode,
  token: String,
): ByteArray {
  val header = when (command) {
    CommandCode.lock -> "85034A20"
    CommandCode.unlock -> "85034A21"
    CommandCode.powerOn -> "85034A22"
    CommandCode.powerOff -> "85034A23"
    CommandCode.openSeat -> "85034A24"
    CommandCode.find -> "85034A25"
    else -> throw IllegalArgumentException("Unsupported TLink command: ${command.name}")
  }
  // Official writeData fills the command to one 16-byte block before adding
  // the 4-byte token: `85034Axx00 123456789ABCDE` + token.
  return aesEcbEncrypt(keyHex, "$header" + "00123456789ABCDE$token")
}

// ---------------------------------------------------------------------------
// Induction / proximity mode (official TLinkBleManager openMode/closeMode)
//
// All plaintexts are 24 hex chars (12 bytes). The connection manager appends
// the 4-byte session token → 16-byte AES block, matching official
// writeData("8505…ABCDE") after LOGIN.
// ---------------------------------------------------------------------------

/** Query induction switch + distance: `checkMode()`. */
const val TLINK_INDUCTION_CHECK_PLAIN = "85034A3301123456789ABCDE"

/** Open induction: `openMode()` → ECU then system BLE bond. */
const val TLINK_INDUCTION_OPEN_PLAIN = "85054A3302010056789ABCDE"

/** Close induction: `closeMode()` → ECU then remove bond. */
const val TLINK_INDUCTION_CLOSE_PLAIN = "85054A3302020056789ABCDE"

/** After bond success official also writes HID open (`pairingDevice` BOND_BONDED). */
const val TLINK_HID_OPEN_AFTER_BOND_PLAIN = "85044A3402003456789ABCDE"

/**
 * Port of Dart `buildTLinkInductionDistancePlain` — set proximity distance level
 * (official `setModeDistance`, 1–30; clamped to 0..30).
 */
fun buildTLinkInductionDistancePlain(progress: Int): String {
  val level = progress.coerceIn(0, 30)
  val hex = level.toString(16).padStart(2, '0').uppercase()
  return "85044A3303${hex}3456789ABCDE"
}

/** Port of Dart `sealed class TLinkResponse`. */
sealed class TLinkResponse(val raw: String)

// Note: subclasses are plain classes (not data classes) — a data class would require
// every primary-constructor parameter to be `val`/`var`, and Dart's response types are
// identity-based (no value equality), so plain classes are the faithful mapping.

/** Port of Dart `TLinkTokenResponse`. */
class TLinkTokenResponse(raw: String, val token: String) : TLinkResponse(raw)

/** Port of Dart `TLinkLoginResponse`. */
class TLinkLoginResponse(raw: String, val success: Boolean) : TLinkResponse(raw)

/** Port of Dart `TLinkCommandResponse`. */
class TLinkCommandResponse(
  raw: String,
  val commandType: String,
  val statusCode: String,
  val success: Boolean,
) : TLinkResponse(raw)

/**
 * Port of Dart `TLinkInductionStatusResponse` — `HEADER_RECEIVE_INDUCTION_STATUS` =
 * `8506B53301`; switch @ [10,12): `02` = closed else open; distance @ [12,14) hex 1–30.
 */
class TLinkInductionStatusResponse(
  raw: String,
  val enabled: Boolean,
  val distance: Int?,
) : TLinkResponse(raw)

/** Port of Dart `TLinkInductionSetResponse` — `HEADER_RECEIVE_SET_INDUCTION_STATUS` = `8504B53302`. */
class TLinkInductionSetResponse(raw: String, val success: Boolean) : TLinkResponse(raw)

/** Port of Dart `TLinkProximityDistanceSetResponse` — `HEADER_RECEIVE_PROXIMITYDISTANCE_SET` = `8504B53303`. */
class TLinkProximityDistanceSetResponse(raw: String, val success: Boolean) : TLinkResponse(raw)

/** Port of Dart `TLinkUnknownResponse`. */
class TLinkUnknownResponse(raw: String) : TLinkResponse(raw)

/**
 * Port of Dart `parseTLinkResponse`.
 *
 * Exception mapping: Dart `FormatException` → Kotlin `NumberFormatException`,
 * `RangeError` → `IndexOutOfBoundsException`, `ArgumentError` → `IllegalArgumentException`
 * (NumberFormatException is a subclass of IllegalArgumentException, so it is caught first).
 * Malformed frames return [TLinkUnknownResponse] with the ENCRYPTED bytes hex,
 * exactly like Dart.
 */
fun parseTLinkResponse(keyHex: String, encrypted: ByteArray): TLinkResponse {
  return try {
    val hex = aesEcbDecrypt(keyHex, encrypted)
    if (hex.startsWith("85000000") && hex.length >= 16) {
      return TLinkTokenResponse(hex, hex.substring(8, 16))
    }
    if (hex.startsWith("8503B511") && hex.length >= 10) {
      return TLinkLoginResponse(hex, hex.substring(8, 10) == "01")
    }
    // Induction status query reply (official HEADER_RECEIVE_INDUCTION_STATUS).
    if (hex.startsWith("8506B53301") && hex.length >= 14) {
      val switchByte = hex.substring(10, 12).uppercase()
      val distByte = hex.substring(12, 14)
      val enabled = switchByte != "02"
      val dist = distByte.toIntOrNull(16)
      val distance = if (dist != null && dist > 0 && dist < 31) dist else null
      return TLinkInductionStatusResponse(
        hex,
        enabled = enabled,
        distance = distance,
      )
    }
    // Induction open/close set reply.
    if (hex.startsWith("8504B53302") && hex.length >= 12) {
      return TLinkInductionSetResponse(
        hex,
        success = hex.substring(10, 12) == "01",
      )
    }
    // Proximity distance set reply.
    if (hex.startsWith("8504B53303") && hex.length >= 12) {
      return TLinkProximityDistanceSetResponse(
        hex,
        success = hex.substring(10, 12) == "01",
      )
    }
    if (hex.startsWith("8503B5") && hex.length >= 10) {
      val commandType = hex.substring(6, 8)
      val statusCode = hex.substring(8, 10)
      return TLinkCommandResponse(
        hex,
        commandType = commandType,
        statusCode = statusCode,
        success = statusCode == "01",
      )
    }
    TLinkUnknownResponse(hex)
  } catch (e: NumberFormatException) {
    TLinkUnknownResponse(bytesToHex(encrypted))
  } catch (e: IndexOutOfBoundsException) {
    TLinkUnknownResponse(bytesToHex(encrypted))
  } catch (e: IllegalArgumentException) {
    TLinkUnknownResponse(bytesToHex(encrypted))
  }
}

/** Dart `_uint32Hex(int value)`: `(value & 0xFFFFFFFF)` as 8 uppercase hex chars. */
private fun uint32Hex(value: Int): String =
  (value.toLong() and 0xFFFFFFFFL).toString(16).padStart(8, '0').uppercase()
