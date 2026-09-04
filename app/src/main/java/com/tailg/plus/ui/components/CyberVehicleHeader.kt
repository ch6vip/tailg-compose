package com.tailg.plus.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.domain.control.ControlTopBarChannel
import com.tailg.plus.domain.control.ControlTopBarChannelKind
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.CyberHomeColors
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R

/**
 * Port of `lib/widgets/cyber_vehicle_header.dart` — vehicle header for the
 * Cyber control home.
 *
 * The Dart `SliverPersistentHeader(pinned: true)` collapsing behavior is **not**
 * ported: the header is a plain block that scrolls away with the page content
 * (the compact top bar that used to be revealed on collapse was removed). Two
 * consequences, both intentional:
 * - Height is fixed at [CyberHeaderExpandedHeight]; there is no collapse
 *   fraction, no pinned layer and no per-pixel recomposition.
 * - Drags that start on the bike illustration are handled by the page's own
 *   vertical scroll, so they scroll in both directions and keep the fling —
 *   the previous pinned header consumed them through a local
 *   `Modifier.scrollable` with no fling behavior.
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

/**
 * Height of the control-home vehicle header. The header is a plain scrollable
 * block (it scrolls away with the page) — it no longer pins and collapses into
 * a compact top bar, so this is a single fixed extent.
 */
internal val CyberHeaderExpandedHeight = 376.dp

@Composable
fun CyberVehicleHeader(
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
  // Entrance fade (secondary defense): the loading gate now renders as an
  // overlay, so the home list structure never changes and this header
  // is not re-measured on gate clear (the primary fix for the illustration
  // flashing at a wrong offset). This fade still covers a first composition
  // measured with transient constraints (e.g. entering the page directly
  // with no gate at all) and smooths the page entrance; starting transparent
  // hides any such frame before the header fades in fully laid out.
  var headerAppeared by remember { mutableStateOf(false) }
  val enterAlpha by animateFloatAsState(
    targetValue = if (headerAppeared) 1f else 0f,
    animationSpec = tween(durationMillis = 180),
    label = "cyberHeaderEnter",
  )
  LaunchedEffect(Unit) { headerAppeared = true }

  // Self-healing remeasure after rotation: the window can keep geometry
  // measured with mid-rotation constraints (landscape width shown in
  // a portrait viewport = left half of the bike / rear wheel). Tick immediately
  // on viewport change rather than waiting for the entrance fade, then again
  // after the window insets settle. Reading [relayoutTick] in measure
  // invalidates layout only — no recomposition, so the photo loader survives.
  val configuration = LocalConfiguration.current
  val viewportKey = configuration.orientation to configuration.screenWidthDp
  var relayoutTick by remember { mutableFloatStateOf(0f) }
  LaunchedEffect(viewportKey) {
    relayoutTick += 1f
    delay(64)
    relayoutTick += 1f
    delay(200)
    relayoutTick += 1f
  }

  // Static header: the block scrolls away with the page content instead of
  // pinning and collapsing into a compact top bar, so the height stays at the
  // expanded extent and every action is interactive at all times.
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(CyberHeaderExpandedHeight)
      .graphicsLayer { alpha = enterAlpha }
      .layout { measurable, constraints ->
        relayoutTick // measure-invalidation key; see comment above
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
      }
      .background(CyberHomeColors.pageBg),
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
  interactive: Boolean = true,
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
        // BLE action (48×48 primary or card button). The chip state also
        // carries "no BLE adapter" and "busy connecting", which the compact
        // top bar used to render; keep them here now that it is gone.
        HeroAction(
          icon = when (bleChip) {
            OfficialBleChipState.Connected -> Lucide.bluetooth
            OfficialBleChipState.NoBle -> Lucide.bluetoothOff
            else -> Lucide.bluetoothSearching
          },
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
    // Vehicle illustration. Clip so a first-frame overflow cannot cover
    // the range/status row above (the left-edge wheel flash).
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .clipToBounds(),
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
    shadowElevation = 0.dp,
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
private fun CyberStatusLine(
  online: Boolean,
  bluetoothConnected: Boolean,
  isLocked: Boolean,
  powered: Boolean?,
  channelStatus: ControlTopBarChannel,
  onChannelTap: (() -> Unit)?,
) {
  val channelLabel = channelStatus.localizedLabel()
  val iconSize = 18.dp
  val textSize = 13.sp
  val gap = 7.dp

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
            .padding(horizontal = 8.dp, vertical = 3.dp),
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
            text = channelLabel,
            style = androidx.compose.ui.text.TextStyle(
              fontSize = 11.sp,
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
@Composable
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
