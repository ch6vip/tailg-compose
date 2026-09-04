package com.tailg.plus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.data.ble.CommandCode
import com.tailg.plus.domain.control.ControlChannelAvailability
import com.tailg.plus.ui.theme.CyberHomeColors
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R

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
  val armLabel = if (armed == null) stringResource(R.string.control_grid_arm_title) else if (armed) stringResource(R.string.control_grid_disarm) else stringResource(R.string.control_grid_arm)
  fun active(command: CommandCode) = activeCommand == command
  fun subdued(command: CommandCode) = busy && activeCommand != null && !active(command)
  val armActive = active(CommandCode.lock) || active(CommandCode.unlock)
  val armSubdued = busy && activeCommand != null && !armActive

  Column(modifier = modifier.padding(horizontal = 20.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.Top,
    ) {
      CircleKey(
        icon = Lucide.find,
        label = stringResource(R.string.control_card_find),
        available = findAvailability.enabled,
        unavailableReason = findAvailability.disabledReason,
        busy = active(CommandCode.find),
        subdued = subdued(CommandCode.find),
        modifier = Modifier.weight(1f),
        onTap = onFind,
      )
      Box(modifier = Modifier.weight(1.8f), contentAlignment = Alignment.Center) {
        SlidePowerButton(
          isPowered = powered,
          onSlide = onPowerToggle,
          trackWidth = 160.dp,
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
        modifier = Modifier.weight(1f),
        onTap = onArmToggle,
      )
    }
    Spacer(Modifier.height(18.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.Top,
    ) {
      CircleKey(
        icon = Lucide.settings,
        label = stringResource(R.string.control_grid_settings),
        available = true,
        unavailableReason = "",
        busy = false,
        subdued = false,
        modifier = Modifier.weight(1f),
        onTap = onSettings,
      )
      CircleKey(
        icon = Lucide.seat,
        label = stringResource(R.string.control_card_seat),
        available = seatAvailability.enabled,
        unavailableReason = seatAvailability.disabledReason,
        busy = active(CommandCode.openSeat),
        subdued = subdued(CommandCode.openSeat),
        modifier = Modifier.weight(1.8f),
        onTap = onSeat,
      )
      CircleKey(
        icon = Lucide.nfc,
        label = stringResource(R.string.replica_nfc_keys),
        available = true,
        unavailableReason = "",
        busy = false,
        subdued = false,
        modifier = Modifier.weight(1f),
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
      // Dart dims the whole key (not just the caption) when unavailable.
      .alpha(if (subdued || !available) 0.5f else 1f),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    AppPressable(
      // Stay tappable while dimmed so unavailable reason snacks can still fire.
      onClick = { if (!busy) onTap() },
      enabled = true,
      shape = CircleShape,
      background = if (available) CyberHomeColors.card else CyberHomeColors.cardMuted,
      shadowElevation = 0.dp,
      borderWidth = 1.dp,
      borderColor = CyberHomeColors.line,
      semanticsLabel = if (available) {
        label
      } else if (unavailableReason.isEmpty()) {
        stringResource(R.string.control_grid_unavailable_format, label)
      } else {
        stringResource(R.string.control_grid_unavailable_reason_format, label, unavailableReason)
      },
    ) {
      Box(
        modifier = Modifier.size(62.dp),
        contentAlignment = Alignment.Center,
      ) {
        if (busy) {
          CircularProgressIndicator(
            modifier = Modifier.size(26.dp),
            strokeWidth = 2.6.dp,
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
      text = if (busy) stringResource(R.string.control_grid_in_progress_format, label) else label,
      modifier = Modifier.fillMaxWidth(),
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
      style = androidx.compose.ui.text.TextStyle(
        fontSize = 12.sp,
        color = if (available) CyberHomeColors.inkMuted else CyberHomeColors.inkFaint,
        lineHeight = 12.sp * 1.2f,
      ),
    )
    Box(
      modifier = Modifier.heightIn(min = 16.dp),
      contentAlignment = Alignment.TopCenter,
    ) {
      if (!available && !busy) {
        Text(
          text = stringResource(R.string.control_grid_unavailable),
          style = androidx.compose.ui.text.TextStyle(
            fontSize = 10.sp,
            color = CyberHomeColors.inkFaint,
          ),
        )
      }
    }
  }
}
