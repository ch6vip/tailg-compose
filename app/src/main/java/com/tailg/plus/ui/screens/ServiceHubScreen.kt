package com.tailg.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.navigation.Routes
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppTouchTargets
import com.tailg.plus.ui.theme.CyberHomeColors
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R

/**
 * Port of `lib/pages/service_hub_page.dart` → `ServiceHubScreen.kt`.
 *
 * The Dart page is a `StatelessWidget` that builds a scrollable list of glyph
 * sections (定位服务 / 车辆与能耗 / 更多). Navigation goes through
 * `openCloudGatedPage`; here the call site passes an [onNavigate] lambda that
 * receives a [com.tailg.plus.ui.navigation.Routes] key.
 *
 * Map SDK pages (location / travel / fence) are gated behind cloud login in
 * the Dart source; the cloud-gate check is the caller's responsibility here
 * (the route graph can wrap these routes with [requireCloudVehicle]).
 */
@Composable
fun ServiceHubScreen(
  vehicleRouteId: String,
  onNavigate: (String) -> Unit,
) {
  val strLocation = stringResource(R.string.service_location)
  val strTravel = stringResource(R.string.service_travel)
  val strFence = stringResource(R.string.service_fence)
  val strVehicleSettings = stringResource(R.string.service_vehicle_settings)
  val strBattery = stringResource(R.string.service_battery)
  val strRideStats = stringResource(R.string.service_ride_stats)
  Scaffold(
    containerColor = CyberHomeColors.pageBg,
    contentWindowInsets = WindowInsets.statusBars,
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(padding)
        .padding(bottom = 32.dp),
    ) {
      // Title block (Dart inlines the header instead of using CyberPageHeader).
      Column(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
      ) {
        Text(
          text = stringResource(R.string.service_title),
          style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
        )
        Spacer(Modifier.height(6.dp))
        Text(
          text = stringResource(R.string.service_subtitle),
          style = TextStyle(fontSize = 13.sp, lineHeight = 13.sp * 1.4f, color = CyberHomeColors.inkMuted),
        )
      }

      // Hoisted so the tiles' lambdas stay identical across recompositions
      // (fresh lists would defeat GlyphSection skipping).
      val locationItems = remember(vehicleRouteId, onNavigate) {
        listOf(
          GlyphItem(Lucide.mapPin, strLocation) { onNavigate(Routes.location(vehicleRouteId)) },
          GlyphItem(Lucide.route, strTravel) { onNavigate(Routes.location(vehicleRouteId, "travel")) },
          GlyphItem(Lucide.fence, strFence) { onNavigate(Routes.location(vehicleRouteId, "fence")) },
        )
      }
      val vehicleItems = remember(vehicleRouteId, onNavigate) {
        listOf(
          GlyphItem(Lucide.tune, strVehicleSettings) { onNavigate(Routes.vehicleSettings(vehicleRouteId)) },
          GlyphItem(Lucide.battery, strBattery) { onNavigate(Routes.batteryDetails(vehicleRouteId)) },
          GlyphItem(Lucide.chart, strRideStats) { onNavigate(Routes.rideStats(vehicleRouteId)) },
        )
      }

      ServiceSectionLabel(stringResource(R.string.service_location_section))
      GlyphSection(items = locationItems)

      ServiceSectionLabel(stringResource(R.string.service_vehicle_energy))
      GlyphSection(items = vehicleItems)

      ServiceSectionLabel(stringResource(R.string.service_more))
      ServiceListCard {
        ServiceListTile(
          icon = Lucide.stethoscope,
          title = stringResource(R.string.service_fault_diag),
          subtitle = stringResource(R.string.service_fault_diag_desc),
          onClick = { onNavigate(Routes.diagnostic(vehicleRouteId)) },
        )
        HorizontalDivider(
          thickness = 1.dp,
          color = CyberHomeColors.line,
          modifier = Modifier.padding(start = 60.dp),
        )
        ServiceListTile(
          icon = Lucide.cloud,
          title = stringResource(R.string.service_official_account),
          subtitle = stringResource(R.string.service_official_account_desc),
          onClick = { onNavigate(Routes.OFFICIAL_CLOUD) },
        )
      }
    }
  }
}

/** Dart `_ServiceSectionLabel`. */
@Composable
private fun ServiceSectionLabel(text: String) {
  Text(
    text = text,
    modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 8.dp),
    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.inkMuted),
  )
}

/** Dart `_GlyphItem` data holder. */
private data class GlyphItem(
  val icon: ImageVector,
  val label: String,
  val onTap: () -> Unit,
)

/** Dart `_GlyphSection`: a card row of equally-spaced glyph tiles. */
@Composable
private fun GlyphSection(items: List<GlyphItem>) {
  Row(
    modifier = Modifier
      .padding(horizontal = 20.dp)
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
  ) {
    items.forEach { item ->
      Box(modifier = Modifier.weight(1f)) {
        GlyphTile(item)
      }
    }
  }
}

/** Dart `_GlyphTile`: circular icon + label, pressable. */
@Composable
private fun GlyphTile(item: GlyphItem) {
  AppPressable(
    onClick = item.onTap,
    shape = RoundedCornerShape(AppRadii.tile),
    pressedBackground = CyberHomeColors.cardMuted,
    semanticsLabel = item.label,
  ) {
    Column(
      modifier = Modifier
        .height(96.dp)
        .fillMaxWidth(),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
        modifier = Modifier
          .size(52.dp)
          .clip(CircleShape)
          .background(CyberHomeColors.primarySoft)
          .border(1.dp, CyberHomeColors.line, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        LucideIcon(icon = item.icon, color = CyberHomeColors.primary, size = 22.dp)
      }
      Spacer(Modifier.height(10.dp))
      Text(
        text = item.label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      )
    }
  }
}

/** Dart `_serviceCardDecoration` wrapper for the stringResource(R.string.service_more) list. */
@Composable
private fun ServiceListCard(content: @Composable () -> Unit) {
  Column(
    modifier = Modifier
      .padding(horizontal = 20.dp)
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(vertical = 4.dp),
  ) {
    content()
  }
}

/** Dart `_ServiceListTile`: icon + title/subtitle + chevron, pressable. */
@Composable
private fun ServiceListTile(
  icon: ImageVector,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
) {
  AppPressable(
    onClick = onClick,
    shape = RoundedCornerShape(AppRadii.tile),
    pressedBackground = CyberHomeColors.cardMuted,
    semanticsLabel = title,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(AppTouchTargets.min)
          .clip(CircleShape)
          .background(CyberHomeColors.primarySoft)
          .border(1.dp, CyberHomeColors.line, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        LucideIcon(icon = icon, color = CyberHomeColors.primary, size = 20.dp)
      }
      Spacer(Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
        )
        Spacer(Modifier.height(3.dp))
        Text(
          text = subtitle,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          style = TextStyle(fontSize = 13.sp, lineHeight = 13.sp * 1.4f, color = CyberHomeColors.inkMuted),
        )
      }
      LucideIcon(
        icon = Lucide.chevronRight,
        color = CyberHomeColors.inkFaint,
        size = 18.dp,
      )
    }
  }
}
