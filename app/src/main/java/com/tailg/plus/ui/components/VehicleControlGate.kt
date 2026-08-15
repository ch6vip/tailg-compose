package com.tailg.plus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import com.tailg.plus.ui.theme.AppColorsDark
import com.tailg.plus.ui.theme.AppColorsLight
import com.tailg.plus.ui.theme.AppRadii

/**
 * Port of `lib/widgets/vehicle_control_gate.dart` — 爱车 empty/gate states
 * (未登录 / 无车 / 刷新中 / 错误) and the shared VOID gate banner.
 *
 * Token mapping (Dart → Compose):
 * - `VoidColors.voidPanel(0.85)` → [AppColorsDark.surface].copy(alpha = 0.85f).
 * - `VoidColors.energy(0.28 border)` → [AppColorsDark.energyGreen].copy(alpha = 0.28f).
 * - `VoidGlow.energy(0.25)` → shadow with [AppColorsDark.energyGreen] spot.
 * - `VoidColors.ink / inkMuted / inkFaint` → [AppColorsDark.textPrimary] /
 *   [AppColorsDark.textSecondary] / [AppColorsDark.textTertiary].
 * - `VoidRadii.md` (18) → [AppRadii.sheet]; pill → `RoundedCornerShape(999.dp)`.
 * - Dart `Colors.black` on the energy action → [AppColorsLight.textPrimary].
 */
enum class VehicleControlHomeGateKind { SignedOut, NoVehicle, Loading, Error, NearField, None }

object VehicleControlHomeGate {
  fun resolve(
    signedIn: Boolean,
    hasVehicle: Boolean,
    loading: Boolean,
    error: String?,
    showNearFieldHint: Boolean,
  ): VehicleControlHomeGateKind {
    if (!signedIn) return VehicleControlHomeGateKind.SignedOut
    if (loading && !hasVehicle) return VehicleControlHomeGateKind.Loading
    val err = error?.trim().orEmpty()
    if (err.isNotEmpty() && !hasVehicle) return VehicleControlHomeGateKind.Error
    if (!hasVehicle) return VehicleControlHomeGateKind.NoVehicle
    if (showNearFieldHint) return VehicleControlHomeGateKind.NearField
    return VehicleControlHomeGateKind.None
  }
}

/** Shared banner used by 爱车 empty/gate states — VOID glass strip. */
@Composable
fun VehicleControlGateBanner(
  title: String,
  actionLabel: String,
  modifier: Modifier = Modifier,
  busy: Boolean = false,
  onAction: () -> Unit,
) {
  Row(
    modifier = modifier
      .padding(start = 22.dp, top = 8.dp, end = 22.dp, bottom = 8.dp) // VoidSpace.screenX
      .shadow(
        elevation = 16.dp,
        shape = RoundedCornerShape(AppRadii.sheet),
        clip = false,
        ambientColor = Color.Transparent,
        spotColor = AppColorsDark.energyGreen.copy(alpha = 0.045f), // VoidGlow.energy(0.25)
      )
      .clip(RoundedCornerShape(AppRadii.sheet))
      .background(AppColorsDark.surface.copy(alpha = 0.85f))
      .border(1.dp, AppColorsDark.energyGreen.copy(alpha = 0.28f), RoundedCornerShape(AppRadii.sheet))
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = title,
      style = androidx.compose.ui.text.TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.W600,
        color = AppColorsDark.textPrimary,
      ),
      modifier = Modifier.weight(1f),
    )
    AppPressable(
      onClick = if (busy) null else onAction,
      enabled = !busy,
      pressedScale = AppMotion.pressScale,
      shape = RoundedCornerShape(999.dp),
      background = if (busy) AppColorsDark.textTertiary else AppColorsDark.energyGreen,
      shadowElevation = if (busy) 0.dp else 12.dp,
      shadowColor = AppColorsDark.energyGreen.copy(alpha = 0.1f),
      semanticsLabel = actionLabel,
    ) {
      Text(
        text = actionLabel,
        style = androidx.compose.ui.text.TextStyle(
          fontSize = 12.sp,
          fontWeight = FontWeight.W700,
          color = if (busy) AppColorsDark.textSecondary else AppColorsLight.textPrimary,
        ),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
      )
    }
  }
}
