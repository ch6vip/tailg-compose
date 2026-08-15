package com.tailg.plus.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.ui.theme.AppColorsDark
import com.tailg.plus.ui.theme.AppColorsLight
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppSpacing
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Port of `lib/widgets/void_canvas.dart` — immersive void field, glass panel,
 * energy ring and metric displays.
 *
 * Token mapping (Dart → Compose):
 * - `VoidColors.voidDeep` → [AppColorsDark.pageBg]; `voidMid` → [AppColorsDark.pageBgTop];
 *   `voidLift` → [AppColorsDark.surfaceContainerLow]; `voidPanel` → [AppColorsDark.surface];
 *   `voidPanelHi` → [AppColorsDark.surfaceContainerHigh].
 * - `VoidColors.energy/energyDim/energyAmber/energyRed` → [AppColorsDark.energyGreen] /
 *   [AppColorsDark.primaryDark] / [AppColorsDark.energyAmber] / [AppColorsDark.energyRed].
 * - `VoidColors.ink/inkMuted/inkFaint` → [AppColorsDark.textPrimary] / [AppColorsDark.textSecondary] /
 *   [AppColorsDark.textTertiary]; `hairline` → [AppColorsDark.textPrimary].copy(alpha = 0.13f).
 * - Light companions → [AppColorsLight].
 * - `VoidRadii.lg` (24) → [AppRadii.lg] (20); `VoidSpace.screenX` (22) → [AppSpacing.screenX] (20).
 */

/**
 * Full-bleed immersive void field with soft energy nebula + particle field.
 */
@Composable
fun VoidCanvas(
  modifier: Modifier = Modifier,
  intensity: Float = 1f,
  lightMode: Boolean = false,
  particleCount: Int = 32,
  showParticles: Boolean = true,
  content: @Composable BoxScope.() -> Unit,
) {
  val dark = !lightMode
  val top = if (dark) AppColorsDark.pageBg else AppColorsLight.pageBgTop
  val bot = if (dark) AppColorsDark.pageBgTop else AppColorsLight.pageBgBot
  val glow = if (dark) AppColorsDark.energyGreen.copy(alpha = 0.14f * intensity)
  else AppColorsDark.primaryDark.copy(alpha = 0.10f * intensity)
  val blob2 = (if (dark) AppColorsDark.accentSky else AppColorsDark.accentViolet)
    .copy(alpha = 0.08f * intensity)

  Box(
    modifier = modifier.background(
      Brush.linearGradient(
        colors = listOf(top, bot),
        start = Offset.Zero,
        end = Offset.Infinite,
      ),
    ),
  ) {
    if (showParticles) {
      VoidParticleField(
        particleCount = particleCount,
        energyColor = if (dark) AppColorsDark.energyGreen else AppColorsDark.primaryDark,
        driftSpeed = 0.06f,
        scale = intensity,
        modifier = Modifier.matchParentSize(),
      )
    }
    // Top-right energy nebula (Dart top: -80, right: -60).
    Box(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .offset(x = 60.dp, y = (-80).dp)
        .size(280.dp)
        .blur(60.dp)
        .background(glow, CircleShape),
    )
    // Bottom-left cooler nebula (Dart bottom: 80, left: -100).
    Box(
      modifier = Modifier
        .align(Alignment.BottomStart)
        .offset(x = (-100).dp, y = 80.dp)
        .size(320.dp)
        .blur(60.dp)
        .background(blob2, CircleShape),
    )
    // Fine grain noise overlay (deterministic Random(7), low-opacity dots).
    Canvas(modifier = Modifier.matchParentSize()) {
      val rnd = Random(7)
      val count = (size.width * size.height / 1800).toInt().coerceIn(40, 220)
      for (i in 0 until count) {
        val x = rnd.nextFloat() * size.width
        val y = rnd.nextFloat() * size.height
        drawCircle(
          color = AppColorsDark.textPrimary.copy(alpha = 0.03f), // Dart 0x08FFFFFF grain
          radius = 0.6f,
          center = Offset(x, y),
        )
      }
    }
    Box(modifier = Modifier.matchParentSize(), content = content)
  }
}

/**
 * Frosted glass panel with hairline edge (VoidGlass in void_canvas.dart).
 */
@Composable
fun VoidGlass(
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(18.dp),
  radius: Dp = AppRadii.lg,
  border: Boolean = true,
  blur: Boolean = true,
  glow: Boolean = false,
  color: Color? = null,
  content: @Composable () -> Unit,
) {
  val dark = true // VOID dark-first
  val fill = color ?: if (dark) AppColorsDark.surface.copy(alpha = 0.72f)
  else AppColorsLight.surface.copy(alpha = 0.82f)
  val edge = if (dark) AppColorsDark.textPrimary.copy(alpha = 0.13f) else AppColorsLight.border
  val shape = RoundedCornerShape(radius)

  val panelModifier = modifier
    .then(
      if (glow) {
        Modifier.shadow(
          elevation = 24.dp,
          shape = shape,
          clip = false,
          ambientColor = Color.Transparent,
          spotColor = AppColorsDark.energyGreen.copy(alpha = 0.11f),
        )
      } else if (dark) {
        Modifier.shadow(
          elevation = 14.dp,
          shape = shape,
          clip = false,
          ambientColor = Color.Black.copy(alpha = 0.25f),
          spotColor = Color.Transparent,
        )
      } else {
        Modifier
      },
    )
    .clip(shape)
    .background(fill)
    .then(if (border) Modifier.border(1.dp, edge, shape) else Modifier)
    .padding(contentPadding)

  Box(modifier = if (blur) panelModifier.blur(18.dp) else panelModifier) {
    content()
  }
}

/**
 * Kinetic battery ring — progress arc with pulsing glow.
 * `percent` 0–100; color tiers: <15 red, <35 amber, else energy green.
 */
@Composable
fun VoidEnergyRing(
  percent: Float,
  modifier: Modifier = Modifier,
  size: Dp = 196.dp,
  stroke: Dp = 8.dp,
  label: String? = null,
  sublabel: String? = null,
) {
  val p = percent.coerceIn(0f, 100f)
  val loops = MotionPolicy.loopsEnabled()
  val transition = rememberInfiniteTransition(label = "energyRing")
  val pulse by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(2400), // VoidMotion.breathe
      repeatMode = RepeatMode.Reverse,
    ),
    label = "energyPulse",
  )
  val pulseValue = if (loops) pulse else 0.5f
  val glowBoost = 0.7f + pulseValue * 0.3f

  val track = AppColorsDark.surfaceContainerHigh
  val energy = when {
    p < 15f -> AppColorsDark.energyRed
    p < 35f -> AppColorsDark.energyAmber
    else -> AppColorsDark.energyGreen
  }

  Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
    // Soft ambient glow under the ring.
    Box(
      modifier = Modifier
        .size(size * 0.72f)
        .shadow(
          elevation = 32.dp * glowBoost,
          shape = CircleShape,
          clip = false,
          ambientColor = Color.Transparent,
          spotColor = AppColorsDark.energyGreen.copy(alpha = 0.18f * glowBoost),
        ),
    )
    Canvas(modifier = Modifier.size(size)) {
      val c = Offset(this.size.width / 2f, this.size.height / 2f)
      val r = (minOf(this.size.width, this.size.height) - stroke.toPx()) / 2f
      val rect = Rect(center = c, radius = r)

      drawArc(
        color = track,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = rect.topLeft,
        size = Size(rect.width, rect.height),
        style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round),
      )
      if (p > 0f) {
        val start = -90f
        val sweep = 360f * p / 100f
        drawArc(
          color = energy,
          startAngle = start,
          sweepAngle = sweep,
          useCenter = false,
          topLeft = rect.topLeft,
          size = Size(rect.width, rect.height),
          style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round),
        )
        // Leading tip glow (native blur for the neon tip).
        val angle = (start + sweep) * PI.toFloat() / 180f
        val tip = Offset(c.x + r * cos(angle), c.y + r * sin(angle))
        drawContext.canvas.nativeCanvas.drawCircle(
          tip.x, tip.y, stroke.toPx() * 0.55f,
          android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = energy.copy(alpha = 0.9f).toArgb()
            maskFilter = android.graphics.BlurMaskFilter(6f, android.graphics.BlurMaskFilter.Blur.NORMAL)
          },
        )
      }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = if (p <= 0f && label == null) "--" else label ?: p.round().toString(),
        style = TextStyle(
          fontSize = (size * 0.28f).value.sp, // Dart display: size * 0.28
          fontWeight = FontWeight.W300,
          color = AppColorsDark.textPrimary,
        ),
      )
      if (sublabel != null) {
        Spacer(Modifier.height(4.dp))
        Text(
          text = sublabel,
          style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.W600, letterSpacing = 1.6.sp, color = AppColorsDark.textSecondary),
        )
      } else {
        Spacer(Modifier.height(2.dp))
        Text(
          text = "%",
          style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.W600, letterSpacing = 3.sp, color = AppColorsDark.textSecondary),
        )
      }
    }
  }
}

/** Experimental section label — thin rule + wide-tracked micro text. */
@Composable
fun VoidSectionLabel(
  text: String,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.padding(start = AppSpacing.screenX, top = AppSpacing.sectionGap, end = AppSpacing.screenX, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .width(18.dp)
        .height(1.5.dp)
        .background(AppColorsDark.energyGreen.copy(alpha = 0.7f)),
    )
    Spacer(Modifier.width(10.dp))
    Text(
      text = text,
      style = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.W600,
        letterSpacing = 1.6.sp,
        color = AppColorsDark.textTertiary,
      ),
    )
  }
}

/** Massive kinetic headline used on empty / gate states. */
@Composable
fun VoidHeadline(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  align: TextAlign = TextAlign.Left,
) {
  Column(
    modifier = modifier,
    horizontalAlignment = if (align == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start,
  ) {
    Text(
      text = title,
      textAlign = align,
      style = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.W600,
        letterSpacing = (-0.6).sp,
        color = AppColorsDark.textPrimary,
      ),
    )
    if (subtitle != null) {
      Spacer(Modifier.height(10.dp))
      Text(
        text = subtitle,
        textAlign = align,
        style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = AppColorsDark.textSecondary),
      )
    }
  }
}

/** Metric tile — large tabular figure + micro label. */
@Composable
fun VoidMetric(
  value: String,
  label: String,
  modifier: Modifier = Modifier,
  unit: String? = null,
  accent: Boolean = false,
) {
  Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
    Text(
      text = label,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.W600,
        letterSpacing = 1.6.sp,
        color = AppColorsDark.textTertiary,
      ),
    )
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.Bottom) {
      Text(
        text = value,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
          fontSize = 26.sp,
          fontWeight = FontWeight.W400,
          letterSpacing = (-1).sp,
          color = if (accent) AppColorsDark.energyGreen else AppColorsDark.textPrimary,
          fontFeatureSettings = "tnum",
        ),
      )
      if (unit != null) {
        Spacer(Modifier.width(2.dp))
        Text(
          text = unit,
          style = TextStyle(fontSize = 11.sp, color = AppColorsDark.textTertiary),
          modifier = Modifier.padding(bottom = 4.dp),
        )
      }
    }
  }
}
