package com.tailg.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.R
import com.tailg.plus.data.cloud.ResolvedVehicleLocation
import com.tailg.plus.domain.control.OfficialControlChannel
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.components.AppSkeleton
import com.tailg.plus.ui.components.CyberCard
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.CyberHomeColors

/**
 * Composable helpers extracted from [ControlScreen]:
 * the channel bottom-sheet options, the location title and the loading skeleton.
 */

/** Channel bottom-sheet options (constant; avoids rebuilding per recomposition). */
@Composable
internal fun channelSheetOptions() = listOf(
  Triple(OfficialControlChannel.AUTOMATIC, stringResource(R.string.control_channel_auto), stringResource(R.string.control_channel_auto_desc)),
  Triple(OfficialControlChannel.BLE, stringResource(R.string.control_channel_ble), stringResource(R.string.control_channel_ble_desc)),
  Triple(OfficialControlChannel.OFFICIAL_CLOUD, stringResource(R.string.control_channel_cloud), stringResource(R.string.control_channel_cloud_desc)),
)

@Composable
internal fun locationTitle(location: ResolvedVehicleLocation?): String {
  val address = location?.address?.trim() ?: ""
  if (address.isNotEmpty()) return address
  val coords = location?.coordinateText ?: ""
  if (coords.isNotEmpty()) return coords
  return stringResource(R.string.control_no_location)
}

/**
 * Dart `_CyberHomeSkeleton`: hero card + control grid (3 circles) + map placeholder.
 */
@Composable
internal fun CyberHomeSkeleton() {
  val base = CyberHomeColors.control
  val highlight = CyberHomeColors.cardMuted
  Column(modifier = Modifier.padding(horizontal = 20.dp)) {
    // Hero skeleton.
    CyberCard(modifier = Modifier.height(300.dp)) {
      Column(horizontalAlignment = Alignment.Start) {
        AppSkeleton(
          width = 160.dp,
          height = 22.dp,
          baseColor = base,
          highlightColor = highlight,
        )
        Spacer(Modifier.height(22.dp))
        AppSkeleton(
          width = 110.dp,
          height = 44.dp,
          baseColor = base,
          highlightColor = highlight,
        )
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
          AppSkeleton(
            width = 200.dp,
            height = 90.dp,
            borderRadius = RoundedCornerShape(AppRadii.tile),
            baseColor = base,
            highlightColor = highlight,
          )
        }
        Spacer(Modifier.height(16.dp))
        AppSkeleton(
          width = 240.dp,
          height = 12.dp,
          baseColor = base,
          highlightColor = highlight,
        )
      }
    }
    Spacer(Modifier.height(18.dp))
    // Control grid skeleton.
    CyberCard(modifier = Modifier.height(168.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
      ) {
        repeat(3) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppSkeleton(
              width = 56.dp,
              height = 56.dp,
              borderRadius = CircleShape,
              baseColor = base,
              highlightColor = highlight,
            )
            Spacer(Modifier.height(12.dp))
            AppSkeleton(
              width = 56.dp,
              height = 12.dp,
              baseColor = base,
              highlightColor = highlight,
            )
          }
        }
      }
    }
    Spacer(Modifier.height(18.dp))
    // Map skeleton.
    Box(
      modifier = Modifier
        .height(180.dp)
        .clip(RoundedCornerShape(AppRadii.sheet))
        .background(CyberHomeColors.mapPlaceholder),
    )
  }
}
