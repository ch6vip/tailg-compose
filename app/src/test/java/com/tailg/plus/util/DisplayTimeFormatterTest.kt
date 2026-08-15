package com.tailg.plus.util

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayTimeFormatterTest {

    @Test
    fun formatDateText_pads() {
        assertEquals("2024-05-01", formatDateText(LocalDateTime.of(2024, 5, 1, 9, 5)))
        assertEquals("2024-12-31", formatDateText(LocalDateTime.of(2024, 12, 31, 23, 59)))
    }

    @Test
    fun normalizeOfficialDateKey_variants() {
        assertEquals("", normalizeOfficialDateKey("  "))
        assertEquals("2024-05-01", normalizeOfficialDateKey("2024-05-01"))
        assertEquals("2024-05-01", normalizeOfficialDateKey("2024/05/01"))
        assertEquals("2024-05-01", normalizeOfficialDateKey("2024-05-01 12:34:56"))
        assertEquals("2024-05-01", normalizeOfficialDateKey("2024/05/01T12:34"))
    }

    @Test
    fun formatDateMinuteText_andMonthText() {
        val time = LocalDateTime.of(2024, 5, 1, 9, 5)
        assertEquals("2024-05-01 09:05", formatDateMinuteText(time))
        assertEquals("2024-05", formatMonthText(time))
    }

    @Test
    fun parseMonthText_acceptsOnlyValidMonths() {
        assertEquals(LocalDateTime.of(2024, 5, 1, 0, 0), parseMonthText("2024-05"))
        assertNull(parseMonthText("2024-13"))
        assertNull(parseMonthText("2024-00"))
        assertNull(parseMonthText("abc"))
        assertNull(parseMonthText("2024-05-01"))
    }

    @Test
    fun shiftMonthText_wrapsYear() {
        assertEquals("2024-04", shiftMonthText("2024-05", -1))
        assertEquals("2023-12", shiftMonthText("2024-01", -1))
    }

    @Test
    fun shiftMonthText_blocksFutureMonths() {
        val clock = { LocalDateTime.of(2024, 5, 15, 0, 0) }
        assertNull(shiftMonthText("2024-12", 1, clock))
        assertEquals("2024-05", shiftMonthText("2024-04", 1, clock))
    }

    @Test
    fun hourMinuteAndClockTexts() {
        assertEquals("09:05", formatHourMinuteText(9, 5))
        assertEquals("23:59", formatHourMinuteText(23, 59))
        assertEquals("09:05:03", formatLogClockTime(LocalDateTime.of(2024, 5, 1, 9, 5, 3)))
        assertEquals("05/01 09:05", formatMonthDayMinuteText(LocalDateTime.of(2024, 5, 1, 9, 5)))
    }

    @Test
    fun formatRelativeSyncText_ages() {
        val now = LocalDateTime.of(2024, 5, 1, 12, 0, 0)
        assertEquals("尚未同步", formatRelativeSyncText(null) { now })
        assertEquals("刚刚同步", formatRelativeSyncText(now.minusSeconds(5)) { now })
        assertEquals("30秒前同步", formatRelativeSyncText(now.minusSeconds(30)) { now })
        assertEquals("5分钟前同步", formatRelativeSyncText(now.minusMinutes(5)) { now })
        assertEquals("3小时前同步", formatRelativeSyncText(now.minusHours(3)) { now })
        assertEquals("04/30 06:00 同步", formatRelativeSyncText(now.minusHours(30)) { now })
    }
}
