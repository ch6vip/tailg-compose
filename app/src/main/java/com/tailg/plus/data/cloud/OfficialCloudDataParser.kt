package com.tailg.plus.data.cloud

import com.tailg.plus.data.model.OfficialBatteryInfo
import com.tailg.plus.data.model.OfficialBatterySpec
import com.tailg.plus.data.model.OfficialBatteryType
import com.tailg.plus.data.model.OfficialBmsInfo
import com.tailg.plus.data.model.OfficialCloudMessage
import com.tailg.plus.data.model.OfficialFenceData
import com.tailg.plus.data.model.OfficialTravelDay
import com.tailg.plus.data.model.OfficialTravelPoint
import com.tailg.plus.data.model.OfficialUserProfile
import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.data.model.OfficialVehicleLocation
import com.tailg.plus.data.model.parsePersistedMap
import com.tailg.plus.data.model.parsePersistedMapList

/**
 * Port of `OfficialCloudDataParser` from `lib/services/official_cloud_data_parser.dart`.
 *
 * Lenient, shape-tolerant parsing of the `data` portion of official envelopes:
 * lists (optionally wrapping a single item), nested `data`/`info`/`result`
 * wrappers, and page envelopes (`records` / `list` / `rows`). The typed
 * `fromJson` companions in `data.model` do the per-field fallback-key work.
 */
object OfficialCloudDataParser {

    fun vehicles(data: Any?): List<OfficialVehicle> =
        maps(data, wrapSingle = true)
            .map { OfficialVehicle.fromJson(it) }
            .filter { hasVehicleIdentity(it) }

    fun batteryInfo(data: Any?): OfficialBatteryInfo =
        OfficialBatteryInfo.fromJson(unwrapBatteryPayload(map(data)))

    fun bmsInfo(data: Any?): OfficialBmsInfo {
        if (data is Iterable<*>) {
            return OfficialBmsInfo.fromJson(mapOf("details" to data.toList()))
        }
        val map = map(data)
        if (map.isEmpty()) return OfficialBmsInfo.fromJson(emptyMap())
        // Nested: { data: { details: ... } } / { info: ... }
        for (key in listOf("data", "info", "result", "bmsBatteryInfo")) {
            val nested = parsePersistedMap(map[key])
            if (nested != null && nested.isNotEmpty()) {
                return OfficialBmsInfo.fromJson(nested)
            }
        }
        return OfficialBmsInfo.fromJson(map)
    }

    fun vehicleLocation(data: Any?): OfficialVehicleLocation =
        OfficialVehicleLocation.fromJson(map(data))

    fun fenceData(data: Any?): OfficialFenceData =
        OfficialFenceData.fromJson(map(data))

    fun travelDays(data: Any?): List<OfficialTravelDay> =
        maps(data)
            .map { OfficialTravelDay.fromJson(it) }
            .filter { it.hasData }

    fun travelPoints(data: Any?): List<OfficialTravelPoint> =
        maps(data)
            .map { OfficialTravelPoint.fromJson(it) }
            .filter { it.hasCoordinate }

    fun vehicleMessages(data: Any?): List<OfficialCloudMessage> =
        pageRecords(data)
            .map { OfficialCloudMessage.vehicle(it) }
            .filter { it.title.isNotEmpty() || it.content.isNotEmpty() }

    fun systemMessages(data: Any?): List<OfficialCloudMessage> =
        pageRecords(data)
            .map { OfficialCloudMessage.system(it) }
            .filter { it.title.isNotEmpty() || it.content.isNotEmpty() }

    fun userProfile(data: Any?): OfficialUserProfile? {
        val map = map(data)
        if (map.isEmpty()) return null
        val profile = OfficialUserProfile.fromJson(map)
        // Treat completely empty payloads as absent rather than a blank profile.
        if (!profile.hasDisplayName &&
            profile.signature.trim().isEmpty() &&
            profile.avatarPath.trim().isEmpty() &&
            profile.id.trim().isEmpty()
        ) {
            return null
        }
        return profile
    }

    fun batteryTypes(data: Any?): List<OfficialBatteryType> =
        maps(data)
            .map { OfficialBatteryType.fromJson(it) }
            .filter { it.isValid }

    fun batterySpecs(data: Any?): List<OfficialBatterySpec> =
        maps(data)
            .map { OfficialBatterySpec.fromJson(it) }
            .filter { it.isValid }

    private fun pageRecords(data: Any?): List<Map<String, Any?>> {
        if (data is Map<*, *>) {
            val records = data["records"] ?: data["list"] ?: data["rows"]
            return maps(records)
        }
        return maps(data)
    }

    private fun map(data: Any?): Map<String, Any?> =
        if (data is Map<*, *>) parsePersistedMap(data) ?: emptyMap() else emptyMap()

    private fun maps(data: Any?, wrapSingle: Boolean = false): List<Map<String, Any?>> =
        parsePersistedMapList(payloadItems(data, wrapSingle = wrapSingle))

    private fun payloadItems(data: Any?, wrapSingle: Boolean): List<Any?> {
        if (data is List<*>) return data.toList()
        if (wrapSingle && data != null) return listOf(data)
        return emptyList()
    }

    private fun unwrapBatteryPayload(map: Map<String, Any?>): Map<String, Any?> {
        if (map.isEmpty()) return map
        // Already looks like BatteryInfoBean.
        val directKeys = setOf(
            "dumpEnergyPercent",
            "consumePowerPercent",
            "loopCount",
            "temperature",
            "voltage",
            "remainingMileage",
            "capacitance",
            "batteryScore",
        )
        if (map.keys.any { it in directKeys }) return map

        for (key in listOf("batteryInfo", "info", "data", "result", "bean", "battery")) {
            val nested = parsePersistedMap(map[key])
            if (nested != null && nested.isNotEmpty()) {
                return unwrapBatteryPayload(nested)
            }
        }
        return map
    }

    private fun hasVehicleIdentity(vehicle: OfficialVehicle): Boolean =
        vehicle.carId.isNotEmpty() ||
            vehicle.imei.isNotEmpty() ||
            vehicle.imeiGps.isNotEmpty() ||
            vehicle.btmac.isNotEmpty() ||
            vehicle.btname.isNotEmpty() ||
            vehicle.carName.isNotEmpty() ||
            vehicle.frame.isNotEmpty()
}
