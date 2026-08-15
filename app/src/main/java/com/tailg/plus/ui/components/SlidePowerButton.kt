package com.tailg.plus.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.ui.theme.CyberHomeColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Port of `lib/widgets/slide_power_button.dart` — official-like bidirectional
 * power slider.
 *
 * State machine preserved 1:1:
 * - powered → thumb rests right, drag left to stop; unpowered → thumb rests
 *   left, drag right to start; `_completionThreshold` 0.98.
 * - activation pins `commandOriginPowered`, sets `awaitingResult`, calls the
 *   suspend [onSlide], then confirms against the *latest* [isPowered]; failure
 *   triggers a shake + heavy haptic.
 *
 * Token mapping: `CyberHomeColors.controlStrong/card/ink/inkMuted/inkFaint/
 * primary/success/actionShadow` → the same-named [CyberHomeColors] tokens;
 * `AppRadii.pill` → `RoundedCornerShape(999.dp)`.
 *
 * Icons: `Lucide.chevron-left/right` → `Icons.Filled.ChevronLeft/ChevronRight`;
 * `Lucide.power` → `Icons.Filled.PowerSettingsNew`; `Lucide.check` → `Icons.Filled.Check`.
 * Haptics: Dart `mediumImpact/heavyImpact` → `HapticFeedbackType.LongPress` (closest).
 */

private const val TrackWidth = 160f
private const val TrackHeight = 60f
private const val ThumbSize = 60f
private const val CompletionThreshold = 0.98f

/** Dart `Curves.easeOutBack`. */
private val EaseOutBack = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

@Composable
fun SlidePowerButton(
  isPowered: Boolean?,
  onSlide: suspend () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  busy: Boolean = false,
  unavailableReason: String = "",
  onUnavailable: (suspend () -> Unit)? = null,
) {
  val density = LocalDensity.current
  val haptics = LocalHapticFeedback.current
  val scope = rememberCoroutineScope()

  val maxDragPx = with(density) { (TrackWidth - ThumbSize).dp.toPx() }
  val idlePx = if (isPowered == true) maxDragPx else 0f
  val completedPx = if (isPowered == true) 0f else maxDragPx

  var dragPositionPx by remember { mutableFloatStateOf(idlePx) }
  var awaitingResult by remember { mutableStateOf(false) }
  var dragging by remember { mutableStateOf(false) }
  var commandOriginPowered by remember { mutableStateOf<Boolean?>(null) }
  var showSuccess by remember { mutableStateOf(false) }

  val shakeX = remember { Animatable(0f) }
  val successScale = remember { Animatable(0.3f) }

  val canSlide = enabled && !busy && !awaitingResult && isPowered != null
  val canExplainUnavailable = !enabled && !busy && !awaitingResult && onUnavailable != null

  // Latest values for the async confirmation step.
  val currentIsPowered by rememberUpdatedState(isPowered)
  val currentCanSlide by rememberUpdatedState(canSlide)

  val thumbAnimPx by animateFloatAsState(
    targetValue = dragPositionPx,
    animationSpec = if (dragging || awaitingResult) tween(0) else tween(AppMotion.micro, easing = AppMotion.pressCurve),
    label = "slidePowerThumb",
  )

  // External state-confirmed change → success pulse + reset to idle side.
  var prevPowered by remember { mutableStateOf(isPowered) }
  LaunchedEffect(isPowered) {
    val prev = prevPowered
    prevPowered = isPowered
    if (prev != null && isPowered != null && prev != isPowered) {
      showSuccess = true
      successScale.snapTo(0.3f)
      successScale.animateTo(1f, tween(AppMotion.emphasis, easing = EaseOutBack))
      delay(800)
      showSuccess = false
    }
    if (!awaitingResult) {
      dragPositionPx = if (isPowered == true) maxDragPx else 0f
    }
  }

  // Enabled/busy toggles snap the thumb back (Dart didUpdateWidget).
  LaunchedEffect(enabled, busy, maxDragPx) {
    if (!awaitingResult) {
      dragPositionPx = if (currentIsPowered == true) maxDragPx else 0f
    }
  }

  fun activate() {
    if (!currentCanSlide) return
    val origin = currentIsPowered
    commandOriginPowered = origin
    awaitingResult = true
    dragging = false
    dragPositionPx = completedPx
    haptics.performHapticFeedback(HapticFeedbackType.LongPress) // Dart mediumImpact
    scope.launch {
      onSlide()
      val confirmed = currentIsPowered != origin
      awaitingResult = false
      commandOriginPowered = null
      dragPositionPx = if (currentIsPowered == true) maxDragPx else 0f
      if (!confirmed) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress) // Dart heavyImpact
        shakeX.snapTo(0f)
        shakeX.animateTo(-5f, tween(53, easing = AppMotion.exitCurve))
        shakeX.animateTo(4f, tween(107, easing = AppMotion.exitCurve))
        shakeX.animateTo(-2f, tween(107, easing = AppMotion.exitCurve))
        shakeX.animateTo(0f, tween(53, easing = AppMotion.exitCurve))
      }
    }
  }

  val label = when {
    awaitingResult -> if (commandOriginPowered == true) "正在断电" else "正在通电"
    busy -> "指令执行中"
    isPowered == null -> "车辆状态未知"
    !enabled -> "控车不可用"
    isPowered == true -> "左滑关闭"
    else -> "右滑启动"
  }

  val opacity by animateFloatAsState(
    targetValue = if (canSlide || awaitingResult) 1f else 0.58f,
    animationSpec = tween(AppMotion.status),
    label = "slidePowerOpacity",
  )

  Column(
    modifier = modifier
      .semantics {
        contentDescription =
          if (!enabled && unavailableReason.isNotEmpty()) "$label：$unavailableReason" else label
        if (canSlide) {
          onClick { activate(); true }
        } else if (canExplainUnavailable) {
          onClick { scope.launch { onUnavailable?.invoke() }; true }
        }
      }
      .alpha(opacity),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier
        .width(TrackWidth.dp)
        .height(TrackHeight.dp),
    ) {
      // Track.
      Box(
        modifier = Modifier
          .matchParentSize()
          .clip(RoundedCornerShape(999.dp))
          .background(CyberHomeColors.controlStrong),
      ) {
        val arrow = if (isPowered == true) Lucide.chevronLeft else Lucide.chevronRight
        Row(
          modifier = Modifier.matchParentSize(),
          horizontalArrangement = if (isPowered == true) Arrangement.Start else Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (isPowered == true) Spacer(Modifier.width(15.dp))
          repeat(3) { index ->
            LucideIcon(
              icon = arrow,
              size = 20.dp,
              color = CyberHomeColors.inkFaint.copy(alpha = 0.62f - index * 0.12f),
            )
            if (index < 2) Spacer(Modifier.width(1.dp))
          }
          if (isPowered != true) Spacer(Modifier.width(15.dp))
        }
      }

      // Thumb.
      Box(
        modifier = Modifier
          .align(Alignment.CenterStart)
          .offset(x = with(density) { thumbAnimPx.toDp() })
          .graphicsLayer { translationX = shakeX.value }
          .size(ThumbSize.dp)
          .shadow(
            elevation = 8.dp,
            shape = CircleShape,
            clip = false,
            ambientColor = Color.Transparent,
            spotColor = CyberHomeColors.actionShadow,
          )
          .background(CyberHomeColors.card, CircleShape)
          .pointerInput(canSlide, isPowered, maxDragPx) {
            if (!canSlide) return@pointerInput
            detectHorizontalDragGestures(
              onDragStart = { dragging = true },
              onDragEnd = {
                val completed = if (isPowered == true) maxDragPx - dragPositionPx else dragPositionPx
                if (completed >= maxDragPx * CompletionThreshold) {
                  activate()
                } else {
                  dragging = false
                  dragPositionPx = if (currentIsPowered == true) maxDragPx else 0f
                }
              },
              onDragCancel = {
                dragging = false
                dragPositionPx = if (currentIsPowered == true) maxDragPx else 0f
              },
              onHorizontalDrag = { change, dragAmount ->
                change.consume()
                dragPositionPx = (dragPositionPx + dragAmount).coerceIn(0f, maxDragPx)
              },
            )
          },
        contentAlignment = Alignment.Center,
      ) {
        Crossfade(
          targetState = awaitingResult,
          animationSpec = tween(AppMotion.status),
          label = "powerThumbContent",
        ) { awaiting ->
          if (awaiting) {
            CircularProgressIndicator(
              modifier = Modifier.size(24.dp),
              strokeWidth = 2.5.dp,
              color = CyberHomeColors.ink,
            )
          } else {
            LucideIcon(icon = Lucide.power, size = 28.dp, color = CyberHomeColors.ink)
          }
        }
      }

      // Success overlay.
      if (showSuccess) {
        Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
          Box(
            modifier = Modifier
              .graphicsLayer {
                scaleX = successScale.value
                scaleY = successScale.value
              }
              .size(44.dp)
              .background(CyberHomeColors.primary.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
          ) {
            LucideIcon(icon = Lucide.check, size = 22.dp, color = CyberHomeColors.success)
          }
        }
      }
    }

    Spacer(Modifier.height(17.dp))
    Text(
      text = label,
      style = androidx.compose.ui.text.TextStyle(
        fontSize = 14.sp,
        color = CyberHomeColors.inkMuted,
        fontWeight = FontWeight.W500,
      ),
    )
  }
}
