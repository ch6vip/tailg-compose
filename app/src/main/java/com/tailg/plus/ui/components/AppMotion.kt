package com.tailg.plus.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalMotionDurationScale

/**
 * Port of `lib/theme/app_motion.dart` → shared component helpers.
 *
 * Dart `Duration` values are flattened to `Int` millis; Dart `Curve` objects
 * are mapped to Compose [FiniteAnimationSpec] factories. The Flutter theme
 * module has no Compose counterpart yet, so these live next to the components
 * that consume them (promote to `ui.theme` in a later pass if desired).
 */
object AppMotion {
  // ── Durations (ms) ──────────────────────────────────────────────────────
  const val instant = 100
  const val micro = 150
  const val standard = 250
  const val toastEntrance = 300
  const val toastVisible = 1800L
  const val tabIndicator = 200
  const val status = 180
  const val tabSwitch = 220
  const val dataChange = 240
  const val emphasis = 350
  const val reveal = 500
  const val failureFeedback = 320
  const val longPressHold = 1200
  const val pulsePeriod = 1200

  // ── Curves (Dart Curves.easeOutCubic / easeInCubic / easeInOut) ────────
  /** Dart `Curves.easeOutCubic`. */
  val pressCurve: CubicBezierEasing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

  /** Dart `Curves.easeOutCubic` — page entrance. */
  val entranceCurve: CubicBezierEasing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

  /** Dart `Curves.easeInCubic` — page exit. */
  val exitCurve: CubicBezierEasing = CubicBezierEasing(0.55f, 0.055f, 0.675f, 0.19f)

  /** Dart `Curves.easeInOut` — pulse / breathing. */
  val pulseCurve: CubicBezierEasing = FastOutSlowInEasing

  fun tween(durationMillis: Int): FiniteAnimationSpec<Float> =
    tween(durationMillis = durationMillis, easing = pressCurve)

  // ── Scale presets ───────────────────────────────────────────────────────
  const val pressScale = 0.96f
  const val pulseMin = 0.75f
  const val pulseMax = 1.1f
}

/**
 * Port of `lib/theme/motion_policy.dart`.
 *
 * Dart `MediaQuery.disableAnimations` → Compose `MotionDurationScale`
 * (system animator-duration-scale == 0). TickerMode pauses are handled by
 * Compose's own frame clock for `withFrameNanos` loops.
 */
object MotionPolicy {
  @Composable
  fun reduceMotion(): Boolean = LocalMotionDurationScale.current == 0f

  @Composable
  fun loopsEnabled(): Boolean = !reduceMotion()
}
