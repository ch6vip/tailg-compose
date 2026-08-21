package com.tailg.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.tailg.plus.data.store.VehicleStore
import com.tailg.plus.log.LogCategory
import com.tailg.plus.log.LogService
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.theme.AppIconSizes
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppSpacing
import com.tailg.plus.ui.theme.CyberHomeColors
import com.tailg.plus.util.formatDateText
import com.tailg.plus.util.formatDateMinuteText

/**
 * Ride-record page of [OfficialReplicaScreen] (Dart RideRecordPage).
 * Extracted from OfficialReplicaScreen.kt for maintainability.
 */

@Composable
internal fun RideRecordTab(
  cloudService: OfficialCloudService,
  vehicleStore: VehicleStore,
  log: LogService,
) {
  val cloudState by cloudService.stateFlow.collectAsStateWithLifecycle()
  val vehicles by vehicleStore.vehiclesFlow.collectAsStateWithLifecycle()
  val vehicle = vehicleStore.defaultVehicle
  val location = vehicle?.lastLocation
  val cloudVehicle = if (cloudState.signedIn) cloudState.selectedVehicle else null
  val displayName = vehicle?.displayName ?: cloudVehicle?.displayName ?: stringResource(R.string.replica_unbound)
  val logs = remember(log) {
    log.byCategory(LogCategory.OPERATION).takeLast(12).reversed()
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
  ) {
    item {
      Text(
        text = stringResource(R.string.replica_today_overview),
        style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.inkMuted),
        modifier = Modifier.padding(horizontal = AppSpacing.screenX),
      )
    }
    item {
      Row(
        modifier = Modifier
          .padding(horizontal = AppSpacing.screenX, vertical = 8.dp)
          .clip(RoundedCornerShape(AppRadii.tile))
          .background(CyberHomeColors.card)
          .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
          .padding(16.dp),
      ) {
        MetricBlock(label = stringResource(R.string.replica_default_vehicle), value = displayName, modifier = Modifier.weight(1f))
        MetricBlock(label = stringResource(R.string.replica_current_log), value = logs.size.toString(), modifier = Modifier.weight(1f))
      }
    }
    item {
      Row(
        modifier = Modifier
          .padding(horizontal = AppSpacing.screenX, vertical = 8.dp)
          .clip(RoundedCornerShape(AppRadii.tile))
          .background(CyberHomeColors.card)
          .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
          .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        LucideIcon(icon = Lucide.mapPin, color = CyberHomeColors.primary)
        Spacer(Modifier.width(12.dp))
        Text(
          text = if (location == null) {
            stringResource(R.string.replica_no_last_location)
          } else {
            "${location.coordinateText} · ${formatDateMinuteText(java.time.LocalDateTime.ofInstant(location.recordedAt, java.time.ZoneId.systemDefault()))}"
          },
          style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 13.sp * 1.45f, color = CyberHomeColors.inkMuted),
          modifier = Modifier.weight(1f),
        )
      }
    }
    item {
      Text(
        text = stringResource(R.string.replica_recent_actions),
        style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.inkMuted),
        modifier = Modifier.padding(horizontal = AppSpacing.screenX),
      )
    }
    if (logs.isEmpty()) {
      item {
        EmptyReplicaCard(
          icon = Lucide.route,
          title = stringResource(R.string.replica_no_rides),
          subtitle = stringResource(R.string.replica_no_rides_hint),
        )
      }
    } else {
      item {
        Column(
          modifier = Modifier
            .padding(horizontal = AppSpacing.screenX)
            .clip(RoundedCornerShape(AppRadii.tile))
            .background(CyberHomeColors.card)
            .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile)),
        ) {
          logs.forEachIndexed { i, entry ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              LucideIcon(icon = Lucide.history, color = CyberHomeColors.inkMuted)
              Spacer(Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = entry.message,
                  style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
                )
                Text(
                  text = buildString {
                    append(formatDateMinuteText(entry.time))
                    entry.detail?.let { append("  $it") }
                  },
                  style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
                )
              }
            }
            if (i != logs.lastIndex) {
              HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = CyberHomeColors.line)
            }
          }
        }
      }
    }
  }
}
