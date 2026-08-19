/**
 * Edge-coverage tests for `com.tailg.plus.data.ble` that complement the
 * primary Dart-parity suite (`BleProtocolPortTest.kt`). Targets the frame
 * builders and validators that were not exercised there:
 *
 * - `protocol.dart`:  `buildTokenRequest`, `buildCommand` (all 8 codes),
 *   `buildCommandWithParam`, `buildCommand3Params`
 * - `nfc_ble_frames.dart`: `OfficialNfcBleFrames`
 * - `constants.dart`: QGJ fcc1 riding-mode read/patch helpers
 * - `aes.dart` / `hex.dart`: input validation boundaries
 *
 * These are pure-JVM tests using the JDK AES provider, identical to the main
 * port suite. Run with `./gradlew :app:testDebugUnitTest`.
 */
package com.tailg.plus.data.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BleProtocolEdgeTest {

  private val key = "00112233445566778899aabbccddeeff"
  private val token = "A1B2C3D4"

  /** Encrypt then decrypt — proves the plaintext builder matches the wire layout. */
  private fun framePlaintext(bytes: ByteArray): String = aesEcbDecrypt(key, bytes)

  // --- protocol.dart --------------------------------------------------------

  @Test
  fun tokenRequestUsesOfficialPlaintext() {
    assertEquals(
      "780000002D1A683D48271A18316E471A",
      framePlaintext(buildTokenRequest(key)),
    )
  }

  @Test
  fun standardCommandBuildersProduceExpectedLayouts() {
    // buildCommand: `7803C2<cmd>0011111111111111<token>`
    assertEquals(
      "7803C2010011111111111111A1B2C3D4",
      framePlaintext(buildCommand(key, CommandCode.lock, token)),
    )
    assertEquals(
      "7803C2020011111111111111A1B2C3D4",
      framePlaintext(buildCommand(key, CommandCode.unlock, token)),
    )
    assertEquals(
      "7803C2050011111111111111A1B2C3D4",
      framePlaintext(buildCommand(key, CommandCode.openSeat, token)),
    )
    assertEquals(
      "7803C2060011111111111111A1B2C3D4",
      framePlaintext(buildCommand(key, CommandCode.powerOn, token)),
    )
    assertEquals(
      "7803C2070011111111111111A1B2C3D4",
      framePlaintext(buildCommand(key, CommandCode.powerOff, token)),
    )
    assertEquals(
      "7803C2080011111111111111A1B2C3D4",
      framePlaintext(buildCommand(key, CommandCode.find, token)),
    )
    assertEquals(
      "7803C20D0011111111111111A1B2C3D4",
      framePlaintext(buildCommand(key, CommandCode.readState, token)),
    )
    assertEquals(
      "7803C20E0011111111111111A1B2C3D4",
      framePlaintext(buildCommand(key, CommandCode.readAntiTheft, token)),
    )
  }

  @Test
  fun commandWithParamBuildsOfficialLayout() {
    // `7803C2<cmd><param>` + 14×'1' + token.
    val expected = "7803C20601" + "1".repeat(14) + token
    assertEquals(expected, framePlaintext(buildCommandWithParam(key, CommandCode.powerOn, "01", token)))
  }

  @Test
  fun command3ParamsBuildsOfficialLayout() {
    // `7805C2<cmd><p1><p2><p3>` + 10×'1' + token. cmd.code is included once.
    val expected = "7805C201" + "010203" + "1".repeat(10) + token
    assertEquals(expected, framePlaintext(buildCommand3Params(key, CommandCode.lock, "01", "02", "03", token)))
  }

  // --- nfc_ble_frames.dart ---------------------------------------------------

  @Test
  fun addUserKeyHexEncodesPhoneAndBleTails() {
    assertEquals("85094A4105010103842000DE", OfficialNfcBleFrames.addUserKeyHex(1, "1"))
    assertEquals("85094A4105000103842000DE", OfficialNfcBleFrames.addUserKeyHex(1, "0"))
    assertEquals("85064A4109011003789ABCDE", OfficialNfcBleFrames.addUserKeyHex(2, "1"))
    assertEquals("85064A4109001003789ABCDE", OfficialNfcBleFrames.addUserKeyHex(2, "0"))
  }

  @Test
  fun nfcFramesMatchOfficialHeadersAndFillers() {
    assertEquals("85044A32010A3456789ABCDE", OfficialNfcBleFrames.checkNfcHex("0A"))
    assertEquals("85054A32050201000000000000", OfficialNfcBleFrames.delNfcHex("01"))
    assertEquals("85054A32020202000000000000", OfficialNfcBleFrames.addCardHex("02"))
    assertArrayEquals(
      hexToBytes("85044A32010A3456789ABCDE"),
      OfficialNfcBleFrames.toBytes("85044A32010A3456789ABCDE"),
    )
  }

  // --- constants.dart: QGJ riding-mode read/patch ----------------------------

  @Test
  fun extractFcc1StatusBytesHandlesBothLayouts() {
    // Long form: size >= 11, status at offsets 8..10.
    val long = List(11) { 0 }.toMutableList().apply { this[8] = 3; this[9] = 5; this[10] = 7 }
    assertEquals(listOf(3, 5, 7), extractFcc1StatusBytes(long))

    // Short form: size >= 7, header 00 07, status at offsets 4..6.
    val short = listOf(0x00, 0x07, 0x00, 0x02, 1, 2, 3)
    assertEquals(listOf(1, 2, 3), extractFcc1StatusBytes(short))

    // Too short / wrong header → null.
    assertNull(extractFcc1StatusBytes(listOf(0, 7, 0, 2, 1)))
    assertNull(extractFcc1StatusBytes(listOf(0x01, 0x07, 0x00, 0x02, 1, 2, 3)))
  }

  @Test
  fun parseQgjRidingModeReadsPodgValueFromStatusByte() {
    // Short-form state: data[5] holds the patched state byte; low 3 bits = podg.
    assertEquals(
      RidingMode.standard,
      parseQgjRidingMode(listOf(0x00, 0x07, 0x00, 0x02, 0x00, 0x02, 0x00)),
    )
    assertEquals(
      RidingMode.sport,
      parseQgjRidingMode(listOf(0x00, 0x07, 0x00, 0x02, 0x00, 0x03, 0x00)),
    )
    assertEquals(
      RidingMode.eco,
      parseQgjRidingMode(listOf(0x00, 0x07, 0x00, 0x02, 0x00, 0x01, 0x00)),
    )
  }

  @Test
  fun buildQgjRidingModeFramePatchesOnlyPodgBits() {
    // Current standard (podg=2), switch to sport (podg=3). state2 = (2 & 0xF8) | 3 = 3.
    val patched = buildQgjRidingModeFrame(
      readback = listOf(0x00, 0x07, 0x00, 0x02, 0x01, 0x02, 0x03),
      mode = RidingMode.sport,
    )
    assertEquals(listOf(0x00, 0x07, 0x00, 0x02, 0x01, 0x03, 0x03), patched)

    // Round-trip: patched frame re-reads as the requested mode.
    assertEquals(RidingMode.sport, parseQgjRidingMode(patched!!))

    // Non-fcc1 readback → null.
    assertNull(buildQgjRidingModeFrame(listOf(1, 2, 3), RidingMode.eco))
  }

  @Test
  fun qgjControlOpCodesMapAllSixCommands() {
    assertEquals(0x02, QgjControlOpCodes.byCommandCode[CommandCode.lock.code])
    assertEquals(0x01, QgjControlOpCodes.byCommandCode[CommandCode.unlock.code])
    assertEquals(0x07, QgjControlOpCodes.byCommandCode[CommandCode.openSeat.code])
    assertEquals(0x03, QgjControlOpCodes.byCommandCode[CommandCode.powerOn.code])
    assertEquals(0x04, QgjControlOpCodes.byCommandCode[CommandCode.powerOff.code])
    assertEquals(0x08, QgjControlOpCodes.byCommandCode[CommandCode.find.code])
    assertNull(QgjControlOpCodes.byCommandCode[CommandCode.readState.code])
  }

  @Test
  fun qgjControlFrameUsesOfficialOpCodes() {
    // setStatus (0x1002), 1-byte op payload → length=3 → two-byte BE length `00 03`.
    // Frame: A7 00 00 03 10 02 <op>.
    assertArrayEquals(hexToBytes("A7000003100201"), buildQgjControlFrame(CommandCode.unlock))
    assertArrayEquals(hexToBytes("A7000003100207"), buildQgjControlFrame(CommandCode.openSeat))
    assertArrayEquals(hexToBytes("A7000003100208"), buildQgjControlFrame(CommandCode.find))
    // readState is not a QGJ control op → null.
    assertNull(buildQgjControlFrame(CommandCode.readState))
  }

  // --- aes.dart: validation boundaries --------------------------------------

  @Test
  fun aesEncryptRejectsBadInputs() {
    // Wrong key length.
    assertThrowsIllegalArgument { aesEcbEncrypt("00", "11111111111111112222222222222222") }
    // Empty data.
    assertThrowsIllegalArgument { aesEcbEncrypt(key, "") }
    // Non-block-aligned data.
    assertThrowsIllegalArgument { aesEcbEncrypt(key, "1111111111111111") }
  }

  @Test
  fun aesDecryptRejectsBadInputs() {
    assertThrowsIllegalArgument { aesEcbDecrypt("00", ByteArray(16)) }
    assertThrowsIllegalArgument { aesEcbDecrypt(key, ByteArray(0)) }
    // 15 bytes is not a multiple of 16.
    assertThrowsIllegalArgument { aesEcbDecrypt(key, ByteArray(15)) }
  }

  // --- hex.dart: validation boundaries --------------------------------------

  @Test
  fun hexToBytesValidatesEvenLengthAndChars() {
    assertThrowsIllegalArgument { hexToBytes("ABC") }
    assertThrowsIllegalArgument { hexToBytes("0G") }
    assertThrowsIllegalArgument { hexToBytes("zz") }
    // Empty is fine.
    assertArrayEquals(ByteArray(0), hexToBytes(""))
  }

  @Test
  fun intToHexLowerFormsIgnoreLowByteFor2Digit() {
    assertEquals("ff", intToHex2Lower(0x1ff))
    assertEquals("FF", intToHex2(0x1ff))
    assertEquals("0abc", intToHex4Lower(0xabc))
    assertEquals("12345", intToHex4Lower(0x12345))
  }

  @Test
  fun bytesToSpacedHexRendersLowercasePadding() {
    assertEquals("00 0a ff 23", bytesToSpacedHex(listOf(0, 10, 255, 0x123)))
    assertEquals("000AFF", bytesToHex(byteArrayOf(0, 10, -1)))
  }

  // ---------------------------------------------------------------------------

  private fun assertThrowsIllegalArgument(block: () -> Any?) {
    try {
      block()
      throw AssertionError("Expected IllegalArgumentException")
    } catch (expected: IllegalArgumentException) {
      // Expected — the frame validators must never leak other throwables.
    }
  }
}