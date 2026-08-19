package com.tailg.plus.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.domain.control.ControlTopBarChannel
import com.tailg.plus.domain.control.ControlTopBarChannelKind
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppTouchTargets
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

internal val CyberHeaderExpandedHeight = 376.dp
internal val CyberHeaderCollapsedHeight = 152.dp

internal fun cyberHeaderHeight(collapseFraction: Float): Dp {
  val progress = collapseFraction.coerceIn(0f, 1f)
  return CyberHeaderExpandedHeight -
    (CyberHeaderExpandedHeight - CyberHeaderCollapsedHeight) * progress
}

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
  val headerHeight = cyberHeaderHeight(progress)

  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(headerHeight)
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
    // Dart `IgnorePointer(ignoring: expandedOpacity < 0.5)`: Compose alpha()
    // does not block hit testing, so gate the layer's clickable subtree with
    // the `interactive` parameter. Both layers stay composed so their
    // The explicit parent height above mirrors SliverPersistentHeader's
    // min/max extents; matchParentSize children do not participate in Box
    // measurement on their own.
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
        interactive = expandedOpacity >= 0.5f,
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
        carPhoto = carPhoto,
        batteryPercent = batteryPercent,
        online = online,
        bluetoothConnected = bluetoothConnected,
        isLocked = isLocked,
        powered = powered,
        bleChip = bleChip,
        channelStatus = channelStatus,
        interactive = compactOpacity >= 0.5f,
        onTitleTap = onTitleTap,
        onBatteryTap = onBatteryTap,
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
  minExtent: Dp = CyberHeaderCollapsedHeight,
  maxExtent: Dp,
): Float {
  val density = LocalDensity.current
  val collapseRangePx = with(density) {
    (maxExtent - minExtent).toPx().coerceAtLeast(1f)
  }
  var fraction by remember { mutableFloatStateOf(0f) }
  LaunchedEffect(listState, collapseRangePx) {
    snapshotFlow {
      if (listState.firstVisibleItemIndex == 0) {
        listState.firstVisibleItemScrollOffset.toFloat() / collapseRangePx
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
  interactive: Boolean,
  onTitleTap: () -> Unit,
  onBatteryTap: () -> Unit,
  onBleChipTap: () -> Unit,
  onMessages: () -> Unit,
  onChannelTap: () -> Unit,
) {
  val normalizedRange = rangeText.trim()
  val rangeUnit = when {
    normalizedRange.endsWith("km", ignoreCase = true) -> "km"
    normalizedRange.endsWith("mi", ignoreCase = true) -> "mi"
    else -> ""
  }
  val rangeValue = if (rangeUnit.isEmpty()) {
    normalizedRange
  } else {
    normalizedRange.dropLast(rangeUnit.length).trim()
  }
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 0.dp),
  ) {
    // Top row: 94dp height (Dart _CyberHeroHeader.constrained height 94)
    Row(
      modifier = Modifier.height(94.dp),
      verticalAlignment = Alignment.Top,
    ) {
      // Left: vehicle name + range
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        // -- vehicle name (Dart 24sp) --
        AppPressable(
          onClick = if (interactive) onTitleTap else null,
          shape = RoundedCornerShape(AppRadii.sm),
          semanticsLabel = stringResource(R.string.vehicle_header_switch),
        ) {
          Text(
            text = vehicleName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = androidx.compose.ui.text.TextStyle(
              fontSize = 24.sp,
              lineHeight = 24.sp * 1.05f,
              fontWeight = FontWeight.W700,
              color = CyberHomeColors.ink,
            ),
          )
        }
        // -- range text (Dart 48sp) + battery percent, clickable via onBatteryTap --
        AppPressable(
          onClick = if (interactive) onBatteryTap else null,
          shape = RoundedCornerShape(AppRadii.pill),
          semanticsLabel = stringResource(R.string.vehicle_header_battery),
        ) {
          ScaleToFit(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            contentAlignment = Alignment.CenterStart,
          ) {
            Row(verticalAlignment = Alignment.Bottom) {
              AnimatedValueText(
                value = rangeValue,
                maxLines = 1,
                style = androidx.compose.ui.text.TextStyle(
                  fontSize = 48.sp,
                  lineHeight = 48.sp * 0.94f,
                  fontWeight = FontWeight.W700,
                  color = CyberHomeColors.ink,
                ),
              )
              Spacer(Modifier.width(4.dp))
              Text(
                text = rangeUnit,
                maxLines = 1,
                style = androidx.compose.ui.text.TextStyle(
                  fontSize = 17.sp,
                  fontWeight = FontWeight.W600,
                  color = CyberHomeColors.ink,
                ),
                modifier = Modifier.padding(bottom = 3.dp),
              )
              Spacer(Modifier.width(10.dp))
              Text(
                text = if (batteryKnown) "$batteryPercent%" else "--%",
                maxLines = 1,
                style = androidx.compose.ui.text.TextStyle(
                  fontSize = 18.sp,
                  fontWeight = FontWeight.W500,
                  color = CyberHomeColors.inkMuted,
                ),
                modifier = Modifier.padding(bottom = 3.dp),
              )
            }
          }
        }
      }
      Spacer(Modifier.width(12.dp))
      // Right: BLE + messages hero actions (Dart _HeroAction)
      Row {
        // BLE action (48×48 primary or card button)
        HeroAction(
          icon = if (bluetoothConnected) Lucide.bluetooth else Lucide.bluetoothSearching,
          label = if (bluetoothConnected) stringResource(R.string.vehicle_header_connected)
                  else stringResource(R.string.vehicle_header_connect),
          primary = bluetoothConnected,
          interactive = interactive,
          onClick = onBleChipTap,
        )
        Spacer(Modifier.width(10.dp))
        // Messages action
        HeroAction(
          icon = Lucide.message,
          label = stringResource(R.string.vehicle_header_messages),
          primary = false,
          interactive = interactive,
          onClick = onMessages,
        )
      }
    }
    Spacer(Modifier.height(6.dp))
    // Divider line (Dart 116×4 pill)
    Box(
      modifier = Modifier
        .width(116.dp)
        .height(4.dp)
        .clip(RoundedCornerShape(AppRadii.pill))
        .background(CyberHomeColors.ink),
    )
    Spacer(Modifier.height(10.dp))
    // Status line (Dart _CyberStatusLine in hero)
    CyberStatusLine(
      online = online,
      bluetoothConnected = bluetoothConnected,
      isLocked = isLocked,
      powered = powered,
      channelStatus = channelStatus,
      onChannelTap = if (interactive) onChannelTap else null,
    )
    // Vehicle illustration
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
      contentAlignment = Alignment.Center,
    ) {
      VehicleStage(
        batteryLevel = batteryPercent / 100f,
        height = 164.dp,
        imageUrl = carPhoto.ifBlank { null },
      )
    }
    Spacer(Modifier.height(8.dp))
  }
}

/** Dart `_HeroAction` — 48×48 icon button with primary accent. */
@Composable
private fun HeroAction(
  icon: ImageVector,
  label: String,
  primary: Boolean,
  interactive: Boolean,
  onClick: () -> Unit,
) {
  AppPressable(
    onClick = if (interactive) onClick else null,
    shape = RoundedCornerShape(AppRadii.lg),
    background = if (primary) CyberHomeColors.primary else CyberHomeColors.card,
    shadowElevation = 6.dp,
    shadowColor = CyberHomeColors.actionShadow,
    semanticsLabel = label,
  ) {
    Box(
      modifier = Modifier.size(48.dp),
      contentAlignment = Alignment.Center,
    ) {
      LucideIcon(
        icon = icon,
        size = 25.dp,
        color = if (primary) CyberHomeColors.white else CyberHomeColors.ink,
      )
    }
  }
}

@Composable
private fun CyberTopBar(
  vehicleName: String,
  rangeText: String,
  carPhoto: String,
  batteryPercent: Int,
  online: Boolean,
  bluetoothConnected: Boolean,
  isLocked: Boolean,
  powered: Boolean?,
  bleChip: OfficialBleChipState,
  channelStatus: ControlTopBarChannel,
  interactive: Boolean,
  onTitleTap: () -> Unit,
  onBatteryTap: () -> Unit,
  onBleChipTap: () -> Unit,
  onMessages: () -> Unit,
  onChannelTap: () -> Unit,
) {
  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 6.dp),
  ) {
    val actionColumnWidth = (maxWidth * 0.48f)
      .coerceAtMost(160.dp)
      .coerceAtLeast(112.dp)
      .coerceAtMost(maxWidth)
    Row(
      modifier = Modifier.fillMaxSize(),
      verticalAlignment = Alignment.Top,
    ) {
      // Left: vehicle name + range + status line (Dart Column layout)
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.Top,
      ) {
        // -- vehicle name (Dart 25sp) --
        AppPressable(
          onClick = if (interactive) onTitleTap else null,
          shape = RoundedCornerShape(AppRadii.sm),
          semanticsLabel = stringResource(R.string.vehicle_header_switch),
        ) {
          Text(
            text = vehicleName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = androidx.compose.ui.text.TextStyle(
              fontSize = 25.sp,
              fontWeight = FontWeight.W700,
              color = CyberHomeColors.ink,
              lineHeight = 25.sp * 1.15f,
            ),
          )
        }
        Spacer(Modifier.height(6.dp))
        // -- range text (Dart 22sp, clickable → onBatteryTap) --
        AppPressable(
          onClick = if (interactive) onBatteryTap else null,
          shape = RoundedCornerShape(AppRadii.sm),
          semanticsLabel = stringResource(R.string.vehicle_header_battery),
        ) {
          ScaleToFit(
            modifier = Modifier.fillMaxWidth().height(AppTouchTargets.min),
            contentAlignment = Alignment.CenterStart,
          ) {
            AnimatedValueText(
              value = rangeText,
              maxLines = 1,
              style = androidx.compose.ui.text.TextStyle(
                fontSize = 22.sp,
                fontWeight = FontWeight.W600,
                color = CyberHomeColors.inkSecondary,
              ),
            )
          }
        }
        // -- status line (compact) --
        CyberStatusLine(
          online = online,
          bluetoothConnected = bluetoothConnected,
          isLocked = isLocked,
          powered = powered,
          channelStatus = channelStatus,
          compact = true,
          onChannelTap = if (interactive) onChannelTap else null,
        )
      }
      Spacer(Modifier.width(10.dp))
      // Right: vehicle thumb + BLE chip + messages. Bound the action row so
      // long translated BLE states cannot consume the whole compact header.
      Column(
        modifier = Modifier.width(actionColumnWidth),
        horizontalAlignment = Alignment.End,
      ) {
        // Vehicle thumbnail (Dart 112×70)
        VehicleThumb(
          carPhoto = carPhoto,
          batteryPercent = batteryPercent,
          width = 112.dp,
          height = 70.dp,
        )
        Spacer(Modifier.height(5.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (bleChip != OfficialBleChipState.Hidden) {
            CyberBleChip(
              state = bleChip,
              onClick = if (interactive) onBleChipTap else null,
              modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(5.dp))
          }
          RoundIconBtn(
            icon = Lucide.message,
            label = stringResource(R.string.vehicle_header_messages),
            interactive = interactive,
            onClick = onMessages,
          )
        }
      }
    }
  }
}

@Composable
private fun CyberStatusLine(
  online: Boolean,
  bluetoothConnected: Boolean,
  isLocked: Boolean,
  powered: Boolean?,
  channelStatus: ControlTopBarChannel,
  compact: Boolean = false,
  onChannelTap: (() -> Unit)?,
) {
  val channelLabel = channelStatus.localizedLabel()
  val iconSize = if (compact) 15.dp else 18.dp
  val textSize = if (compact) 11.sp else 13.sp
  val gap = if (compact) 5.dp else 7.dp

  // Dart: SingleChildScrollView(horizontal) around the whole row
  Row(
    modifier = Modifier.horizontalScroll(rememberScrollState()),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // 1. Online dot (Dart success green, not primary)
    AnimatedStatusIconV2(
      icon = Lucide.circleDot,
      size = iconSize,
      color = if (online) CyberHomeColors.success else CyberHomeColors.inkFaint,
    )
    Spacer(Modifier.width(gap))
    // 2. Bluetooth icon
    AnimatedStatusIconV2(
      icon = if (bluetoothConnected) Lucide.bluetooth else Lucide.bluetoothOff,
      size = iconSize,
      color = if (bluetoothConnected) CyberHomeColors.primary else CyberHomeColors.inkFaint,
    )
    Spacer(Modifier.width(gap))
    // 3. Radio tower (Dart Lucide.radioTower)
    AnimatedStatusIconV2(
      icon = Lucide.radioTower,
      size = iconSize,
      color = if (online) CyberHomeColors.ink else CyberHomeColors.inkFaint,
    )
    Spacer(Modifier.width(gap))
    // 4. Lock state text
    AnimatedContent(
      targetState = isLocked,
      transitionSpec = { fadeIn(tween(AppMotion.status)) togetherWith fadeOut(tween(AppMotion.status)) },
      label = "lockState",
    ) { locked ->
      Text(
        text = if (locked) stringResource(R.string.vehicle_header_armed)
               else stringResource(R.string.vehicle_header_disarmed),
        style = androidx.compose.ui.text.TextStyle(
          fontSize = textSize,
          color = CyberHomeColors.inkMuted,
          fontWeight = FontWeight.W600,
        ),
      )
    }
    Spacer(Modifier.width(gap))
    // 5. Channel status pill
    if (onChannelTap != null) {
      AppPressable(
        onClick = onChannelTap,
        shape = RoundedCornerShape(AppRadii.pill),
        background = CyberHomeColors.control,
        semanticsLabel = stringResource(R.string.vehicle_header_channel_format, channelLabel),
      ) {
        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(AppRadii.pill))
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 2.dp else 3.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier = Modifier
              .size(if (compact) 6.dp else 6.dp)
              .clip(CircleShape)
              .background(channelDotColor(channelStatus.kind)),
          )
          Spacer(Modifier.width(4.dp))
          Text(
            text = channelLabel,
            style = androidx.compose.ui.text.TextStyle(
              fontSize = if (compact) 10.sp else 11.sp,
              fontWeight = FontWeight.W600,
              color = CyberHomeColors.inkMuted,
            ),
          )
        }
      }
    }
  }
}

/** Value state for [AnimatedStatusIconV2] (drives AnimatedContent lookup). */
private data class StatusIconState(
  val icon: ImageVector,
  val size: Dp,
  val color: Color,
)

/** Dart `_AnimatedStatusIcon` — AnimatedSwitcher with Fade + Scale transition. */
@Composable
private fun AnimatedStatusIconV2(
  icon: ImageVector,
  size: Dp,
  color: Color,
) {
  val state = StatusIconState(icon = icon, size = size, color = color)
  AnimatedContent(
    targetState = state,
    transitionSpec = {
      (fadeIn(tween(AppMotion.status)) + scaleIn(
        initialScale = 0.88f,
        animationSpec = tween(AppMotion.status),
      )) togetherWith
        (fadeOut(tween(AppMotion.status)) + scaleOut(
          targetScale = 0.88f,
          animationSpec = tween(AppMotion.status),
        ))
    },
    label = "statusIcon",
  ) { target ->
    LucideIcon(icon = target.icon, size = target.size, color = target.color)
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

/** Dart `_VehicleThumb` — 112×70 clipped image or fallback painter. */
@Composable
private fun VehicleThumb(
  carPhoto: String,
  batteryPercent: Int,
  width: Dp = 112.dp,
  height: Dp = 70.dp,
) {
  val level = (batteryPercent / 100f).coerceIn(0f, 1f)
  Box(
    modifier = Modifier
      .width(width)
      .height(height)
      .clip(RoundedCornerShape(AppRadii.card))
      .background(CyberHomeColors.mapPlaceholder),
  ) {
    VehicleImageOrFallback(
      imageUrl = carPhoto,
      batteryLevel = level,
      modifier = Modifier.fillMaxSize(),
    )
  }
}

@Composable
private fun CyberBleChip(
  state: OfficialBleChipState,
  onClick: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  if (state == OfficialBleChipState.Hidden) return
  val connected = state == OfficialBleChipState.Connected
  val connecting = state == OfficialBleChipState.Connecting || state == OfficialBleChipState.Disconnecting

  val label = when (state) {
    OfficialBleChipState.NoBle -> stringResource(R.string.vehicle_header_no_ble)
    OfficialBleChipState.ClickToConnect -> stringResource(R.string.vehicle_header_connect)
    OfficialBleChipState.Connecting -> stringResource(R.string.vehicle_header_connecting)
    OfficialBleChipState.Disconnecting -> stringResource(R.string.vehicle_header_disconnecting)
    OfficialBleChipState.Connected -> stringResource(R.string.vehicle_header_connected)
    OfficialBleChipState.Hidden -> ""
  }

  AppPressable(
    onClick = onClick,
    modifier = modifier,
    shape = RoundedCornerShape(AppRadii.sheet),
    background = if (connected) CyberHomeColors.primary.copy(alpha = 0.12f) else CyberHomeColors.control,
    borderWidth = 1.dp,
    borderColor = if (connected) CyberHomeColors.primary.copy(alpha = 0.35f) else CyberHomeColors.line,
    semanticsLabel = label,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(AppTouchTargets.min)
        .padding(horizontal = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Icon area with animated content switcher
      AnimatedContent(
        targetState = if (connecting) "connecting" else if (connected) "connected" else "disconnected",
        transitionSpec = {
          fadeIn(tween(AppMotion.status)) togetherWith fadeOut(tween(AppMotion.status))
        },
        label = "bleChipIcon",
      ) { stateKey ->
        when (stateKey) {
          "connecting" -> CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            strokeWidth = 1.6.dp,
            color = CyberHomeColors.primary,
          )
          else -> {
            val chipIcon = when {
              connected -> Lucide.bluetooth
              connecting -> Lucide.bluetoothSearching
              state == OfficialBleChipState.ClickToConnect -> Lucide.bluetoothSearching
              state == OfficialBleChipState.NoBle -> Lucide.bluetoothOff
              else -> Lucide.bluetoothSearching
            }
            LucideIcon(
              icon = chipIcon,
              size = 14.dp,
              color = if (connected) CyberHomeColors.primary else CyberHomeColors.inkMuted,
            )
          }
        }
      }
      Spacer(Modifier.width(4.dp))
      // Label text
      AnimatedContent(
        targetState = label,
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.CenterStart,
        transitionSpec = {
          fadeIn(tween(AppMotion.status)) togetherWith fadeOut(tween(AppMotion.status))
        },
        label = "bleChipLabel",
      ) { chipLabel ->
        Text(
          text = chipLabel,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          style = androidx.compose.ui.text.TextStyle(
            fontSize = 11.sp,
            fontWeight = FontWeight.W600,
            color = if (connected) CyberHomeColors.primary else CyberHomeColors.inkMuted,
          ),
        )
      }
    }
  }
}

@Composable
private fun RoundIconBtn(
  icon: ImageVector,
  label: String,
  interactive: Boolean,
  onClick: () -> Unit,
) {
  AppPressable(
    onClick = if (interactive) onClick else null,
    enabled = interactive,
    modifier = Modifier.size(AppTouchTargets.min),
    shape = CircleShape,
    background = CyberHomeColors.control,
    semanticsLabel = label,
    haptic = false,
  ) {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center,
    ) {
      LucideIcon(icon = icon, size = 19.dp, color = CyberHomeColors.inkMuted)
    }
  }
}
