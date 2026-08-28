package com.tailg.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.data.cloud.OfficialCloudState
import com.tailg.plus.data.model.OfficialTravelDay
import com.tailg.plus.data.model.OfficialTravelRecord
import com.tailg.plus.data.model.formatCompactDuration
import com.tailg.plus.data.model.sumTravelDurationSeconds
import com.tailg.plus.data.model.sumTravelMileageKm
import com.tailg.plus.data.preferences.DistanceUnitPreference
import com.tailg.plus.ui.components.CyberMapView
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.components.ScaleToFit
import com.tailg.plus.ui.theme.AppIconSizes
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.CyberHomeColors
import com.tailg.plus.ui.theme.LocalDistanceUnitPreference
import com.tailg.plus.util.formatSpeedKilometersPerHourValue
import com.tailg.plus.util.formatTravelMileageMeters
import com.tailg.plus.util.formatTravelMileageMetersText
import com.tailg.plus.util.parseTravelMileageMeters
import com.tailg.plus.util.speedUnitSuffix
import com.tailg.plus.util.travelMetersToKm
import com.tailg.plus.util.formatDateText
import com.tailg.plus.util.formatDateMinuteText
import com.tailg.plus.util.normalizeOfficialDateKey
import com.tailg.plus.util.formatHourMinuteText
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R

/**
 * Travel-history tab of [LocationScreen] (Dart location_travel_tab.dart).
 * Extracted from LocationScreen.kt for maintainability.
 */
@Composable
internal fun TravelTab(
  cloudState: OfficialCloudState,
  onRefresh: () -> Unit,
  onChangeMonth: (Int) -> Unit,
  onRecordTap: (OfficialTravelRecord) -> Unit,
  modifier: Modifier = Modifier,
) {
  val records = remember(cloudState.travelDays) {
    cloudState.travelDays.flatMap { it.records }
  }
  val dateGroups = remember(cloudState.travelDays) {
    cloudState.travelDays.filter { it.records.isNotEmpty() || it.hasData }
  }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 24.dp),
  ) {
    item {
      TravelMonthSelector(
        month = if (cloudState.travelMonth.isEmpty()) stringResource(R.string.location_travel_current_month) else cloudState.travelMonth,
        onPreviousMonth = if (!cloudState.travelLoading) { { onChangeMonth(-1) } } else null,
        onNextMonth = if (!cloudState.travelLoading) { { onChangeMonth(1) } } else null,
      )
    }
    item { Spacer(Modifier.height(14.dp)) }
    // Track map (Dart _MapPanel in location_travel_tab.dart).
    item {
      val trackPoints = remember(cloudState.travelDetails) {
        cloudState.travelDetails.values.flatten().mapNotNull { p ->
          val lat = p.latitude ?: return@mapNotNull null
          val lng = p.longitude ?: return@mapNotNull null
          org.osmdroid.util.GeoPoint(lat, lng)
        }
      }
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(260.dp)
          .clip(RoundedCornerShape(AppRadii.tile))
          .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile)),
      ) {
        CyberMapView(
          latitude = trackPoints.lastOrNull()?.latitude,
          longitude = trackPoints.lastOrNull()?.longitude,
          trackPoints = trackPoints,
          showVehiclePin = trackPoints.isEmpty(),
          modifier = Modifier.fillMaxSize(),
        )
      }
    }
    item { Spacer(Modifier.height(14.dp)) }
    when {
      cloudState.travelLoading -> item { LoadingCard(text = stringResource(R.string.location_travel_loading)) }
      !cloudState.signedIn -> item {
        EmptyCard(icon = Lucide.cloudOff, title = stringResource(R.string.location_travel_need_login), subtitle = stringResource(R.string.location_travel_need_login_hint))
      }
      cloudState.travelError != null -> item {
        EmptyCard(icon = Lucide.info, title = stringResource(R.string.location_travel_not_available), subtitle = cloudState.travelError)
      }
      records.isEmpty() -> item {
        EmptyCard(icon = Lucide.route, title = stringResource(R.string.location_travel_no_data), subtitle = stringResource(R.string.location_travel_no_data_hint))
      }
      else -> itemsIndexed(dateGroups, key = { index, day -> day.travelDate.ifEmpty { "day-$index" } }, contentType = { _, _ -> "travel-day" }) { _, day ->
        Spacer(Modifier.height(10.dp))
        TravelDayCard(
          day = day,
          travelDetails = cloudState.travelDetails,
          detailLoading = cloudState.travelDetailLoading,
           onRecordTap = onRecordTap,
         )
      }
    }
    item { Spacer(Modifier.height(4.dp)) }
    item {
      ReadOnlyNotice(title = stringResource(R.string.location_travel_service), subtitle = stringResource(R.string.location_travel_service_desc))
    }
  }
}

@Composable
internal fun TravelMonthSelector(month: String, onPreviousMonth: (() -> Unit)?, onNextMonth: (() -> Unit)?) {
  Row(
    modifier = Modifier
      .height(48.dp)
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = { onPreviousMonth?.invoke() }, enabled = onPreviousMonth != null) {
      LucideIcon(icon = Lucide.chevronLeft, size = AppIconSizes.md, contentDescription = stringResource(R.string.location_prev_month))
    }
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = month,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W800, color = CyberHomeColors.ink),
        )
        Spacer(Modifier.width(6.dp))
        LucideIcon(icon = Lucide.chevronDown, color = CyberHomeColors.inkFaint, size = AppIconSizes.sm)
      }
    }
    IconButton(onClick = { onNextMonth?.invoke() }, enabled = onNextMonth != null) {
      LucideIcon(icon = Lucide.chevronRight, size = AppIconSizes.md, contentDescription = stringResource(R.string.location_next_month))
    }
  }
}

@Composable
internal fun TravelDayCard(
  day: OfficialTravelDay,
  travelDetails: Map<String, List<com.tailg.plus.data.model.OfficialTravelPoint>>,
  detailLoading: Boolean,
  onRecordTap: (OfficialTravelRecord) -> Unit,
) {
  val distanceUnit = LocalDistanceUnitPreference.current
  val records = day.records
  val summedKm = sumTravelMileageKm(records)
  val totalMeters = if (day.totalMileage.trim().isNotEmpty()) parseTravelMileageMeters(day.totalMileage) else summedKm * 1000
  val mileageParts = travelMileageSummaryParts(totalMeters, distanceUnit)
  val duration = if (day.totalTime.isNotEmpty()) day.totalTime else formatCompactDuration(sumTravelDurationSeconds(records), emptyWhenZero = true)

  Column(
    modifier = Modifier
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(start = 15.dp, top = 14.dp, end = 15.dp, bottom = 12.dp),
  ) {
    Text(
      text = if (day.travelDate.isEmpty()) stringResource(R.string.location_official_travel) else day.travelDate,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W800, color = CyberHomeColors.inkMuted),
    )
    Spacer(Modifier.height(14.dp))
    Row(
      modifier = Modifier
        .height(75.dp)
        .clip(RoundedCornerShape(AppRadii.tile))
        .background(CyberHomeColors.control)
        .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile)),
    ) {
      SummaryValue(label = stringResource(R.string.location_total_count), value = "${records.size}", unit = stringResource(R.string.location_count_unit), modifier = Modifier.weight(1f))
      VerticalDivider(modifier = Modifier.height(75.dp), color = CyberHomeColors.lineStrong)
      SummaryValue(label = stringResource(R.string.location_total_distance), value = mileageParts.first, unit = mileageParts.second, modifier = Modifier.weight(1f))
      VerticalDivider(modifier = Modifier.height(75.dp), color = CyberHomeColors.lineStrong)
      SummaryValue(label = stringResource(R.string.location_total_duration), value = if (duration.isEmpty()) "--" else duration, unit = "", modifier = Modifier.weight(1f))
    }
    if (records.isNotEmpty()) {
      Spacer(Modifier.height(12.dp))
      records.forEach { record ->
        TravelRecordCard(
          record = record,
          loadedPoints = travelDetails[record.deviceTravelId.trim()]?.size,
          loading = detailLoading,
          onTap = { onRecordTap(record) },
        )
        Spacer(Modifier.height(8.dp))
      }
    }
  }
}

@Composable
internal fun SummaryValue(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.padding(12.dp),
  ) {
    ScaleToFit(
      modifier = Modifier.fillMaxWidth().height(28.dp),
      contentAlignment = Alignment.CenterStart,
    ) {
      com.tailg.plus.ui.components.AnimatedValueText(
        value = value,
        unit = unit.takeIf { it.isNotEmpty() },
        style = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
        unitStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkFaint),
        maxLines = 1,
      )
    }
    Spacer(Modifier.height(2.dp))
    Text(
      text = label,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkFaint),
    )
  }
}

@Composable
internal fun TravelRecordCard(
  record: OfficialTravelRecord,
  loadedPoints: Int?,
  loading: Boolean,
  onTap: () -> Unit,
) {
  val distanceUnit = LocalDistanceUnitPreference.current
  val pendingText = stringResource(R.string.battery_pending_read)
  val mileageLabel = if (record.mileage.isEmpty()) {
    pendingText
  } else {
    formatTravelMileageMetersText(record.mileage, unit = distanceUnit)
  }
  val speedLabel = if (record.averageSpeed.isEmpty()) {
    pendingText
  } else {
    "${formatSpeedKilometersPerHourValue(record.averageSpeed, unit = distanceUnit)}${speedUnitSuffix(distanceUnit)}"
  }
  val actionText = when {
    loading && loadedPoints == null -> stringResource(R.string.location_reading)
    loadedPoints != null && loadedPoints >= 2 -> stringResource(R.string.location_loaded_points_format, loadedPoints)
    loadedPoints != null -> stringResource(R.string.location_no_points)
    else -> stringResource(R.string.location_tap_read)
  }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(86.dp)
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .clickable(onClick = onTap),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.width(76.dp)) {
      Text(
        text = if (record.startTime.isEmpty()) "--" else record.startTime,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
      )
      Spacer(Modifier.height(20.dp))
      Text(
        text = if (record.endTime.isEmpty()) "--" else record.endTime,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
      )
    }
    Spacer(Modifier.width(14.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = mileageLabel,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.W800, color = CyberHomeColors.ink),
      )
      Spacer(Modifier.height(6.dp))
      Text(
        text = "$speedLabel  ·  ${record.durationLabel}",
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
      )
    }
    Text(
      text = actionText,
      modifier = Modifier.widthIn(max = 96.dp),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
    )
    Spacer(Modifier.width(8.dp))
    LucideIcon(icon = Lucide.chevronRight, color = CyberHomeColors.inkFaint, size = AppIconSizes.md)
    Spacer(Modifier.width(10.dp))
  }
}

internal fun travelMileageSummaryParts(
  meters: Double,
  unit: DistanceUnitPreference = DistanceUnitPreference.Metric,
): Pair<String, String> {
  if (meters <= 0 || meters.isNaN() || meters.isInfinite()) return "--" to ""
  val formatted = formatTravelMileageMeters(meters, unit = unit)
  val suffix = if (unit == DistanceUnitPreference.Imperial) {
    if (formatted.endsWith("ft")) "ft" else "mi"
  } else {
    if (formatted.endsWith("m")) "m" else "km"
  }
  return formatted.removeSuffix(suffix) to suffix
}


@Composable
internal fun LoadingCard(text: String) {
  Column(
    modifier = Modifier
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    CircularProgressIndicator(color = CyberHomeColors.primary)
    Spacer(Modifier.height(12.dp))
    Text(
      text = text,
      style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 13.sp * 1.4f, color = CyberHomeColors.inkMuted),
    )
  }
}

@Composable
internal fun EmptyCard(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
) {
  Column(
    modifier = Modifier
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(20.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    LucideIcon(icon = icon, size = AppIconSizes.xl, color = CyberHomeColors.inkFaint)
    Spacer(Modifier.height(10.dp))
    Text(
      text = title,
      style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
    )
    Spacer(Modifier.height(4.dp))
    Text(
      text = subtitle,
      textAlign = TextAlign.Center,
      style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
    )
  }
}

