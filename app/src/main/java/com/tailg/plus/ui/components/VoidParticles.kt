package com.tailg.plus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.tailg.plus.ui.theme.AppColorsDark
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Port of `lib/widgets/void_particles.dart` — volumetric particle field.
 *
 * The Dart `Ticker` + `CustomPainter(repaint)` loop maps to a
 * `withFrameNanos` frame loop driving an `elapsed` state; particle positions
 * are advanced per-frame in the composable, then drawn in [Canvas]. The
 * interaction-repel force and pulse opacity are preserved. `enableAnimation`
 * is a parameter (Dart static flag).
 *
 * Token mapping: `VoidColors.energy` → [AppColorsDark.energyGreen]
 * (light-mode callers pass [AppColorsDark.primaryDark] explicitly, as Dart did).
 */

private class Particle(
  var x: Float,
  var y: Float,
  val size: Float,
  val speedX: Float,
  val speedY: Float,
  val phase: Float,
  val pulsePeriod: Float,
  val opacity: Float,
)

/**
 * Volumetric particle field — animated, reactive, immersive (60fps Canvas).
 */
@Composable
fun VoidParticleField(
  modifier: Modifier = Modifier,
  particleCount: Int = 48,
  energyColor: Color = AppColorsDark.energyGreen,
  driftSpeed: Float = 0.08f,
  scale: Float = 1f,
  interactionOffset: Offset? = null,
  enableAnimation: Boolean = true,
) {
  val particles = remember(particleCount) {
    val rng = Random(42)
    List(particleCount) {
      Particle(
        x = rng.nextFloat(),
        y = rng.nextFloat(),
        size = 1.2f + rng.nextFloat() * 3.2f,
        speedX = (rng.nextFloat() - 0.5f) * driftSpeed,
        speedY = (rng.nextFloat() - 0.5f) * driftSpeed * 0.6f,
        phase = rng.nextFloat() * 2f * kotlin.math.PI.toFloat(),
        pulsePeriod = 1.2f + rng.nextFloat() * 2.8f,
        opacity = 0.12f + rng.nextFloat() * 0.35f,
      )
    }
  }
  var elapsedMs by remember { mutableFloatStateOf(0f) }
  val loopsEnabled = enableAnimation && MotionPolicy.loopsEnabled()

  LaunchedEffect(loopsEnabled) {
    if (!loopsEnabled) return@LaunchedEffect
    while (true) {
      withFrameNanos { nanos ->
        elapsedMs = nanos / 1_000_000f
      }
    }
  }

  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val ms = elapsedMs

    for (p in particles) {
      // Drift (~60fps step, same as Dart dt * 60).
      p.x += p.speedX * scale
      p.y += p.speedY * scale

      // Wrap around.
      if (p.x < -0.05f) p.x = 1.05f
      if (p.x > 1.05f) p.x = -0.05f
      if (p.y < -0.05f) p.y = 1.05f
      if (p.y > 1.05f) p.y = -0.05f

      // Interaction repel.
      if (interactionOffset != null) {
        val dx = (p.x * w - interactionOffset.x) / w
        val dy = (p.y * h - interactionOffset.y) / h
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < 0.12f) {
          val force = (0.12f - dist) / 0.12f * 0.03f
          p.x += dx * force
          p.y += dy * force
        }
      }

      // Pulse opacity (same formula as Dart).
      val pulse = sin(ms * (2f * kotlin.math.PI.toFloat() / p.pulsePeriod) + p.phase)
      val norm = (pulse + 1f) / 2f
      val alpha = (0.4f + 0.6f * norm) * p.opacity
      val drawSize = p.size * (0.8f + 0.2f * norm)
      val px = p.x * w
      val py = p.y * h

      // Glow halo (native blur like Dart MaskFilter.blur(NORMAL, 8)).
      val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = energyColor.copy(alpha = alpha * 0.3f).toArgb()
        maskFilter = android.graphics.BlurMaskFilter(8f, android.graphics.BlurMaskFilter.Blur.NORMAL)
      }
      drawContext.canvas.nativeCanvas.drawCircle(px, py, drawSize * 3f, glowPaint)

      // Core.
      drawCircle(color = energyColor.copy(alpha = alpha * 0.9f), radius = drawSize * 0.8f, center = Offset(px, py))
    }

    // Subtle connection lines between nearby particles (Dart step of 2).
    val threshold = 0.08f
    for (i in particles.indices step 2) {
      for (j in i + 1 until particles.size step 2) {
        val a = particles[i]
        val b = particles[j]
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < threshold) {
          val alpha = (1f - dist / threshold) * 0.12f
          drawLine(
            color = energyColor.copy(alpha = alpha),
            start = Offset(a.x * w, a.y * h),
            end = Offset(b.x * w, b.y * h),
            strokeWidth = 0.4f,
          )
        }
      }
    }
  }
}
