package com.tailg.plus.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Port of `lib/models/official_vehicle.dart` — part 1/3:
 * [OfficialCloudCommand], [OfficialVehicle], [OfficialGaragePage] and their
 * private helpers. (Parts 2/3 and 3/3: `OfficialTravel.kt`, `OfficialBatteryInfo.kt`.)
 *
 * [OfficialVehicle] is a wire DTO (`app/userCarPage` / `CarControlInfoBean`)
 * → Moshi adapter using the canonical key names. The companion [fromJson]
 * keeps the full Dart lenient semantics: `btmac`/`btMac`/`BTMAC`/`bluetoothMac`,
 * `mac`/`Mac`/`identityMac`/`bleMac`, `passwordInfo`/`password_info`/`pwdInfo`/`password`,
 * `mqUsername`/`mqUserName`/`mqttUsername`, `mqPassword`/`mqttPassword` fallbacks,
 * plus the `raw` enrichment (normalized mac + passwordInfo). Note the generated
 * adapter sets `raw = emptyMap()` (`@Json(ignore = true)`), so any code that
 * needs the raw-derived getters ([mainBlePassword], [childBlePasswords],
 * [batterySpecLabel], feature flags, …) must construct via [OfficialVehicle.fromJson].
 */
enum class OfficialCloudCommand(val apiName: String, val commandCode: CommandCode) {
    LOCK("lock", CommandCode.LOCK),
    UNLOCK("unlock", CommandCode.UNLOCK),
    START("start", CommandCode.POWER_ON),
    STOP("stop", CommandCode.POWER_OFF),
    SEARCH("search", CommandCode.FIND),
    OPEN_CUSHION("openCushion", CommandCode.OPEN_SEAT);

    companion object {
        fun fromCommandCode(command: CommandCode): OfficialCloudCommand? =
            entries.firstOrNull { it.commandCode == command }
    }
}

@JsonClass(generateAdapter = true)
data class OfficialVehicle(
    val imei: String = "",
    val imeiGps: String = "",
    val carId: String = "",
    val carName: String = "",
    val carNickName: String = "",
    val carPhoto: String = "",
    val frame: String = "",
    val defenceStatus: Int? = null,
    val acc: Int? = null,
    val electricQuantity: Int? = null,
    val voltage: Double? = null,
    val online: Boolean = false,
    val btname: String = "",
    val btmac: String = "",
    val longitude: String = "",
    val latitude: String = "",
    val modelType: Int? = null,
    val isGps: Int? = null,
    val mqHost: String = "",
    val mqPort: String = "",
    /** Official C18/QGJ MQTT auth (`CarControlInfoBean.mqUsername`). */
    val mqUsername: String = "",
    /** Official C18/QGJ MQTT auth (`CarControlInfoBean.mqPassword`). */
    val mqPassword: String = "",
    val mileage: Double? = null,
    @Json(ignore = true) val raw: Map<String, Any?> = emptyMap(),
) {
    val key: String
        get() {
            if (carId.isNotEmpty()) return carId
            if (imei.isNotEmpty()) return imei
            if (imeiGps.isNotEmpty()) return imeiGps
            return "$btmac|$btname|$carName"
        }

    val displayName: String
        get() = firstNonBlank(listOf(carNickName, carName, btname, frame, imei)) ?: "官方车辆"

    val normalizedDeviceMac: String
        get() {
            val compact = btmac.replace(BTMAC_SEPARATOR_PATTERN, "").uppercase()
            if (compact.length != 12) return ""
            return compact.chunked(2).joinToString(":")
        }

    /**
     * QGJ compares this backend identity with advertisement manufacturer data.
     * Other stacks usually return the same value as [normalizedDeviceMac].
     */
    val bleIdentityMac: String
        get() {
            val rawMac = parsePersistedString(
                raw["mac"] ?: raw["Mac"] ?: raw["identityMac"] ?: raw["bleMac"],
            )
            val source = if (rawMac.isNotEmpty()) rawMac else btmac
            val compact = source.replace(BTMAC_SEPARATOR_PATTERN, "").uppercase()
            return if (compact.length == 12) compact else ""
        }

    val mainBlePassword: Int?
        get() {
            val passwordInfo = passwordInfoMap()
            val direct = parsePersistedInt(
                passwordInfo?.get("main") ?: passwordInfo?.get("mainPassword") ?: passwordInfo?.get("password"),
            )
            if (direct != null) return direct
            return parsePersistedInt(raw["mainPassword"] ?: raw["mainPwd"] ?: raw["password"])
        }

    val childBlePasswords: List<Int>
        get() {
            val passwordInfo = passwordInfoMap()
            val source = passwordInfo?.get("children")
                ?: passwordInfo?.get("child")
                ?: passwordInfo?.get("childrenPassword")
                ?: raw["childrenPassword"]
                ?: raw["children"]
            if (source !is Iterable<*>) return emptyList()
            return source.mapNotNull { parsePersistedInt(it) }
        }

    val shareCarFlag: Boolean get() = parsePersistedBool(raw["shareCarFlag"])

    val simNo: String get() = parsePersistedString(raw["simNo"])

    val iccId: String get() = parsePersistedString(raw["iccId"] ?: raw["iccid"])

    /** GarageV2 `UserCarPageDataBean.isUsing`. */
    val isUsing: Boolean get() = parsePersistedBool(raw["isUsing"] ?: raw["using"])

    /** GarageV2 hides the share badge when this value is zero. */
    val shareCount: Int get() = parsePersistedInt(raw["shareCount"]) ?: 0

    val authStatus: Int? get() = parsePersistedInt(raw["authStatus"])

    val isTelligence: String get() = parsePersistedString(raw["isTelligence"])

    /** Official `CarControlInfoBean.batterySpecLabel` — "当前使用：xx". */
    val batterySpecLabel: String
        get() = parsePersistedString(
            raw["batterySpecLabel"] ?: raw["batterySpec"] ?: raw["batteryTypeLabel"] ?: raw["batteryType"],
        )

    /** Official `CarControlInfoBean.batteryTypeId`. */
    val batteryTypeId: String get() = parsePersistedString(raw["batteryTypeId"] ?: raw["batteryTypeID"])

    /** Official `CarControlInfoBean.batteryBindDate` (yyyy-MM-dd…). */
    val batteryBindDate: String
        get() = parsePersistedString(raw["batteryBindDate"] ?: raw["bindDate"] ?: raw["batteryBindTime"])

    /** Official `CarControlInfoBean.bmsTlvType` for BMS/TLV page routing. */
    val bmsTlvType: String get() = parsePersistedString(raw["bmsTlvType"] ?: raw["bmsTlv"])

    /** Official `CarControlInfoBean.cityAddress` (weather lookup seed). */
    val cityAddress: String
        get() = parsePersistedString(raw["cityAddress"] ?: raw["city"] ?: raw["cityName"])

    val hasDeviceMac: Boolean get() = normalizedDeviceMac.isNotEmpty()

    val hasGpsService: Boolean
        get() {
            val type = modelType
            return type != null && GPS_MODEL_TYPES.contains(type) && imeiGps.isNotEmpty()
        }

    val commandImei: String get() = if (hasGpsService) imeiGps else imei

    val isLocked: Boolean get() = defenceStatus == 1

    val isPowerOn: Boolean get() = acc == 1

    /**
     * Official `CarControlInfoBean.isCushionLock`. Null means the backend did
     * not provide the capability, which is intentionally treated as unknown.
     */
    val isCushionLockSupported: Boolean?
        get() {
            for (key in listOf("isCushionLock", "cushionLock", "isSeatLock", "seatLock")) {
                if (!raw.containsKey(key)) continue
                val value = raw[key]
                when (value) {
                    is Boolean -> return value
                    is Number -> return value.toDouble() != 0.0
                    else -> {
                        val text = value?.toString()?.trim()?.lowercase()
                        if (text == "1" || text == "true") return true
                        if (text == "0" || text == "false") return false
                        return null
                    }
                }
            }
            return null
        }

    val supportsNavigationProjection: Boolean
        get() = rawFeatureFlag(raw, listOf("navigationProjection", "navProjection", "screenProjection", "mapEs", "mapProjection"))

    val supportsCamera: Boolean
        get() = rawFeatureFlag(raw, listOf("camera", "cameraService", "videoService"))

    val supportsSmartMeter: Boolean
        get() = rawFeatureFlag(raw, listOf("smartMeter", "smartInstrument", "instrumentService", "sqService"))

    val supportsServiceRenewal: Boolean
        get() = rawFeatureFlag(raw, listOf("bleRenewal", "bluetoothRenewal", "bleRecharge", "bleServiceRenew"))

    val supportsChargingStation: Boolean
        get() = rawFeatureFlag(raw, listOf("chargingStation", "chargeStation", "tailgCharging"))

    val onlineLabel: String get() = if (online) "车辆在线" else "车辆离线"
    val defenceLabel: String get() = if (isLocked) "已设防" else "已解防"
    val powerLabel: String get() = if (isPowerOn) "车辆已启动" else "车辆未启动"

    fun toJson(): Map<String, Any?> {
        val json = raw.toMutableMap()
        json.putAll(
            linkedMapOf(
                "imei" to imei,
                "imeiGps" to imeiGps,
                "carId" to carId,
                "carName" to carName,
                "carNickName" to carNickName,
                "carPhoto" to carPhoto,
                "frame" to frame,
                "defenceStatus" to defenceStatus,
                "acc" to acc,
                "electricQuantity" to electricQuantity,
                "voltage" to voltage,
                "online" to online,
                "btname" to btname,
                "btmac" to btmac,
                "longitude" to longitude,
                "latitude" to latitude,
                "modelType" to modelType,
                "isGps" to isGps,
                "mqHost" to mqHost,
                "mqPort" to mqPort,
                "mqUsername" to mqUsername,
                "mqPassword" to mqPassword,
                "mileage" to mileage,
            ),
        )
        return json
    }

    fun copyWith(
        defenceStatus: Int? = null,
        acc: Int? = null,
        electricQuantity: Int? = null,
        voltage: Double? = null,
        online: Boolean? = null,
        carNickName: String? = null,
        longitude: String? = null,
        latitude: String? = null,
        mileage: Double? = null,
    ): OfficialVehicle = OfficialVehicle(
        imei = imei,
        imeiGps = imeiGps,
        carId = carId,
        carName = carName,
        carNickName = carNickName ?: this.carNickName,
        carPhoto = carPhoto,
        frame = frame,
        defenceStatus = defenceStatus ?: this.defenceStatus,
        acc = acc ?: this.acc,
        electricQuantity = electricQuantity ?: this.electricQuantity,
        voltage = voltage ?: this.voltage,
        online = online ?: this.online,
        btname = btname,
        btmac = btmac,
        longitude = longitude ?: this.longitude,
        latitude = latitude ?: this.latitude,
        modelType = modelType,
        isGps = isGps,
        mqHost = mqHost,
        mqPort = mqPort,
        mqUsername = mqUsername,
        mqPassword = mqPassword,
        mileage = mileage ?: this.mileage,
        raw = raw,
    )

    private fun passwordInfoMap(): Map<String, Any?>? =
        parsePersistedMap(raw["passwordInfo"])
            ?: parsePersistedMap(raw["password_info"])
            ?: parsePersistedMap(raw["pwdInfo"])
            ?: parsePersistedMap(raw["password"])

    companion object {
        private val GPS_MODEL_TYPES = setOf(3, 8, 1501, 1601, 1701)
        private val BTMAC_SEPARATOR_PATTERN = Regex("[^0-9a-fA-F]")

        fun fromJson(json: Map<String, Any?>): OfficialVehicle {
            // Official ControlFragment reads both `mac` (identity) and `btmac`.
            // Some payloads only fill one of them; keep both usable for BLE near-field.
            val btmacRaw = parsePersistedString(
                json["btmac"] ?: json["btMac"] ?: json["BTMAC"] ?: json["bluetoothMac"],
            )
            val identityMacRaw = parsePersistedString(
                json["mac"] ?: json["Mac"] ?: json["identityMac"] ?: json["bleMac"],
            )
            val normalizedBtmac = if (btmacRaw.isNotEmpty()) btmacRaw else identityMacRaw

            // passwordInfo may arrive nested, stringified, or under alternate keys.
            val passwordInfo = parsePersistedMap(json["passwordInfo"])
                ?: parsePersistedMap(json["password_info"])
                ?: parsePersistedMap(json["pwdInfo"])
                ?: parsePersistedMap(json["password"])
            val enriched = json.toMutableMap()
            if (normalizedBtmac.isNotEmpty() && parsePersistedString(enriched["btmac"]).isEmpty()) {
                enriched["btmac"] = normalizedBtmac
            }
            if (identityMacRaw.isNotEmpty() && parsePersistedString(enriched["mac"]).isEmpty()) {
                enriched["mac"] = identityMacRaw
            } else if (identityMacRaw.isEmpty() && normalizedBtmac.isNotEmpty() && parsePersistedString(enriched["mac"]).isEmpty()) {
                // Fall back so QGJ identity path still has a mac-like field.
                enriched["mac"] = normalizedBtmac
            }
            if (passwordInfo != null && enriched["passwordInfo"] !is Map<*, *>) {
                enriched["passwordInfo"] = passwordInfo
            }

            return OfficialVehicle(
                imei = parsePersistedString(json["imei"]),
                imeiGps = parsePersistedString(json["imeiGps"]),
                carId = parsePersistedString(json["carId"]),
                carName = parsePersistedString(json["carName"]),
                carNickName = parsePersistedString(json["carNickName"]),
                carPhoto = parsePersistedString(json["carPhoto"]),
                frame = parsePersistedString(json["frame"]),
                defenceStatus = parsePersistedInt(json["defenceStatus"]),
                acc = parsePersistedInt(json["acc"]),
                electricQuantity = parsePersistedInt(json["electricQuantity"]),
                voltage = parsePersistedDouble(json["voltage"]),
                online = parsePersistedBool(json["online"]),
                btname = parsePersistedString(
                    json["btname"] ?: json["btName"] ?: json["bluetoothName"],
                ),
                btmac = normalizedBtmac,
                longitude = parsePersistedString(json["longitude"]),
                latitude = parsePersistedString(json["latitude"]),
                modelType = parsePersistedInt(json["modelType"]),
                isGps = parsePersistedInt(json["isGps"]),
                mqHost = parsePersistedString(json["mqHost"]),
                mqPort = parsePersistedString(json["mqPort"]),
                mqUsername = parsePersistedString(
                    json["mqUsername"] ?: json["mqUserName"] ?: json["mqttUsername"],
                ),
                mqPassword = parsePersistedString(
                    json["mqPassword"] ?: json["mqttPassword"],
                ),
                mileage = parsePersistedDouble(json["mileage"]),
                raw = enriched.toMap(),
            )
        }

        private fun firstNonBlank(values: List<String>): String? {
            for (value in values) {
                val text = value.trim()
                if (text.isNotEmpty()) return text
            }
            return null
        }

        private fun rawFeatureFlag(raw: Map<String, Any?>, keys: List<String>): Boolean {
            if (raw.isEmpty()) return false
            val targets = keys.map { it.lowercase() }
            return rawEntries(raw).any { (key, value) ->
                val lowerKey = key.lowercase()
                targets.any { lowerKey.contains(it) } && truthyFeatureValue(value)
            }
        }

        private fun rawEntries(raw: Map<String, Any?>, prefix: String = ""): Sequence<Pair<String, Any?>> = sequence {
            for (entry in raw.entries) {
                val key = entry.key.toString()
                val path = if (prefix.isEmpty()) key else "$prefix.$key"
                val value = entry.value
                yield(path to value)
                if (value is Map<*, *>) {
                    val child = value.entries.associate { it.key.toString() to it.value }
                    yieldAll(rawEntries(child, path))
                }
            }
        }

        private fun truthyFeatureValue(value: Any?): Boolean {
            return when (value) {
                is Boolean -> value
            is Number -> value.toDouble() != 0.0
            is String -> {
                val text = value.trim().lowercase()
                if (text.isEmpty()) false
                else text !in FALSY_FEATURE_TEXTS
            }
            is Iterable<*> -> value.iterator().hasNext()
            is Map<*, *> -> {
                for (key in listOf("enabled", "enable", "support", "supported", "open")) {
                    if (value.containsKey(key)) return truthyFeatureValue(value[key])
                }
                value.isNotEmpty()
            }
            else -> value != null
        }

        private val FALSY_FEATURE_TEXTS = setOf("0", "false", "no", "n", "off", "关闭", "无", "none", "null")
    }
}

/**
 * Official `app/userCarPage` page envelope used by GarageV2.
 *
 * NOT annotated: [fromPayload] is the only construction path — it applies
 * per-item lenient parsing + the carId/imei/frame/carName filter and derives
 * `pageIndex`/`pageSize`/`total`/`hasNext` with fallbacks, which a generated
 * Moshi adapter cannot reproduce.
 */
data class OfficialGaragePage(
    val vehicles: List<OfficialVehicle>,
    val pageIndex: Int,
    val pageSize: Int,
    val total: Int,
    val hasNext: Boolean,
) {
    companion object {
        fun fromPayload(
            payload: Any?,
            requestedPageIndex: Int,
            requestedPageSize: Int = 5,
        ): OfficialGaragePage {
            val map = parsePersistedMap(payload) ?: emptyMap()
            val rawItems = map["pageData"] ?: map["records"] ?: map["list"] ?: map["rows"]
            val items = if (rawItems is Iterable<*>) {
                rawItems
                    .mapNotNull { parsePersistedMap(it) }
                    .map { OfficialVehicle.fromJson(it) }
                    .filter { vehicle ->
                        vehicle.carId.isNotEmpty() || vehicle.imei.isNotEmpty() ||
                            vehicle.frame.isNotEmpty() || vehicle.carName.isNotEmpty()
                    }
            } else {
                emptyList()
            }
            val pageIndex = parsePersistedInt(map["nowPageIndex"]) ?: requestedPageIndex
            val pageSize = parsePersistedInt(map["pageSize"]) ?: requestedPageSize
            val total = parsePersistedInt(map["total"]) ?: items.size
            val explicitHasNext = if (map.containsKey("hasNext")) parsePersistedBool(map["hasNext"]) else null
            val hasNext = explicitHasNext
                ?: ((pageSize > 0 && pageIndex * pageSize < total) ||
                    (items.size >= requestedPageSize && total > items.size))
            return OfficialGaragePage(
                vehicles = items,
                pageIndex = pageIndex,
                pageSize = pageSize,
                total = total,
                hasNext = hasNext,
            )
        }
    }
}
