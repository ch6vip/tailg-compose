package com.tailg.plus.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.ui.theme.AppColors
import com.tailg.plus.ui.theme.AppRadii
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R

/**
 * Port of `lib/widgets/status_badge.dart` — unified status badge (v8 chip).
 *
 * Token mapping: `AppColors.energyRed/energyGreen/textTertiary` →
 * [AppColors.energyRed] / [AppColors.energyGreen] / [AppColors.textTertiary];
 * `AppColors.surfaceBrandRedTint/surfaceBrandTealTint/surfaceContainerHigh` →
 * the same-named [AppColors] tokens; `AppRadii.pill` → [AppRadii.pill].
 *
 * Motion: `AppMotion.pulsePeriod/pulseMin/pulseMax` → [AppMotion].
 */
enum class StatusBadgeType { Armed, Idle, Connected, Online, Offline }

private val StatusBadgeType.dotColor: Color
  get() = when (this) {
    StatusBadgeType.Armed, StatusBadgeType.Offline -> AppColors.energyRed
    StatusBadgeType.Idle -> AppColors.textTertiary
    StatusBadgeType.Connected, StatusBadgeType.Online -> AppColors.energyGreen
  }

private val StatusBadgeType.bgColor: Color
  get() = when (this) {
    StatusBadgeType.Armed, StatusBadgeType.Offline -> AppColors.surfaceBrandRedTint
    StatusBadgeType.Idle -> AppColors.surfaceContainerHigh
    StatusBadgeType.Connected, StatusBadgeType.Online -> AppColors.surfaceBrandTealTint
  }

/** Armed, connected and online states are "active" — their dot should pulse. */
private val StatusBadgeType.isActive: Boolean
  get() = when (this) {
    StatusBadgeType.Armed, StatusBadgeType.Connected, StatusBadgeType.Online -> true
    StatusBadgeType.Idle, StatusBadgeType.Offline -> false
  }

@Composable
private fun StatusBadgeType.defaultLabel(): String
  = when (this) {
    StatusBadgeType.Armed -> stringResource(R.string.status_armed)
    StatusBadgeType.Idle -> stringResource(R.string.status_unpowered)
    StatusBadgeType.Connected -> stringResource(R.string.status_connected)
    StatusBadgeType.Online -> stringResource(R.string.status_online)
    StatusBadgeType.Offline -> stringResource(R.string.status_offline)
  }

/** Unified status badge for the v8 design system (chip style). */
@Composable
fun StatusBadge(
  type: StatusBadgeType,
  modifier: Modifier = Modifier,
  label: String? = null,
  showDot: Boolean = true,
  compact: Boolean = false,
) {
  val displayLabel = label ?: type.defaultLabel()
  if (compact) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
      if (showDot) {
        Box(
          modifier = Modifier
            .padding(end = 5.dp)
            .size(8.dp)
            .clip(CircleShape)
            .background(type.dotColor),
        )
      }
      Text(
        text = displayLabel,
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.W600, color = type.dotColor),
      )
    }
    return
  }

  Row(
    modifier = modifier
      .clip(RoundedCornerShape(AppRadii.pill))
      .background(type.bgColor)
      .padding(horizontal = if (showDot) 10.dp else 12.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (showDot) {
      PulsingDot(color = type.dotColor, pulsing = type.isActive)
      Box(Modifier.width(5.dp))
    }
    Text(
      text = displayLabel,
      style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.W600, color = type.dotColor),
    )
  }
}

/** Pulsing dot indicator — static when [pulsing] is false (idle/offline). */
@Composable
private fun PulsingDot(
  color: Color,
  pulsing: Boolean,
) {
  val loops = pulsing && MotionPolicy.loopsEnabled()
  // The infinite transition only runs while [loops] is true. When a badge
  // flips to a non-active state (or reduce-motion is on), the transition is
  // removed from composition and the dot renders statically — no per-frame
  // recomposition is kept alive for idle/offline badges.
  if (!loops) {
    Box(
      modifier = Modifier
        .size(7.dp)
        .clip(CircleShape)
        .background(color),
    )
    return
  }
  val transition = rememberInfiniteTransition(label = "pulsingDot")
  val t by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(AppMotion.pulsePeriod),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "dotPulse",
  )
  val scale = AppMotion.pulseMin + (AppMotion.pulseMax - AppMotion.pulseMin) * t
  Box(
    modifier = Modifier
      .scale(scale)
      .size(7.dp)
      .clip(CircleShape)
      .background(color),
  )
}
