package com.tailg.plus.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayNumberFormatterTest {

    @Test
    fun formatCompactDecimal_dropsTrailingDotZero() {
        assertEquals("12", formatCompactDecimal(12.0))
        assertEquals("12.5", formatCompactDecimal(12.5))
        assertEquals("12", formatCompactDecimal(12.04))
        assertEquals("12.00", formatCompactDecimal(12.0, fractionDigits = 2))
    }

    @Test
    fun formatCompactDecimalText_passthroughNonNumeric() {
        assertEquals("abc", formatCompactDecimalText("abc"))
        assertEquals("12", formatCompactDecimalText("12.0"))
        assertEquals("12.5", formatCompactDecimalText("12.5"))
        assertEquals("12", formatCompactDecimalText(" 12.0 "))
    }

    @Test
    fun formatDistanceMeters_switchesAtOneKm() {
        assertEquals("500m", formatDistanceMeters(500.0))
        assertEquals("999m", formatDistanceMeters(999.4))
        assertEquals("1km", formatDistanceMeters(1000.0))
        assertEquals("1.5km", formatDistanceMeters(1500.0))
    }

    @Test
    fun parseTravelMileageMeters_parsesIntegerPart() {
        assertEquals(0.0, parseTravelMileageMeters(null), 0.0)
        assertEquals(0.0, parseTravelMileageMeters(""), 0.0)
        assertEquals(0.0, parseTravelMileageMeters("abc"), 0.0)
        assertEquals(1234.0, parseTravelMileageMeters("1234.56"), 0.0)
        assertEquals(150.0, parseTravelMileageMeters("-150.9"), 0.0)
        assertEquals(12.0, parseTravelMileageMeters(" 12.5 km "), 0.0)
    }

    @Test
    fun travelMetersToKm_dividesByThousand() {
        assertEquals(1.5, travelMetersToKm(1500.0), 0.0)
    }

    @Test
    fun formatTravelMileageMeters_listVsRideStats() {
        assertEquals("500m", formatTravelMileageMeters(500.0))
        assertEquals("57.29km", formatTravelMileageMeters(57290.0))
        assertEquals("0.57km", formatTravelMileageMeters(572.0, alwaysKm = true))
        assertEquals("--", formatTravelMileageMeters(Double.NaN))
        assertEquals("--", formatTravelMileageMeters(Double.POSITIVE_INFINITY))
    }

    @Test
    fun formatTravelMileageMetersText_blankIsEmpty() {
        assertEquals("", formatTravelMileageMetersText(null))
        assertEquals("", formatTravelMileageMetersText("  "))
        assertEquals("57.29km", formatTravelMileageMetersText("57290"))
    }

    @Test
    fun formatDecimalDown_truncatesTowardZeroAndTrims() {
        assertEquals("12.34", formatDecimalDown(12.345))
        assertEquals("12.3", formatDecimalDown(12.345, fractionDigits = 1))
        assertEquals("12", formatDecimalDown(12.345, fractionDigits = 0))
        assertEquals("-12.34", formatDecimalDown(-12.345))
        assertEquals("0", formatDecimalDown(0.0001, fractionDigits = 2))
        assertEquals("12", formatDecimalDown(12.0, fractionDigits = 2))
        assertEquals("12.5", formatDecimalDown(12.5, fractionDigits = 2))
    }
}
