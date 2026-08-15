package com.tailg.plus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.data.ble.CommandCode
import com.tailg.plus.data.cloud.ControlChannelAvailability
import com.tailg.plus.ui.theme.CyberHomeColors

/**
 * Port of `lib/widgets/cyber_control_grid.dart` — six-key control grid
 * (寻车 / power slider / 设防+设置 / 坐垫 / NFC).
 *
 * **Pending reference**: [ControlChannelAvailability] (Dart
 * `lib/services/control_channel_resolver.dart`, fields `enabled` /
 * `disabledReason`) → `com.tailg.plus.data.cloud`.
 * [CommandCode] is already ported to `com.tailg.plus.data.ble`.
 *
 * Token mapping: `CyberHomeColors.primary/control/card/controlStrong/ink/
 * inkMuted/inkFaint/warning/success/actionShadow` → the same-named
 * [CyberHomeColors] tokens; `AppRadii.sheet` → [AppRadii.sheet].
 *
 * Icons: `Lucide.find` → `Icons.Filled.Radio`; `Lucide.lock/unlock` →
 * `Icons.Filled.Lock/LockOpen`; `Lucide.settings` → `Icons.Filled.Settings`;
 * `Lucide.seat` → `Icons.Filled.Inventory2`; `Lucide.nfc` → `Icons.Filled.Nfc`.
 */
@Composable
fun CyberControlGrid(
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
  val armLabel = if (armed == null) "设防/解防" else if (armed) "解防" else "设防"
  fun active(command: CommandCode) = activeCommand == command
  fun subdued(command: CommandCode) = busy && activeCommand != null && !active(command)
  val armActive = active(CommandCode.lock) || active(CommandCode.unlock)
  val armSubdued = busy && activeCommand != null && !armActive

  Column(modifier = modifier.padding(horizontal = 20.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top,
    ) {
      CircleKey(
        icon = Lucide.find,
        label = "寻车",
        available = findAvailability.enabled,
        unavailableReason = findAvailability.disabledReason,
        busy = active(CommandCode.find),
        subdued = subdued(CommandCode.find),
        onTap = onFind,
      )
      Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
        SlidePowerButton(
          isPowered = powered,
          onSlide = onPowerToggle,
          enabled = powerAvailability.enabled,
          busy = busy,
          unavailableReason = powerAvailability.disabledReason,
          onUnavailable = onPowerToggle,
        )
      }
      CircleKey(
        icon = if (armed == true) Lucide.unlock else Lucide.lock,
        label = armLabel,
        available = armAvailability.enabled,
        unavailableReason = armAvailability.disabledReason,
        busy = armActive,
        subdued = armSubdued,
        onTap = onArmToggle,
      )
    }
    Spacer(Modifier.height(18.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top,
    ) {
      CircleKey(
        icon = Lucide.settings,
        label = "设置",
        available = true,
        unavailableReason = "",
        busy = false,
        subdued = false,
        onTap = onSettings,
      )
      CircleKey(
        icon = Lucide.seat,
        label = "坐垫",
        available = seatAvailability.enabled,
        unavailableReason = seatAvailability.disabledReason,
        busy = active(CommandCode.openSeat),
        subdued = subdued(CommandCode.openSeat),
        onTap = onSeat,
      )
      CircleKey(
        icon = Lucide.nfc,
        label = "NFC",
        available = true,
        unavailableReason = "",
        busy = false,
        subdued = false,
        onTap = onNfc,
      )
    }
  }
}

/** Circular control key — 62dp button with busy spinner + unavailable caption. */
@Composable
private fun CircleKey(
  icon: ImageVector,
  label: String,
  available: Boolean,
  unavailableReason: String,
  busy: Boolean,
  subdued: Boolean,
  modifier: Modifier = Modifier,
  onTap: () -> Unit,
) {
  Column(
    modifier = modifier
      .alpha(if (subdued) 0.5f else 1f),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    AppPressable(
      onClick = { if (!busy) onTap() },
      enabled = true,
      shape = CircleShape,
      background = CyberHomeColors.card,
      shadowElevation = 6.dp,
      shadowColor = CyberHomeColors.actionShadow,
      semanticsLabel = label,
    ) {
      Box(
        modifier = Modifier.size(62.dp),
        contentAlignment = Alignment.Center,
      ) {
        if (busy) {
          CircularProgressIndicator(
            modifier = Modifier.size(26.dp),
            strokeWidth = 2.6f,
            color = CyberHomeColors.primary,
          )
        } else {
          LucideIcon(
            icon = icon,
            size = 25.dp,
            color = if (available) CyberHomeColors.ink else CyberHomeColors.inkFaint,
          )
        }
      }
    }
    Spacer(Modifier.height(8.dp))
    Text(
      text = if (busy) "${label}中" else label,
      textAlign = TextAlign.Center,
      style = androidx.compose.ui.text.TextStyle(
        fontSize = 12.sp,
        color = if (available) CyberHomeColors.inkMuted else CyberHomeColors.inkFaint,
        lineHeight = 12.sp * 1.2f,
      ),
    )
    Box(Modifier.height(16.dp)) {
      if (!available && !busy) {
        Text(
          text = "不可用",
          style = androidx.compose.ui.text.TextStyle(
            fontSize = 10.sp,
            color = CyberHomeColors.inkFaint,
          ),
        )
      }
    }
  }
}
