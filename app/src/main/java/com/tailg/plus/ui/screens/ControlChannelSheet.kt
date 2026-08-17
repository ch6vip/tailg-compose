package com.tailg.plus.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.R
import com.tailg.plus.domain.control.ControlTopBarChannel
import com.tailg.plus.domain.control.OfficialControlChannel
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.theme.CyberHomeColors

/**
 * Channel-selection bottom sheet (Dart CyberChannelStrip bottom sheet),
 * extracted from [ControlScreen] into its own file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ControlChannelSheet(
  currentChannel: OfficialControlChannel,
  channelStatus: ControlTopBarChannel,
  busy: Boolean,
  onSelect: (OfficialControlChannel) -> Unit,
  onDismiss: () -> Unit,
  onOpenInduction: () -> Unit,
  onBusyError: () -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = CyberHomeColors.card,
  ) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
      // Header: title + status + control_induction link.
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.control_channel),
          style = TextStyle(
            fontSize = 16.sp,
            fontWeight = FontWeight.W700,
            color = CyberHomeColors.ink,
          ),
        )
        Spacer(Modifier.weight(1f))
        Text(
          text = channelStatus.label,
          style = TextStyle(
            fontSize = 12.sp,
            color = CyberHomeColors.inkMuted,
          ),
        )
        Spacer(Modifier.width(10.dp))
        TextButton(
          onClick = {
            onDismiss()
            onOpenInduction()
          },
          contentPadding = PaddingValues(0.dp),
        ) {
          Text(
            text = stringResource(R.string.control_induction),
            style = TextStyle(
              fontSize = 12.sp,
              fontWeight = FontWeight.W600,
              color = CyberHomeColors.primary,
            ),
          )
        }
      }
      Spacer(Modifier.height(12.dp))
      channelSheetOptions().forEach { (channel, label, subtitle) ->
        val active = currentChannel == channel
        AppPressable(
          onClick = {
            // Keep busy guard, then trigger channel-specific side effects
            // (BLE auto-link, official-cloud MQTT preconnect) in the caller.
            if (busy) {
              onBusyError()
              return@AppPressable
            }
            if (currentChannel == channel) {
              onDismiss()
              return@AppPressable
            }
            onSelect(channel)
          },
          shape = RoundedCornerShape(12.dp),
          background = if (active) CyberHomeColors.primarySoft else CyberHomeColors.cardMuted,
          semanticsLabel = label,
        ) {
          Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
              text = label,
              style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.W700,
                color = if (active) CyberHomeColors.primary else CyberHomeColors.ink,
              ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
              text = subtitle,
              style = TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkMuted),
            )
          }
        }
        Spacer(Modifier.height(8.dp))
      }
    }
  }
}
