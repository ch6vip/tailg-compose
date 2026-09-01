package com.tailg.plus.ui.components

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tailg.plus.ui.theme.CyberHomeColors

/**
 * Port of `lib/widgets/app_chrome.dart` — retained surface is only the
 * pulsing [AppSkeleton] placeholder (used by the Cyber home gate, garage and
 * message screens). The VOID dark page chrome ([AppPageHeader], [AppCard],
 * [AppEmptyState] etc.) was dead code in the light-only app and was removed
 * together with the other unused VOID widgets.
 */

/** AppSkeleton — pulsing placeholder bar (pulse disabled under reduce-motion). */
@Composable
fun AppSkeleton(
  width: Dp,
  modifier: Modifier = Modifier,
  height: Dp = 12.dp,
  borderRadius: Shape = RoundedCornerShape(height / 2f),
  baseColor: Color = CyberHomeColors.control,
  highlightColor: Color = CyberHomeColors.controlStrong,
) {
  val loopsEnabled = MotionPolicy.loopsEnabled()
  // Static skeleton when animations are disabled: skip the infinite
  // transition entirely so no per-frame work is scheduled for loading bars
  // that only exist for a moment.
  if (!loopsEnabled) {
    Box(
      modifier = modifier
        .width(width)
        .height(height)
        .clip(borderRadius)
        .background(baseColor),
    )
    return
  }
  val transition: InfiniteTransition = rememberInfiniteTransition(label = "skeleton")
  val t by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(AppMotion.pulsePeriod, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "skeletonPulse",
  )
  val color = lerpColor(baseColor, highlightColor, t)
  Box(
    modifier = modifier
      .width(width)
      .height(height)
      .clip(borderRadius)
      .background(color),
  )
}

private fun lerpColor(a: Color, b: Color, t: Float): Color =
  androidx.compose.ui.graphics.lerp(a, b, t)
