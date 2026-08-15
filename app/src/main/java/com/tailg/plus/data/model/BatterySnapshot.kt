package com.tailg.plus.data.model

import com.tailg.plus.util.formatFixed
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Port of `lib/models/battery_snapshot.dart`.
 *
 * A UI/aggregation snapshot (not a wire DTO) → no Moshi adapter. Combines up
 * to three official sources (vehicle state / battery API / BMS API) and tracks
 * where each metric came from via [BatteryDataSource].
 *
 * `DateTime` → `java.time.Instant`; formatting helpers use [formatFixed]
 * (Dart `toStringAsFixed` half-even semantics).
 */
enum class BatteryDataSource(val label: String) {
    OFFICIAL_VEHICLE("官方车辆状态"),
    OFFICIAL_BATTERY("官方电池接口"),
    OFFICIAL_BMS("官方 BMS 接口"),
    BMS_RESERVED("官方字段预留"),
}

data class BatterySnapshot(
    val percent: Int?,
    val voltage: Double?,
    val temperature: Double?,
    val signalStrength: Int?,
    val faults: List<String>,
    val updatedAt: Instant,
    val remainingMileage: String?,
    val totalMileage: String?,
    val capacitance: String?,
    val consumePowerPercent: String?,
    val loopCount: String?,
    val batteryScore: String?,
    val officialVehicle: OfficialVehicle?,
    val officialBatteryInfo: OfficialBatteryInfo?,
    val officialBmsInfo: OfficialBmsInfo?,
    val percentSource: BatteryDataSource,
    val voltageSource: BatteryDataSource,
    val temperatureSource: BatteryDataSource,
    val mileageSource: BatteryDataSource,
) {
    val hasData: Boolean
        get() = percent != null || voltage != null || temperature != null ||
            signalStrength != null || hasOfficialBatteryInfo || hasOfficialBmsInfo ||
            officialVehicle != null

    val hasOfficialBatteryInfo: Boolean get() = officialBatteryInfo?.hasData == true

    val hasOfficialBmsInfo: Boolean get() = officialBmsInfo?.hasData == true

    val dataSourceLabel: String
        get() {
            val labels = linkedSetOf<String>()
            if (percent != null) labels.add(percentSource.label)
            if (voltage != null) labels.add(voltageSource.label)
            if (temperature != null) labels.add(temperatureSource.label)
            if (hasOfficialBatteryInfo) {
                labels.add(BatteryDataSource.OFFICIAL_BATTERY.label)
            }
            if (hasOfficialBmsInfo) {
                labels.add(BatteryDataSource.OFFICIAL_BMS.label)
            }
            if (officialVehicle != null) {
                labels.add(BatteryDataSource.OFFICIAL_VEHICLE.label)
            }
            if (labels.isEmpty()) return "等待数据"
            return labels.joinToString(" / ")
        }

    val estimatedRangeKm: Double?
        get() {
            val officialRange = parseNumber(remainingMileage)
            if (officialRange != null) return officialRange
            val value = percent
            return value?.let { it.coerceIn(0, 100) * KM_PER_PERCENT }
        }

    val bms: BmsSnapshot get() = BmsSnapshot.fromBatterySnapshot(this)

    val healthLabel: String
        get() {
            if (faults.isNotEmpty()) return "异常"
            val value = percent
            if (value == null) return "等待数据"
            if (value <= 20) return "低电量"
            return "正常"
        }

    companion object {
        private const val KM_PER_PERCENT = 0.65
        private val NUMBER_PATTERN = Regex("-?\\d+(\\.\\d+)?")

        fun fromSources(
            officialVehicle: OfficialVehicle? = null,
            officialBatteryInfo: OfficialBatteryInfo? = null,
            officialBmsInfo: OfficialBmsInfo? = null,
            updatedAt: Instant? = null,
            clock: () -> Instant = { Instant.now() },
        ): BatterySnapshot {
            val bmsDetail = officialBmsInfo?.primaryDetail
            val officialPercent = parsePercent(officialBatteryInfo?.dumpEnergyPercent)
            val bmsPercent = parsePercent(bmsDetail?.soc ?: officialBmsInfo?.soc)
            val vehiclePercent = officialVehicle?.electricQuantity
            val officialVoltage = parseNumber(officialBatteryInfo?.voltage)
            val bmsVoltage = parseNumber(bmsDetail?.currentBatteryVoltage)
            val vehicleVoltage = officialVehicle?.voltage
            val officialTemperature = parseNumber(officialBatteryInfo?.temperature)
            val bmsTemperature = parseNumber(bmsDetail?.batteryTemperature)
            val vehicleMileage = officialVehicle?.mileage
            val officialRemainingMileage = cleanText(officialBatteryInfo?.remainingMileage)

            val percent = (officialPercent ?: bmsPercent ?: vehiclePercent)?.coerceIn(0, 100)
            val voltage = officialVoltage ?: bmsVoltage ?: vehicleVoltage
            val temperature = officialTemperature ?: bmsTemperature
            val remainingMileage = firstText(
                listOf(officialRemainingMileage, estimatedMileageText(percent)),
            )
            val totalMileage = firstText(
                listOf(officialBatteryInfo?.mileage, vehicleMileage?.let { formatFixed(it, 1) }),
            )
            val loopCount = firstText(
                listOf(officialBatteryInfo?.loopCount, bmsDetail?.batteryCyclesNum),
            )
            val capacitance = firstText(
                listOf(
                    officialBatteryInfo?.capacitance,
                    bmsDetail?.batteryCapacity,
                    officialBmsInfo?.batterySpec,
                ),
            )
            val batteryScore = firstText(
                listOf(officialBatteryInfo?.batteryScore, bmsDetail?.soh),
            )

            return BatterySnapshot(
                percent = percent,
                voltage = voltage,
                temperature = temperature,
                signalStrength = null,
                faults = emptyList(),
                updatedAt = updatedAt ?: clock(),
                remainingMileage = remainingMileage,
                totalMileage = totalMileage,
                capacitance = capacitance,
                consumePowerPercent = cleanText(officialBatteryInfo?.consumePowerPercent),
                loopCount = loopCount,
                batteryScore = batteryScore,
                officialVehicle = officialVehicle,
                officialBatteryInfo = officialBatteryInfo,
                officialBmsInfo = officialBmsInfo,
                percentSource = dataSource(
                    officialBatteryValue = officialPercent,
                    officialBmsValue = bmsPercent,
                    officialVehicleValue = vehiclePercent,
                ),
                voltageSource = dataSource(
                    officialBatteryValue = officialVoltage,
                    officialBmsValue = bmsVoltage,
                    officialVehicleValue = vehicleVoltage,
                ),
                temperatureSource = dataSource(
                    officialBatteryValue = officialTemperature,
                    officialBmsValue = bmsTemperature,
                ),
                mileageSource = mileageSource(
                    officialRemainingMileage = officialRemainingMileage,
                    vehicleMileage = vehicleMileage,
                    percent = percent,
                ),
            )
        }

        private fun parsePercent(value: String?): Int? {
            val parsed = parseNumber(value?.replace("%", ""))
            return parsed?.let { roundHalfAwayFromZero(it) }?.coerceIn(0, 100)
        }

        private fun parseNumber(value: String?): Double? {
            val cleaned = cleanText(value)
            if (cleaned == null) return null
            val match = NUMBER_PATTERN.find(cleaned)
            if (match == null) return null
            return match.value.toDoubleOrNull()
        }

        private fun firstText(values: List<String?>): String? {
            for (value in values) {
                val cleaned = cleanText(value)
                if (cleaned != null) return cleaned
            }
            return null
        }

        private fun cleanText(value: String?): String? {
            val text = value?.trim() ?: return null
            // Official battery API may return "0" for 今日耗电 / 循环次数 — keep it.
            if (text.isEmpty() || text == "--") return null
            if (text.lowercase() == "null") return null
            return text
        }

        /**
         * Display helper: empty/missing → fallback; keeps zero values.
         * Dart `toStringAsFixed`-style units: no space between value and unit.
         */
        fun displayMetric(
            value: String?,
            unit: String = "",
            missing: String = "待读取",
        ): String {
            val cleaned = cleanText(value)
            if (cleaned == null) return missing
            if (unit.isEmpty()) return cleaned
            if (cleaned.endsWith(unit)) return cleaned
            return "$cleaned$unit"
        }

        private fun estimatedMileageText(percent: Int?): String? {
            val value = percent?.coerceIn(0, 100)?.toDouble()
            return value?.let { formatFixed(it * KM_PER_PERCENT, 1) }
        }

        /** Dart `double.round()`: nearest integer, ties away from zero. */
        private fun roundHalfAwayFromZero(value: Double): Int =
            if (value >= 0) floor(value + 0.5).toInt() else ceil(value - 0.5).toInt()
    }
}

private fun dataSource(
    officialBatteryValue: Any? = null,
    officialBmsValue: Any? = null,
    officialVehicleValue: Any? = null,
): BatteryDataSource = when {
    officialBatteryValue != null -> BatteryDataSource.OFFICIAL_BATTERY
    officialBmsValue != null -> BatteryDataSource.OFFICIAL_BMS
    officialVehicleValue != null -> BatteryDataSource.OFFICIAL_VEHICLE
    else -> BatteryDataSource.BMS_RESERVED
}

private fun mileageSource(
    officialRemainingMileage: String?,
    vehicleMileage: Double?,
    percent: Int?,
): BatteryDataSource = when {
    officialRemainingMileage != null -> BatteryDataSource.OFFICIAL_BATTERY
    vehicleMileage != null -> BatteryDataSource.OFFICIAL_VEHICLE
    else -> BatteryDataSource.BMS_RESERVED
}

data class BmsSnapshot(
    val estimateBatteryCapacity: String? = null,
    val soc: String? = null,
    val soh: String? = null,
    val currentBatteryVoltage: String? = null,
    val batteryChargeStatus: String? = null,
    val batteryCapacity: String? = null,
    val batteryCurrent: String? = null,
    val ambientTemperature: String? = null,
    val batteryCyclesNum: String? = null,
    val batteryTemperature: String? = null,
    val batteryType: String? = null,
    val hwVer: String? = null,
    val swVer: String? = null,
    val remainingMileage: String? = null,
    val totalMileage: String? = null,
    val consumePowerPercent: String? = null,
    val batteryScore: String? = null,
    val socSource: BatteryDataSource = BatteryDataSource.BMS_RESERVED,
    val voltageSource: BatteryDataSource = BatteryDataSource.BMS_RESERVED,
    val temperatureSource: BatteryDataSource = BatteryDataSource.BMS_RESERVED,
) {
    val fields: List<BmsField> get() = bmsFields(this)

    companion object {
        fun fromBatterySnapshot(snapshot: BatterySnapshot): BmsSnapshot {
            val detail = snapshot.officialBmsInfo?.primaryDetail
            val soh = detail?.soh
            val current = detail?.batteryCurrent
            val type = detail?.batteryType
            val version = detail?.batteryVersion
            val chargeStatus = if (detail == null) {
                null
            } else {
                if (detail.batteryChargeNum.isNotEmpty()) "充电 ${detail.batteryChargeNum}" else null
            }
            return BmsSnapshot(
                estimateBatteryCapacity = snapshot.capacitance,
                soc = snapshot.percent?.toString() ?: detail?.soc,
                soh = if (soh?.isEmpty() == true) null else soh,
                currentBatteryVoltage = snapshot.voltage?.let { formatFixed(it, 1) }
                    ?: detail?.currentBatteryVoltage,
                batteryChargeStatus = chargeStatus,
                batteryCapacity = snapshot.capacitance ?: detail?.batteryCapacity,
                batteryCurrent = if (current?.isEmpty() == true) null else current,
                batteryCyclesNum = snapshot.loopCount ?: detail?.batteryCyclesNum,
                batteryTemperature = snapshot.temperature?.let { formatFixed(it, 1) }
                    ?: detail?.batteryTemperature,
                batteryType = if (type?.isEmpty() == true) null else type,
                hwVer = if (version?.isEmpty() == true) null else version,
                swVer = if (version?.isEmpty() == true) null else version,
                remainingMileage = snapshot.remainingMileage,
                totalMileage = snapshot.totalMileage,
                consumePowerPercent = snapshot.consumePowerPercent,
                batteryScore = snapshot.batteryScore ?: soh,
                socSource = snapshot.percentSource,
                voltageSource = snapshot.voltageSource,
                temperatureSource = snapshot.temperatureSource,
            )
        }
    }
}

private fun bmsFields(snapshot: BmsSnapshot): List<BmsField> = listOf(
    BmsField(
        "估算容量",
        snapshot.estimateBatteryCapacity,
        source = BatteryDataSource.OFFICIAL_BATTERY,
    ),
    BmsField("SOC", snapshot.soc, unit = "%", source = snapshot.socSource),
    BmsField(
        "SOH",
        snapshot.soh,
        unit = "%",
        source = if (snapshot.soh == null) BatteryDataSource.BMS_RESERVED else BatteryDataSource.OFFICIAL_BMS,
    ),
    BmsField(
        "当前电压",
        snapshot.currentBatteryVoltage,
        unit = "V",
        source = snapshot.voltageSource,
    ),
    BmsField(
        "充电状态",
        snapshot.batteryChargeStatus,
        source = if (snapshot.batteryChargeStatus == null) BatteryDataSource.BMS_RESERVED else BatteryDataSource.OFFICIAL_BMS,
    ),
    BmsField(
        "电池容量",
        snapshot.batteryCapacity,
        source = if (snapshot.batteryCapacity == null) BatteryDataSource.BMS_RESERVED else BatteryDataSource.OFFICIAL_BATTERY,
    ),
    BmsField(
        "电池电流",
        snapshot.batteryCurrent,
        unit = "A",
        source = if (snapshot.batteryCurrent == null) BatteryDataSource.BMS_RESERVED else BatteryDataSource.OFFICIAL_BMS,
    ),
    BmsField(
        "环境温度",
        snapshot.ambientTemperature,
        unit = "°C",
        source = BatteryDataSource.BMS_RESERVED,
    ),
    BmsField(
        "循环次数",
        snapshot.batteryCyclesNum,
        source = if (snapshot.batteryCyclesNum == null) BatteryDataSource.BMS_RESERVED else BatteryDataSource.OFFICIAL_BATTERY,
    ),
    BmsField(
        "电池温度",
        snapshot.batteryTemperature,
        unit = "°C",
        source = snapshot.temperatureSource,
    ),
    BmsField(
        "电池类型",
        snapshot.batteryType,
        source = if (snapshot.batteryType == null) BatteryDataSource.BMS_RESERVED else BatteryDataSource.OFFICIAL_BMS,
    ),
    BmsField(
        "硬件版本",
        snapshot.hwVer,
        source = if (snapshot.hwVer == null) BatteryDataSource.BMS_RESERVED else BatteryDataSource.OFFICIAL_BMS,
    ),
    BmsField(
        "软件版本",
        snapshot.swVer,
        source = if (snapshot.swVer == null) BatteryDataSource.BMS_RESERVED else BatteryDataSource.OFFICIAL_BMS,
    ),
)

data class BmsField(
    val label: String,
    val value: String?,
    val unit: String? = null,
    val source: BatteryDataSource,
) {
    val hasValue: Boolean get() = !value.isNullOrBlank()

    val displayValue: String
        get() {
            val text = value
            if (text == null || text.trim().isEmpty()) return "待读取"
            return if (unit == null) text else "$text$unit"
        }
}
