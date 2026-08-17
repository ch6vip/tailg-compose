package com.tailg.plus.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.data.model.CommandCode
import com.tailg.plus.data.model.ControlCommandActivity
import com.tailg.plus.data.model.ControlCommandActivityStatus
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.CyberHomeColors
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R

/**
 * Port of `lib/widgets/cyber_recent_commands.dart` — recent control-command
 * activity card.
 *
 * [CommandCode] / [ControlCommandActivity] / [ControlCommandActivityStatus]
 * are already ported (`data.ble` / `data.model`).
 *
 * Token mapping: `CyberHomeColors.card/ink/inkFaint/primary/warning/danger/
 * actionShadow` → the same-named [CyberHomeColors] tokens; `AppRadii.sheet` →
 * [AppRadii.sheet].
 *
 * Icons (Dart `commandActivityIcon`): `Lucide.power` → `Icons.Filled.PowerSettingsNew`;
 * `Lucide.lock` → `Icons.Filled.Lock`; `Lucide.unlock` → `Icons.Filled.LockOpen`;
 * `Lucide.find` → `Icons.Filled.Radio`; `Lucide.seat` → `Icons.Filled.Inventory2`.
 */

/** Dart `commandActivityIcon(CommandCode)`. */
fun commandActivityIcon(command: CommandCode): ImageVector = when (command) {
  CommandCode.POWER_ON, CommandCode.POWER_OFF -> Lucide.power
  CommandCode.LOCK -> Lucide.lock
  CommandCode.UNLOCK -> Lucide.unlock
  CommandCode.FIND -> Lucide.find
  CommandCode.OPEN_SEAT -> Lucide.seat
  else -> Lucide.find
}

@Composable
fun CyberRecentCommands(
  commands: List<ControlCommandActivity>,
  modifier: Modifier = Modifier,
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
      .padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 10.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = stringResource(R.string.recent_commands_title),
        style = androidx.compose.ui.text.TextStyle(
          fontSize = 14.sp,
          fontWeight = FontWeight.W600,
          color = CyberHomeColors.ink,
        ),
      )
      Spacer(Modifier.weight(1f))
      Text(
        text = if (commands.isEmpty()) stringResource(R.string.recent_commands_empty) else stringResource(R.string.recent_commands_count_format, commands.size),
        style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
      )
    }
    if (commands.isEmpty()) {
      Text(
        text = stringResource(R.string.recent_commands_empty_hint),
        style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
        modifier = Modifier.padding(vertical = 12.dp),
      )
    } else {
      commands.forEach { entry ->
        AnimatedCmdRow(entry = entry)
      }
    }
  }
}

/** Entrance animation for a newly appended command row (Dart SizeTransition). */
@Composable
private fun AnimatedCmdRow(
  entry: ControlCommandActivity,
) {
  var visible by remember(entry.id) { mutableStateOf(false) }
  LaunchedEffect(entry.id) {
    visible = true
  }
  AnimatedVisibility(
    visible = visible,
    enter = fadeIn(tween(AppMotion.dataChange)) +
      expandVertically(tween(AppMotion.dataChange)) +
      slideInVertically(tween(AppMotion.dataChange)) { -it / 8 },
    exit = fadeOut(tween(AppMotion.dataChange)) +
      shrinkVertically(tween(AppMotion.dataChange)),
  ) {
    CmdRow(entry = entry)
  }
}

@Composable
private fun CmdRow(entry: ControlCommandActivity) {
  val statusColor = when (entry.status) {
    ControlCommandActivityStatus.SUCCEEDED -> CyberHomeColors.primary
    ControlCommandActivityStatus.PENDING -> CyberHomeColors.warning
    ControlCommandActivityStatus.FAILED -> CyberHomeColors.danger
    ControlCommandActivityStatus.CANCELLED -> CyberHomeColors.inkFaint
  }
  Row(
    modifier = Modifier.padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .size(32.dp)
        .clip(CircleShape)
        .background(statusColor.copy(alpha = 0.12f)),
      contentAlignment = Alignment.Center,
    ) {
      LucideIcon(icon = commandActivityIcon(entry.command), size = 14.dp, color = statusColor)
    }
    Spacer(Modifier.width(10.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = entry.title,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = androidx.compose.ui.text.TextStyle(
          fontSize = 13.sp,
          fontWeight = FontWeight.W600,
          color = CyberHomeColors.ink,
        ),
      )
      Text(
        text = entry.subtitle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkFaint),
      )
    }
    Text(
      text = stringResource(R.string.recent_commands_just_now),
      style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkFaint),
    )
  }
}
