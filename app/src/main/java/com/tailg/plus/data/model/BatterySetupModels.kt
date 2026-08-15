package com.tailg.plus.data.model

import com.squareup.moshi.JsonClass

/**
 * Port of `lib/models/battery_setup_models.dart`.
 *
 * [OfficialBatteryType] (`BatteryTypeBean` from `app/centralControl/batteryType(/ext)`)
 * and [OfficialBatterySpec] (`BatterySpecBean` from `app/centralControl/batterySpecByType`)
 * are wire DTOs → Moshi adapters (canonical key names). The hand-written
 * `fromJson` companions preserve the Dart fallback-key semantics
 * (`type`/`id`/`typeId`, `code`/`specCode`/`id`, …).
 *
 * [AffirmBatteryInfoRequest] is the payload for `POST app/centralControl/batterySetUp`.
 * It is intentionally NOT annotated: the Dart `toBody()` trims and omits empty
 * values, a shape a generated Moshi adapter cannot reproduce. The wire body is
 * built by [AffirmBatteryInfoRequest.toBody] exactly like the Dart original.
 */
@JsonClass(generateAdapter = true)
data class OfficialBatteryType(
    val type: String = "",
    val name: String = "",
) {
    /** Official custom type uses type id `"0"` and free-form V/AH inputs. */
    val isCustom: Boolean get() = type == "0"

    val isValid: Boolean get() = type.isNotEmpty() && name.isNotEmpty()

    companion object {
        fun fromJson(json: Map<String, Any?>): OfficialBatteryType = OfficialBatteryType(
            type = parsePersistedString(json["type"] ?: json["id"] ?: json["typeId"]),
            name = parsePersistedString(json["name"] ?: json["label"] ?: json["typeName"]),
        )
    }
}

@JsonClass(generateAdapter = true)
data class OfficialBatterySpec(
    val code: String = "",
    val spec: String = "",
) {
    val isValid: Boolean get() = code.isNotEmpty() && spec.isNotEmpty()

    companion object {
        fun fromJson(json: Map<String, Any?>): OfficialBatterySpec = OfficialBatterySpec(
            code = parsePersistedString(json["code"] ?: json["specCode"] ?: json["id"]),
            spec = parsePersistedString(json["spec"] ?: json["label"] ?: json["name"]),
        )
    }
}

/** Payload for `POST app/centralControl/batterySetUp` (affirmBatteryInfo). */
data class AffirmBatteryInfoRequest(
    val carId: String,
    val batteryCode: String? = null,
    val bindDate: String? = null,
    val batteryType: String? = null,
    val batteryVoltage: String? = null,
    val batteryCapacity: String? = null,
) {
    /** Builds the JSON body; trims values and omits empty ones (Dart `toBody()`). */
    fun toBody(): Map<String, String> {
        val body = linkedMapOf<String, String>("carId" to carId)

        fun put(key: String, value: String?) {
            val text = value?.trim() ?: ""
            if (text.isNotEmpty()) body[key] = text
        }

        put("batteryCode", batteryCode)
        put("bindDate", bindDate)
        put("batteryType", batteryType)
        put("batteryVoltage", batteryVoltage)
        put("batteryCapacity", batteryCapacity)
        return body
    }
}
