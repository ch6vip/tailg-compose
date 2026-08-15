package com.tailg.plus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.domain.control.ControlTopBarChannel
import com.tailg.plus.domain.control.OfficialControlChannel
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.CyberHomeColors

/**
 * Port of `lib/widgets/cyber_channel_strip.dart` — control-channel selector
 * strip (智能 / 仅蓝牙 / 仅云端).
 *
 * **Pending references** (ported in a later batch, per CONVENTIONS.md):
 * - [OfficialControlChannel] (Dart `lib/services/control_channel_resolver.dart`
 *   enum `automatic`/`ble`/`officialCloud`) → `com.tailg.plus.data.cloud`.
 * - [ControlTopBarChannel] (Dart `lib/services/control_channel_status.dart`
 *   class with `label` + `kind`) → `com.tailg.plus.data.cloud`.
 *
 * Token mapping: `CyberHomeColors.card/control/primary/white/ink/inkMuted/
 * warning/actionShadow` → the same-named [CyberHomeColors] tokens;
 * `AppRadii.sheet` → [AppRadii.sheet].
 */
@Composable
fun CyberChannelStrip(
  selected: OfficialControlChannel,
  status: ControlTopBarChannel,
  busy: Boolean,
  modifier: Modifier = Modifier,
  onChanged: (OfficialControlChannel) -> Unit,
  onInduction: () -> Unit,
) {
  Column(
    modifier = modifier
      .padding(horizontal = 20.dp)
      .shadow(
        elevation = 6.dp,
        shape = RoundedCornerShape(AppRadii.sheet),
        clip = false,
        ambientColor = Color.Transparent,
        spotColor = CyberHomeColors.actionShadow,
      )
      .clip(RoundedCornerShape(AppRadii.sheet))
      .background(CyberHomeColors.card)
      .padding(14.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = "控车渠道",
        style = androidx.compose.ui.text.TextStyle(
          fontSize = 14.sp,
          fontWeight = FontWeight.W600,
          color = CyberHomeColors.ink,
        ),
      )
      Spacer(Modifier.weight(1f))
      Text(
        text = if (busy) "指令执行中" else status.label,
        style = androidx.compose.ui.text.TextStyle(
          fontSize = 12.sp,
          color = if (busy) CyberHomeColors.warning else CyberHomeColors.inkMuted,
        ),
      )
      Spacer(Modifier.width(8.dp))
      AppPressable(
        onClick = onInduction,
        shape = RoundedCornerShape(AppRadii.sm),
        semanticsLabel = "感应设置",
      ) {
        Text(
          text = "感应",
          style = androidx.compose.ui.text.TextStyle(
            fontSize = 12.sp,
            color = CyberHomeColors.primary,
            fontWeight = FontWeight.W600,
          ),
        )
      }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      ChannelChip(
        channel = OfficialControlChannel.AUTOMATIC,
        label = "智能",
        selected = selected,
        busy = busy,
        onSelect = { onChanged(OfficialControlChannel.AUTOMATIC) },
        modifier = Modifier.weight(1f),
      )
      ChannelChip(
        channel = OfficialControlChannel.BLE,
        label = "仅蓝牙",
        selected = selected,
        busy = busy,
        onSelect = { onChanged(OfficialControlChannel.BLE) },
        modifier = Modifier.weight(1f),
      )
      ChannelChip(
        channel = OfficialControlChannel.OFFICIAL_CLOUD,
        label = "仅云端",
        selected = selected,
        busy = busy,
        onSelect = { onChanged(OfficialControlChannel.OFFICIAL_CLOUD) },
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun ChannelChip(
  channel: OfficialControlChannel,
  label: String,
  selected: OfficialControlChannel,
  busy: Boolean,
  modifier: Modifier = Modifier,
  onSelect: () -> Unit,
) {
  val on = selected == channel
  Box(
    modifier = modifier
      .height(34.dp)
      .clip(RoundedCornerShape(17.dp))
      .background(if (on) CyberHomeColors.primary else CyberHomeColors.control),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      style = androidx.compose.ui.text.TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.W600,
        color = if (on) CyberHomeColors.white else CyberHomeColors.inkMuted,
      ),
      modifier = Modifier
        .matchParentSize()
        .clickableWithoutRipple(enabled = !busy) { onSelect() },
    )
  }
}

private fun Modifier.clickableWithoutRipple(enabled: Boolean, onClick: () -> Unit): Modifier =
  this.clickable(enabled = enabled, onClick = onClick)
