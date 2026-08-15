/**
 * Port of `lib/ble/qgj_protocol.dart` (tailg-ble-app) → package `com.tailg.plus.data.ble`.
 *
 * QGJ (0xA7) ECU frames over the standard stack.
 *
 * Frame layout (official encoder `com.kuyi.h.d`, `E:\ctf-aaa\tlddc\3.5.9\sources\com\kuyi\h\d.java`):
 * ```
 * byte[0] = 0xA7
 * byte[1] = 0x00            (status nibble lives in the high 4 bits on replies)
 * byte[2..3] = BE16 length  = payload.size + 2   (the 2 cmd-id bytes)
 * byte[4..5] = BE16 cmdId
 * byte[6..]  = payload
 * ```
 * Official encoder: `pushValue(167,17)`; `pushValue(0,17)`;
 * `pushValue(size+2,18)`; `pushValue(getCmdID(),18)`; then the payload.
 * Official decoder: `byte[0] != 167 → error`; status `(byte[1] >> 4) & 0x0F`;
 * for status 0 the length field must equal `totalSize - 4`.
 *
 * Payload encoders (all BE16 cmd ids from the `com.kuyi.h.y0` registry):
 * - login 0x1001: 8-byte payload `[BE32 password][BE32 userId]` (`com.kuyi.h.m0`)
 * - setStatus 0x1002: single op-code byte (`com.kuyi.h.s0` → `MutableData.opCode`)
 * - proximity status set 0x2031: OPEN/SET/ADD → 1 else 0 (`com.kuyi.h.a1` + `com.kuyi.h.b`)
 * - proximity distance set 0x2033: single UInt8 (0..100)
 * - autoLock set 0x2001: BE16 (45 = on, 0 = off)
 * - HID set 0x2140: `OpHID.ordinal` Close=0/Open=1/OpenWithAutolock=2 (`com.kuyi.h.j`)
 * - seat support gate: key-version (0x1005) payload first byte ∈ {2, 6, 9}
 */
package com.tailg.plus.data.ble

/** Port of Dart `buildQgjLoginFrame` — 8-byte payload `[BE32 password][BE32 userId]`. */
fun buildQgjLoginFrame(password: Int = 0, userId: Int = 0): ByteArray {
  val payload = ByteArray(8)
  writeUInt32BE(payload, 0, password)
  writeUInt32BE(payload, 4, userId)
  return buildQgjCommand(QgjCommandIds.login, payload)
}

/**
 * Port of Dart `buildQgjCommand`.
 * `payload == null` (or empty) yields a header-only frame with length 2.
 */
fun buildQgjCommand(cmdId: Int, payload: ByteArray? = null): ByteArray {
  val body = payload ?: ByteArray(0)
  val length = body.size + 2
  val frame = ByteArray(4 + 2 + body.size)
  frame[0] = 0xA7.toByte()
  frame[1] = 0x00
  frame[2] = ((length shr 8) and 0xFF).toByte()
  frame[3] = (length and 0xFF).toByte()
  frame[4] = ((cmdId shr 8) and 0xFF).toByte()
  frame[5] = (cmdId and 0xFF).toByte()
  body.copyInto(frame, destinationOffset = 6)
  return frame
}

/** Port of Dart `buildQgjUInt8Payload`. */
fun buildQgjUInt8Payload(value: Int): ByteArray = byteArrayOf((value and 0xFF).toByte())

/** Port of Dart `buildQgjUInt16Payload` — BE16. */
fun buildQgjUInt16Payload(value: Int): ByteArray {
  val v = value and 0xFFFF
  return byteArrayOf(((v shr 8) and 0xFF).toByte(), (v and 0xFF).toByte())
}

/**
 * Port of Dart `buildQgjSwitchPayload` — official OpCode encoding for proximity
 * status set (`0x2031`): OPEN / SET / ADD → 1, everything else (incl. CLOSE) → 0
 * (`com.kuyi.h.a1.encode(OpCode)`).
 */
fun buildQgjSwitchPayload(enabled: Boolean): ByteArray =
  byteArrayOf((if (enabled) 1 else 0).toByte())

/** Port of Dart `buildQgjProximityStatusPayload` (alias of [buildQgjSwitchPayload]). */
fun buildQgjProximityStatusPayload(enabled: Boolean): ByteArray =
  buildQgjSwitchPayload(enabled)

/** Port of Dart `buildQgjProximityDistancePayload` — single UInt8 level, clamped 0..100. */
fun buildQgjProximityDistancePayload(level: Int): ByteArray =
  buildQgjUInt8Payload(level.coerceIn(0, 100))

/** Port of Dart `buildQgjAutoLockPayload` — BE16 (45 = on, 0 = off). */
fun buildQgjAutoLockPayload(enabled: Boolean): ByteArray =
  buildQgjUInt16Payload(if (enabled) 45 else 0)

/**
 * Port of Dart `buildQgjHidPayload` — official OpHID ordinal for `0x2140`:
 * Close=0, Open=1, OpenWithAutolock=2 (`com.kuyi.h.j`), clamped 0..2.
 */
fun buildQgjHidPayload(mode: Int): ByteArray =
  buildQgjUInt8Payload(mode.coerceIn(0, 2))

/** Port of Dart `parseQgjProximityEnabled` — first payload byte != 0. */
fun parseQgjProximityEnabled(payload: List<Int>): Boolean? {
  if (payload.isEmpty()) return null
  return payload[0] != 0
}

/** Port of Dart `parseQgjProximityDistance` — first payload byte (unsigned). */
fun parseQgjProximityDistance(payload: List<Int>): Int? {
  if (payload.isEmpty()) return null
  return payload[0] and 0xFF
}

/** Port of Dart `buildQgjControlFrame` — six-key CommandCode → set-status op frame. */
fun buildQgjControlFrame(cmd: CommandCode): ByteArray? {
  val opCode = QgjControlOpCodes.byCommandCode[cmd.code] ?: return null
  return buildQgjCommand(QgjCommandIds.setStatus, byteArrayOf(opCode.toByte()))
}

/** Port of Dart `class QgjResponse`. */
data class QgjResponse(
  val cmdId: Int,
  val payload: ByteArray,
  val success: Boolean,
)

/**
 * Port of Dart `parseQgjSeatSupport` — official QGJ seat capability gate
 * (`ECU_QUERY_KEY_VERSION`, 0x1005). Key versions 2, 6 and 9 expose the seat-lock command.
 */
fun parseQgjSeatSupport(response: QgjResponse?): Boolean? {
  if (response == null || !response.success || response.payload.isEmpty()) return null
  return (response.payload.first().toInt() and 0xFF) in setOf(2, 6, 9)
}

/**
 * Port of Dart `parseQgjResponse` — validates `0xA7` header + length field,
 * extracts BE16 cmdId + payload, success = status nibble == 0.
 */
fun parseQgjResponse(data: ByteArray): QgjResponse? {
  if (data.size < 6 || data[0] != 0xA7.toByte()) return null
  val length = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
  if (length != data.size - 4) return null
  val cmdId = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
  val payload = data.copyOfRange(6, data.size)
  val statusNibble = ((data[1].toInt() and 0xFF) shr 4) and 0x0F
  return QgjResponse(
    cmdId = cmdId,
    payload = payload,
    success = statusNibble == 0,
  )
}

/** Write `value` as big-endian 32-bit into [dst] at [offset] (Dart `setUint32(…, Endian.big)`). */
private fun writeUInt32BE(dst: ByteArray, offset: Int, value: Int) {
  val v = value.toLong() and 0xFFFFFFFFL
  dst[offset] = ((v shr 24) and 0xFF).toByte()
  dst[offset + 1] = ((v shr 16) and 0xFF).toByte()
  dst[offset + 2] = ((v shr 8) and 0xFF).toByte()
  dst[offset + 3] = (v and 0xFF).toByte()
}
