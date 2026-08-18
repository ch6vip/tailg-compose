package com.tailg.plus.data.mqtt

import com.squareup.moshi.JsonReader
import okio.Buffer

/**
 * Port of `lib/services/official_mqtt_payload.dart` — parsed subset of the
 * official `MqttPayloadBean` used for control UI refresh.
 *
 * JSON parsing uses Moshi's lenient `JsonReader.readJsonValue()` instead of
 * Dart's `jsonDecode`, so numbers arrive as `Double`/`Long` rather than
 * Dart's `int`/`double` split. [normalizeMqttStatusString] normalizes integral doubles to
 * Dart's int rendering (`{"ACC":1}` → `"1"`), matching the official
 * string-typed fields in practice; a genuine double literal such as `1.0`
 * would render `"1"` here vs `"1.0"` in Dart (documented, accepted divergence
 * — official payloads send these fields as strings).
 */
data class OfficialMqttStatusPayload(
    val acc: String? = null,
    val defenceStatus: String? = null,
    val imei: String? = null,
    val muteStatus: Int? = null,
    val accErrorStatus: Int? = null,
    val defenceErrorStatus: Int? = null,
    val bikeSetSourceValue: Int? = null,
) {

    /** Dart `isMoving => accErrorStatus == 4 || defenceErrorStatus == 2`. */
    val isMoving: Boolean get() = accErrorStatus == 4 || defenceErrorStatus == 2

    /** Dart `isKeyStarted => accErrorStatus == 8`. */
    val isKeyStarted: Boolean get() = accErrorStatus == 8

    /**
     * Dart: `defenceErrorStatus != 3` → false; otherwise true unless
     * `bikeSetSourceValue` is one of `{0, 2, 5, 6}` (vehicle powered off).
     */
    val isNotPoweredOff: Boolean
        get() {
            if (defenceErrorStatus != 3) return false
            return !bikeSetSourceValue.inSet(NOT_POWERED_OFF_EXCLUDED_SOURCES)
        }

    val accInt: Int? get() = acc?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()

    val defenceStatusInt: Int?
        get() = defenceStatus?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()

    val hasVehicleState: Boolean get() = accInt != null || defenceStatusInt != null

    /**
     * Apply official ControlFragment-style confirmation against a pending
     * command. Returns true when the payload confirms [pendingCommandApiName],
     * or when no pending command is set but vehicle state fields are present.
     */
    fun confirmsCommand(pendingCommandApiName: String?): Boolean {
        val pending = pendingCommandApiName?.trim().orEmpty()
        if (pending.isEmpty()) return hasVehicleState

        return when (pending) {
            "start" -> acc == "1"
            "stop" -> acc == "0"
            "lock" -> defenceStatus == "1"
            "unlock" -> defenceStatus == "0"
            // find / openCushion: official still refreshes ACC/defence opportunistically
            else -> hasVehicleState
        }
    }

    /**
     * Official error-field mapping. Command responses are evaluated against
     * the pending command only; `start`/`stop` failure codes never leak to
     * other commands.
     */
    fun controlErrorMessage(pendingCommandApiName: String?): String? {
        if (isMoving) return VEHICLE_MOVING_DISABLED_REASON
        if (isKeyStarted) return KEY_STARTED_DISABLED_REASON
        if (isNotPoweredOff) return NOT_POWERED_OFF_DISABLED_REASON
        if (accErrorStatus.inSet(ACC_ERROR_FAILURE_CODES)) {
            return when (pendingCommandApiName) {
                "start" -> "车辆启动失败"
                "stop" -> "车辆熄火失败"
                else -> null
            }
        }
        return null
    }

    companion object {
        /**
         * Mirrors `ControlCommandPolicy` constants
         * (`lib/services/control_command_policy.dart`; `com.tailg.plus.domain`
         * port pending). Kept here so the payload parser stays a
         * self-contained pure module.
         */
        private const val VEHICLE_MOVING_DISABLED_REASON = "车辆行驶中，请勿操作"
        private const val KEY_STARTED_DISABLED_REASON = "您已使用钥匙启动车辆，当前不支持此操作"
        private const val NOT_POWERED_OFF_DISABLED_REASON = "车辆未断电，请勿操作"

        private val NOT_POWERED_OFF_EXCLUDED_SOURCES = setOf(0, 2, 5, 6)
        private val ACC_ERROR_FAILURE_CODES = setOf(5, 6, 7, 20)

        /**
         * Parse official status JSON. Returns null when the payload is empty,
         * not a JSON object, or malformed (Dart `tryParse` semantics).
         */
        fun tryParse(raw: String): OfficialMqttStatusPayload? {
            val text = raw.trim()
            if (text.isEmpty()) return null
            return try {
                val reader = JsonReader.of(Buffer().writeUtf8(text))
                val decoded = reader.readJsonValue()
                if (decoded !is Map<*, *>) {
                    null
                } else {
                    val map = decoded.entries.associate { it.key.toString() to it.value }
                    OfficialMqttStatusPayload(
                        acc = normalizeMqttStatusString(map["ACC"] ?: map["acc"]),
                        defenceStatus = normalizeMqttStatusString(
                            map["defenceStatus"] ?: map["DefenseStatus"] ?: map["defenseStatus"],
                        ),
                        imei = normalizeMqttStatusString(map["imei"]),
                        muteStatus = asInt(map["muteStatus"]),
                        accErrorStatus = asInt(map["accErrorStatus"]),
                        defenceErrorStatus = asInt(map["defenceErrorStatus"]),
                        bikeSetSourceValue = asInt(map["bikeSetSourceValue"]),
                    )
                }
            } catch (_: Exception) {
                // Malformed status payload — treat as no update.
                null
            }
        }

        private fun asInt(value: Any?): Int? = when (value) {
            is Int -> value
            is Number -> value.toInt()
            else -> value?.toString()?.toIntOrNull()
        }

        private fun Int?.inSet(values: Set<Int>): Boolean = this != null && this in values
    }
}

internal fun normalizeMqttStatusString(value: Any?): String? {
    if (value == null) return null
    val text = when (value) {
        is Double -> if (value == Math.floor(value) && !value.isInfinite()) {
            value.toLong().toString()
        } else {
            value.toString()
        }
        is Float -> value.toDouble().let { number ->
            if (number == Math.floor(number) && !number.isInfinite()) {
                number.toLong().toString()
            } else {
                value.toString()
            }
        }
        else -> value.toString()
    }.trim()
    return text.takeIf { it.isNotEmpty() }
}
