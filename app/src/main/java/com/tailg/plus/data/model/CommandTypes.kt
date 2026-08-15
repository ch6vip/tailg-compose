package com.tailg.plus.data.model

/**
 * Port of `lib/models/command_types.dart`.
 *
 * The Dart enum carries a hex-string BLE command code (not an int), so the
 * int-valued `value`/`fromValue` convention does not apply here. The `code`
 * values are verified against the official decompiled Java source
 * (`ble/tailg/TailgBleCmd.java`):
 *
 * | entry       | code | official constant                  |
 * |-------------|------|------------------------------------|
 * | LOCK        | 01   | CAR_LOCK_CODE                      |
 * | UNLOCK      | 02   | CAR_UNLOCK_CODE                    |
 * | OPEN_SEAT   | 05   | CAR_OPEN_CUSHION_CODE              |
 * | POWER_ON    | 06   | CAR_REMOTE_POWER_ON_CODE           |
 * | POWER_OFF   | 07   | CAR_REMOTE_POWER_OFF_CODE          |
 * | FIND        | 08   | CAR_SEARCH_CODE                    |
 * | READ_STATE  | 0D   | CAR_VEHICLE_STATE_CODE             |
 * | READ_ANTI_THEFT | 0E  | CAR_ANTI_THEFT_STATE_CODE          |
 */
enum class CommandCode(val code: String, val label: String) {
    LOCK("01", "设防"),
    UNLOCK("02", "解锁"),
    OPEN_SEAT("05", "开座桶"),
    POWER_ON("06", "启动"),
    POWER_OFF("07", "熄火"),
    FIND("08", "寻车"),
    READ_STATE("0D", "读取状态"),
    READ_ANTI_THEFT("0E", "读取防盗"),
}
