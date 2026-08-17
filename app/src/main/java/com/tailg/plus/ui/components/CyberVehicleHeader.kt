package com.tailg.plus.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.domain.control.ControlTopBarChannel
import com.tailg.plus.domain.control.ControlTopBarChannelKind
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.CyberHomeColors
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R

/**
 * Port of `lib/widgets/cyber_vehicle_header.dart` — collapsing header for the
 * Cyber control home.
 *
 * The Dart `SliverPersistentHeaderDelegate` collapses between
 * `minExtent = 152` and `maxExtent = expandedExtent`; this port exposes a
 * [collapseFraction] (0 = expanded, 1 = collapsed) so any scroll source can
 * drive it. [rememberCyberCollapseFraction] derives it from a [LazyListState]
 * whose **first item is the header** (same usage as the Dart sliver).
 *
 * **Pending reference**: [ControlTopBarChannel] / [ControlTopBarChannelKind]
 * (Dart `lib/services/control_channel_status.dart`) → `com.tailg.plus.data.cloud`.
 *
 * Token mapping: `CyberHomeColors.pageBg/card/control/primary/primarySoft/
 * ink/inkSecondary/inkMuted/inkFaint/warning/danger/actionShadow` → the
 * same-named [CyberHomeColors] tokens.
 *
 * Icons: `Lucide.bluetooth/bluetoothOff/bluetoothSearching` →
 * `Icons.Filled.Bluetooth/BluetoothDisabled/BluetoothSearching`;
 * `Lucide.message` → `Icons.Filled.Notifications`; `Lucide.lock/unlock` →
 * `Icons.Filled.Lock/LockOpen`; `Lucide.circleDot` → `Icons.Filled.Adjust`;
 * `Lucide.power` → `Icons.Filled.PowerSettingsNew`; `Lucide.chevronDown` →
 * `Icons.Filled.KeyboardArrowDown`.
 */

/** BLE chip visual state (drives label + spinner in the header). */
enum class OfficialBleChipState { Hidden, NoBle, ClickToConnect, Connecting, Disconnecting, Connected }

@Composable
fun CyberVehicleHeader(
  collapseFraction: Float,
  vehicleName: String,
  rangeText: String,
  carPhoto: String,
  batteryPercent: Int,
  batteryKnown: Boolean,
  online: Boolean,
  bluetoothConnected: Boolean,
  isLocked: Boolean,
  powered: Boolean?,
  bleChip: OfficialBleChipState,
  channelStatus: ControlTopBarChannel,
  modifier: Modifier = Modifier,
  onTitleTap: () -> Unit,
  onBatteryTap: () -> Unit,
  onBleChipTap: () -> Unit,
  onMessages: () -> Unit,
  onChannelTap: () -> Unit,
) {
  val progress = collapseFraction.coerceIn(0f, 1f)
  val expandedOpacity = (1f - progress * 1.8f).coerceIn(0f, 1f)
  val compactOpacity = ((progress - 0.42f) / 0.58f).coerceIn(0f, 1f)

  Box(
    modifier = modifier
      .background(CyberHomeColors.pageBg)
      .then(
        if (progress > 0.95f) {
          Modifier.shadow(
            elevation = 8.dp,
            shape = RoundedCornerShape(bottomStart = AppRadii.md, bottomEnd = AppRadii.md),
            clip = false,
            ambientColor = Color.Transparent,
            spotColor = CyberHomeColors.actionShadow,
          )
        } else {
          Modifier
        },
      ),
  ) {
    // Expanded hero layer.
    Box(
      modifier = Modifier
        .matchParentSize()
        .alpha(expandedOpacity),
    ) {
      CyberHeroHeader(
        vehicleName = vehicleName,
        rangeText = rangeText,
        carPhoto = carPhoto,
        batteryPercent = batteryPercent,
        batteryKnown = batteryKnown,
        online = online,
        bluetoothConnected = bluetoothConnected,
        isLocked = isLocked,
        powered = powered,
        bleChip = bleChip,
        channelStatus = channelStatus,
        onTitleTap = onTitleTap,
        onBatteryTap = onBatteryTap,
        onBleChipTap = onBleChipTap,
        onMessages = onMessages,
        onChannelTap = onChannelTap,
      )
    }
    // Compact top-bar layer.
    Box(
      modifier = Modifier
        .matchParentSize()
        .alpha(compactOpacity),
    ) {
      CyberTopBar(
        vehicleName = vehicleName,
        rangeText = rangeText,
        online = online,
        bluetoothConnected = bluetoothConnected,
        isLocked = isLocked,
        powered = powered,
        bleChip = bleChip,
        channelStatus = channelStatus,
        onTitleTap = onTitleTap,
        onBleChipTap = onBleChipTap,
        onMessages = onMessages,
        onChannelTap = onChannelTap,
      )
    }
  }
}

/** Collapse fraction from a LazyColumn whose first item is the header. */
@Composable
fun rememberCyberCollapseFraction(
  listState: LazyListState,
  minExtent: Int = 152,
  maxExtent: Int,
): Float {
  val collapseRange = (maxExtent - minExtent).coerceAtLeast(1)
  var fraction by remember { mutableFloatStateOf(0f) }
  LaunchedEffect(listState, collapseRange) {
    snapshotFlow {
      if (listState.firstVisibleItemIndex == 0) {
        listState.firstVisibleItemScrollOffset.toFloat() / collapseRange
      } else {
        1f
      }
    }.collect { value ->
      fraction = value.coerceIn(0f, 1f)
    }
  }
  return fraction
}

@Composable
private fun CyberHeroHeader(
  vehicleName: String,
  rangeText: String,
  carPhoto: String,
  batteryPercent: Int,
  batteryKnown: Boolean,
  online: Boolean,
  bluetoothConnected: Boolean,
  isLocked: Boolean,
  powered: Boolean?,
  bleChip: OfficialBleChipState,
  channelStatus: ControlTopBarChannel,
  onTitleTap: () -> Unit,
  onBatteryTap: () -> Unit,
  onBleChipTap: () -> Unit,
  onMessages: () -> Unit,
  onChannelTap: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 14.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      AppPressable(
        onClick = onTitleTap,
        shape = RoundedCornerShape(AppRadii.sm),
        semanticsLabel = stringResource(R.string.vehicle_header_switch),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = vehicleName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
          )
          Spacer(Modifier.width(4.dp))
          LucideIcon(icon = Lucide.chevronDown, size = 16.dp, color = CyberHomeColors.inkMuted)
        }
      }
      Spacer(Modifier.weight(1f))
      // Battery pill.
      AppPressable(
        onClick = onBatteryTap,
        shape = RoundedCornerShape(AppRadii.pill),
        semanticsLabel = stringResource(R.string.vehicle_header_battery),
      ) {
        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(AppRadii.pill))
            .background(CyberHomeColors.primary.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          LucideIcon(icon = Lucide.batteryFull, size = 14.dp, color = CyberHomeColors.ink)
          Spacer(Modifier.width(5.dp))
          Text(
            text = if (batteryKnown) "$batteryPercent%" else "--",
            style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
          )
        }
      }
      Spacer(Modifier.width(6.dp))
      CyberBleChip(state = bleChip, onClick = onBleChipTap)
      Spacer(Modifier.width(8.dp))
      RoundIconBtn(icon = Lucide.message, badge = true, onClick = onMessages)
    }
    Spacer(Modifier.height(10.dp))
    // Vehicle illustration (painter fallback; `carPhoto` remote loading deferred).
    VehicleStage(
      batteryLevel = batteryPercent / 100f,
      height = 148.dp,
      imageUrl = carPhoto,
    )
    Spacer(Modifier.height(2.dp))
    CyberStatusLine(
      online = online,
      bluetoothConnected = bluetoothConnected,
      isLocked = isLocked,
      powered = powered,
      channelStatus = channelStatus,
      onChannelTap = onChannelTap,
    )
  }
}

@Composable
private fun CyberTopBar(
  vehicleName: String,
  rangeText: String,
  online: Boolean,
  bluetoothConnected: Boolean,
  isLocked: Boolean,
  powered: Boolean?,
  bleChip: OfficialBleChipState,
  channelStatus: ControlTopBarChannel,
  onTitleTap: () -> Unit,
  onBleChipTap: () -> Unit,
  onMessages: () -> Unit,
  onChannelTap: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AppPressable(
      onClick = onTitleTap,
      shape = RoundedCornerShape(AppRadii.sm),
      semanticsLabel = stringResource(R.string.vehicle_header_switch),
    ) {
      Column(modifier = Modifier.widthIn(max = 140.dp)) {
        Text(
          text = vehicleName,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          style = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
        )
        Text(
          text = rangeText,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkFaint),
        )
      }
    }
    Spacer(Modifier.weight(1f))
    Row(verticalAlignment = Alignment.CenterVertically) {
      AnimatedStatusIcon(online = online, bluetoothConnected = bluetoothConnected)
      Spacer(Modifier.width(6.dp))
      Text(
        text = if (online) stringResource(R.string.vehicle_header_online) else stringResource(R.string.vehicle_header_offline),
        style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.inkMuted),
      )
    }
    Spacer(Modifier.width(12.dp))
    Box(
      modifier = Modifier
        .size(46.dp)
        .clip(CircleShape)
        .background(CyberHomeColors.card)
        .shadow(elevation = 2.dp, shape = CircleShape, clip = false, ambientColor = Color.Transparent, spotColor = CyberHomeColors.actionShadow),
      contentAlignment = Alignment.Center,
    ) {
      VehicleThumb(powered = powered, isLocked = isLocked)
    }
    Spacer(Modifier.width(8.dp))
    CyberBleChip(state = bleChip, onClick = onBleChipTap)
    Spacer(Modifier.width(8.dp))
    RoundIconBtn(icon = Lucide.message, badge = true, onClick = onMessages)
  }
}

@Composable
private fun CyberStatusLine(
  online: Boolean,
  bluetoothConnected: Boolean,
  isLocked: Boolean,
  powered: Boolean?,
  channelStatus: ControlTopBarChannel,
  onChannelTap: () -> Unit,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    AnimatedStatusIcon(online = online, bluetoothConnected = bluetoothConnected)
    Spacer(Modifier.width(6.dp))
    Text(
      text = if (online) stringResource(R.string.vehicle_header_online) else stringResource(R.string.vehicle_header_offline),
      style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.inkMuted),
    )
    Spacer(Modifier.width(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      LucideIcon(
        icon = if (isLocked) Lucide.lock else Lucide.unlock,
        size = 13.dp,
        color = CyberHomeColors.inkMuted,
      )
      Spacer(Modifier.width(3.dp))
      Text(
        text = if (isLocked) stringResource(R.string.vehicle_header_armed) else stringResource(R.string.vehicle_header_disarmed),
        style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkMuted),
      )
    }
    Spacer(Modifier.width(10.dp))
    AppPressable(
      onClick = onChannelTap,
      shape = RoundedCornerShape(AppRadii.pill),
      semanticsLabel = stringResource(R.string.vehicle_header_channel_format, channelStatus.label),
    ) {
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(AppRadii.pill))
          .background(channelDotColor(channelStatus.kind).copy(alpha = 0.12f))
          .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(channelDotColor(channelStatus.kind)),
        )
        Spacer(Modifier.width(4.dp))
        Text(
          text = channelStatus.label,
          style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.inkMuted),
        )
      }
    }
    if (powered != null) {
      Spacer(Modifier.width(10.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        LucideIcon(icon = Lucide.power, size = 13.dp, color = CyberHomeColors.inkFaint)
        Spacer(Modifier.width(3.dp))
        Text(
          text = if (powered) stringResource(R.string.vehicle_header_powered) else stringResource(R.string.vehicle_header_unpowered),
          style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkFaint),
        )
      }
    }
  }
}

/** Dart `_channelDotColor` switch. */
private fun channelDotColor(kind: ControlTopBarChannelKind): Color = when (kind) {
  ControlTopBarChannelKind.BLE_DIRECT,
  ControlTopBarChannelKind.MQTT_REMOTE,
  ControlTopBarChannelKind.CLOUD_STANDBY,
  -> CyberHomeColors.primary
  ControlTopBarChannelKind.BLE_CONNECTING,
  ControlTopBarChannelKind.MQTT_CONNECTING,
  ControlTopBarChannelKind.MQTT_RETRY,
  -> CyberHomeColors.warning
  ControlTopBarChannelKind.UNAVAILABLE -> CyberHomeColors.danger
}

@Composable
private fun AnimatedStatusIcon(online: Boolean, bluetoothConnected: Boolean) {
  val (icon, color) = when {
    online -> Lucide.circleDot to CyberHomeColors.primary
    bluetoothConnected -> Lucide.bluetooth to CyberHomeColors.primary
    else -> Lucide.bluetoothOff to CyberHomeColors.inkFaint
  }
  val animatedColor by animateColorAsState(color, label = "statusIconColor")
  LucideIcon(icon = icon, size = 14.dp, color = animatedColor)
}

/** Tiny vehicle thumbnail (placeholder disc; painter when image deferred). */
@Composable
private fun VehicleThumb(powered: Boolean?, isLocked: Boolean) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    LucideIcon(
      icon = if (powered == true) Lucide.power else if (isLocked) Lucide.lock else Lucide.unlock,
      size = 18.dp,
      color = CyberHomeColors.inkSecondary,
    )
  }
}

@Composable
private fun CyberBleChip(state: OfficialBleChipState, onClick: () -> Unit) {
  if (state == OfficialBleChipState.Hidden) return
  val (icon, label, connected) = when (state) {
    OfficialBleChipState.NoBle -> Triple(Lucide.bluetoothOff, stringResource(R.string.vehicle_header_no_ble), false)
    OfficialBleChipState.ClickToConnect -> Triple(Lucide.bluetooth, stringResource(R.string.vehicle_header_connect), false)
    OfficialBleChipState.Connecting -> Triple(Lucide.bluetoothSearching, stringResource(R.string.vehicle_header_connecting), false)
    OfficialBleChipState.Disconnecting -> Triple(Lucide.bluetooth, stringResource(R.string.vehicle_header_disconnecting), false)
    OfficialBleChipState.Connected -> Triple(Lucide.bluetooth, stringResource(R.string.vehicle_header_connected), true)
    OfficialBleChipState.Hidden -> Triple(Lucide.bluetooth, "", false)
  }
  AppPressable(
    onClick = onClick,
    shape = RoundedCornerShape(AppRadii.pill),
    semanticsLabel = label,
  ) {
    Row(
      modifier = Modifier
        .clip(RoundedCornerShape(AppRadii.pill))
        .background(if (connected) CyberHomeColors.primary.copy(alpha = 0.10f) else CyberHomeColors.card)
        .padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (state == OfficialBleChipState.Connecting || state == OfficialBleChipState.Disconnecting) {
        CircularProgressIndicator(
          modifier = Modifier.size(13.dp),
          strokeWidth = 2.dp,
          color = CyberHomeColors.primary,
        )
      } else {
        LucideIcon(
          icon = icon,
          size = 14.dp,
          color = if (connected) CyberHomeColors.primary else CyberHomeColors.inkMuted,
        )
      }
      Spacer(Modifier.width(4.dp))
      Text(
        text = label,
        style = androidx.compose.ui.text.TextStyle(
          fontSize = 11.sp,
          fontWeight = FontWeight.W600,
          color = if (connected) CyberHomeColors.primary else CyberHomeColors.inkMuted,
        ),
      )
    }
  }
}

@Composable
private fun RoundIconBtn(
  icon: ImageVector,
  onClick: () -> Unit,
  badge: Boolean = false,
) {
  Box(
    modifier = Modifier
      .size(36.dp)
      .clip(CircleShape)
      .background(CyberHomeColors.card)
      .shadow(elevation = 2.dp, shape = CircleShape, clip = false, ambientColor = Color.Transparent, spotColor = CyberHomeColors.actionShadow)
      .clickableWithoutRipple(enabled = true) { onClick() },
    contentAlignment = Alignment.Center,
  ) {
    LucideIcon(icon = icon, size = 18.dp, color = CyberHomeColors.inkSecondary)
    if (badge) {
      Box(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .size(8.dp)
          .clip(CircleShape)
          .background(CyberHomeColors.primary),
      )
    }
  }
}

private fun Modifier.clickableWithoutRipple(enabled: Boolean, onClick: () -> Unit): Modifier =
  this.clickable(enabled = enabled, onClick = onClick)
