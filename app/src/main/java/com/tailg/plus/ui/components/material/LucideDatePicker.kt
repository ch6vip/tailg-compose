@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tailg.plus.ui.components.material

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tailg.plus.R
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import java.text.NumberFormat
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Lucide-only calendar presentation with Material's saved state and UTC date contract.
 * Browsing months or years does not change the selection. Both calendar and numeric input
 * enforce [DatePickerState.yearRange] and [DatePickerState.selectableDates].
 */
@Composable
fun LucideDatePicker(state: DatePickerState, modifier: Modifier = Modifier) {
  val locale = state.locale
  val formatter = remember { DatePickerDefaults.dateFormatter() }
  val displayedMonth = YearMonth.from(lucideUtcDate(state.displayedMonthMillis))
  var showYears by rememberSaveable(state) { mutableStateOf(false) }
  val inputMode = state.displayMode == DisplayMode.Input
  val title = stringResource(if (inputMode) R.string.lucide_date_input else R.string.lucide_date_select)
  val selectedLabel = formatter.formatDate(state.selectedDateMillis, locale) ?: title

  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f).semantics { heading() }) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(selectedLabel, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
      }
      IconButton(
        onClick = {
          showYears = false
          state.displayMode = if (inputMode) DisplayMode.Picker else DisplayMode.Input
        },
        modifier = Modifier.size(48.dp).testTag("lucide-date-mode"),
      ) {
        LucideIcon(
          if (inputMode) Lucide.calendar else Lucide.edit,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          contentDescription = stringResource(if (inputMode) R.string.lucide_date_show_calendar else R.string.lucide_date_show_input),
        )
      }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    if (inputMode) {
      LucideDateInput(state)
    } else {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val rotation by animateFloatAsState(if (showYears) 180f else 0f, label = "calendar-years")
        TextButton(
          onClick = { showYears = !showYears },
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("lucide-date-years"),
        ) {
          Text(
            formatter.formatMonthYear(state.displayedMonthMillis, locale).orEmpty(),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Start,
            style = MaterialTheme.typography.titleSmall,
          )
          LucideIcon(
            Lucide.chevronDown,
            modifier = Modifier.rotate(rotation),
            color = MaterialTheme.colorScheme.primary,
            contentDescription = stringResource(if (showYears) R.string.lucide_date_show_days else R.string.lucide_date_select_year),
          )
        }
        if (!showYears) {
          IconButton(
            onClick = { state.moveLucideMonth(-1) },
            enabled = lucideShiftedMonth(displayedMonth, -1, state.yearRange) != null,
            modifier = Modifier.size(48.dp).testTag("lucide-date-previous"),
          ) {
            LucideIcon(
              Lucide.chevronLeft,
              color = if (lucideShiftedMonth(displayedMonth, -1, state.yearRange) == null)
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface,
              contentDescription = stringResource(R.string.lucide_date_previous_month),
            )
          }
          IconButton(
            onClick = { state.moveLucideMonth(1) },
            enabled = lucideShiftedMonth(displayedMonth, 1, state.yearRange) != null,
            modifier = Modifier.size(48.dp).testTag("lucide-date-next"),
          ) {
            LucideIcon(
              Lucide.chevronRight,
              color = if (lucideShiftedMonth(displayedMonth, 1, state.yearRange) == null)
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface,
              contentDescription = stringResource(R.string.lucide_date_next_month),
            )
          }
        }
      }
      if (showYears) {
        LucideYearGrid(state, displayedMonth.year) { showYears = false }
      } else {
        LucideMonthGrid(state, displayedMonth)
      }
    }
  }
}

@Composable
private fun LucideMonthGrid(state: DatePickerState, month: YearMonth) {
  val locale = state.locale
  val firstDay = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
  val weekdays = remember(firstDay) { List(7) { firstDay.plus(it.toLong()) } }
  val cells = remember(month, firstDay) { lucideMonthCells(month, firstDay) }
  val numberFormat = remember(locale) { NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false } }
  val formatter = remember { DatePickerDefaults.dateFormatter() }
  val today = LocalDate.now()
  val selectedDate = state.selectedDateMillis?.let(::lucideUtcDate)

  Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
    Row(Modifier.fillMaxWidth()) {
      weekdays.forEach { day ->
        Text(
          day.getDisplayName(TextStyle.SHORT_STANDALONE, locale),
          modifier = Modifier.weight(1f).padding(vertical = 8.dp).clearAndSetSemantics {},
          textAlign = TextAlign.Center,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Column(Modifier.semantics { collectionInfo = CollectionInfo(rowCount = 6, columnCount = 7) }) {
      cells.chunked(7).forEachIndexed { rowIndex, week ->
        Row(Modifier.fillMaxWidth()) {
          week.forEachIndexed { columnIndex, date ->
            if (date == null) {
              Spacer(Modifier.weight(1f).height(48.dp))
            } else {
              val enabled = state.isLucideDateSelectable(date)
              val selected = date == selectedDate
              val formattedDate = formatter.formatDate(lucideUtcMillis(date), locale, forContentDescription = true).orEmpty()
              val description = if (date == today) stringResource(R.string.lucide_date_today, formattedDate) else formattedDate
              Box(
                modifier = Modifier.weight(1f).height(48.dp).clip(CircleShape)
                  .testTag("lucide-date-$date")
                  .selectable(selected = selected, enabled = enabled, role = Role.RadioButton) {
                    state.selectLucideDate(date)
                  }
                  .semantics(mergeDescendants = true) {
                    contentDescription = description
                    collectionItemInfo = CollectionItemInfo(rowIndex, 1, columnIndex, 1)
                  },
                contentAlignment = Alignment.Center,
              ) {
                val circle = Modifier.size(36.dp).clip(CircleShape)
                  .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                Box(
                  modifier = if (date == today && !selected) circle.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape) else circle,
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    numberFormat.format(date.dayOfMonth),
                    modifier = Modifier.clearAndSetSemantics {},
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected || date == today) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                      !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                      selected -> MaterialTheme.colorScheme.onPrimary
                      else -> MaterialTheme.colorScheme.onSurface
                    },
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LucideYearGrid(state: DatePickerState, displayedYear: Int, onSelected: () -> Unit) {
  val years = remember(state.yearRange) { state.yearRange.toList() }
  val currentIndex = (displayedYear - state.yearRange.first).coerceAtLeast(0)
  val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = ((currentIndex / 3 - 2).coerceAtLeast(0) * 3))
  val numberFormat = remember(state.locale) { NumberFormat.getIntegerInstance(state.locale).apply { isGroupingUsed = false } }
  LazyVerticalGrid(
    columns = GridCells.Fixed(3),
    state = gridState,
    modifier = Modifier.fillMaxWidth().height(320.dp).padding(horizontal = 8.dp).testTag("lucide-date-year-grid"),
  ) {
    items(years, key = { it }) { year ->
      val enabled = state.selectableDates.isSelectableYear(year)
      val selected = year == displayedYear
      Box(
        modifier = Modifier.height(48.dp).padding(horizontal = 4.dp).clip(MaterialTheme.shapes.small)
          .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
          .testTag("lucide-date-year-$year")
          .selectable(selected = selected, enabled = enabled, role = Role.RadioButton) {
            if (state.showLucideYear(year)) onSelected()
          },
        contentAlignment = Alignment.Center,
      ) {
        Text(
          numberFormat.format(year),
          style = MaterialTheme.typography.bodyLarge,
          color = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            selected -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurface
          },
        )
      }
    }
  }
}

@Composable
private fun LucideDateInput(state: DatePickerState) {
  val initialDate = state.selectedDateMillis?.let(::lucideUtcDate)
  var year by rememberSaveable(state) { mutableStateOf(initialDate?.year?.toString().orEmpty()) }
  var month by rememberSaveable(state) { mutableStateOf(initialDate?.monthValue?.toString().orEmpty()) }
  var day by rememberSaveable(state) { mutableStateOf(initialDate?.dayOfMonth?.toString().orEmpty()) }
  val order = remember(state.locale) { lucideDateFieldOrder(state.locale) }
  val complete = year.isNotEmpty() && month.isNotEmpty() && day.isNotEmpty()
  val parsedDate = parseLucideDate(year, month, day)
  val valid = parsedDate != null && state.isLucideDateSelectable(parsedDate)
  val error = when {
    !complete -> null
    parsedDate == null -> stringResource(R.string.lucide_date_invalid)
    parsedDate.year !in state.yearRange -> stringResource(R.string.lucide_date_range, state.yearRange.first.toString(), state.yearRange.last.toString())
    !valid -> stringResource(R.string.lucide_date_unavailable)
    else -> null
  }

  fun updateSelection() {
    val candidate = parseLucideDate(year, month, day)
    if (candidate == null || !state.selectLucideDate(candidate)) state.selectedDateMillis = null
  }

  Column(Modifier.fillMaxWidth().padding(16.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      order.forEachIndexed { index, field ->
        OutlinedTextField(
          value = when (field) { 'y' -> year; 'M' -> month; else -> day },
          onValueChange = { input ->
            val digits = input.filter { Character.digit(it, 10) >= 0 }.take(if (field == 'y') 9 else 2)
            when (field) { 'y' -> year = digits; 'M' -> month = digits; else -> day = digits }
            updateSelection()
          },
          label = { Text(stringResource(when (field) {
            'y' -> R.string.lucide_date_year
            'M' -> R.string.lucide_date_month
            else -> R.string.lucide_date_day
          })) },
          modifier = Modifier.weight(if (field == 'y') 1.3f else 1f).testTag("lucide-date-input-$field"),
          singleLine = true,
          isError = error != null,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = if (index == order.lastIndex) ImeAction.Done else ImeAction.Next),
        )
      }
    }
    Text(
      error ?: stringResource(R.string.lucide_date_range, state.yearRange.first.toString(), state.yearRange.last.toString()),
      modifier = Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
      style = MaterialTheme.typography.bodySmall,
      color = if (error == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
    )
  }
}

internal fun lucideUtcDate(millis: Long): LocalDate = LocalDate.ofEpochDay(Math.floorDiv(millis, 86_400_000L))

internal fun lucideUtcMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun lucideMonthCells(month: YearMonth, firstDay: DayOfWeek): List<LocalDate?> {
  val offset = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
  return List(42) { index ->
    val day = index - offset + 1
    if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
  }
}

internal fun lucideShiftedMonth(month: YearMonth, delta: Int, yearRange: IntRange): YearMonth? =
  month.plusMonths(delta.toLong()).takeIf { it.year in yearRange }

internal fun DatePickerState.moveLucideMonth(delta: Int): Boolean {
  val current = YearMonth.from(lucideUtcDate(displayedMonthMillis))
  val next = lucideShiftedMonth(current, delta, yearRange) ?: return false
  displayedMonthMillis = lucideUtcMillis(next.atDay(1))
  return true
}

internal fun DatePickerState.showLucideYear(year: Int): Boolean {
  if (year !in yearRange || !selectableDates.isSelectableYear(year)) return false
  val current = YearMonth.from(lucideUtcDate(displayedMonthMillis))
  displayedMonthMillis = lucideUtcMillis(current.withYear(year).atDay(1))
  return true
}

internal fun DatePickerState.isLucideDateSelectable(date: LocalDate): Boolean =
  date.year in yearRange && selectableDates.isSelectableYear(date.year) && selectableDates.isSelectableDate(lucideUtcMillis(date))

internal fun DatePickerState.selectLucideDate(date: LocalDate): Boolean {
  if (!isLucideDateSelectable(date)) return false
  selectedDateMillis = lucideUtcMillis(date)
  displayedMonthMillis = lucideUtcMillis(date.withDayOfMonth(1))
  return true
}

internal fun parseLucideDate(year: String, month: String, day: String): LocalDate? {
  fun digits(value: String): Int? {
    if (value.isBlank()) return null
    val normalized = value.map { Character.digit(it, 10).takeIf { digit -> digit >= 0 } ?: return null }.joinToString("")
    return normalized.toIntOrNull()
  }
  val y = digits(year) ?: return null
  val m = digits(month) ?: return null
  val d = digits(day) ?: return null
  return try { LocalDate.of(y, m, d) } catch (_: DateTimeException) { null }
}

internal fun lucideDateFieldOrder(locale: Locale): List<Char> {
  val pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.SHORT, null, IsoChronology.INSTANCE, locale)
  val unquoted = pattern.replace(Regex("'([^']|'')*'"), "")
  return unquoted.mapNotNull { when (it) { 'y', 'u' -> 'y'; 'L', 'M' -> 'M'; 'd' -> 'd'; else -> null } }
    .distinct().takeIf { it.size == 3 } ?: listOf('y', 'M', 'd')
}
