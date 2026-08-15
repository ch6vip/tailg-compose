package com.tailg.plus.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Port of `lib/models/official_bms_info.dart`.
 *
 * Official `BmsBatteryInfoBean` from `POST app/mine/bmsBatteryInfo`. Wire DTOs
 * → Moshi adapters (canonical key names); the `fromJson` companions keep the
 * Dart fallback-key semantics (`soh`/`SOH`, `currentBatteryVoltage`/`batteryVoltage`,
 * `batteryCapacity`/`capacitance`, `batteryCyclesNum`/`loopCount`/`cycles`,
 * `batteryTemperature`/`temperature`, `batteryVersion`/`swVer`/`hwVer`).
 */
@JsonClass(generateAdapter = true)
data class OfficialBmsInfo(
    @Json(ignore = true) val raw: Map<String, Any?> = emptyMap(),
    val imei: String = "",
    val num: String = "",
    val soc: String = "",
    val batterySpec: String = "",
    val details: List<OfficialBmsDetail> = emptyList(),
) {
    val hasData: Boolean
        get() = details.isNotEmpty() || soc.isNotEmpty() || batterySpec.isNotEmpty() || imei.isNotEmpty()

    val primaryDetail: OfficialBmsDetail? get() = details.firstOrNull()

    companion object {
        fun fromJson(json: Map<String, Any?>): OfficialBmsInfo {
            val detailsRaw = json["details"]
            val details = mutableListOf<OfficialBmsDetail>()
            if (detailsRaw is Iterable<*>) {
                for (item in detailsRaw) {
                    val map = parsePersistedMap(item)
                    if (map != null) details.add(OfficialBmsDetail.fromJson(map))
                }
            }
            return OfficialBmsInfo(
                raw = stringKeyedMap(json),
                imei = parsePersistedString(json["imei"]),
                num = parsePersistedString(json["num"]),
                soc = parsePersistedString(json["soc"]),
                batterySpec = parsePersistedString(json["batterySpec"]),
                details = details.toList(),
            )
        }
    }
}

@JsonClass(generateAdapter = true)
data class OfficialBmsDetail(
    @Json(ignore = true) val raw: Map<String, Any?> = emptyMap(),
    val name: String = "",
    val sn: String = "",
    val soc: String = "",
    val soh: String = "",
    val currentBatteryVoltage: String = "",
    val batteryVoltage: String = "",
    val batteryCurrent: String = "",
    val batteryCapacity: String = "",
    val batteryCyclesNum: String = "",
    val batteryTemperature: String = "",
    val batteryType: String = "",
    val batteryVersion: String = "",
    val batteryChargeNum: String = "",
    val batteryDischargeNum: String = "",
) {
    val hasData: Boolean
        get() = soc.isNotEmpty() || soh.isNotEmpty() || currentBatteryVoltage.isNotEmpty() ||
            batteryCyclesNum.isNotEmpty() || batteryTemperature.isNotEmpty() || batteryCapacity.isNotEmpty()

    companion object {
        fun fromJson(json: Map<String, Any?>): OfficialBmsDetail = OfficialBmsDetail(
            raw = stringKeyedMap(json),
            name = parsePersistedString(json["name"]),
            sn = parsePersistedString(json["sn"]),
            soc = parsePersistedString(json["soc"]),
            soh = parsePersistedString(json["soh"] ?: json["SOH"]),
            currentBatteryVoltage = parsePersistedString(
                json["currentBatteryVoltage"] ?: json["batteryVoltage"],
            ),
            batteryVoltage = parsePersistedString(json["batteryVoltage"]),
            batteryCurrent = parsePersistedString(json["batteryCurrent"]),
            batteryCapacity = parsePersistedString(
                json["batteryCapacity"] ?: json["capacitance"],
            ),
            batteryCyclesNum = parsePersistedString(
                json["batteryCyclesNum"] ?: json["loopCount"] ?: json["cycles"],
            ),
            batteryTemperature = parsePersistedString(
                json["batteryTemperature"] ?: json["temperature"],
            ),
            batteryType = parsePersistedString(json["batteryType"]),
            batteryVersion = parsePersistedString(
                json["batteryVersion"] ?: json["swVer"] ?: json["hwVer"],
            ),
            batteryChargeNum = parsePersistedString(json["batteryChargeNum"]),
            batteryDischargeNum = parsePersistedString(json["batteryDischargeNum"]),
        )
    }
}
