package com.tailg.plus.data.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression coverage for the BMS hardware/software version split: the two UI
 * rows ("硬件版本"/"软件版本") must not both show the legacy `batteryVersion`
 * when the payload carries distinct `hwVer`/`swVer`.
 */
class OfficialBmsVersionTest {

    private fun snapshot(bms: OfficialBmsInfo?): BatterySnapshot = BatterySnapshot(
        percent = null,
        voltage = null,
        temperature = null,
        signalStrength = null,
        faults = emptyList(),
        updatedAt = Instant.EPOCH,
        remainingMileage = null,
        totalMileage = null,
        capacitance = null,
        consumePowerPercent = null,
        loopCount = null,
        batteryScore = null,
        officialVehicle = null,
        officialBatteryInfo = null,
        officialBmsInfo = bms,
        percentSource = BatteryDataSource.OFFICIAL_VEHICLE,
        voltageSource = BatteryDataSource.OFFICIAL_VEHICLE,
        temperatureSource = BatteryDataSource.OFFICIAL_VEHICLE,
        mileageSource = BatteryDataSource.OFFICIAL_VEHICLE,
    )

    @Test
    fun hwAndSwVersionsAreParsedDistinctly() {
        val detail = OfficialBmsDetail.fromJson(mapOf("hwVer" to "HW-1", "swVer" to "SW-2"))

        assertEquals("HW-1", detail.hwVer)
        assertEquals("SW-2", detail.swVer)
        // Legacy fallback field still prefers batteryVersion ?: swVer ?: hwVer.
        assertEquals("SW-2", detail.batteryVersion)
    }

    @Test
    fun bmsSnapshotKeepsHardwareAndSoftwareVersionsSeparate() {
        val info = OfficialBmsInfo.fromJson(
            mapOf("details" to listOf(mapOf("hwVer" to "HW-1", "swVer" to "SW-2", "soc" to "80"))),
        )

        val bms = snapshot(info).bms

        assertEquals("HW-1", bms.hwVer)
        assertEquals("SW-2", bms.swVer)
    }

    @Test
    fun bmsSnapshotFallsBackToLegacyBatteryVersionForBothRows() {
        val info = OfficialBmsInfo.fromJson(
            mapOf("details" to listOf(mapOf("batteryVersion" to "V-9", "soc" to "80"))),
        )

        val bms = snapshot(info).bms

        assertEquals("V-9", bms.hwVer)
        assertEquals("V-9", bms.swVer)
    }
}