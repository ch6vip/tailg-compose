package com.tailg.plus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
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
      semanticsLabel = "车辆位置 $address",
      shadowElevation = 6.dp,
      shadowColor = CyberHomeColors.actionShadow,
    ) {
      MiniMap(
        location = location,
        address = address,
        height = if (stacked) 210.dp else 260.dp,
      )
    }
    val rideCard = AppPressable(
      onClick = onRideStatsTap,
      shape = RoundedCornerShape(AppRadii.sheet),
      semanticsLabel = "查看骑行统计",
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

/** Placeholder mini map — real tiles deferred to the map SDK pass. */
@Composable
private fun MiniMap(
  location: ResolvedVehicleLocation?,
  address: String,
  height: Dp,
) {
  val hasPin = location?.hasCoordinate == true
  val lat = location?.latitude ?: 30.2741
  val lng = location?.longitude ?: 120.1551

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(height)
      .clip(RoundedCornerShape(AppRadii.sheet))
      .background(CyberHomeColors.mapPlaceholder),
  ) {
    // Grid hint lines (stand-in for tile roads).
    Canvas(modifier = Modifier.matchParentSize()) {
      val step = 32.dp.toPx()
      var x = step
      while (x < size.width) {
        drawLine(
          color = CyberHomeColors.line.copy(alpha = 0.5f),
          start = Offset(x, 0f),
          end = Offset(x, size.height),
          strokeWidth = 1f,
        )
        x += step
      }
      var y = step
      while (y < size.height) {
        drawLine(
          color = CyberHomeColors.line.copy(alpha = 0.5f),
          start = Offset(0f, y),
          end = Offset(size.width, y),
          strokeWidth = 1f,
        )
        y += step
      }
    }
    // Centered map glyph.
    Box(modifier = Modifier.align(Alignment.Center)) {
      LucideIcon(icon = Lucide.map, size = 40.dp, color = CyberHomeColors.inkFaint.copy(alpha = 0.6f))
    }
    // Pin marker when a usable coordinate exists (Dart flutter_map marker).
    if (hasPin) {
      Canvas(modifier = Modifier.matchParentSize()) {
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
          color = CyberHomeColors.primary.copy(alpha = 0.15f),
          radius = 26.dp.toPx(),
          center = c,
        )
        drawCircle(
          color = CyberHomeColors.primary,
          radius = 8.dp.toPx(),
          center = c,
        )
        drawCircle(
          color = CyberHomeColors.white,
          radius = 3.dp.toPx(),
          center = c,
        )
      }
      Text(
        text = "%.5f, %.5f".format(lat, lng),
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
        text = address.ifEmpty { "暂无位置信息" },
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
        text = "骑行数据",
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
          text = "今日里程 (km)",
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
          text = "总里程 (km)",
          style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkFaint),
        )
      }
    }
    Spacer(Modifier.weight(1f))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Metric(value = lastDistance, label = "上次骑行距离")
      Metric(value = lastDuration, label = "上次骑行时长")
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
