/**
 * Port of `lib/models/command_types.dart` (tailg-ble-app) → package `com.tailg.plus.data.ble`.
 *
 * `lib/ble/constants.dart` re-exports this enum (`export '../models/command_types.dart'
 * show CommandCode;`). Per CONVENTIONS.md, `lib/models` (directory) belongs in
 * `com.tailg.plus.data.model`; this copy lives in the BLE package so the BLE protocol
 * layer compiles standalone. When the `data.model` port lands, move this file there and
 * re-export/alias from the BLE package.
 *
 * Command codes match the official decompiled constants
 * (`com.tailg.run.intelligence.ble.tailg.TailgBleCmd`):
 *   CAR_LOCK_CODE="01" · CAR_UNLOCK_CODE="02" · CAR_OPEN_CUSHION_CODE="05" ·
 *   CAR_REMOTE_POWER_ON_CODE="06" · CAR_REMOTE_POWER_OFF_CODE="07" ·
 *   CAR_SEARCH_CODE="08" · CAR_VEHICLE_STATE_CODE="0D" · CAR_ANTI_THEFT_STATE_CODE="0E".
 */
package com.tailg.plus.data.ble

enum class CommandCode(val code: String, val label: String) {
  lock("01", "设防"),
  unlock("02", "解锁"),
  openSeat("05", "开座桶"),
  powerOn("06", "启动"),
  powerOff("07", "熄火"),
  find("08", "寻车"),
  readState("0D", "读取状态"),
  readAntiTheft("0E", "读取防盗"),
}
