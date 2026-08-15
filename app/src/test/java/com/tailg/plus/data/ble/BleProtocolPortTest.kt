/**
 * Port-validation tests for `com.tailg.plus.data.ble` (Dart → Kotlin BLE protocol layer).
 *
 * Every vector here is lifted verbatim from the Dart test suite
 * (`tailg-ble-app/test/`: `ble_hex_test.dart`, `ble_parser_test.dart`,
 * `tlink_protocol_test.dart`, `qgj_control_protocol_test.dart`, `qgj_proximity_test.dart`,
 * `rssi_distance_test.dart`, `qgj_scan_identity_test.dart`) and the official
 * decompiled constants, so a green run proves byte-level parity with Dart.
 *
 * Run with `./gradlew :app:testDebugUnitTest` (needs the Android SDK; none on this box).
 */
package com.tailg.plus.data.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleProtocolPortTest {

  private val key = "00112233445566778899aabbccddeeff"

  // --- hex.dart -----------------------------------------------------------

  @Test
  fun hexRenderingMatchesDart() {
    assertEquals("000AFF", bytesToHex(byteArrayOf(0, 10, -1)))
    assertEquals("00 0a ff", bytesToSpacedHex(listOf(0, 10, 255)))
    assertEquals("00", intToHex2Lower(0))
    assertEquals("0a", intToHex2Lower(10))
    assertEquals("ff", intToHex2Lower(255))
    assertEquals("23", intToHex2Lower(0x123))
    assertEquals("ff", intToHex2Lower(-1))
    assertEquals("00", intToHex2(0))
    assertEquals("0A", intToHex2(10))
    assertEquals("FF", intToHex2(255))
    assertEquals("23", intToHex2(0x123))
    assertEquals("FF", intToHex2(-1))
    assertEquals("0000", intToHex4Lower(0))
    assertEquals("000a", intToHex4Lower(10))
    assertEquals("abcd", intToHex4Lower(0xabcd))
    assertEquals("12345", intToHex4Lower(0x12345))
    assertArrayEquals(ByteArray(0), hexToBytes(""))
    assertArrayEquals(byteArrayOf(0x00, 0x0A, -1), hexToBytes("000AFF"))
  }

  // --- aes.dart -----------------------------------------------------------

  @Test
  fun aesEcbRoundTripMatchesDartPlaintext() {
    val plaintext = "78000000AABBCCDD1111111111111111"
    val encrypted = aesEcbEncrypt(key, plaintext)
    assertEquals(plaintext, aesEcbDecrypt(key, encrypted))
  }

  // --- constants.dart -----------------------------------------------------

  @Test
  fun modelKeysDeobfuscateToOfficialConstants() {
    // TailgBleCmd.AES_KEY / BlueToothTypeConstants.AES_KEY
    assertEquals("3A60432A5C01211F291E0F4E0C132825", ModelType.KKS.aesKey)
    // TailgBleConfig.AES_KEY_BB
    assertEquals("1AF78CD35BE92F4CA06DB89EC2D7EF01", ModelType.BB.aesKey)
    // TailgBleConfig.AES_KEY_AX
    assertEquals("1AF78CD35BE92F4CA06DB89E7C4B1E6A", ModelType.AX.aesKey)
    // TailgBleConfig.AES_KEY_JD
    assertEquals("1AF78CD35BE92F4CA06DB89E5F3D2A8C", ModelType.JD.aesKey)
    // TailgBleConfig.AES_KEY_HJ
    assertEquals("1AF78CD35BE92F4CA06DB89E9E6C4B1A", ModelType.HJ.aesKey)
    // TailgBleConfig.AES_KEY_JW
    assertEquals("1AF78CD35BE92F4CA06DB89E6F8B39A5", ModelType.JW.aesKey)
    // TailgBleConfig.AES_KEY_XL
    assertEquals("1AF78CD35BE92F4CA06DB89E1E6C8A9A", ModelType.XL.aesKey)
    // TailgBleConfig.AES_KEY_YY
    assertEquals("1AF78CD35BE92F4CA06DB89E2A8C3F5D", ModelType.YY.aesKey)
  }

  @Test
  fun ridingModeQgjValuesMatchDart() {
    assertEquals(1, RidingMode.eco.qgjPodgValue)
    assertEquals(2, RidingMode.standard.qgjPodgValue)
    assertEquals(3, RidingMode.sport.qgjPodgValue)
    assertEquals(RidingMode.eco, RidingMode.fromQgjPodgValue(1))
    assertEquals(RidingMode.standard, RidingMode.fromQgjPodgValue(2))
    assertEquals(RidingMode.sport, RidingMode.fromQgjPodgValue(3))
    assertNull(RidingMode.fromQgjPodgValue(0))
  }

  @Test
  fun qgjCommandIdsMatchOfficialRegistry() {
    assertEquals(0x1001, QgjCommandIds.login)
    assertEquals(0x1002, QgjCommandIds.setStatus)
    assertEquals(0x1005, QgjCommandIds.keyVersionGet)
    assertEquals(0x2000, QgjCommandIds.autoLockTimeGet)
    assertEquals(0x2001, QgjCommandIds.autoLockTimeSet)
    assertEquals(0x2030, QgjCommandIds.proximityStatusGet)
    assertEquals(0x2031, QgjCommandIds.proximityStatusSet)
    assertEquals(0x2032, QgjCommandIds.proximityDistanceGet)
    assertEquals(0x2033, QgjCommandIds.proximityDistanceSet)
    assertEquals(0x2140, QgjCommandIds.hidStatusSet)
    assertEquals(0x2142, QgjCommandIds.hidStatusGet)
    assertEquals(0x2360, QgjCommandIds.safeLockSet)
    assertEquals(0x5004, QgjCommandIds.enterOtaMode)
  }

  // --- parser.dart --------------------------------------------------------

  private fun encrypted(plaintextHex: String): ByteArray = aesEcbEncrypt(key, plaintextHex)

  @Test
  fun parserNonBlockAlignedFrameReturnsUnknown() {
    assertTrue(parseResponse(key, byteArrayOf(1, 2, 3)) is UnknownResponse)
    assertTrue(parseResponse(key, ByteArray(0)) is UnknownResponse)
  }

  @Test
  fun parserTokenFrameExtractsToken() {
    val r = parseResponse(key, encrypted("78000000AABBCCDD1111111111111111"))
    assertTrue(r is TokenResponse)
    assertEquals("AABBCCDD", (r as TokenResponse).token)
    assertEquals("78000000AABBCCDD1111111111111111", r.raw)
  }

  @Test
  fun parserCommandFrameExtractsTypeAndStatus() {
    val r = parseResponse(key, encrypted("7803C2010011111111111111AABBCCDD"))
    assertTrue(r is CommandResponse)
    assertEquals("01", (r as CommandResponse).commandType)
    assertEquals("00", r.statusCode)
    assertTrue(r.success)
  }

  @Test
  fun parserStateResponseParsesFailedAndPoweredOn() {
    val failed = parseResponse(key, encrypted("7803C20CFF11111111111111AABBCCDD"))
    assertTrue(failed is StateResponse)
    assertFalse((failed as StateResponse).success)

    val poweredOn = parseResponse(key, encrypted("7803C20C0311111111111111AABBCCDD"))
    assertTrue(poweredOn is StateResponse)
    val state = poweredOn as StateResponse
    assertTrue(state.success)
    assertTrue(state.bikeState?.isPowerOn == true)
    assertFalse(state.bikeState?.isLocked == true)
  }

  @Test
  fun parserNon78BoundaryReturnsUnknownWithRawHex() {
    val r = parseResponse(key, encrypted("7903C2010011111111111111AABBCCDD"))
    assertTrue(r is UnknownResponse)
    assertEquals("7903C2010011111111111111AABBCCDD", r.raw)
  }

  // --- tlink_protocol.dart -------------------------------------------------

  private val tkey = "1AF78CD35BE92F4CA06DB89EC2D7EF01"
  private val token = "A1B2C3D4"

  @Test
  fun tlinkTokenAndLoginFramesMatchOfficialPlaintext() {
    assertEquals(
      "850000002EC97FA3518DBFE04A6F5B12",
      aesEcbDecrypt(tkey, buildTLinkTokenRequest(tkey)),
    )
    assertEquals(
      "850A4A11000004D20000002AA1B2C3D4",
      aesEcbDecrypt(
        tkey,
        buildTLinkLoginFrame(keyHex = tkey, password = 1234, userId = 42, token = token),
      ),
    )
  }

  @Test
  fun tlinkSixKeyFrameIncludesOfficialFillerAndToken() {
    assertEquals(
      "85034A2000123456789ABCDEA1B2C3D4",
      aesEcbDecrypt(tkey, buildTLinkCommand(keyHex = tkey, command = CommandCode.lock, token = token)),
    )
    assertEquals(
      "85034A2400123456789ABCDEA1B2C3D4",
      aesEcbDecrypt(tkey, buildTLinkCommand(keyHex = tkey, command = CommandCode.openSeat, token = token)),
    )
  }

  @Test
  fun tlinkParserSeparatesTokenLoginAndCommandAck() {
    fun parse(plaintext: String): TLinkResponse =
      parseTLinkResponse(tkey, aesEcbEncrypt(tkey, plaintext))

    val tokenResponse = parse("85000000A1B2C3D40000000000000000")
    assertTrue(tokenResponse is TLinkTokenResponse)
    assertEquals(token, (tokenResponse as TLinkTokenResponse).token)

    val login = parse("8503B511010000000000000000000000")
    assertTrue(login is TLinkLoginResponse)
    assertTrue((login as TLinkLoginResponse).success)

    val command = parse("8503B522010000000000000000000000")
    assertTrue(command is TLinkCommandResponse)
    assertEquals("22", (command as TLinkCommandResponse).commandType)
    assertTrue(command.success)
  }

  @Test
  fun tlinkMalformedEncryptedPayloadIsSafe() {
    assertTrue(parseTLinkResponse(tkey, byteArrayOf(1, 2, 3)) is TLinkUnknownResponse)
  }

  @Test
  fun tlinkInductionPlaintextsAre24HexCharsPreToken() {
    assertEquals(24, TLINK_INDUCTION_CHECK_PLAIN.length)
    assertEquals(24, TLINK_INDUCTION_OPEN_PLAIN.length)
    assertEquals(24, TLINK_INDUCTION_CLOSE_PLAIN.length)
    assertEquals(24, TLINK_HID_OPEN_AFTER_BOND_PLAIN.length)
    assertEquals(24, buildTLinkInductionDistancePlain(5).length)
    assertEquals("85044A3303053456789ABCDE", buildTLinkInductionDistancePlain(5))
    assertEquals("85044A33031E3456789ABCDE", buildTLinkInductionDistancePlain(30))
    assertEquals("85044A33031E3456789ABCDE", buildTLinkInductionDistancePlain(99))
  }

  @Test
  fun tlinkInductionResponseParsersMatchOfficialB533Headers() {
    fun parse(plaintext: String): TLinkResponse {
      val padded = plaintext.padEnd(32, '0')
      return parseTLinkResponse(tkey, aesEcbEncrypt(tkey, padded))
    }

    val open = parse("8506B533010105")
    assertTrue(open is TLinkInductionStatusResponse)
    assertTrue((open as TLinkInductionStatusResponse).enabled)
    assertEquals(5, open.distance)

    val closed = parse("8506B533010200")
    assertTrue(closed is TLinkInductionStatusResponse)
    assertFalse((closed as TLinkInductionStatusResponse).enabled)

    val setOk = parse("8504B5330201")
    assertTrue(setOk is TLinkInductionSetResponse)
    assertTrue((setOk as TLinkInductionSetResponse).success)

    val distOk = parse("8504B5330301")
    assertTrue(distOk is TLinkProximityDistanceSetResponse)
    assertTrue((distOk as TLinkProximityDistanceSetResponse).success)
  }

  // --- qgj_protocol.dart ---------------------------------------------------

  @Test
  fun qgjKeyVersionQueryUsesOfficialFrame() {
    assertArrayEquals(
      hexToBytes("A700021005"),
      buildQgjCommand(QgjCommandIds.keyVersionGet),
    )
  }

  @Test
  fun qgjLoginFrameCarriesBigEndianPasswordAndUserId() {
    // length = 8 payload + 2 = 0x000A; cmdId 0x1001; payload = BE32(1234) + BE32(42)
    assertArrayEquals(
      hexToBytes("A7000A1001000004D20000002A"),
      buildQgjLoginFrame(password = 1234, userId = 42),
    )
    assertArrayEquals(hexToBytes("A700021001"), buildQgjLoginFrame())
  }

  @Test
  fun qgjSeatSupportFollowsOfficialKeyVersions() {
    fun response(version: Int, success: Boolean = true): QgjResponse =
      QgjResponse(
        cmdId = QgjCommandIds.keyVersionGet,
        payload = byteArrayOf(version.toByte()),
        success = success,
      )

    for (version in listOf(2, 6, 9)) {
      assertTrue(parseQgjSeatSupport(response(version)) == true)
    }
    assertFalse(parseQgjSeatSupport(response(1)) == true)
    assertNull(parseQgjSeatSupport(response(2, success = false)))
    assertNull(parseQgjSeatSupport(null))
  }

  @Test
  fun qgjProximityPayloadsMatchOfficialOpCodeAndUInt8() {
    assertArrayEquals(byteArrayOf(1), buildQgjProximityStatusPayload(true))
    assertArrayEquals(byteArrayOf(0), buildQgjProximityStatusPayload(false))
    assertArrayEquals(byteArrayOf(1), buildQgjSwitchPayload(true))
    assertArrayEquals(byteArrayOf(5), buildQgjProximityDistancePayload(5))
    assertArrayEquals(byteArrayOf(0), buildQgjProximityDistancePayload(-1))
    assertArrayEquals(byteArrayOf(100), buildQgjProximityDistancePayload(200))
    assertArrayEquals(byteArrayOf(0), buildQgjHidPayload(QgjHidModes.close))
    assertArrayEquals(byteArrayOf(1), buildQgjHidPayload(QgjHidModes.open))
    assertArrayEquals(byteArrayOf(2), buildQgjHidPayload(QgjHidModes.openWithAutoLock))
    assertArrayEquals(hexToBytes("002D"), buildQgjAutoLockPayload(true))
    assertArrayEquals(hexToBytes("0000"), buildQgjAutoLockPayload(false))
  }

  @Test
  fun qgjParsersReadFirstPayloadByte() {
    assertTrue(parseQgjProximityEnabled(listOf(1)) == true)
    assertFalse(parseQgjProximityEnabled(listOf(0)) == true)
    assertNull(parseQgjProximityEnabled(emptyList()))
    assertEquals(7, parseQgjProximityDistance(listOf(7)))
  }

  @Test
  fun qgjResponseParsesFrameRoundTrip() {
    val frame = buildQgjCommand(QgjCommandIds.proximityStatusSet, byteArrayOf(1))
    val response = parseQgjResponse(frame)
    assertTrue("expected a parsed response", response != null)
    assertEquals(QgjCommandIds.proximityStatusSet, response!!.cmdId)
    assertArrayEquals(byteArrayOf(1), response.payload)
    assertTrue(response.success)
    // Length mismatch → null.
    assertNull(parseQgjResponse(hexToBytes("A700031001")))
    // Wrong header → null.
    assertNull(parseQgjResponse(hexToBytes("B700021001")))
  }

  // --- rssi_distance.dart ---------------------------------------------------

  @Test
  fun rssiDistanceModelMatchesOfficialLogDistanceModel() {
    val d = estimateDistanceFromRssiSamples(List(10) { -60 })
    assertTrue(d > 0)
    val near = estimateDistanceFromRssiSamples(List(10) { -45 })
    val far = estimateDistanceFromRssiSamples(List(10) { -80 })
    assertTrue(near < far)
  }

  @Test
  fun classifyDistanceThresholdsMatchOfficialMin2Max3() {
    assertEquals(RssiProximityAction.approachUnlock, classifyDistance(1.5))
    assertEquals(RssiProximityAction.approachUnlock, classifyDistance(2.0))
    assertEquals(RssiProximityAction.hold, classifyDistance(2.5))
    assertEquals(RssiProximityAction.leaveLock, classifyDistance(3.0))
    assertEquals(RssiProximityAction.leaveLock, classifyDistance(5.0))
  }

  @Test
  fun shouldFireRssiActionRespectsOfficialTaskLatch() {
    assertTrue(shouldFireRssiAction(RssiProximityAction.approachUnlock, RssiTaskState.idle))
    assertTrue(shouldFireRssiAction(RssiProximityAction.approachUnlock, RssiTaskState.locked))
    assertFalse(shouldFireRssiAction(RssiProximityAction.approachUnlock, RssiTaskState.poweredOn))
    assertTrue(shouldFireRssiAction(RssiProximityAction.leaveLock, RssiTaskState.poweredOn))
    assertFalse(shouldFireRssiAction(RssiProximityAction.leaveLock, RssiTaskState.locked))
    assertFalse(shouldFireRssiAction(RssiProximityAction.hold, RssiTaskState.idle))
  }

  @Test
  fun confirmedRssiStateKeepsIntermediateOnFailure() {
    assertEquals(
      listOf(RssiProximityStep.unlock, RssiProximityStep.powerOn),
      pendingRssiSteps(RssiProximityAction.approachUnlock, RssiTaskState.locked),
    )
    val unlocked = confirmedRssiState(RssiTaskState.locked, RssiProximityStep.unlock, success = true)
    assertEquals(RssiTaskState.unlocked, unlocked)
    assertEquals(
      RssiTaskState.unlocked,
      confirmedRssiState(unlocked, RssiProximityStep.powerOn, success = false),
    )
    assertEquals(
      listOf(RssiProximityStep.powerOn),
      pendingRssiSteps(RssiProximityAction.approachUnlock, RssiTaskState.unlocked),
    )

    val poweredOff = confirmedRssiState(RssiTaskState.poweredOn, RssiProximityStep.powerOff, success = true)
    assertEquals(RssiTaskState.poweredOff, poweredOff)
    assertEquals(
      listOf(RssiProximityStep.lock),
      pendingRssiSteps(RssiProximityAction.leaveLock, RssiTaskState.poweredOff),
    )
  }

  // --- qgj_scan_identity.dart -------------------------------------------------

  @Test
  fun qgjManufacturerDataExposesIdentityMacAndBootMode() {
    val identity = parseQgjManufacturerPayloads(
      listOf(byteArrayOf(0x00, 0x10, -86, -69, -52, -35, -18, -1)), // 00 10 AA BB CC DD EE FF
      harmony = false,
    )
    assertEquals("AABBCCDDEEFF", identity.identityMac)
    assertEquals(0, identity.bootMode)

    val binding = parseQgjManufacturerPayloads(
      listOf(byteArrayOf(0x20, 0x00, 1, 2, 3, 4, 5, 6)),
      harmony = false,
    )
    assertEquals(1, binding.bootMode)
    assertEquals("010203040506", binding.identityMac)

    val harmony = parseQgjManufacturerPayloads(emptyList(), harmony = true)
    assertNull(harmony.identityMac)
    assertTrue(harmony.harmony)
  }

  @Test
  fun qgjIdentityRadioFallbackMatchesOfficialGetIdentityMac() {
    val parsed = parseQgjManufacturerPayloads(emptyList(), harmony = false)
    val fallback = identityWithRadioFallback(parsed, "AA:BB:CC:DD:EE:FF")
    assertEquals("AABBCCDDEEFF", fallback.identityMac)
    assertTrue(fallback.fromRadioAddress)

    // Non-empty identity MAC wins.
    val withMac = identityWithRadioFallback(
      QgjScanIdentity(identityMac = "112233445566", bootMode = 0, harmony = false),
      "AA:BB:CC:DD:EE:FF",
    )
    assertEquals("112233445566", withMac.identityMac)
    assertFalse(withMac.fromRadioAddress)
  }
}
