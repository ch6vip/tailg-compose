package com.tailg.plus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.data.cloud.ResolvedVehicleLocation
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.CyberHomeColors
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R

/**
 * Port of `lib/widgets/cyber_map_stats.dart` — map + ride-stats card row.
 *
 * **Pending references**: [ResolvedVehicleLocation] (Dart
 * `lib/services/vehicle_location_resolver.dart`, fields `hasCoordinate`,
 * `latitude`, `longitude`, `address`) → `com.tailg.plus.data.cloud`.
 *
 * **Map deferral**: the Dart embeds a `flutter_map` tile map. This project has
 * no map SDK yet (UI_PORT_PLAN "map SDK choice TODO"), so the mini map is a
 * placeholder canvas: mapPlaceholder fill + centered map glyph + a drawn pin
 * when [ResolvedVehicleLocation.hasCoordinate]. Swap the Canvas in [MiniMap]
 * for the chosen map composable in the map SDK pass.
 *
 * Token mapping: `CyberHomeColors.card/mapPlaceholder/ink/inkMuted/inkFaint/
 * primary/primarySoft/line/actionShadow` → the same-named [CyberHomeColors]
 * tokens; `AppRadii.sheet` → [AppRadii.sheet].
 *
 * Icons: `Lucide.map-pin` → `Icons.Filled.LocationOn`; `Lucide.map` →
 * `Icons.Filled.Map`; `Lucide.chart` → `Icons.Filled.BarChart`.
 */
@Composable
fun CyberMapStatsRow(
  location: ResolvedVehicleLocation?,
  address: String,
  todayKm: String,
  totalKm: String,
  lastDistance: String,
  lastDuration: String,
  modifier: Modifier = Modifier,
  onMapTap: () -> Unit,
  onRideStatsTap: () -> Unit,
) {
  BoxWithConstraints(
    modifier = modifier.padding(horizontal = 20.dp),
  ) {
    val stacked = maxWidth < 420.dp
    val mapCard = AppPressable(
      onClick = onMapTap,
      shape = RoundedCornerShape(AppRadii.sheet),
      semanticsLabel = stringResource(R.string.map_stats_vehicle_location, address),
      shadowElevation = 6.dp,
      shadowColor = CyberHomeColors.actionShadow,
    ) {
      MiniMap(
        location = location,
        address = address,
        height = if (stacked) 210.dp else 260.dp,
        onMapTap = onMapTap,
      )
    }
    val rideCard = AppPressable(
      onClick = onRideStatsTap,
      shape = RoundedCornerShape(AppRadii.sheet),
      semanticsLabel = stringResource(R.string.map_stats_view_ride),
      shadowElevation = 6.dp,
      shadowColor = CyberHomeColors.actionShadow,
    ) {
      RideCard(
        height = if (stacked) 216.dp else 260.dp,
        todayKm = todayKm,
        totalKm = totalKm,
        lastDistance = lastDistance,
        lastDuration = lastDuration,
      )
    }
    if (stacked) {
      Column(modifier = Modifier.fillMaxWidth()) {
        mapCard
        Spacer(Modifier.height(12.dp))
        rideCard
      }
    } else {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.weight(1f)) { mapCard }
        Spacer(Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f)) { rideCard }
      }
    }
  }
}

/** Mini map on osmdroid tiles (Dart flutter_map embed); tap opens the map page. */
@Composable
private fun MiniMap(
  location: ResolvedVehicleLocation?,
  address: String,
  height: Dp,
  onMapTap: () -> Unit,
) {
  val hasPin = location?.hasCoordinate == true
  val lat = location?.latitude
  val lng = location?.longitude

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(height)
      .clip(RoundedCornerShape(AppRadii.sheet))
      .background(CyberHomeColors.mapPlaceholder),
  ) {
    CyberMapView(
      latitude = if (hasPin) lat else null,
      longitude = if (hasPin) lng else null,
      modifier = Modifier.matchParentSize(),
    )
    // The AndroidView consumes gestures, so re-surface the card tap on top.
    Box(
      modifier = Modifier
        .matchParentSize()
        .clickable(
          interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
          indication = null,
        ) { onMapTap() },
    )
    if (hasPin) {
      Text(
        text = "%.5f, %.5f".format(lat ?: 0.0, lng ?: 0.0),
        style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = CyberHomeColors.inkFaint),
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = 34.dp)
          .background(CyberHomeColors.card.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
          .padding(horizontal = 6.dp, vertical = 2.dp),
      )
    }
    // Address footer chip.
    Row(
      modifier = Modifier
        .align(Alignment.BottomStart)
        .fillMaxWidth()
        .background(CyberHomeColors.card.copy(alpha = 0.92f))
        .padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LucideIcon(icon = Lucide.mapPin, size = 13.dp, color = CyberHomeColors.primary)
      Spacer(Modifier.width(5.dp))
      Text(
        text = address.ifEmpty { stringResource(R.string.map_stats_no_location) },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkMuted),
      )
    }
  }
}

@Composable
private fun RideCard(
  height: Dp,
  todayKm: String,
  totalKm: String,
  lastDistance: String,
  lastDuration: String,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .height(height)
      .clip(RoundedCornerShape(AppRadii.sheet))
      .background(CyberHomeColors.card)
      .padding(14.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = stringResource(R.string.map_stats_ride_data),
        style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.ink),
      )
      Spacer(Modifier.weight(1f))
      Box(
        modifier = Modifier
          .size(32.dp)
          .clip(CircleShape)
          .background(CyberHomeColors.primarySoft),
        contentAlignment = Alignment.Center,
      ) {
        LucideIcon(icon = Lucide.chart, size = 16.dp, color = CyberHomeColors.primary)
      }
    }
    Spacer(Modifier.height(14.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column {
        AnimatedValueText(
          value = todayKm,
          style = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
        )
        Text(
          text = stringResource(R.string.map_stats_today_distance),
          style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkFaint),
        )
      }
      Column(horizontalAlignment = Alignment.End) {
        AnimatedValueText(
          value = totalKm,
          textAlign = TextAlign.End,
          style = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
        )
        Text(
          text = stringResource(R.string.map_stats_total_distance),
          style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkFaint),
        )
      }
    }
    Spacer(Modifier.weight(1f))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Metric(value = lastDistance, label = stringResource(R.string.map_stats_last_ride_distance))
      Metric(value = lastDuration, label = stringResource(R.string.map_stats_last_ride_duration))
    }
  }
}

@Composable
private fun Metric(value: String, label: String) {
  Column {
    AnimatedValueText(
      value = value,
      style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.ink),
    )
    Text(
      text = label,
      style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkFaint),
    )
  }
}
