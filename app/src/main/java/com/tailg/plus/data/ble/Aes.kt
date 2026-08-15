/**
 * Port of `lib/ble/aes.dart` (tailg-ble-app) → package `com.tailg.plus.data.ble`.
 *
 * ## AES mode / padding / key derivation — conclusion with evidence
 *
 * Dart uses the `encrypt` package: `Encrypter(AES(Key(hexToBytes(keyHex)), mode: AESMode.ecb, padding: null))`.
 *
 * Official decompiled Java uses the identical primitive:
 * - `com.tailg.run.intelligence.tlink_ble.util.AESUtils` (Kotlin, JADX-decompiled)
 *   - `getAESEncode(String hexKey, String text)`: `Cipher.getInstance("AES/ECB/NoPadding")`,
 *     `SecretKeySpec(parseHexStr2Byte(hexKey), "AES")`, `cipher.init(ENCRYPT_MODE, key)`, `doFinal(parseHexStr2Byte(text))`.
 *   - `getAESDecode(String hexKey, byte[] byteData)`: same, `DECRYPT_MODE`.
 *   - Evidence file: `E:\ctf-aaa\tlddc\3.5.9\sources\com\tailg\run\intelligence\tlink_ble\util\AESUtils.java` (lines 97–129).
 * - `com.tailg.run.intelligence.model.util.AESUtils` is the same implementation (lines 54–65).
 *
 * → **AES-128-ECB, NO padding (PKCS7 off), raw 16-byte key** — the 32-hex-char key IS the
 *   AES key; there is no KDF. Per-block alignment is the caller's contract (validated below).
 *
 * ## Key "derivation" (deobfuscation)
 *
 * The obfuscated model keys in `constants.dart` are deobfuscated with the XOR mask
 * `0x5A3C6F91D2E84B7A` (big-endian byte order, cycled). Cross-check against the official
 * constants proves the mapping (see `Constants.kt` header):
 * - `ModelType.KKS` deobfuscates to `3A60432A5C01211F291E0F4E0C132825`
 *   == `com.tailg.run.intelligence.ble.tailg.TailgBleCmd.AES_KEY`.
 * - All 7 model keys (BB/AX/JD/HJ/JW/XL/YY) match `TailgBleConfig.AES_KEY_*`.
 *
 * So `aesEcbEncrypt/Decrypt` take the already-deobfuscated 32-char hex key.
 */
package com.tailg.plus.data.ble

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * AES-128-ECB/NoPadding encrypt (Dart `aesEcbEncrypt`).
 *
 * Validates exactly like Dart:
 * - key must be 32 hex chars (16 bytes),
 * - data hex must be non-empty and a multiple of 32 hex chars (16-byte blocks).
 * Returns the raw ciphertext bytes.
 */
fun aesEcbEncrypt(keyHex: String, dataHex: String): ByteArray {
  if (keyHex.length != 32) {
    throw IllegalArgumentException(
      "AES key hex must be 32 characters (16 bytes), got ${keyHex.length}",
    )
  }
  if (dataHex.isEmpty()) {
    throw IllegalArgumentException("Data hex must not be empty")
  }
  if (dataHex.length % 32 != 0) {
    throw IllegalArgumentException(
      "Data hex length must be a multiple of 32 (16-byte blocks), got ${dataHex.length}",
    )
  }
  val key = SecretKeySpec(hexToBytes(keyHex), "AES")
  return try {
    val cipher = Cipher.getInstance("AES/ECB/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    cipher.doFinal(hexToBytes(dataHex))
  } catch (e: GeneralSecurityException) {
    // Inputs are pre-validated, so this only fires on provider-level failures.
    // Wrapped as IllegalArgumentException to mirror Dart's ArgumentError path
    // (parseResponse treats it as an unknown frame).
    throw IllegalArgumentException("AES/ECB/NoPadding encrypt failed: ${e.message}", e)
  }
}

/**
 * AES-128-ECB/NoPadding decrypt (Dart `aesEcbDecrypt`).
 *
 * Validates exactly like Dart:
 * - key must be 32 hex chars (16 bytes),
 * - data must be non-empty and a multiple of 16 bytes.
 * Returns the decrypted plaintext as UPPERCASE contiguous hex (Dart `bytesToHex`).
 */
fun aesEcbDecrypt(keyHex: String, data: ByteArray): String {
  if (keyHex.length != 32) {
    throw IllegalArgumentException(
      "AES key hex must be 32 characters (16 bytes), got ${keyHex.length}",
    )
  }
  if (data.isEmpty()) {
    throw IllegalArgumentException("Data must not be empty")
  }
  if (data.size % 16 != 0) {
    throw IllegalArgumentException(
      "Data length must be a multiple of 16 bytes, got ${data.size}",
    )
  }
  val key = SecretKeySpec(hexToBytes(keyHex), "AES")
  val decrypted = try {
    val cipher = Cipher.getInstance("AES/ECB/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key)
    cipher.doFinal(data)
  } catch (e: GeneralSecurityException) {
    // See aesEcbEncrypt: pre-validated inputs, so this is a defensive wrap.
    throw IllegalArgumentException("AES/ECB/NoPadding decrypt failed: ${e.message}", e)
  }
  return bytesToHex(decrypted)
}
