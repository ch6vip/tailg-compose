@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tailg.plus.ui.components.material

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LucideDatePickerTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun calendarContainsLeapDayOnlyInLeapYearsAndKeepsLocaleWeekStart() {
    val leapMonth = lucideMonthCells(YearMonth.of(2024, 2), DayOfWeek.MONDAY)
    assertEquals(LocalDate.of(2024, 2, 1), leapMonth[3])
    assertEquals(LocalDate.of(2024, 2, 29), leapMonth.filterNotNull().last())
    assertEquals(29, leapMonth.count { it != null })
    val sundayStart = lucideMonthCells(YearMonth.of(2024, 2), DayOfWeek.SUNDAY)
    assertEquals(LocalDate.of(2024, 2, 1), sundayStart[4])
    assertEquals(28, lucideMonthCells(YearMonth.of(2100, 2), DayOfWeek.MONDAY).count { it != null })
    assertEquals(29, lucideMonthCells(YearMonth.of(2000, 2), DayOfWeek.MONDAY).count { it != null })
  }

  @Test
  fun utcConversionDoesNotShiftDatesBeforeTheEpochOrAcrossTimeZones() {
    assertEquals(LocalDate.of(1969, 12, 31), lucideUtcDate(-1L))
    val leapDay = LocalDate.of(2024, 2, 29)
    val originalZone = TimeZone.getDefault()
    try {
      listOf("Pacific/Kiritimati", "Pacific/Pago_Pago").forEach { zone ->
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        assertEquals(1_709_164_800_000L, lucideUtcMillis(leapDay))
        assertEquals(leapDay, lucideUtcDate(lucideUtcMillis(leapDay) + 86_399_999L))
      }
    } finally {
      TimeZone.setDefault(originalZone)
    }
  }

  @Test
  fun browsingMonthsCrossesYearsAndStopsAtBothRangeLimitsWithoutSelecting() {
    val state = state(LocalDate.of(2024, 12, 31), years = 2024..2025)
    val originalSelection = state.selectedDateMillis
    assertTrue(state.moveLucideMonth(1))
    assertEquals(LocalDate.of(2025, 1, 1), lucideUtcDate(state.displayedMonthMillis))
    assertTrue(state.moveLucideMonth(11))
    assertFalse(state.moveLucideMonth(1))
    assertEquals(LocalDate.of(2025, 12, 1), lucideUtcDate(state.displayedMonthMillis))
    assertTrue(state.moveLucideMonth(-23))
    assertFalse(state.moveLucideMonth(-1))
    assertEquals(LocalDate.of(2024, 1, 1), lucideUtcDate(state.displayedMonthMillis))
    assertEquals(originalSelection, state.selectedDateMillis)
  }

  @Test
  fun changingTheDisplayedYearFromLeapDayPreservesTheUnconfirmedSelection() {
    val state = state(LocalDate.of(2024, 2, 29), years = 2023..2025)
    assertTrue(state.showLucideYear(2023))
    assertEquals(LocalDate.of(2023, 2, 1), lucideUtcDate(state.displayedMonthMillis))
    assertEquals(LocalDate.of(2024, 2, 29), lucideUtcDate(state.selectedDateMillis!!))
    assertFalse(state.showLucideYear(2022))
    assertEquals(LocalDate.of(2023, 2, 1), lucideUtcDate(state.displayedMonthMillis))
  }

  @Test
  fun calendarAndInputCannotSelectBlockedDaysYearsOrOutOfRangeDates() {
    val blocked = LocalDate.of(2024, 2, 29)
    val state = state(LocalDate.of(2024, 2, 28), years = 2023..2025, selectableDates = object : SelectableDates {
      override fun isSelectableYear(year: Int) = year != 2025
      override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis != lucideUtcMillis(blocked)
    })
    val original = state.selectedDateMillis
    assertFalse(state.selectLucideDate(blocked))
    assertFalse(state.selectLucideDate(LocalDate.of(2025, 1, 1)))
    assertFalse(state.selectLucideDate(LocalDate.of(2026, 1, 1)))
    assertFalse(state.showLucideYear(2025))
    assertEquals(original, state.selectedDateMillis)
    assertTrue(state.selectLucideDate(LocalDate.of(2023, 12, 1)))
    assertEquals(LocalDate.of(2023, 12, 1), lucideUtcDate(state.selectedDateMillis!!))
  }

  @Test
  fun numericInputRejectsInvalidDatesAndAcceptsLocalizedDecimalDigits() {
    assertNull(parseLucideDate("2023", "2", "29"))
    assertNull(parseLucideDate("2100", "2", "29"))
    assertNull(parseLucideDate("2024", "4", "31"))
    assertNull(parseLucideDate("", "2", "29"))
    assertEquals(LocalDate.of(2024, 2, 29), parseLucideDate("2024", "02", "29"))
    assertEquals(LocalDate.of(2024, 2, 29), parseLucideDate("٢٠٢٤", "٠٢", "٢٩"))
    assertEquals(listOf('M', 'd', 'y'), lucideDateFieldOrder(Locale.US))
    assertEquals(listOf('d', 'M', 'y'), lucideDateFieldOrder(Locale.UK))
    assertEquals(listOf('y', 'M', 'd'), lucideDateFieldOrder(Locale.CHINA))
  }

  @Test
  fun clickingALeapDaySelectsItAndYearPickerChangesOnlyTheDisplayedYear() {
    val state = state(LocalDate.of(2024, 2, 28), years = 2023..2025)
    render(state)
    compose.onNodeWithTag("lucide-date-2024-02-29").performClick().assertIsSelected()
    compose.runOnIdle { assertEquals(LocalDate.of(2024, 2, 29), lucideUtcDate(state.selectedDateMillis!!)) }
    compose.onNodeWithTag("lucide-date-years").performClick()
    compose.onNodeWithTag("lucide-date-year-2023").performClick()
    compose.onNodeWithTag("lucide-date-2023-02-28").assertExists()
    compose.onNodeWithTag("lucide-date-2023-02-29").assertDoesNotExist()
    compose.runOnIdle { assertEquals(LocalDate.of(2024, 2, 29), lucideUtcDate(state.selectedDateMillis!!)) }
  }

  @Test
  fun blockedDatesAndNavigationAtRangeEdgesAreDisabledInTheCalendar() {
    val state = state(LocalDate.of(2024, 1, 1), years = 2024..2024, selectableDates = object : SelectableDates {
      override fun isSelectableDate(utcTimeMillis: Long) = lucideUtcDate(utcTimeMillis).dayOfMonth != 2
    })
    render(state)
    compose.onNodeWithTag("lucide-date-previous").assertIsNotEnabled()
    compose.onNodeWithTag("lucide-date-2024-01-02").assertIsNotEnabled()
    compose.runOnIdle { state.moveLucideMonth(11) }
    compose.onNodeWithTag("lucide-date-next").assertIsNotEnabled()
  }

  @Test
  fun invalidNumericInputClearsSelectionUntilAnAllowedDateIsEntered() {
    val state = state(LocalDate.of(2024, 2, 29), years = 2023..2025)
    render(state)
    compose.onNodeWithTag("lucide-date-mode").performClick()
    compose.onNodeWithTag("lucide-date-input-y").performTextReplacement("2023")
    compose.runOnIdle { assertNull(state.selectedDateMillis) }
    compose.onNodeWithTag("lucide-date-input-d").performTextReplacement("28")
    compose.runOnIdle { assertEquals(LocalDate.of(2023, 2, 28), lucideUtcDate(state.selectedDateMillis!!)) }
    compose.onNodeWithTag("lucide-date-mode").performClick()
    compose.onNodeWithTag("lucide-date-2023-02-28").assertIsSelected()
    compose.runOnIdle { assertEquals(DisplayMode.Picker, state.displayMode) }
  }

  private fun render(state: DatePickerState) {
    compose.setContent {
      MaterialTheme {
        Box(Modifier.width(360.dp)) { LucideDatePicker(state) }
      }
    }
  }

  private fun state(
    selected: LocalDate,
    years: IntRange,
    selectableDates: SelectableDates = object : SelectableDates {},
  ): DatePickerState = DatePickerState(
    locale = Locale.US,
    initialSelectedDateMillis = lucideUtcMillis(selected),
    yearRange = years,
    selectableDates = selectableDates,
  )
}
