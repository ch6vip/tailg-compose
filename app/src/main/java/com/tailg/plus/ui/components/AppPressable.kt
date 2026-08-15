package com.tailg.plus.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Port of `lib/widgets/app_pressable.dart`.
 *
 * Reusable press-feedback composable: consistent scale + background tint on
 * press (Dart `AnimatedScale` + `AnimatedContainer`). The Dart
 * `BoxShadow`-list API is mapped to a single elevation-based shadow
 * ([Modifier.shadow] with `spotColor`), which Compose can render per-shape.
 *
 * Token mapping (Dart → Compose):
 * - `AppMotion.pressScale/micro/pressCurve` → [AppMotion].
 * - default `background` `Colors.transparent` → `Color.Transparent`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppPressable(
  onClick: (() -> Unit)?,
  modifier: Modifier = Modifier,
  onLongPress: (() -> Unit)? = null,
  enabled: Boolean = true,
  pressedScale: Float = AppMotion.pressScale,
  background: Color = Color.Transparent,
  pressedBackground: Color? = null,
  shape: Shape = RoundedCornerShape(0.dp),
  shadowElevation: Dp = 0.dp,
  pressedShadowElevation: Dp? = null,
  shadowColor: Color = Color.Transparent,
  pressedShadowColor: Color? = null,
  borderWidth: Dp = 0.dp,
  borderColor: Color = Color.Transparent,
  haptic: Boolean = true,
  semanticsLabel: String? = null,
  semanticsButton: Boolean = true,
  semanticsEnabled: Boolean? = null,
  semanticsSelected: Boolean? = null,
  builder: (@Composable (pressed: Boolean) -> Unit)? = null,
  content: @Composable () -> Unit = {},
) {
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val isActive = enabled && pressed

  val scale by animateFloatAsState(
    targetValue = if (isActive) pressedScale else 1f,
    animationSpec = AppMotion.tween(AppMotion.micro),
    label = "appPressableScale",
  )
  val bg by animateColorAsState(
    targetValue = if (isActive) pressedBackground ?: background.copy(alpha = background.alpha * 0.7f)
    else background,
    animationSpec = AppMotion.tween(AppMotion.micro),
    label = "appPressableBackground",
  )
  val elevation = if (isActive) pressedShadowElevation ?: shadowElevation else shadowElevation
  val spot = if (isActive) pressedShadowColor ?: shadowColor else shadowColor

  val haptics = LocalHapticFeedback.current

  // Dart `excludeSemantics: true` when a label is set; button/enabled/selected
  // states ride on combinedClickable's own semantics (role + enabled).
  val semanticsModifier = if (semanticsLabel != null) {
    Modifier.clearAndSetSemantics {
      contentDescription = semanticsLabel
    }
  } else {
    Modifier
  }
  val roleModifier = if (semanticsButton) Modifier.semantics { this.role = Role.Button } else Modifier

  Box(
    modifier = modifier
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
      .shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = Color.Transparent,
        spotColor = spot,
      )
      .clip(shape)
      .background(bg)
      .then(if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, shape) else Modifier)
      .then(roleModifier)
      .then(semanticsModifier)
      .combinedClickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClick = {
          if (haptic && onClick != null) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          }
          onClick?.invoke()
        },
        onLongClick = {
          if (haptic) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
          }
          onLongPress?.invoke()
        },
      ),
  ) {
    if (builder != null) {
      builder(isActive)
    } else {
      content()
    }
  }
}

/**
 * Convenience shape helper — Dart call sites frequently pass
 * `BorderRadius.circular(x)` into [AppPressable].
 */
fun roundedPressableShape(radius: Dp): Shape = RoundedCornerShape(radius)
