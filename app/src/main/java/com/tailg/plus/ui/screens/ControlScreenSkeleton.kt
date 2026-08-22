package com.tailg.plus.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.tailg.plus.ui.components.AppMotion
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.components.AppSkeleton
import com.tailg.plus.ui.components.CyberCard
import com.tailg.plus.ui.components.VehicleControlGateBanner
import com.tailg.plus.ui.components.VehicleControlHomeGateKind
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

/**
 * Gate overlay for the Cyber control home — banner (signed-out / error /
 * no-vehicle) or loading banner + [CyberHomeSkeleton], cross-fading between
 * states and into the content beneath.
 *
 * The Dart original prepends the gate as a sliver before the pinned header.
 * A Compose scrollable list instead re-measures the header with transient
 * constraints when a prepended item is removed, which made the Canvas
 * vehicle illustration flash at a wrong offset for a frame — visible even
 * after the header's entrance fade had already run (cold start through a
 * loading gate). Rendering the gate as an overlay keeps the list item
 * structure constant from cold start, so the header is never re-measured on
 * gate clear.
 *
 * [Crossfade] keeps the outgoing state composed during the transition, so
 * the skeleton fades out over the already-laid-out content instead of
 * snapping. Banner-only gates render a bare (non-consuming) Column so the
 * content below stays interactive exactly as before; the loading state
 * covers with [CyberHomeColors.pageBg] and swallows input.
 */
@Composable
internal fun CyberControlGateOverlay(
  gateKind: VehicleControlHomeGateKind,
  error: String?,
  onRetry: () -> Unit,
  onLogin: () -> Unit,
  onAddVehicle: () -> Unit,
) {
  Crossfade(
    targetState = gateKind,
    animationSpec = tween(AppMotion.status),
    label = "cyberGateOverlay",
  ) { kind ->
    if (kind == VehicleControlHomeGateKind.Loading) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(CyberHomeColors.pageBg)
          // Self-contained while loading: drags scroll the skeleton, taps are
          // consumed so they never reach the covered header actions.
          .verticalScroll(rememberScrollState())
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
          ) {},
      ) {
        VehicleControlGateBanner(
          title = "正在同步官方车辆…",
          actionLabel = stringResource(R.string.control_syncing_action),
          busy = true,
          onAction = {},
        )
        Spacer(Modifier.height(18.dp))
        CyberHomeSkeleton()
      }
    } else {
      Column {
        when (kind) {
          VehicleControlHomeGateKind.SignedOut -> VehicleControlGateBanner(
            title = stringResource(R.string.control_need_login),
            actionLabel = stringResource(R.string.control_login_action),
            onAction = onLogin,
          )
          VehicleControlHomeGateKind.Error -> VehicleControlGateBanner(
            title = error?.trim()?.ifEmpty { null } ?: "车辆同步失败，请重试",
            actionLabel = stringResource(R.string.control_retry_action),
            onAction = onRetry,
          )
          VehicleControlHomeGateKind.NoVehicle -> VehicleControlGateBanner(
            title = "暂无车辆，请先同步官方车辆",
            actionLabel = stringResource(R.string.control_add_vehicle_action),
            onAction = onAddVehicle,
          )
          VehicleControlHomeGateKind.Loading,
          VehicleControlHomeGateKind.NearField,
          VehicleControlHomeGateKind.None -> {}
        }
      }
    }
  }
}
