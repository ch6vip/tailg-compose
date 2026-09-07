package com.tailg.plus.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.R
import com.tailg.plus.data.ble.CommandCode
import com.tailg.plus.data.cloud.ResolvedVehicleLocation
import com.tailg.plus.domain.control.ControlChannelAvailability
import com.tailg.plus.domain.control.ControlTopBarChannel
import com.tailg.plus.domain.control.ControlTopBarChannelKind

/**
 * VECTOR: an editorial instrument panel, composed around range, an illustrated
 * vehicle plate, and deliberate control keys. All displayed state comes from
 * the caller; entrance and press springs are the only state owned here.
 */
@Composable
fun VectorVehicleHeader(
  vehicleName: String,
  rangeText: String,
  carPhoto: String,
  batteryPercent: Int,
  batteryKnown: Boolean,
  online: Boolean,
  bluetoothConnected: Boolean,
  isLocked: Boolean?,
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
  val colors = MaterialTheme.colorScheme
  val reducedMotion = MotionPolicy.reduceMotion()
  val entrance = remember { Animatable(if (reducedMotion) 1f else 0f) }
  LaunchedEffect(reducedMotion) {
    if (reducedMotion) entrance.snapTo(1f)
    else entrance.animateTo(1f, spring(dampingRatio = 0.92f, stiffness = 220f))
  }
  val channelLabel = channelStatus.localizedLabel()
  val bleBusy = bleChip == OfficialBleChipState.Connecting ||
    bleChip == OfficialBleChipState.Disconnecting
  val bleLabel = when (bleChip) {
    OfficialBleChipState.NoBle -> stringResource(R.string.vehicle_header_no_ble)
    OfficialBleChipState.Connecting -> stringResource(R.string.vehicle_header_connecting)
    OfficialBleChipState.Disconnecting -> stringResource(R.string.vehicle_header_disconnecting)
    OfficialBleChipState.Connected -> stringResource(R.string.vehicle_header_connected)
    else -> stringResource(R.string.vehicle_header_connect)
  }
  Column(
    modifier = modifier
      .fillMaxWidth()
      .testTag("vectorHero")
      .graphicsLayer {
        alpha = entrance.value.coerceIn(0f, 1f)
        translationY = (1f - entrance.value) * 16.dp.toPx()
      }
      .padding(horizontal = 20.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = stringResource(R.string.vector_control_wordmark),
        style = MaterialTheme.typography.labelLarge.copy(
          fontWeight = FontWeight.Bold,
          letterSpacing = 4.sp,
        ),
        color = colors.onSurface,
      )
      VectorStatusLabel(
        icon = NinebotLucide.signal,
        label = stringResource(if (online) R.string.vehicle_header_online else R.string.vehicle_header_offline),
        color = if (online) colors.primary else colors.onSurfaceVariant,
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      VectorPressable(
        onClick = onTitleTap,
        semanticsLabel = stringResource(R.string.vehicle_header_switch),
        modifier = Modifier.weight(1f).testTag("vectorVehicle"),
      ) {
        Row(
          modifier = Modifier.heightIn(min = 56.dp).padding(end = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = vehicleName,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurface,
          )
          NinebotIcon(NinebotLucide.chevronDown, size = 17.dp, color = colors.onSurface)
        }
      }
      if (bleChip != OfficialBleChipState.Hidden) {
        VectorHeaderAction(
          icon = when (bleChip) {
            OfficialBleChipState.NoBle -> NinebotLucide.bluetoothOff
            OfficialBleChipState.Connected -> NinebotLucide.bluetooth
            else -> NinebotLucide.bluetoothSearching
          },
          label = bleLabel,
          accented = bluetoothConnected,
          busy = bleBusy,
          modifier = Modifier.testTag("vectorBle"),
          onClick = onBleChipTap,
        )
      }
      VectorHeaderAction(
        icon = NinebotLucide.messageSquare,
        label = stringResource(R.string.vehicle_header_messages),
        modifier = Modifier.testTag("vectorMessages"),
        onClick = onMessages,
      )
    }
    Spacer(Modifier.height(16.dp))
    HorizontalDivider(color = colors.outlineVariant)
    Spacer(Modifier.height(20.dp))
    VectorRangeInstrument(
      rangeText = rangeText,
      batteryPercent = batteryPercent,
      batteryKnown = batteryKnown,
      onBatteryTap = onBatteryTap,
    )
    Spacer(Modifier.height(18.dp))
    VectorVehiclePlate(carPhoto = carPhoto, batteryPercent = batteryPercent, batteryKnown = batteryKnown)
    Spacer(Modifier.height(12.dp))
    // Separate status lines let long channel diagnostics and larger system
    // fonts grow vertically instead of squeezing the instrument display.
    VectorPressable(
      onClick = onChannelTap,
      semanticsLabel = stringResource(R.string.vehicle_header_channel_format, channelLabel),
      modifier = Modifier.fillMaxWidth().testTag("vectorChannel"),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        NinebotIcon(
          icon = when (channelStatus.kind) {
            ControlTopBarChannelKind.BLE_DIRECT,
            ControlTopBarChannelKind.BLE_CONNECTING -> NinebotLucide.bluetooth
            ControlTopBarChannelKind.UNAVAILABLE -> NinebotLucide.triangleAlert
            else -> NinebotLucide.signal
          },
          size = 18.dp,
          color = if (channelStatus.kind == ControlTopBarChannelKind.UNAVAILABLE) colors.error else colors.primary,
        )
        Text(
          text = channelLabel,
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.labelLarge,
          color = colors.onSurface,
        )
        NinebotIcon(NinebotLucide.arrowUpRight, size = 18.dp, color = colors.onSurfaceVariant)
      }
    }
    Row(
      modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(18.dp),
      verticalAlignment = Alignment.Top,
    ) {
      VectorStatusLabel(
        icon = NinebotLucide.zap,
        label = stringResource(when (powered) {
          true -> R.string.vehicle_header_powered
          false -> R.string.vehicle_header_unpowered
          null -> R.string.vector_control_power_unknown
        }),
        color = colors.onSurfaceVariant,
        modifier = Modifier.weight(1f),
      )
      VectorStatusLabel(
        icon = if (isLocked == false) NinebotLucide.lockOpen else NinebotLucide.lock,
        label = stringResource(when (isLocked) {
          true -> R.string.vehicle_header_armed
          false -> R.string.vehicle_header_disarmed
          null -> R.string.vector_control_lock_unknown
        }),
        color = colors.onSurfaceVariant,
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun VectorRangeInstrument(
  rangeText: String,
  batteryPercent: Int,
  batteryKnown: Boolean,
  onBatteryTap: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val normalized = rangeText.trim().ifEmpty { "--" }
  val unit = when {
    normalized.endsWith("km", ignoreCase = true) -> "km"
    normalized.endsWith("mi", ignoreCase = true) -> "mi"
    else -> ""
  }
  val value = if (unit.isEmpty()) normalized else normalized.dropLast(unit.length).trim().ifEmpty { "--" }
  VectorPressable(
    onClick = onBatteryTap,
    semanticsLabel = stringResource(
      R.string.vector_control_battery_description,
      rangeText,
      if (batteryKnown) "${batteryPercent.coerceIn(0, 100)}%" else stringResource(R.string.vehicle_header_unknown),
    ),
    modifier = Modifier.fillMaxWidth().testTag("vectorBattery"),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = stringResource(R.string.vector_control_range),
          style = MaterialTheme.typography.labelLarge,
          color = colors.onSurfaceVariant,
        )
        NinebotIcon(NinebotLucide.arrowUpRight, size = 22.dp, color = colors.onSurface)
      }
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        BasicText(
          text = value,
          modifier = Modifier.weight(1f).alignByBaseline().testTag("vectorRange"),
          maxLines = 1,
          autoSize = TextAutoSize.StepBased(minFontSize = 32.sp, maxFontSize = 112.sp, stepSize = 2.sp),
          style = MaterialTheme.typography.displayLarge.copy(
            fontSize = 112.sp,
            lineHeight = TextUnit.Unspecified,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-5).sp,
            color = colors.onSurface,
          ),
        )
        if (unit.isNotEmpty()) {
          Text(
            text = unit,
            modifier = Modifier.alignByBaseline(),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
            color = colors.onSurfaceVariant,
          )
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        NinebotIcon(NinebotLucide.battery, size = 20.dp, color = colors.onSurfaceVariant)
        Canvas(modifier = Modifier.weight(1f).height(7.dp)) {
          val segments = 24
          val gap = 2.dp.toPx()
          val segmentWidth = ((size.width - gap * (segments - 1)) / segments).coerceAtLeast(0f)
          val filled = if (batteryKnown) batteryPercent.coerceIn(0, 100) / 100f * segments else 0f
          repeat(segments) { segment ->
            val x = segment * (segmentWidth + gap)
            drawRect(
              color = colors.onSurface.copy(alpha = 0.13f),
              topLeft = Offset(x, 0f),
              size = Size(segmentWidth, size.height),
            )
            val fill = (filled - segment).coerceIn(0f, 1f)
            if (fill > 0f) {
              drawRect(
                color = colors.primary,
                topLeft = Offset(x, 0f),
                size = Size(segmentWidth * fill, size.height),
              )
            }
          }
        }
        Text(
          text = if (batteryKnown) "${batteryPercent.coerceIn(0, 100)}%" else "--%",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
          color = colors.onSurface,
        )
      }
    }
  }
}

/** Decorative vehicle artwork, never a synthetic map or telemetry readout. */
@Composable
private fun VectorVehiclePlate(carPhoto: String, batteryPercent: Int, batteryKnown: Boolean) {
  val colors = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(topStart = 4.dp, topEnd = 64.dp, bottomEnd = 4.dp, bottomStart = 40.dp)
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(180.dp)
      .testTag("vectorArtwork")
      .clip(shape)
      .background(colors.secondaryContainer)
      .clearAndSetSemantics {},
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val ink = colors.onSecondaryContainer
      val center = Offset(size.width * 0.71f, size.height * 0.44f)
      drawCircle(ink.copy(alpha = 0.055f), size.height * 0.69f, center)
      rotate(-23f, center) {
        repeat(3) { index ->
          val expansion = index * 22.dp.toPx()
          drawOval(
            color = ink.copy(alpha = if (index == 0) 0.20f else 0.10f),
            topLeft = Offset(-size.width * 0.10f - expansion, size.height * 0.19f - expansion),
            size = Size(size.width * 1.20f + expansion * 2, size.height * 0.52f + expansion * 2),
            style = Stroke(width = 0.75.dp.toPx()),
          )
        }
      }
      drawLine(
        color = ink.copy(alpha = 0.18f),
        start = Offset(20.dp.toPx(), size.height * 0.85f),
        end = Offset(size.width - 20.dp.toPx(), size.height * 0.85f),
        strokeWidth = 0.75.dp.toPx(),
      )
      if (carPhoto.isBlank()) {
        drawVectorVehicle(ink, colors.secondaryContainer)
      }
    }
    if (carPhoto.isNotBlank()) {
      // Reuse the existing bounded, cached image loader. No skin-specific
      // network requests, cache, or lifecycle are introduced here.
      VehicleImageOrFallback(
        imageUrl = carPhoto,
        batteryLevel = if (batteryKnown) batteryPercent.coerceIn(0, 100) / 100f else 0f,
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp),
      )
    }
  }
}

private fun DrawScope.drawVectorVehicle(ink: Color, accent: Color) {
  val factor = minOf(size.width / 360f, size.height / 190f)
  translate((size.width - 360f * factor) / 2f, (size.height - 190f * factor) / 2f + 2f * factor) {
    scale(factor, factor, pivot = Offset.Zero) {
      val frame = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
      for (wheelX in floatArrayOf(84f, 280f)) {
        drawCircle(ink, radius = 29f, center = Offset(wheelX, 139f))
        drawCircle(accent, radius = 20f, center = Offset(wheelX, 139f))
        drawCircle(ink.copy(alpha = 0.38f), radius = 17f, center = Offset(wheelX, 139f), style = Stroke(1f))
        drawCircle(ink, radius = 5f, center = Offset(wheelX, 139f))
        drawLine(ink, Offset(wheelX - 13f, 139f), Offset(wheelX + 13f, 139f), 1.2f)
        drawLine(ink, Offset(wheelX, 126f), Offset(wheelX, 152f), 1.2f)
      }
      drawLine(ink, Offset(84f, 139f), Offset(119f, 105f), 7f, StrokeCap.Round)
      drawLine(ink, Offset(280f, 139f), Offset(259f, 78f), 6f, StrokeCap.Round)
      val chassis = Path().apply {
        moveTo(82f, 90f)
        lineTo(152f, 87f)
        quadraticTo(163f, 90f, 165f, 110f)
        lineTo(174f, 131f)
        lineTo(215f, 131f)
        quadraticTo(234f, 127f, 235f, 111f)
        lineTo(246f, 72f)
        quadraticTo(250f, 62f, 263f, 68f)
        lineTo(271f, 79f)
        quadraticTo(263f, 92f, 255f, 112f)
        quadraticTo(247f, 141f, 225f, 142f)
        lineTo(159f, 142f)
        lineTo(139f, 120f)
        lineTo(94f, 118f)
        close()
      }
      drawPath(chassis, ink)
      drawLine(accent.copy(alpha = 0.65f), Offset(178f, 136f), Offset(216f, 136f), 2f, StrokeCap.Round)
      drawRoundRect(ink, Offset(70f, 76f), Size(90f, 13f), CornerRadius(6f))
      drawLine(accent.copy(alpha = 0.65f), Offset(81f, 80f), Offset(146f, 80f), 1f)
      drawLine(ink, Offset(254f, 75f), Offset(274f, 43f), 5f, StrokeCap.Round)
      drawLine(ink, Offset(260f, 43f), Offset(291f, 39f), 6f, StrokeCap.Round)
      drawRoundRect(ink, Offset(248f, 43f), Size(14f, 9f), CornerRadius(2f))
      drawArc(ink, 214f, 124f, false, Offset(49f, 103f), Size(71f, 57f), style = frame)
      drawArc(ink, 208f, 120f, false, Offset(244f, 103f), Size(73f, 55f), style = frame)
      drawRoundRect(accent, Offset(111f, 98f), Size(30f, 6f), CornerRadius(1f))
    }
  }
}

@Composable
fun VectorControlGrid(
  powered: Boolean?,
  armed: Boolean?,
  busy: Boolean,
  activeCommand: CommandCode?,
  findAvailability: ControlChannelAvailability,
  powerAvailability: ControlChannelAvailability,
  armAvailability: ControlChannelAvailability,
  seatAvailability: ControlChannelAvailability,
  modifier: Modifier = Modifier,
  onFind: () -> Unit,
  onPowerToggle: suspend () -> Unit,
  onArmToggle: () -> Unit,
  onSettings: () -> Unit,
  onSeat: () -> Unit,
  onNfc: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val armLabel = stringResource(when (armed) {
    true -> R.string.control_grid_disarm
    false -> R.string.control_grid_arm
    null -> R.string.control_grid_arm_title
  })
  Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("vectorControlGrid")) {
    VectorSectionTitle(stringResource(R.string.vector_control_controls))
    Spacer(Modifier.height(12.dp))
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(CutCornerShape(bottomEnd = 28.dp))
        .background(colors.surfaceContainer)
        .border(1.dp, colors.outlineVariant, CutCornerShape(bottomEnd = 28.dp))
        .padding(16.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(modifier = Modifier.width(4.dp).height(24.dp).background(colors.primary))
        Text(
          text = stringResource(R.string.vector_control_power),
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
          color = colors.onSurface,
        )
        NinebotIcon(NinebotLucide.zap, size = 22.dp, color = colors.onSurfaceVariant)
      }
      Spacer(Modifier.height(16.dp))
      BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        SlidePowerButton(
          isPowered = powered,
          onSlide = onPowerToggle,
          modifier = Modifier.testTag("vectorPower"),
          trackWidth = maxWidth.coerceAtMost(280.dp),
          enabled = powerAvailability.enabled,
          busy = busy,
          unavailableReason = powerAvailability.disabledReason,
          onUnavailable = onPowerToggle,
          thumbBackground = colors.secondaryContainer,
          thumbContentColor = colors.onSecondaryContainer,
          trackBackground = colors.onSurface.copy(alpha = 0.09f),
        )
      }
    }
    Spacer(Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      VectorControlKey(
        icon = NinebotLucide.radar,
        label = stringResource(R.string.control_card_find),
        available = findAvailability.enabled,
        unavailableReason = findAvailability.disabledReason,
        busy = busy,
        active = busy && activeCommand == CommandCode.find,
        modifier = Modifier.weight(1f).fillMaxHeight().testTag("vectorFind"),
        onClick = onFind,
      )
      VectorControlKey(
        icon = if (armed == true) NinebotLucide.lockOpen else NinebotLucide.lock,
        label = armLabel,
        available = armAvailability.enabled,
        unavailableReason = armAvailability.disabledReason,
        busy = busy,
        active = busy && (activeCommand == CommandCode.lock || activeCommand == CommandCode.unlock),
        modifier = Modifier.weight(1f).fillMaxHeight().testTag("vectorArm"),
        onClick = onArmToggle,
      )
    }
    Spacer(Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      VectorControlKey(
        icon = NinebotLucide.armchair,
        label = stringResource(R.string.control_card_seat),
        available = seatAvailability.enabled,
        unavailableReason = seatAvailability.disabledReason,
        busy = busy,
        active = busy && activeCommand == CommandCode.openSeat,
        modifier = Modifier.weight(1f).fillMaxHeight().testTag("vectorSeat"),
        onClick = onSeat,
      )
      VectorControlKey(
        icon = NinebotLucide.settings,
        label = stringResource(R.string.control_grid_settings),
        available = true,
        unavailableReason = "",
        busy = false,
        active = false,
        modifier = Modifier.weight(1f).fillMaxHeight().testTag("vectorSettings"),
        onClick = onSettings,
      )
    }
    Spacer(Modifier.height(10.dp))
    VectorPressable(
      onClick = onNfc,
      semanticsLabel = stringResource(R.string.replica_nfc_keys),
      modifier = Modifier.fillMaxWidth().testTag("vectorNfc"),
      background = colors.secondaryContainer,
      shape = CutCornerShape(bottomEnd = 16.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        NinebotIcon(NinebotLucide.nfc, size = 23.dp, color = colors.onSecondaryContainer)
        Text(
          text = stringResource(R.string.replica_nfc_keys),
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
          color = colors.onSecondaryContainer,
        )
        NinebotIcon(NinebotLucide.arrowUpRight, size = 21.dp, color = colors.onSecondaryContainer)
      }
    }
  }
}

@Composable
private fun VectorControlKey(
  @DrawableRes icon: Int,
  label: String,
  available: Boolean,
  unavailableReason: String,
  busy: Boolean,
  active: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val description = when {
    active -> stringResource(R.string.control_grid_in_progress_format, label)
    available -> label
    unavailableReason.isNotEmpty() -> stringResource(R.string.control_grid_unavailable_reason_format, label, unavailableReason)
    else -> stringResource(R.string.control_grid_unavailable_format, label)
  }
  VectorPressable(
    onClick = onClick,
    enabled = !busy,
    semanticsLabel = description,
    modifier = modifier,
    background = if (active) colors.secondaryContainer else colors.surface,
    borderColor = colors.outlineVariant,
    shape = RoundedCornerShape(4.dp),
  ) {
    val ink = if (active) colors.onSecondaryContainer else colors.onSurface
    Column(
      modifier = Modifier.fillMaxWidth().heightIn(min = 118.dp).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        if (active) {
          CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp, color = ink)
        } else {
          NinebotIcon(icon, size = 26.dp, color = if (available) ink else colors.onSurfaceVariant)
        }
        if (!available && !busy) {
          NinebotIcon(NinebotLucide.info, size = 15.dp, color = colors.onSurfaceVariant)
        }
      }
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = if (active) stringResource(R.string.control_grid_in_progress_format, label) else label,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
          color = ink,
        )
        if (!available && !busy) {
          Text(
            text = stringResource(R.string.control_grid_unavailable),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
fun VectorStatsRow(
  location: ResolvedVehicleLocation?,
  address: String,
  todayKm: String,
  totalKm: String,
  modifier: Modifier = Modifier,
  onMapTap: () -> Unit,
  onRideStatsTap: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val locationLabel = addressStripText(
    address,
    stringResource(if (location?.hasCoordinate == true) R.string.location_title else R.string.map_stats_no_location),
  )
  Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("vectorStats")) {
    VectorPressable(
      onClick = onRideStatsTap,
      semanticsLabel = stringResource(R.string.map_stats_view_ride),
      modifier = Modifier.fillMaxWidth().testTag("vectorRideStats"),
    ) {
      Column {
        VectorSectionTitle(stringResource(R.string.vector_control_ride), trailingIcon = NinebotLucide.arrowUpRight)
        Spacer(Modifier.height(16.dp))
        Row(
          modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
          verticalAlignment = Alignment.Top,
          horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
          VectorDistance(
            label = stringResource(R.string.map_stats_today_distance),
            valueWithUnit = todayKm,
            prominent = true,
            modifier = Modifier.weight(1f),
          )
          Box(modifier = Modifier.width(1.dp).height(72.dp).background(colors.outlineVariant))
          VectorDistance(
            label = stringResource(R.string.map_stats_total_distance),
            valueWithUnit = totalKm,
            prominent = false,
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
    HorizontalDivider(color = colors.outlineVariant)
    VectorPressable(
      onClick = onMapTap,
      semanticsLabel = stringResource(R.string.map_stats_vehicle_location, locationLabel),
      modifier = Modifier.fillMaxWidth().testTag("vectorLocation"),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        NinebotIcon(NinebotLucide.mapPin, size = 24.dp, color = colors.primary)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = locationLabel,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.onSurface,
          )
          location?.timeLabel?.takeIf { it.isNotBlank() }?.let { time ->
            Text(text = time, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
          }
        }
        NinebotIcon(NinebotLucide.arrowUpRight, size = 21.dp, color = colors.onSurface)
      }
    }
    HorizontalDivider(color = colors.outlineVariant)
  }
}

@Composable
private fun VectorDistance(label: String, valueWithUnit: String, prominent: Boolean, modifier: Modifier) {
  val colors = MaterialTheme.colorScheme
  val (value, unit) = splitValueWithUnit(valueWithUnit.ifBlank { "--" })
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    BasicText(
      text = value,
      modifier = Modifier.fillMaxWidth(),
      maxLines = 1,
      autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = if (prominent) 48.sp else 38.sp),
      style = MaterialTheme.typography.displaySmall.copy(
        fontSize = if (prominent) 48.sp else 38.sp,
        lineHeight = TextUnit.Unspecified,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-1.5).sp,
        color = colors.onSurface,
      ),
    )
    if (unit.isNotBlank()) {
      Text(text = unit, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    }
  }
}

@Composable
private fun VectorSectionTitle(title: String, @DrawableRes trailingIcon: Int? = null) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
      color = colors.onSurface,
    )
    HorizontalDivider(modifier = Modifier.weight(1f), color = colors.outlineVariant)
    trailingIcon?.let { NinebotIcon(it, size = 24.dp, color = colors.onSurface) }
  }
}

@Composable
private fun VectorStatusLabel(@DrawableRes icon: Int, label: String, color: Color, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    NinebotIcon(icon, size = 15.dp, color = color)
    Text(text = label, style = MaterialTheme.typography.labelMedium, color = color)
  }
}

@Composable
private fun VectorHeaderAction(
  @DrawableRes icon: Int,
  label: String,
  modifier: Modifier = Modifier,
  accented: Boolean = false,
  busy: Boolean = false,
  onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  VectorPressable(
    onClick = onClick,
    semanticsLabel = label,
    enabled = !busy,
    modifier = modifier,
    background = if (accented) colors.secondaryContainer else Color.Transparent,
    borderColor = if (accented) Color.Transparent else colors.outlineVariant,
    shape = RoundedCornerShape(8.dp),
  ) {
    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
      val ink = if (accented) colors.onSecondaryContainer else colors.onSurface
      if (busy) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = ink)
      } else {
        NinebotIcon(icon, size = 22.dp, color = ink)
      }
    }
  }
}

/** Retains the shared click/accessibility implementation with a finite spring. */
@Composable
private fun VectorPressable(
  onClick: () -> Unit,
  semanticsLabel: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  background: Color = Color.Transparent,
  borderColor: Color = Color.Transparent,
  shape: Shape = RoundedCornerShape(4.dp),
  content: @Composable () -> Unit,
) {
  val reducedMotion = MotionPolicy.reduceMotion()
  AppPressable(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    pressedScale = 1f,
    background = background,
    pressedBackground = background,
    borderWidth = if (borderColor.alpha > 0f) 1.dp else 0.dp,
    borderColor = borderColor,
    shape = shape,
    semanticsLabel = semanticsLabel,
    builder = { pressed ->
      val scale by animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 650f),
        label = "vectorPress",
      )
      Box(modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
        content()
      }
    },
  )
}
