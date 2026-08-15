package com.tailg.plus.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Port of `lib/models/official_vehicle_self_check.dart`.
 *
 * Wire DTO → Moshi adapter; the wire key for [message] is `msg` (Dart reads
 * `json['msg']`, with no `message` fallback). [data] is kept as `Any?` like
 * the Dart `Object? data`.
 */
@JsonClass(generateAdapter = true)
data class OfficialVehicleSelfCheck(
    @Json(ignore = true) val raw: Map<String, Any?> = emptyMap(),
    val code: Int? = null,
    @Json(name = "msg") val message: String = "",
    val data: Any? = null,
) {
    val hasData: Boolean get() = data != null

    val dataMap: Map<String, Any?> get() = dataMap(data)

    val displayMessage: String
        get() {
            val text = message.trim()
            if (text.isNotEmpty()) return text
            if (code != null) return "code=$code"
            return "自检已返回"
        }

    companion object {
        fun fromResponse(json: Map<String, Any?>): OfficialVehicleSelfCheck = OfficialVehicleSelfCheck(
            raw = stringKeyedMap(json),
            code = parsePersistedInt(json["code"]),
            message = json["msg"]?.toString() ?: "",
            data = json["data"],
        )

        private fun dataMap(value: Any?): Map<String, Any?> =
            if (value is Map<*, *>) stringKeyedMap(value) else emptyMap()
    }
}
