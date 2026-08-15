/**
 * Port of `lib/ble/constants.dart` (tailg-ble-app) → package `com.tailg.plus.data.ble`.
 *
 * All constant values are kept byte-for-byte identical to the Dart originals.
 *
 * ## AES key obfuscation — evidence
 *
 * `_obfuscate` XORs each key byte with the big-endian bytes of `0x5A3C6F91D2E84B7A`
 * (cycled). Deobfuscating the enum values reproduces the official keys exactly:
 *
 * | Model | deobfuscated key | official constant |
 * |---|---|---|
 * | KKS | 3A60432A5C01211F291E0F4E0C132825 | `TailgBleCmd.AES_KEY` (also `BlueToothTypeConstants.AES_KEY`) |
 * | BB  | 1AF78CD35BE92F4CA06DB89EC2D7EF01 | `TailgBleConfig.AES_KEY_BB` |
 * | AX  | 1AF78CD35BE92F4CA06DB89E7C4B1E6A | `TailgBleConfig.AES_KEY_AX` |
 * | JD  | 1AF78CD35BE92F4CA06DB89E5F3D2A8C | `TailgBleConfig.AES_KEY_JD` |
 * | HJ  | 1AF78CD35BE92F4CA06DB89E9E6C4B1A | `TailgBleConfig.AES_KEY_HJ` |
 * | JW  | 1AF78CD35BE92F4CA06DB89E6F8B39A5 | `TailgBleConfig.AES_KEY_JW` |
 * | XL  | 1AF78CD35BE92F4CA06DB89E1E6C8A9A | `TailgBleConfig.AES_KEY_XL` |
 * | YY  | 1AF78CD35BE92F4CA06DB89E2A8C3F5D | `TailgBleConfig.AES_KEY_YY` |
 *
 * (official files: `E:\ctf-aaa\tlddc\3.5.9\sources\com\tailg\run\intelligence\ble\tailg\TailgBleCmd.java`,
 * `...\tlink_ble\TailgBleConfig.java`)
 *
 * ## QGJ command ids — evidence
 *
 * All match the `com.kuyi.h.y0` command registry
 * (`E:\ctf-aaa\tlddc\3.5.9\sources\com\kuyi\h\y0.java`), e.g. login=0x1001 (`m0`),
 * setStatus=0x1002 (`s0`), proximity 0x2030–0x2033, hidStatus 0x2140 (`j`)/0x2142 (`i`),
 * safeLock 0x2360/0x2361, kickstand 0x2370/0x2371, seat sensor 0x2400/0x2401,
 * light sensor 0x2410/0x2411, sound adjust 0x2420 (`y`)/0x2421 (`x`), enterOtaMode 0x5004.
 */
package com.tailg.plus.data.ble

import kotlin.time.Duration

/** Dart `_keyMask = 0x5A3C6F91D2E84B7A`. */
private const val KEY_MASK = 0x5A3C6F91D2E84B7AL

/** Dart `_obfuscate(String hexKey)` — XOR key bytes with big-endian mask bytes (cycled). */
private fun obfuscate(hexKey: String): String {
  val maskBytes = IntArray(8)
  for (i in 7 downTo 0) {
    maskBytes[7 - i] = ((KEY_MASK shr (i * 8)) and 0xFF).toInt()
  }
  val result = StringBuilder()
  var i = 0
  while (i < hexKey.length) {
    val keyByte = hexKey.substring(i, i + 2).toInt(16)
    val maskByte = maskBytes[(i / 2) % maskBytes.size]
    result.append(intToHex2(keyByte xor maskByte))
    i += 2
  }
  return result.toString()
}

/** Dart `_deobfuscate(String) => _obfuscate(obfuscatedHex)` (XOR is self-inverse). */
private fun deobfuscate(obfuscatedHex: String): String = obfuscate(obfuscatedHex)

/** Port of Dart `enum ModelType` — obfuscated keys + deobfuscated [aesKey]. */
enum class ModelType(private val obfuscatedKey: String) {
  // Obfuscated keys generated via obfuscate() with KEY_MASK.
  KKS("605C2CBB8EE96A65732260DFDEFB635F"),
  BB("40CBE34289016436FA51D70F103FA47B"),
  AX("40CBE34289016436FA51D70FAEA35510"),
  JD("40CBE34289016436FA51D70F8DD561F6"),
  HJ("40CBE34289016436FA51D70F4C840060"),
  JW("40CBE34289016436FA51D70FBD6372DF"),
  XL("40CBE34289016436FA51D70FCC84C1E0"),
  YY("40CBE34289016436FA51D70FF8647427");

  /** Deobfuscated 16-byte AES key (upper-case hex). */
  val aesKey: String
    get() = deobfuscate(obfuscatedKey)
}

/** Port of Dart `enum RidingMode`. */
enum class RidingMode(val code: Int, val label: String) {
  eco(0, "超能跑"),
  standard(1, "全速跑"),
  sport(2, "超速跑");

  /** Dart `qgjPodgValue => code + 1`. */
  val qgjPodgValue: Int
    get() = code + 1

  companion object {
    /** Dart `fromQgjPodgValue`: 1→eco, 2→standard, 3→sport, else null. */
    fun fromQgjPodgValue(value: Int): RidingMode? = when (value) {
      1 -> RidingMode.eco
      2 -> RidingMode.standard
      3 -> RidingMode.sport
      else -> null
    }
  }
}

/** Port of Dart `class BleUuids`. */
object BleUuids {
  const val serviceFee5 = "0000fee5-0000-1000-8000-00805f9b34fb"
  const val serviceFcc0 = "0000fcc0-0000-1000-8000-00805f9b34fb"
  const val serviceFe01 = "0000fe01-0000-1000-8000-00805f9b34fb"
  const val serviceFeb0 = "0000feb0-0000-1000-8000-00805f9b34fb"
  const val serviceOta = "00002600-0000-1000-8000-00805f9b34fb"
  const val writeChar = "0000feb5-0000-1000-8000-00805f9b34fb"
  const val notifyChar = "0000feb6-0000-1000-8000-00805f9b34fb"
  const val feb1 = "0000feb1-0000-1000-8000-00805f9b34fb"
  const val feb2 = "0000feb2-0000-1000-8000-00805f9b34fb"
  const val feb3 = "0000feb3-0000-1000-8000-00805f9b34fb"
  const val fe02 = "0000fe02-0000-1000-8000-00805f9b34fb"
  const val fe03 = "0000fe03-0000-1000-8000-00805f9b34fb"
  const val fcc1 = "0000fcc1-0000-1000-8000-00805f9b34fb"
  const val fcc2 = "0000fcc2-0000-1000-8000-00805f9b34fb"
  const val fbb1 = "0000fbb1-0000-1000-8000-00805f9b34fb"
  const val fbb2 = "0000fbb2-0000-1000-8000-00805f9b34fb"
  const val otaOrder = "00007000-0000-1000-8000-00805f9b34fb"
  const val otaFile = "00007001-0000-1000-8000-00805f9b34fb"
}

/**
 * Port of Dart `class BleTimings`.
 *
 * Durations use `kotlin.time.Duration` (module choice documented in CONVENTIONS.md);
 * GATT/BLE waits will use coroutine `delay(Duration)` in the connection-manager port.
 */
object BleTimings {
  val connectTimeout: Duration = Duration.seconds(10)
  val reconnectConnectTimeout: Duration = Duration.seconds(8)
  val initialConnectRetryDelay: Duration = Duration.milliseconds(500)
  val failedConnectRecoveryDelay: Duration = Duration.milliseconds(600)
  val androidGattErrorRecoveryDelay: Duration = Duration.milliseconds(1200)
  val qgjRequestedMtu: Int = 515
  val autoConnectScanTimeout: Duration = Duration.seconds(8)
  val manualScanTimeout: Duration = Duration.seconds(30)
  val proximityScanTimeout: Duration = Duration.seconds(30)
  val serviceSetupDelay: Duration = Duration.milliseconds(500)
  val heartbeatInitialDelay: Duration = Duration.milliseconds(500)
  val heartbeatInterval: Duration = Duration.seconds(5)
  val qgjStatusPollInterval: Duration = Duration.seconds(1)
  val commandAckTimeout: Duration = Duration.seconds(5)
  val fccReadbackDelay: Duration = Duration.milliseconds(200)
  val fccRetryDelay: Duration = Duration.milliseconds(500)
  val locationCaptureTimeout: Duration = Duration.seconds(8)
  val silentLocationThrottle: Duration = Duration.seconds(60)
  val qgjSearchCountdown: Duration = Duration.seconds(30)
  val gpsSearchCountdown: Duration = Duration.seconds(6)
  val gattOperationTimeout: Duration = Duration.seconds(30)

  /**
   * Max time to wait for the device to deliver the token (standard) or QGJ
   * login response after GATT setup completes. If the state is still
   * `connected` (not `ready`) when this elapses, the link is torn down and
   * reconnection is attempted — prevents the UI from hanging on
   * "连接中" forever when a device silently drops the handshake.
   */
  val readyHandshakeTimeout: Duration = Duration.seconds(8)
}

/** Port of Dart `class QgjCommandHeaders`. */
object QgjCommandHeaders {
  val checkSound: List<Int> = listOf(0x85, 0x03, 0x4A, 0x3C)
  val setSound: List<Int> = listOf(0x85, 0x06, 0x4A, 0x3C)
  val checkSensitivity: List<Int> = listOf(0x85, 0x03, 0x4A, 0x36)
  val setSensitivity: List<Int> = listOf(0x85, 0x04, 0x4A, 0x36)
  val inductionStatus: List<Int> = listOf(0x85, 0x03, 0x4A, 0x33)
  val inductionSet: List<Int> = listOf(0x85, 0x04, 0x4A, 0x33)
  val autoLockSearch: List<Int> = listOf(0x85, 0x03, 0x4A, 0x30)
  val autoLockSet: List<Int> = listOf(0x85, 0x05, 0x4A, 0x30)
}

/** Port of Dart `class QgjCommandIds` (see file header for official registry evidence). */
object QgjCommandIds {
  const val login = 0x1001
  const val setStatus = 0x1002
  const val keyVersionGet = 0x1005
  const val autoLockTimeGet = 0x2000
  const val autoLockTimeSet = 0x2001
  const val autoLockGet = autoLockTimeGet
  const val autoLockSet = autoLockTimeSet
  const val powerOnAutoLockTimeGet = 0x2010
  const val powerOnAutoLockTimeSet = 0x2011
  const val proximityStatusGet = 0x2030
  const val proximityStatusSet = 0x2031
  const val proximityDistanceGet = 0x2032
  const val proximityDistanceSet = 0x2033
  const val handlebarLockSet = 0x2050
  const val handlebarLockGet = 0x2051
  const val vibrateSensitivityGet = 0x2060
  const val vibrateSensitivitySet = 0x2061
  const val postureDetectionSet = 0x2070
  const val postureDetectionGet = 0x2071
  const val passwordUnlockGet = 0x2080
  const val passwordUnlockSet = 0x2081
  const val hidStatusSet = 0x2140
  const val hidStatusGet = 0x2142
  const val safeLockSet = 0x2360
  const val safeLockGet = 0x2361
  const val kickstandSet = 0x2370
  const val kickstandGet = 0x2371
  const val seatSensorSet = 0x2400
  const val seatSensorGet = 0x2401
  const val lightSensorSet = 0x2410
  const val lightSensorGet = 0x2411
  const val soundAdjustGet = 0x2420
  const val soundAdjustSet = 0x2421
  const val enterOtaMode = 0x5004
}

/** Port of Dart `class QgjHidModes` — official `OpHID` ordinals (Close/Open/OpenWithAutolock). */
object QgjHidModes {
  const val close = 0
  const val open = 1
  const val openWithAutoLock = 2
}

/** Port of Dart `class QgjSoundIndexes`. */
object QgjSoundIndexes {
  const val lock = 1
  const val unlock = 3
  const val start = 14
  const val stop = 15
  const val speed = 17
  const val all = 255

  val known: List<Int> = listOf(lock, unlock, start, stop, speed)
}

/**
 * Port of Dart `class QgjControlOpCodes` — CommandCode → QGJ set-status op code.
 * Official `com.kuyi.h.a1.encode(OpCode)`: OPEN/SET/ADD → 1, everything else → 0
 * (see `com.kuyi.h.b` ordinal map).
 */
object QgjControlOpCodes {
  val byCommandCode: Map<String, Int> = mapOf(
    "01" to 0x02, // lock
    "02" to 0x01, // unlock
    "05" to 0x07, // open seat
    "06" to 0x03, // power on
    "07" to 0x04, // power off
    "08" to 0x08, // find
  )
}

/** Port of Dart `extractFcc1StatusBytes`. */
fun extractFcc1StatusBytes(data: List<Int>): List<Int>? {
  if (data.size >= 11) {
    return fcc1StatusBytes(data, 8)
  }
  if (data.size >= 7 && data[0] == 0x00 && data[1] == 0x07) {
    return fcc1StatusBytes(data, 4)
  }
  return null
}

/** Port of Dart `_fcc1StatusBytes`. */
private fun fcc1StatusBytes(data: List<Int>, start: Int): List<Int> =
  listOf(data[start], data[start + 1], data[start + 2])

/** Port of Dart `parseQgjRidingMode`. */
fun parseQgjRidingMode(data: List<Int>): RidingMode? {
  val status = extractFcc1StatusBytes(data) ?: return null
  return RidingMode.fromQgjPodgValue(status[1] and 0x07)
}

/** Port of Dart `buildQgjRidingModeFrame`. */
fun buildQgjRidingModeFrame(readback: List<Int>, mode: RidingMode): List<Int>? {
  val status = extractFcc1StatusBytes(readback) ?: return null
  return qgjRidingModeFrame(status, mode)
}

/** Port of Dart `_qgjRidingModeFrame`. */
private fun qgjRidingModeFrame(status: List<Int>, mode: RidingMode): List<Int> {
  val state2 = (status[1] and 0xF8) or mode.qgjPodgValue
  return listOf(0x00, 0x07, 0x00, 0x02, status[0], state2, status[2])
}

/**
 * Port of Dart `class BikeState` (equals/hashCode come from `data class`,
 * matching the Dart manual implementations).
 */
data class BikeState(
  val isLocked: Boolean,
  val isPowerOn: Boolean,
  val isMuted: Boolean = false,
  val voltage: Double? = null,
  val temperature: Double? = null,
  val batteryPercent: Int? = null,
  val signalStrength: Int? = null,
  val faultMotor: Boolean = false,
  val faultController: Boolean = false,
  val faultBrake: Boolean = false,
  val faultLowVoltage: Boolean = false,
) {
  companion object {
    /** Port of Dart `BikeState.fromFeb3` — byte-exact bit unpacking. */
    fun fromFeb3(data: ByteArray): BikeState? {
      if (data.size < 6) return null

      val status1 = data[0].toInt() and 0xFF
      val isLocked = (status1 and 0x01) != 0
      val isPowerOn = (status1 and 0x02) != 0
      val isMuted = (status1 and 0x04) != 0

      val voltageRaw = ((data[3].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
      val voltage = voltageRaw / 10.0

      val faults = data[5].toInt() and 0xFF
      val faultMotor = (faults and 0x01) != 0
      val faultController = (faults and 0x04) != 0
      val faultBrake = (faults and 0x10) != 0
      val faultLowVoltage = (faults and 0x20) != 0

      val batteryRaw = if (data.size > 6) data[6].toInt() and 0xFF else null
      var batteryPercent: Int? = null
      if (batteryRaw != null && (batteryRaw and 0x80) != 0) {
        val value = batteryRaw and 0x7F
        batteryPercent = if (value > 100) 100 else value
      }

      return BikeState(
        isLocked = isLocked,
        isPowerOn = isPowerOn,
        isMuted = isMuted,
        voltage = if (voltage > 0 && voltage < 200) voltage else null,
        temperature = null,
        batteryPercent = batteryPercent,
        faultMotor = faultMotor,
        faultController = faultController,
        faultBrake = faultBrake,
        faultLowVoltage = faultLowVoltage,
      )
    }
  }
}
