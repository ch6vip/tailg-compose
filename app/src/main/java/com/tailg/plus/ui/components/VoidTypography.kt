package com.tailg.plus.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tailg.plus.ui.theme.AppColorsDark

/**
 * Port of `lib/widgets/void_typography.dart` — kinetic / glowing display text.
 *
 * Token mapping (Dart → Compose):
 * - `VoidColors.energy` → [AppColorsDark.energyGreen].
 * - `VoidType.displaySm` → `TailgTypography.displaySmall` (24sp); hero figures keep explicit styles.
 * - `Curves.easeOutBack` → [VoidTypography.EaseOutBack]; `Curves.easeOutCubic` → [AppMotion.entranceCurve].
 */

/** Dart `Curves.easeOutBack`. */
private val EaseOutBack = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/** Dart `Curves.easeOutCubic`. */
private val EaseOutCubic = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

enum class KineticTypeMode { Sequential, Word, Block }

/**
 * Kinetic typography — each character/word animates in with staggered
 * scale + fade + slide. [enableAnimation] disables the entrance animation
 * (Dart `KineticType.enableAnimation` static flag).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KineticType(
  text: String,
  modifier: Modifier = Modifier,
  style: TextStyle = TextStyle(),
  mode: KineticTypeMode = KineticTypeMode.Sequential,
  staggerDelay: Int = 32,
  durationMillis: Int = 420,
  autoPlay: Boolean = true,
  alignment: TextAlign = TextAlign.Left,
  maxLines: Int? = null,
  overflow: TextOverflow? = null,
  enableAnimation: Boolean = true,
) {
  val items = remember(text, mode) {
    when (mode) {
      KineticTypeMode.Sequential -> text.map { it.toString() }
      KineticTypeMode.Word -> text.split(" ").filter { it.isNotEmpty() }
      KineticTypeMode.Block -> listOf(text)
    }
  }
  if (items.isEmpty()) return

  val totalMillis = durationMillis + items.size * staggerDelay
  val progress = remember { Animatable(0f) }

  LaunchedEffect(text, mode, autoPlay, enableAnimation, totalMillis) {
    if (autoPlay && enableAnimation) {
      progress.snapTo(0f)
      progress.animateTo(1f, tween(durationMillis = totalMillis, easing = EaseOutCubic))
    } else {
      progress.snapTo(1f)
    }
  }

  fun itemValue(index: Int): Float {
    val start = index * staggerDelay
    val end = durationMillis + index * staggerDelay
    val local = ((progress.value * totalMillis - start) / (end - start).toFloat()).coerceIn(0f, 1f)
    return EaseOutBack.transform(local)
  }

  if (mode == KineticTypeMode.Block) {
    val v = if (items.isNotEmpty() && enableAnimation) itemValue(0) else 1f
    Box(modifier = modifier) {
      Text(
        text = text,
        style = style,
        textAlign = alignment,
        maxLines = maxLines ?: Int.MAX_VALUE,
        overflow = overflow ?: TextOverflow.Clip,
        modifier = Modifier
          .alpha(v)
          .graphicsLayer { translationY = (1f - v) * 30f },
      )
    }
    return
  }

  FlowRow(
    modifier = modifier.sizeIn(maxWidth = Dp.Infinity),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalArrangement = Arrangement.Center,
  ) {
    items.forEachIndexed { index, item ->
      val v = if (enableAnimation) itemValue(index) else 1f
      Text(
        text = item,
        style = style,
        modifier = Modifier
          .alpha(v)
          .graphicsLayer {
            scaleX = 0.4f + 0.6f * v
            scaleY = 0.4f + 0.6f * v
            translationY = (1f - v) * 20f
          },
      )
    }
  }
}

/**
 * Glowing display text with energy neon effect (glow layer + core text).
 */
@Composable
fun VoidGlowText(
  text: String,
  modifier: Modifier = Modifier,
  style: TextStyle = TextStyle(),
  glowColor: Color = AppColorsDark.energyGreen,
  glowIntensity: Float = 1f,
  textAlign: TextAlign? = null,
  maxLines: Int? = null,
) {
  Box(modifier = modifier) {
    Text(
      text = text,
      maxLines = maxLines ?: Int.MAX_VALUE,
      textAlign = textAlign,
      style = style.copy(
        color = glowColor.copy(alpha = 0.3f * glowIntensity),
        shadow = androidx.compose.ui.graphics.Shadow(
          color = glowColor.copy(alpha = 0.4f * glowIntensity),
          blurRadius = 12f * glowIntensity,
        ),
      ),
    )
    Text(
      text = text,
      maxLines = maxLines ?: Int.MAX_VALUE,
      textAlign = textAlign,
      style = style,
    )
  }
}

/**
 * Animated metric counter — counts from 0 to [value] (Dart easeOutCubic).
 * Tabular figures via `fontFeatureSettings = "tnum"`.
 */
@Composable
fun VoidMetricCounter(
  value: Double,
  modifier: Modifier = Modifier,
  style: TextStyle = TextStyle(),
  durationMillis: Int = 1200,
  prefix: String = "",
  suffix: String = "",
  decimalPlaces: Int = 0,
  autoPlay: Boolean = true,
) {
  val animated = remember { Animatable(0f) }
  LaunchedEffect(value, autoPlay, durationMillis) {
    if (autoPlay) {
      animated.animateTo(value.toFloat(), tween(durationMillis = durationMillis, easing = EaseOutCubic))
    } else {
      animated.snapTo(value.toFloat())
    }
  }
  val formatted = String.format("%.${decimalPlaces}f", animated.value)
  Text(
    text = "$prefix$formatted$suffix",
    style = style.copy(fontFeatureSettings = "tnum"),
    modifier = modifier,
  )
}

/**
 * Animated section divider — energy line that draws in (width 0 → [width]).
 */
@Composable
fun VoidDivider(
  modifier: Modifier = Modifier,
  color: Color = AppColorsDark.energyGreen,
  height: Dp = 1.5.dp,
  width: Dp = 60.dp,
  margin: PaddingValues = PaddingValues(vertical = 16.dp),
) {
  val progress = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    progress.animateTo(1f, tween(durationMillis = 600, easing = EaseOutCubic))
  }
  Box(
    modifier = modifier
      .padding(margin)
      .width(width * progress.value)
      .height(height)
      .clip(RoundedCornerShape(height / 2f))
      .background(
        Brush.horizontalGradient(
          listOf(
            color.copy(alpha = 0f),
            color.copy(alpha = 0.8f),
            color.copy(alpha = 0f),
          ),
        ),
      ),
  )
}
