package com.tailg.plus.data.model

import com.tailg.plus.data.preferences.DistanceUnitPreference
import org.junit.Assert.assertEquals
import org.junit.Test

class OfficialRideStatisticsTest {

    @Test
    fun formatMileage_preservesMetricAndConvertsImperial() {
        assertEquals("1.60", OfficialRideStatistics.formatMileage("1609", DistanceUnitPreference.Metric))
        assertEquals("1.00", OfficialRideStatistics.formatMileage("1609", DistanceUnitPreference.Imperial))
        assertEquals("--", OfficialRideStatistics.formatMileage("", DistanceUnitPreference.Imperial))
        assertEquals("--", OfficialRideStatistics.formatMileage("invalid", DistanceUnitPreference.Imperial))
    }

    @Test
    fun formatMileage_handlesLargeCumulativeOdometersWithoutIntegerOverflow() {
        assertEquals("35000.00", OfficialRideStatistics.formatMileageKm("35000000"))
    }
}
