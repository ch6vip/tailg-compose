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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val mapCard: @Composable () -> Unit = {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .shadow(
            elevation = 6.dp,
            shape = RoundedCornerShape(AppRadii.sheet),
            clip = false,
            ambientColor = Color.Transparent,
            spotColor = CyberHomeColors.actionShadow,
          ),
      ) {
        MiniMap(
          location = location,
          address = address,
          height = if (stacked) 210.dp else 260.dp,
          onMapTap = onMapTap,
        )
      }
    }
    val rideCard: @Composable () -> Unit = {
      AppPressable(
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
    }
    if (stacked) {
      Column(modifier = Modifier.fillMaxWidth()) {
        mapCard()
        Spacer(Modifier.height(12.dp))
        rideCard()
      }
    } else {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.weight(1f)) { mapCard() }
        Spacer(Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f)) { rideCard() }
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
  val mapDescription = if (address.isBlank()) {
    stringResource(R.string.map_no_location)
  } else {
    "${stringResource(R.string.location_title)}：$address"
  }

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
        .semantics { contentDescription = mapDescription }
        .clickable(
          interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
          indication = null,
          role = Role.Button,
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
        modifier = Modifier.weight(1f),
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
    ) {
      Column(modifier = Modifier.weight(1f)) {
        ScaleToFit(
          modifier = Modifier.fillMaxWidth().height(36.dp),
          contentAlignment = Alignment.CenterStart,
        ) {
          AnimatedValueText(
            value = todayKm,
            maxLines = 1,
            style = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
          )
        }
        Text(
          text = stringResource(R.string.map_stats_today_distance),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkFaint),
        )
      }
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
        ScaleToFit(
          modifier = Modifier.fillMaxWidth().height(36.dp),
          contentAlignment = Alignment.CenterEnd,
        ) {
          AnimatedValueText(
            value = totalKm,
            textAlign = TextAlign.End,
            maxLines = 1,
            style = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
          )
        }
        Text(
          text = stringResource(R.string.map_stats_total_distance),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkFaint),
        )
      }
    }
    Spacer(Modifier.weight(1f))
    Row(
      modifier = Modifier.fillMaxWidth(),
    ) {
      Metric(
        value = lastDistance,
        label = stringResource(R.string.map_stats_last_ride_distance),
        modifier = Modifier.weight(1f),
      )
      Spacer(Modifier.width(12.dp))
      Metric(
        value = lastDuration,
        label = stringResource(R.string.map_stats_last_ride_duration),
        modifier = Modifier.weight(1f),
        textAlign = TextAlign.End,
        horizontalAlignment = Alignment.End,
      )
    }
  }
}

@Composable
private fun Metric(
  value: String,
  label: String,
  modifier: Modifier = Modifier,
  textAlign: TextAlign = TextAlign.Start,
  horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
  Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
    ScaleToFit(
      modifier = Modifier.fillMaxWidth().height(22.dp),
      contentAlignment = if (textAlign == TextAlign.End) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
      AnimatedValueText(
        value = value,
        textAlign = textAlign,
        maxLines = 1,
        style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.ink),
      )
    }
    Text(
      text = label,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = textAlign,
      modifier = Modifier.fillMaxWidth(),
      style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkFaint),
    )
  }
}
