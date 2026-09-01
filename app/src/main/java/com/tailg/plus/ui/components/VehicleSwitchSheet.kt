package com.tailg.plus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.ui.theme.CyberHomeColors
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R
import kotlinx.coroutines.launch

/**
 * Port of `lib/widgets/vehicle_switch_sheet.dart` — bottom sheet for switching
 * the selected cloud vehicle.
 *
 * **Pending reference**: [OfficialVehicle] (Dart `lib/models/official_vehicle.dart`,
 * fields `key`, `displayName`, `online`, `electricQuantity`) →
 * `com.tailg.plus.data.model`.
 *
 * **API adaptation**: the Dart sheet reads `officialCloudService.state` and
 * calls `selectVehicle` itself; the Compose version is controlled — the caller
 * passes the vehicle list + selection and handles [onSelect] (returns success
 * so the sheet can dismiss / show error). Selection progress is kept internal
 * like the Dart's `_selectingKey`.
 *
 * Token mapping: sheet bg `Colors.white` → [CyberHomeColors.card];
 * drag handle → [CyberHomeColors.line]; title/name → [CyberHomeColors.ink];
 * subtitle → [CyberHomeColors.inkMuted]; selection → [CyberHomeColors.primary]
 * / [CyberHomeColors.primarySoft].
 *
 * Icons: `Lucide.check-circle` → `Icons.Filled.CheckCircle`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleSwitchSheet(
  vehicles: List<OfficialVehicle>,
  selectedKey: String?,
  modifier: Modifier = Modifier,
  onSelect: suspend (OfficialVehicle) -> Boolean,
  onDismiss: () -> Unit,
) {
  var selectingKey by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    modifier = modifier,
    containerColor = CyberHomeColors.card,
    dragHandle = null,
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Box(
        modifier = Modifier
          .align(Alignment.CenterHorizontally)
          .size(width = 36.dp, height = 4.dp)
          .clip(RoundedCornerShape(2.dp))
          .background(CyberHomeColors.line),
      )
      Spacer(Modifier.height(16.dp))
      Text(
        text = stringResource(R.string.vehicle_switch_title),
        style = androidx.compose.ui.text.TextStyle(
          fontSize = 16.sp,
          fontWeight = FontWeight.W600,
          color = CyberHomeColors.ink,
        ),
        modifier = Modifier.align(Alignment.CenterHorizontally),
      )
      Spacer(Modifier.height(12.dp))
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 480.dp),
      ) {
        items(vehicles, key = { it.key }, contentType = { "vehicle-switch" }) { vehicle ->
          VehicleTile(
            vehicle = vehicle,
            selected = vehicle.key == selectedKey,
            selecting = vehicle.key == selectingKey,
            onTap = if (selectingKey == null) {
              {
                if (selectingKey == null) {
                  selectingKey = vehicle.key
                  scope.launch {
                    val ok = onSelect(vehicle)
                    selectingKey = null
                    if (ok) onDismiss()
                  }
                }
              }
            } else {
              null
            },
          )
        }
      }
      Spacer(Modifier.height(16.dp))
    }
  }
}

@Composable
private fun VehicleTile(
  vehicle: OfficialVehicle,
  selected: Boolean,
  selecting: Boolean,
  onTap: (() -> Unit)?,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(if (selected) CyberHomeColors.primary.copy(alpha = 0.06f) else Color.Transparent)
      .padding(horizontal = 20.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = vehicle.displayName,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = androidx.compose.ui.text.TextStyle(
          fontSize = 15.sp,
          fontWeight = if (selected) FontWeight.W600 else FontWeight.W400,
          color = CyberHomeColors.ink,
        ),
      )
      Spacer(Modifier.height(2.dp))
      Text(
        text = subtitle(vehicle),
        style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkMuted),
      )
    }
    if (selecting) {
      CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = CyberHomeColors.inkMuted)
    } else if (selected) {
      LucideIcon(icon = Lucide.checkCircle, size = 20.dp, color = CyberHomeColors.primary)
    }
  }
}

@Composable
private fun subtitle(vehicle: OfficialVehicle): String {
  val strOnline = stringResource(R.string.vehicle_switch_online)
  val strOffline = stringResource(R.string.vehicle_switch_offline)
  val parts = mutableListOf(if (vehicle.online) strOnline else strOffline)
  val battery = vehicle.electricQuantity
  if (battery != null && battery > 0) {
    parts += "$battery%"
  }
  return parts.joinToString(" · ")
}
