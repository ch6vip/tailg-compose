package com.tailg.plus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.data.cloud.ControlChannelAvailability
import com.tailg.plus.data.cloud.ControlTopBarChannel
import com.tailg.plus.data.cloud.ControlTopBarChannelKind
import com.tailg.plus.data.cloud.OfficialControlChannel
import com.tailg.plus.ui.theme.AppColorsDark
import com.tailg.plus.ui.theme.AppColorsLight
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppTouchTargets

/**
 * Port of `lib/widgets/control_and_unlock_card.dart` — home card for the
 * control channel (智能 / 仅蓝牙 / 仅云端).
 *
 * **Pending references**: [OfficialControlChannel], [ControlChannelAvailability],
 * [ControlTopBarChannel], [ControlTopBarChannelKind] →
 * `com.tailg.plus.data.cloud` (see CyberChannelStrip for the batch list).
 *
 * Token mapping (Dart → Compose):
 * - `VoidColors.inkMuted/inkFaint/voidPanelHi/hairline` →
 *   [AppColorsDark.textSecondary] / [AppColorsDark.textTertiary] /
 *   [AppColorsDark.surfaceContainerHigh] /
 *   [AppColorsDark.textPrimary].copy(alpha = 0.13f).
 * - `VoidColors.energy/energyAmber/energyRed` → [AppColorsDark.energyGreen] /
 *   [AppColorsDark.energyAmber] / [AppColorsDark.energyRed].
 * - Dart `Colors.black` (selected segment text) → [AppColorsLight.textPrimary].
 * - `VoidRadii.sm` → [AppRadii.sm]; card radius 20 → [AppRadii.lg].
 *
 * Icons: `Lucide.channel` → `Icons.Filled.AltRoute`; `Lucide.settings` →
 * `Icons.Filled.Settings`.
 */
@Composable
fun ControlAndUnlockCard(
  channelSelected: OfficialControlChannel,
  availability: ControlChannelAvailability,
  channelStatus: ControlTopBarChannel,
  channelBusy: Boolean,
  modifier: Modifier = Modifier,
  cardMargin: PaddingValues = PaddingValues(horizontal = 20.dp),
  cardRadius: Dp = 20.dp,
  onChannelChanged: (OfficialControlChannel) -> Unit,
  onOpenInductionSettings: (() -> Unit)? = null,
) {
  VoidGlassCard(
    modifier = modifier.padding(cardMargin),
    borderRadius = cardRadius,
    contentPadding = PaddingValues(14.dp),
  ) {
    Column(modifier = Modifier.padding(top = 0.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        LucideIcon(icon = Lucide.channel, size = 16.dp, color = AppColorsDark.textSecondary)
        Spacer(Modifier.width(8.dp))
        Text(
          text = "控车渠道",
          style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontWeight = FontWeight.W600, color = AppColorsDark.textPrimary),
        )
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
          if (channelBusy) {
            Box(
              modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(16.dp),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2f,
                color = AppColorsDark.energyGreen,
              )
            }
          } else {
            Row(
              modifier = Modifier.align(Alignment.CenterEnd),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier = Modifier
                  .size(7.dp)
                  .clip(CircleShape)
                  .background(channelDotColor(channelBusy, channelStatus.kind)),
              )
              Spacer(Modifier.width(6.dp))
              Text(
                text = channelStatusLabel(channelBusy, availability.enabled, channelStatus),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = AppColorsDark.textSecondary),
              )
            }
          }
        }
        if (onOpenInductionSettings != null) {
          Spacer(Modifier.width(4.dp))
          AppPressable(
            onClick = if (channelBusy) null else onOpenInductionSettings,
            enabled = !channelBusy,
            shape = RoundedCornerShape(AppRadii.sm),
            semanticsLabel = "感应解锁设置",
          ) {
            Box(
              modifier = Modifier.size(AppTouchTargets.min),
              contentAlignment = Alignment.Center,
            ) {
              LucideIcon(icon = Lucide.settings, size = 18.dp, color = AppColorsDark.textSecondary)
            }
          }
        }
      }
      Spacer(Modifier.height(12.dp))
      SingleChoiceSegmentedButtonRow(
        modifier = Modifier.height(40.dp),
      ) {
        val segmentShape = { index: Int ->
          SegmentedButtonDefaults.itemShape(
            index = index,
            count = 3,
            baseShape = RoundedCornerShape(AppRadii.sm),
          )
        }
        val colors = SegmentedButtonDefaults.colors(
          activeContainerColor = AppColorsDark.energyGreen,
          activeContentColor = AppColorsLight.textPrimary, // Dart Colors.black
          activeBorderColor = AppColorsDark.energyGreen,
          inactiveContainerColor = AppColorsDark.surfaceContainerHigh,
          inactiveContentColor = AppColorsDark.textSecondary,
          inactiveBorderColor = AppColorsDark.textPrimary.copy(alpha = 0.13f),
        )
        Segment(
          value = OfficialControlChannel.automatic,
          label = "智能",
          selected = channelSelected,
          enabled = !channelBusy,
          shape = segmentShape(0),
          colors = colors,
          onSelect = onChannelChanged,
        )
        Segment(
          value = OfficialControlChannel.ble,
          label = "仅蓝牙",
          selected = channelSelected,
          enabled = !channelBusy,
          shape = segmentShape(1),
          colors = colors,
          onSelect = onChannelChanged,
        )
        Segment(
          value = OfficialControlChannel.officialCloud,
          label = "仅云端",
          selected = channelSelected,
          enabled = !channelBusy,
          shape = segmentShape(2),
          colors = colors,
          onSelect = onChannelChanged,
        )
      }
      Spacer(Modifier.height(10.dp))
      Text(
        text = channelDescription(channelBusy, availability, channelSelected),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = AppColorsDark.textTertiary),
      )
    }
  }
}

@Composable
private fun Segment(
  value: OfficialControlChannel,
  label: String,
  selected: OfficialControlChannel,
  enabled: Boolean,
  shape: androidx.compose.foundation.shape.RoundedCornerShape,
  colors: androidx.compose.material3.SegmentedButtonColors,
  onSelect: (OfficialControlChannel) -> Unit,
) {
  SegmentedButton(
    selected = selected == value,
    onClick = { onSelect(value) },
    enabled = enabled,
    shape = shape,
    colors = colors,
    // Dart `showSelectedIcon: false` — suppress M3's default checkmark slot.
    icon = {},
    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Transparent),
  ) {
    Text(
      text = label,
      style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontWeight = FontWeight.W600),
    )
  }
}

/** Dart `_channelDotColor`. */
private fun channelDotColor(busy: Boolean, kind: ControlTopBarChannelKind): Color =
  if (busy) {
    AppColorsDark.energyAmber
  } else {
    when (kind) {
      ControlTopBarChannelKind.bleDirect,
      ControlTopBarChannelKind.mqttRemote,
      ControlTopBarChannelKind.cloudStandby,
      -> AppColorsDark.energyGreen
      ControlTopBarChannelKind.bleConnecting,
      ControlTopBarChannelKind.mqttConnecting,
      ControlTopBarChannelKind.mqttRetry,
      -> AppColorsDark.energyAmber
      ControlTopBarChannelKind.unavailable -> AppColorsDark.energyRed
    }
  }

/** Dart `_channelStatusLabel`. */
private fun channelStatusLabel(
  busy: Boolean,
  enabled: Boolean,
  status: ControlTopBarChannel,
): String {
  if (busy) return "指令执行中"
  if (enabled ||
    status.kind == ControlTopBarChannelKind.bleConnecting ||
    status.kind == ControlTopBarChannelKind.mqttConnecting ||
    status.kind == ControlTopBarChannelKind.mqttRetry
  ) {
    return status.label
  }
  return "当前不可用"
}

/** Dart `_channelDescription`. */
private fun channelDescription(
  busy: Boolean,
  availability: ControlChannelAvailability,
  selected: OfficialControlChannel,
): String {
  if (busy) return "指令执行中，暂不能切换渠道"
  if (!availability.enabled) {
    val reason = when (selected) {
      OfficialControlChannel.automatic -> availability.disabledReason
      OfficialControlChannel.ble -> availability.bleUnavailableReason
      OfficialControlChannel.officialCloud -> availability.cloudUnavailableReason
    }
    if (reason.trim().isNotEmpty()) return reason.trim()
  }
  return when (selected) {
    OfficialControlChannel.automatic -> "按车辆能力自动选择蓝牙或云端"
    OfficialControlChannel.ble -> "仅附近蓝牙直连"
    OfficialControlChannel.officialCloud -> "仅官方账号远程"
  }
}
