package com.tailg.plus.ui.components

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.ui.theme.AppColorsDark
import com.tailg.plus.ui.theme.AppIconSizes
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppTouchTargets

/**
 * Port of `lib/widgets/app_chrome.dart` — page header, section label, card,
 * header action, skeleton and empty state.
 *
 * Token mapping (Dart → Compose):
 * - `VoidColors.voidPanel(0.7)` → [AppColorsDark.surface].copy(alpha = 0.7f); `hairline` → [AppColorsDark.textPrimary].copy(alpha = 0.13f).
 * - `VoidColors.energy` → [AppColorsDark.energyGreen]; `inkMuted` → [AppColorsDark.textSecondary]; `inkFaint` → [AppColorsDark.textTertiary].
 * - `VoidRadii.lg` (24) → [AppRadii.lg] (20); `VoidGlow.panel` → shadow via [Modifier.shadow].
 * - `VoidSpace.screenX` (22) → [AppSpacing.screenX] (20); `VoidSpace.section` (28) → [AppSpacing.sectionGap] (20).
 *
 * Icons: `Lucide.arrow-left` → `Icons.Filled.ArrowBack`.
 */

/** AppPageHeader — back circle + kinetic title + trailing actions. */
@Composable
fun AppPageHeader(
  title: String,
  modifier: Modifier = Modifier,
  showBack: Boolean = true,
  onBack: (() -> Unit)? = null,
  actions: (@Composable RowScope.() -> Unit)? = null,
) {
  Row(
    modifier = modifier.padding(start = 12.dp, top = 12.dp, end = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (showBack) {
      AppPressable(
        onClick = onBack,
        pressedScale = AppMotion.pressScale,
        shape = CircleShape,
        background = AppColorsDark.surface.copy(alpha = 0.7f),
        borderWidth = 1.dp,
        borderColor = AppColorsDark.textPrimary.copy(alpha = 0.13f),
      ) {
        Box(
          modifier = Modifier.size(AppTouchTargets.min),
          contentAlignment = Alignment.Center,
        ) {
          LucideIcon(icon = Lucide.arrowLeft, size = 18.dp, color = AppColorsDark.textSecondary)
        }
      }
      Spacer(Modifier.width(10.dp))
    }
    KineticType(
      text = title,
      mode = KineticTypeMode.Word,
      staggerDelay = 25,
      durationMillis = 350,
      style = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, fontWeight = FontWeight.W600, color = AppColorsDark.textPrimary),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    actions?.invoke(this)
  }
}

/** AppSectionLabel — energy rule + wide-tracked micro text. */
@Composable
fun AppSectionLabel(
  text: String,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .width(14.dp)
        .height(1.5.dp)
        .background(AppColorsDark.energyGreen.copy(alpha = 0.7f)),
    )
    Spacer(Modifier.width(8.dp))
    Text(
      text = text,
      style = androidx.compose.ui.text.TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.W600,
        letterSpacing = 1.6.sp,
        color = AppColorsDark.textTertiary,
      ),
    )
  }
}

/** AppCard — VOID dark surface card with hairline border + panel shadow. */
@Composable
fun AppCard(
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(16.dp),
  color: Color = AppColorsDark.surface.copy(alpha = 0.72f),
  content: @Composable () -> Unit,
) {
  val shape = RoundedCornerShape(AppRadii.lg)
  Box(
    modifier = modifier
      .shadow(
        elevation = 14.dp,
        shape = shape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = 0.25f),
        spotColor = Color.Transparent,
      )
      .clip(shape)
      .background(color)
      .border(1.dp, AppColorsDark.textPrimary.copy(alpha = 0.13f), shape)
      .padding(contentPadding),
  ) {
    content()
  }
}

/** AppHeaderAction — 44dp icon button for page headers. */
@Composable
fun AppHeaderAction(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  tooltip: String? = null,
) {
  AppPressable(
    onClick = onClick,
    enabled = onClick != null,
    pressedScale = AppMotion.pressScale,
  ) {
    Box(
      modifier = Modifier.size(AppTouchTargets.min),
      contentAlignment = Alignment.Center,
    ) {
      LucideIcon(icon = icon, size = 20.dp, color = AppColorsDark.textSecondary)
    }
  }
}

/** AppSkeleton — pulsing placeholder bar (pulse disabled under reduce-motion). */
@Composable
fun AppSkeleton(
  width: Dp,
  modifier: Modifier = Modifier,
  height: Dp = 12.dp,
  borderRadius: Shape = RoundedCornerShape(height / 2f),
  baseColor: Color = AppColorsDark.surfaceContainerHigh,
  highlightColor: Color = AppColorsDark.surfaceContainerLow,
) {
  val loopsEnabled = MotionPolicy.loopsEnabled()
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
  val progress = if (loopsEnabled) t else 0.5f
  val color = lerpColor(baseColor, highlightColor, progress)
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

/** AppEmptyState — circular glyph + title + optional subtitle. */
@Composable
fun AppEmptyState(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  contentPadding: PaddingValues = PaddingValues(horizontal = 40.dp, vertical = 48.dp),
) {
  Column(
    modifier = modifier.padding(contentPadding),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier
        .size(72.dp)
        .clip(CircleShape)
        .background(AppColorsDark.surfaceContainerHigh)
        .border(1.dp, AppColorsDark.textPrimary.copy(alpha = 0.13f), CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      LucideIcon(icon = icon, size = AppIconSizes.md, color = AppColorsDark.textTertiary)
    }
    Spacer(Modifier.size(18.dp))
    Text(
      text = title,
      textAlign = TextAlign.Center,
      style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W600, color = AppColorsDark.textSecondary),
    )
    if (subtitle != null) {
      Spacer(Modifier.size(8.dp))
      Text(
        text = subtitle,
        textAlign = TextAlign.Center,
        style = androidx.compose.ui.text.TextStyle(
          fontSize = 12.sp,
          lineHeight = 18.sp,
          color = AppColorsDark.textTertiary,
        ),
      )
    }
  }
}
