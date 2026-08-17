package com.tailg.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.R
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.cloud.OfficialCloudState
import com.tailg.plus.data.cloud.ResolvedVehicleLocation
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.components.AppSnack
import com.tailg.plus.ui.components.CyberMapView
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.theme.AppIconSizes
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppTouchTargets
import com.tailg.plus.ui.theme.CyberHomeColors
import com.tailg.plus.util.formatDistanceMeters
import kotlinx.coroutines.launch

/**
 * Fence (geofence) tab of [LocationScreen] (Dart location_fence_tab.dart).
 * Extracted from LocationScreen.kt for maintainability.
 */
@Composable
internal fun FenceTab(
  cloudState: OfficialCloudState,
  location: ResolvedVehicleLocation?,
  onRefresh: () -> Unit,
  onTabChanged: (Int) -> Unit,
  scope: kotlinx.coroutines.CoroutineScope,
  cloudService: OfficialCloudService,
  snackbarHostState: androidx.compose.material3.SnackbarHostState,
) {
  val fence = cloudState.fenceData
  var enabled by remember(fence) { mutableStateOf(fence?.enabled ?: false) }
  var radiusValue by remember(fence) {
    mutableStateOf((fence?.fenceRadius?.toDoubleOrNull() ?: 1.0).coerceIn(
      fence?.fenceRadiusMin?.toDoubleOrNull() ?: 1.0,
      fence?.fenceRadiusMax?.toDoubleOrNull() ?: 100.0,
    ))
  }
  var timeFrom by remember(fence) { mutableStateOf(fence?.fenceTimeFr ?: "08:00") }
  var timeTo by remember(fence) { mutableStateOf(fence?.fenceTimeTo ?: "22:00") }
  var saving by remember { mutableStateOf(false) }
  var dirty by remember(fence) { mutableStateOf(false) }
  val strFenceSaved = stringResource(R.string.location_fence_saved)
  val strFenceSaveFailed = stringResource(R.string.location_fence_save_failed)

  val minRadius = fence?.fenceRadiusMin?.toDoubleOrNull() ?: 1.0
  val maxRadius = fence?.fenceRadiusMax?.toDoubleOrNull() ?: 100.0
  val radius = radiusValue * 100
  val minRadiusDisplay = minRadius * 100
  val maxRadiusDisplay = maxRadius * 100
  val source = if (fence?.hasData == true) stringResource(R.string.location_fence_sync_desc) else if (cloudState.signedIn) stringResource(R.string.location_fence_no_data) else stringResource(R.string.location_fence_login_required)

  Column(modifier = Modifier.fillMaxSize()) {
    // Floating header for fence tab.
    Row(
      modifier = Modifier
        .padding(start = 8.dp, top = 10.dp, end = 8.dp)
        .height(48.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      AppPressable(
        onClick = { onTabChanged(0) },
        shape = CircleShape,
        background = CyberHomeColors.white96,
        semanticsLabel = stringResource(R.string.common_back),
      ) {
        Box(modifier = Modifier.size(AppTouchTargets.min), contentAlignment = Alignment.Center) {
          LucideIcon(icon = Lucide.arrowLeft, color = CyberHomeColors.ink)
        }
      }
      Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(AppRadii.tile))
            .background(CyberHomeColors.white96)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
          Text(
            text = stringResource(R.string.location_fence_title),
            style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
          )
        }
      }
    }
    Spacer(Modifier.height(8.dp))
    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
      LocationSegmentedTabs(index = 2, onChanged = onTabChanged)
    }
    Spacer(Modifier.height(14.dp))
    // Full-bleed fence map centered on the vehicle pin with the fence circle.
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .padding(horizontal = 20.dp)
        .clip(RoundedCornerShape(AppRadii.tile))
        .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile)),
    ) {
      val hasCoordinate = location != null && location.hasCoordinate
      CyberMapView(
        latitude = if (hasCoordinate) location.latitude else null,
        longitude = if (hasCoordinate) location.longitude else null,
        fenceRadiusMeters = if (hasCoordinate) fence?.radiusMeters else null,
        fenceEnabled = enabled,
        initialZoom = 15.5,
        modifier = Modifier.fillMaxSize(),
      )
    }
    Spacer(Modifier.height(14.dp))
    // Fence settings sheet.
    Column(
      modifier = Modifier
        .clip(RoundedCornerShape(topStart = AppRadii.sheet, topEnd = AppRadii.sheet))
        .background(CyberHomeColors.card)
        .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 18.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = stringResource(R.string.location_fence_settings),
          style = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
        )
        Spacer(Modifier.width(8.dp))
        LucideIcon(icon = Lucide.help, size = AppIconSizes.sm, color = CyberHomeColors.inkFaint)
        Spacer(Modifier.weight(1f))
        AppPressable(
          onClick = { if (!cloudState.fenceLoading) onRefresh() },
          shape = CircleShape,
          semanticsLabel = stringResource(R.string.location_fence_refresh),
        ) {
          Box(modifier = Modifier.size(AppTouchTargets.min), contentAlignment = Alignment.Center) {
            if (cloudState.fenceLoading) {
              CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = CyberHomeColors.primary)
            } else {
              LucideIcon(icon = Lucide.refresh, size = AppIconSizes.md)
            }
          }
        }
      }
      Spacer(Modifier.height(8.dp))
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(AppRadii.tile))
          .background(CyberHomeColors.cardMuted)
          .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
          .padding(horizontal = 16.dp, vertical = 12.dp)
          .clickable {
            enabled = !enabled
            dirty = true
          },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.location_fence_title),
            style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
          )
          Spacer(Modifier.height(3.dp))
          Text(
            text = source,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
          )
        }
        Spacer(Modifier.width(12.dp))
        FenceSwitchPill(enabled = enabled)
      }
      Spacer(Modifier.height(10.dp))
      Column(
        modifier = Modifier
          .clip(RoundedCornerShape(AppRadii.tile))
          .background(CyberHomeColors.card)
          .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
          .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 18.dp),
      ) {
        Row {
          Text(
            text = stringResource(R.string.location_fence_range),
            style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
            modifier = Modifier.weight(1f),
          )
          Text(
            text = formatDistanceMeters(radius),
            style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W800, color = CyberHomeColors.primary),
          )
        }
        Spacer(Modifier.height(12.dp))
        Slider(
          value = radiusValue.toFloat(),
          onValueChange = { radiusValue = it.toDouble(); dirty = true },
          valueRange = minRadius.toFloat()..maxRadius.toFloat(),
          enabled = enabled,
        )
        Row {
          Text(text = formatDistanceMeters(minRadiusDisplay), style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint))
          Spacer(Modifier.weight(1f))
          Text(text = formatDistanceMeters(maxRadiusDisplay), style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint))
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = CyberHomeColors.line)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.location_fence_time),
              style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
            )
            Spacer(Modifier.height(3.dp))
            Text(
              text = "$timeFrom - $timeTo",
              style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
            )
          }
          LucideIcon(icon = Lucide.chevronRight, color = CyberHomeColors.inkFaint)
        }
      }
      if (cloudState.fenceError != null) {
        Spacer(Modifier.height(8.dp))
        Text(
          text = cloudState.fenceError,
          style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.warning),
        )
      }
      Spacer(Modifier.height(12.dp))
      val canSave = dirty && !saving && !cloudState.fenceLoading
      androidx.compose.material3.Button(
        onClick = {
          // Dart fence tab: await save, then update dirty AFTER completion.
          saving = true
          scope.launch {
            try {
              cloudService.updateFenceData(
                enabled = enabled,
                radiusValue = radiusValue.toInt(),
                timeFrom = timeFrom,
                timeTo = timeTo,
              )
              dirty = false
              AppSnack.success(snackbarHostState, strFenceSaved)
            } catch (e: Exception) {
              // dirty stays true so user can retry.
              AppSnack.error(snackbarHostState, strFenceSaveFailed)
            } finally {
              saving = false
            }
          }
        },
        enabled = canSave,
        shape = RoundedCornerShape(AppRadii.tile),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
          containerColor = CyberHomeColors.primary,
          contentColor = CyberHomeColors.white,
          disabledContainerColor = CyberHomeColors.controlStrong,
          disabledContentColor = CyberHomeColors.inkFaint,
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp),
      ) {
        if (saving) {
          CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = CyberHomeColors.white)
        } else {
          Text(text = stringResource(R.string.common_save), style = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = FontWeight.W800))
        }
      }
    }
  }
}

@Composable
internal fun FenceSwitchPill(enabled: Boolean) {
  val color = if (enabled) CyberHomeColors.success else CyberHomeColors.inkFaint
  Box(
    modifier = Modifier
      .width(52.dp)
      .height(28.dp)
      .clip(RoundedCornerShape(AppRadii.pill))
      .background(color.copy(alpha = if (enabled) 0.22f else 0.16f))
      .padding(3.dp),
  ) {
    Box(
      modifier = Modifier
        .size(22.dp)
        .align(if (enabled) Alignment.CenterEnd else Alignment.CenterStart)
        .clip(CircleShape)
        .background(color),
    )
  }
}
