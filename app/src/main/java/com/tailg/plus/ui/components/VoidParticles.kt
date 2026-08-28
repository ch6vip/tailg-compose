package com.tailg.plus.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.Paint
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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
 *
 * ## Performance (mobile jank fixes)
 *
 * The original port allocated a new `Paint` + `BlurMaskFilter` per particle
 * per frame — 48+ native blur allocations every 16ms, which is pure
 * software-blur churn on the GPU/CPU and a top frame-time offender on
 * mid-range devices. This rewrite:
 * - renders the glow halo once into a small cached [Bitmap] sprite
 *   ([glowSprite] / [glowSpritePaint]) and blits it per particle with a
 *   single shared [Paint];
 * - reuses one `Paint` per frame for the core circles;
 * - culls connection-line pairs that are not within the draw threshold.
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

// ── Shared draw resources ──────────────────────────────────────────────────

/**
 * One-time 48×48 glow sprite (energy-colored radial gradient with a soft
 * edge). Drawn via `drawBitmap` per particle instead of per-frame
 * `BlurMaskFilter` — the expensive blur runs exactly once at class-load time.
 */
private val glowSprite: Bitmap by lazy {
  val size = 48
  val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
  val canvas = NativeCanvas(bmp)
  val center = size / 2f
  val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  for (i in 6 downTo 1) {
    val alpha = (0.10f * (7 - i) * 255f).toInt().coerceIn(0, 255)
    paint.color = NativeColor.argb(alpha, 0x2F, 0xE0, 0xA6) // energy green base
    val radius = center * i / 6f
    canvas.drawCircle(center, center, radius, paint)
  }
  bmp
}

/** Shared paint for blitting [glowSprite]; tinted via alpha on the layer. */
private val glowSpritePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

/** Shared paint for the core circles (color set per frame, no allocation). */
private val corePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

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

  // Static connection graph, computed once from the initial normalized
  // positions. The previous implementation re-ran the full O(n²) pair scan
  // (plus a `energyColor.copy(alpha)` allocation per hit) on every frame of
  // the 60fps loop — with 48 particles that is ~576 distance computations per
  // frame. Drift is slow enough that the initial-neighbourhood edges stay
  // visually coherent for the whole field lifetime, so drawing the fixed
  // edge set each frame is O(E) with zero per-frame allocations.
  val connectionPairs = remember(particles) {
    val thresholdSq = PARTICLE_LINK_THRESHOLD * PARTICLE_LINK_THRESHOLD
    val pairs = ArrayList<Pair<Int, Int>>()
    for (i in particles.indices step 2) {
      val a = particles[i]
      for (j in i + 1 until particles.size step 2) {
        val b = particles[j]
        val dx = a.x - b.x
        val dy = a.y - b.y
        if (dx * dx + dy * dy < thresholdSq) pairs += i to j
      }
    }
    pairs
  }
  // One shared line color instance — drawLine receives it as-is every frame,
  // so the old per-hit `energyColor.copy(alpha = …)` allocation disappears.
  val lineColor = remember(energyColor) { energyColor.copy(alpha = 0.10f) }

  var elapsedMs by remember { mutableFloatStateOf(0f) }
  val loopsEnabled = enableAnimation && MotionPolicy.loopsEnabled()

  // Avoid running full particle animation if particle count is 0 or loops are disabled.
  // Also pre-allocate RectF to avoid per-particle allocations in the draw loop.
  val rectF = remember { android.graphics.RectF() }

  LaunchedEffect(loopsEnabled) {
    if (!loopsEnabled) return@LaunchedEffect
    while (true) {
      withFrameNanos { nanos ->
        elapsedMs = nanos / 1_000_000f
      }
    }
  }

  val sprite = remember { glowSprite.asImageBitmap() }
  val spriteAndroid = remember(sprite) { sprite.asAndroidBitmap() }
  val coreArgb = energyColor.toArgb()

  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val ms = elapsedMs
    val canvas = drawContext.canvas.nativeCanvas

    corePaint.color = coreArgb

    // ── Pass 1: advance particles + draw glow/core ────────────────────────
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

      val pulse = sin(ms * (2f * Math.PI.toFloat() / p.pulsePeriod) + p.phase).toFloat()
      val norm = (pulse + 1f) / 2f
      val alpha = (0.4f + 0.6f * norm) * p.opacity
      val drawSize = (p.size * (0.8f + 0.2f * norm)).toFloat()
      val px = p.x * w
      val py = p.y * h

      // Glow halo — cached sprite blit with per-particle alpha (the blur was
      // hoisted into [glowSprite] at class-load time; zero per-frame allocs).
      val haloSize = drawSize * 6f
      corePaint.alpha = (alpha * 0.28f * 255f).toInt().coerceIn(0, 255)
      val halfHalo = haloSize / 2f
      rectF.set(px - halfHalo, py - halfHalo, px + halfHalo, py + halfHalo)
      canvas.drawBitmap(
        spriteAndroid,
        null,
        rectF,
        glowSpritePaint,
      )

      // Core.
      corePaint.alpha = (alpha * 0.9f * 255f).toInt().coerceIn(0, 255)
      canvas.drawCircle(px, py, drawSize * 0.8f, corePaint)
    }

    // ── Pass 2: static connection lines (precomputed pair list, no per-frame
    // O(n²) scan and no per-line Color allocation) ─────────────────────────
    for ((i, j) in connectionPairs) {
      val a = particles[i]
      val b = particles[j]
      drawLine(
        color = lineColor,
        start = Offset(a.x * w, a.y * h),
        end = Offset(b.x * w, b.y * h),
        strokeWidth = 0.4f,
      )
    }
  }
}

/** Connection-line distance threshold (normalized space), matching the old cull. */
private const val PARTICLE_LINK_THRESHOLD = 0.08f
