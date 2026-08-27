package com.tailg.plus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tailg.plus.ui.theme.AppColorsDark
import com.tailg.plus.ui.theme.AppRadii

/**
 * Port of `lib/widgets/void_glass.dart` — glass-state cards and panels.
 *
 * Dart `BackdropFilter(blur)` → Compose `Modifier.blur(radius)` (backdrop
 * blur; applied outermost, mirroring Flutter's BackdropFilter wrapping the
 * container).
 *
 * Token mapping (Dart → Compose):
 * - `VoidColors.energy/energyDim` → [AppColorsDark.energyGreen] / [AppColorsDark.primaryDark].
 * - `VoidColors.voidPanel` → [AppColorsDark.surface]; Dart dark fill `0x1A151B26` → [AppColorsDark.surface].copy(alpha = 0.1f).
 * - `VoidColors.hairline` (0x2AFFFFFF) → [AppColorsDark.textPrimary].copy(alpha = 0.13f).
 * - `VoidRadii.lg` (20) → [AppRadii.lg]; `VoidRadii.md` (18) → [AppRadii.sheet].
 * - Spacing values without a matching [AppSpacing] token keep the Dart literal (documented).
 */

/**
 * VoidGlassCard — 玻璃态卡片组件（半透明 + 能量色发光边框 + 内嵌微光）。
 * Dart `borderRadius` default 20 → [AppRadii.lg]; `padding` default 18 kept as Dart value.
 *
 * **Performance note**: the original port applied `Modifier.blur(blurSigma)`
 * (live backdrop blur) on every card. The official 3.5.9 app is View-based
 * and renders these cards as plain translucent fills — no live blur — so
 * [blurSigma] is now only used when [blur] is explicitly enabled (defaults to
 * false). The default path is a static translucent panel, which keeps the
 * glass look without re-rasterizing the backdrop on every frame.
 */
@Composable
fun VoidGlassCard(
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(18.dp),
  borderRadius: Dp = AppRadii.lg,
  blurSigma: Dp = 12.dp,
  blur: Boolean = false,
  glowColor: Color = AppColorsDark.energyGreen,
  glowOpacity: Float = 0.15f,
  borderColor: Color = AppColorsDark.textPrimary.copy(alpha = 0.13f), // VoidColors.hairline
  borderWidth: Dp = 1.dp,
  elevation: Float = 0f,
  clipContent: Boolean = true,
  onClick: (() -> Unit)? = null,
  content: @Composable () -> Unit,
) {
  val shape = RoundedCornerShape(borderRadius)
  val fill = AppColorsDark.surface.copy(alpha = 0.1f) // Dart dark 0x1A151B26
  val clipModifier = if (clipContent) Modifier.clip(shape) else Modifier

  val base = modifier
    .then(if (blur) Modifier.blur(blurSigma) else Modifier)
    .shadow(
      elevation = 24.dp,
      shape = shape,
      clip = false,
      ambientColor = Color.Transparent,
      spotColor = glowColor.copy(alpha = glowOpacity * 0.3f),
    )
    .then(
      if (elevation > 0f) {
        Modifier.shadow(
          elevation = (8.dp.value + 4f * elevation).dp,
          shape = shape,
          clip = false,
          ambientColor = Color.Black.copy(alpha = 0.08f * elevation),
          spotColor = Color.Transparent,
        )
      } else {
        Modifier
      },
    )
    .then(clipModifier)
    .background(fill)
    .border(borderWidth, borderColor, shape)
    .padding(contentPadding)

  Box(
    modifier = if (onClick != null) {
      base.clickable(role = Role.Button, onClick = onClick)
    } else {
      base
    },
  ) {
    content()
  }
}

/**
 * VoidGlassPanel — 全宽玻璃面板，用于页面 section 容器（仅底部 hairline）。
 * Dart `blurSigma` default 8; fill `0x0AFFFFFF` → [AppColorsDark.textPrimary].copy(alpha = 0.04f);
 * border `0x1AFFFFFF` → [AppColorsDark.textPrimary].copy(alpha = 0.1f).
 *
 * Same perf note as [VoidGlassCard]: live backdrop blur is disabled by
 * default (official-app parity); [blur] opts back in when a screen truly
 * needs the frosted look.
 */
@Composable
fun VoidGlassPanel(
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
  blurSigma: Dp = 8.dp,
  blur: Boolean = false,
  glowColor: Color = AppColorsDark.energyGreen,
  glowOpacity: Float = 0.10f,
  content: @Composable () -> Unit,
) {
  val fill = AppColorsDark.textPrimary.copy(alpha = 0.04f)
  val edge = AppColorsDark.textPrimary.copy(alpha = 0.1f)
  Box(
    modifier = modifier
      .then(if (blur) Modifier.blur(blurSigma) else Modifier)
      .background(fill)
      .border(width = 0.5.dp, color = edge, shape = RoundedCornerShape(0.dp))
      .padding(contentPadding),
  ) {
    content()
  }
}
