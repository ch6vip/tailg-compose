/**
 * Port of `lib/ble/protocol.dart` (tailg-ble-app) → package `com.tailg.plus.data.ble`.
 *
 * Standard (feb5/feb6) BLE command frames. Every plaintext is exactly 16 bytes
 * (32 hex chars): `78 03 C2 <cmd> …` + filler + 4-byte session token, then
 * AES-128-ECB/NoPadding with the model key.
 *
 * Evidence — official `com.tailg.run.intelligence.ble.tailg.TailgBleCmd`:
 * - `getTokenCommandData()`: `AESUtils.getAESEncode(AES_KEY, "780000002D1A683D48271A18316E471A")`
 * - `getBleCommandData(String cmd)`: `"7803C2" + cmd + "0011111111111111" + PrefsUtil.getBleConnectToken()`
 * - `getBleCommandData(String cmd, String param)`: `"7803C2" + cmd + param + "11111111111111" + token`
 * - `getBleCommandData05(...)`: `"7805C2" + cmd + p1 + p2 + p3 + "1111111111" + token`
 */
package com.tailg.plus.data.ble

/** Dart `_tokenRequestPlaintext` — 16 bytes. */
private const val TOKEN_REQUEST_PLAINTEXT = "780000002D1A683D48271A18316E471A"

/**
 * Port of Dart `buildTokenRequest` — AES-encrypted token-request frame.
 * (Dart guards the constant length with an `assert`; `aesEcbEncrypt` re-validates
 * block alignment at runtime, mirroring Dart's release behavior.)
 */
fun buildTokenRequest(keyHex: String): ByteArray = aesEcbEncrypt(keyHex, TOKEN_REQUEST_PLAINTEXT)

/** Port of Dart `buildCommand` — plaintext `7803C2<cmd>0011111111111111<token>`. */
fun buildCommand(keyHex: String, cmd: CommandCode, token: String): ByteArray {
  val frame = "7803C2${cmd.code}0011111111111111$token"
  if (frame.length != 32) {
    throw IllegalArgumentException(
      "Command frame must be 32 hex chars (16 bytes), got ${frame.length}",
    )
  }
  return aesEcbEncrypt(keyHex, frame)
}

/** Port of Dart `buildCommandWithParam` — plaintext `7803C2<cmd><param>11111111111111<token>`. */
fun buildCommandWithParam(
  keyHex: String,
  cmd: CommandCode,
  param: String,
  token: String,
): ByteArray {
  val frame = "7803C2${cmd.code}$param" + "11111111111111" + token
  if (frame.length != 32) {
    throw IllegalArgumentException(
      "CommandWithParam frame must be 32 hex chars (16 bytes), got ${frame.length}",
    )
  }
  return aesEcbEncrypt(keyHex, frame)
}

/** Port of Dart `buildCommand3Params` — plaintext `7805C2<cmd><p1><p2><p3>1111111111<token>`. */
fun buildCommand3Params(
  keyHex: String,
  cmd: CommandCode,
  p1: String,
  p2: String,
  p3: String,
  token: String,
): ByteArray {
  val frame = "7805C2${cmd.code}$p1$p2$p3" + "1111111111" + token
  if (frame.length != 32) {
    throw IllegalArgumentException(
      "Command3Params frame must be 32 hex chars (16 bytes), got ${frame.length}",
    )
  }
  return aesEcbEncrypt(keyHex, frame)
}
