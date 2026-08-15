package com.tailg.plus.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Port of `lib/models/official_vehicle.dart` — part 3/3: [OfficialBatteryInfo]
 * (official `BatteryInfoBean`) and its private field-mapping helpers.
 *
 * Wire DTO → Moshi adapter (canonical keys). The companion [fromJson] keeps
 * the Dart fallback semantics (`dumpEnergyPercent`/`dumpEnergy`/`soc`/`SOC`/
 * `electricQuantity`/`batteryPercent`, `remainingMileage`/`remainMileage`/
 * `leftMileage`/`estimateMileage`, …) and the "keep real zero" rule: numeric
 * `0` for 今日耗电 / 循环次数 is preserved, never normalized away.
 */
@JsonClass(generateAdapter = true)
data class OfficialBatteryInfo(
    @Json(ignore = true) val raw: Map<String, Any?> = emptyMap(),
    val dumpEnergyPercent: String = "",
    val dumpEnergyPercentLabel: String = "",
    val remainingMileage: String = "",
    val mileage: String = "",
    val capacitance: String = "",
    val consumePowerPercent: String = "",
    val loopCount: String = "",
    val temperature: String = "",
    val batteryScore: String = "",
    val voltage: String = "",
) {
    val hasData: Boolean
        get() = dumpEnergyPercent.isNotEmpty() || remainingMileage.isNotEmpty() ||
            mileage.isNotEmpty() || capacitance.isNotEmpty() || consumePowerPercent.isNotEmpty() ||
            loopCount.isNotEmpty() || temperature.isNotEmpty() || batteryScore.isNotEmpty() ||
            voltage.isNotEmpty()

    companion object {
        fun fromJson(json: Map<String, Any?>): OfficialBatteryInfo {
            // Official BatteryInfoBean field names, plus common alternates seen in
            // nested / BMS payloads. Numeric 0 must be kept (今日耗电/循环次数 can be 0).
            val dumpEnergyPercent = batteryField(
                json,
                listOf("dumpEnergyPercent", "dumpEnergy", "soc", "SOC", "electricQuantity", "batteryPercent"),
            )
            val dumpEnergyPercentLabel = batteryField(
                json,
                listOf("dumpEnergyPercentLabel", "socLabel"),
            ) ?: if (dumpEnergyPercent == null) "" else "$dumpEnergyPercent%"
            return OfficialBatteryInfo(
                raw = stringKeyedMap(json),
                dumpEnergyPercent = dumpEnergyPercent ?: "",
                dumpEnergyPercentLabel = dumpEnergyPercentLabel,
                remainingMileage = batteryField(
                    json,
                    listOf("remainingMileage", "remainMileage", "leftMileage", "estimateMileage"),
                ) ?: "",
                mileage = batteryField(
                    json,
                    listOf("mileage", "totalMileage", "odometer"),
                ) ?: "",
                capacitance = batteryField(
                    json,
                    listOf("capacitance", "capacity", "batteryCapacity", "estimateBatteryCapacity"),
                ) ?: "",
                consumePowerPercent = batteryField(
                    json,
                    listOf("consumePowerPercent", "consumePower", "todayConsumePower", "todayPowerConsume", "powerConsumePercent", "dayConsumePower"),
                ) ?: "",
                loopCount = batteryField(
                    json,
                    listOf("loopCount", "cycleCount", "cycles", "batteryCyclesNum", "batteryCycle", "cycleTimes"),
                ) ?: "",
                temperature = batteryField(
                    json,
                    listOf("temperature", "batteryTemperature", "temp", "batteryTemp", "currentTemperature"),
                ) ?: "",
                batteryScore = batteryField(
                    json,
                    listOf("batteryScore", "score", "soh", "SOH", "healthScore"),
                ) ?: "",
                voltage = batteryField(
                    json,
                    listOf("voltage", "batteryVoltage", "currentBatteryVoltage", "vol"),
                ) ?: "",
            )
        }

        /** Read first non-empty battery metric. Keeps numeric `0` / `"0"`. */
        private fun batteryField(json: Map<String, Any?>, keys: List<String>): String? {
            for (key in keys) {
                if (!json.containsKey(key)) continue
                val cleaned = clean(json[key])
                if (cleaned != null) return cleaned
            }
            return null
        }

        private fun clean(value: Any?): String? {
            if (value == null) return null
            // Keep real zero values from the official battery API.
            if (value is Number) return value.toString()
            val text = value.toString().trim()
            if (text.isEmpty() || text == "--" || text.lowercase() == "null") return null
            return text
        }
    }
}
