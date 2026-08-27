package com.tailg.plus.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle

/**
 * Port of `lib/widgets/animated_value_text.dart` — stable text slot that
 * cross-fades + slides when [value] (or [unit]) changes after a refresh.
 *
 * Dart `AnimatedSwitcher` (slide 0.16 + fade, `AppMotion.dataChange`) →
 * Compose [AnimatedContent]. The Dart `MediaQuery.disableAnimations` fast
 * path is handled via [MotionPolicy.reduceMotion].
 *
 * ## Performance (mobile jank fixes)
 * - `AnnotatedString` has no structural `equals`, so the original port passed
 *   a freshly built AnnotatedString as [AnimatedContent]'s targetState —
 *   every recomposition of the parent (BLE heartbeat, cloud refresh) rebuilt
 *   the string AND made AnimatedContent believe the target changed, firing a
 *   240ms slide/fade while the animation kept the whole subtree re-composing
 *   every frame.
 * - Fix: the text is rebuilt only when (value, unit, style) actually change
 *   ([remember]), and the animation target is a structural string key so an
 *   unrelated recomposition is a true no-op for the animation state.
 */
@Composable
fun AnimatedValueText(
  value: String,
  style: TextStyle,
  modifier: Modifier = Modifier,
  unit: String? = null,
  unitStyle: TextStyle? = null,
  textAlign: TextAlign? = null,
  maxLines: Int? = null,
  overflow: TextOverflow? = null,
) {
  val reduceMotion = MotionPolicy.reduceMotion()
  val text: AnnotatedString = remember(value, unit, style, unitStyle) {
    buildAnnotatedString {
      append(value)
      if (unit != null) {
        withStyle(unitStyle?.toSpanStyle() ?: style.toSpanStyle()) {
          append(unit)
        }
      }
    }
  }
  if (reduceMotion) {
    Text(
      text = text,
      style = style,
      textAlign = textAlign,
      maxLines = maxLines ?: Int.MAX_VALUE,
      overflow = overflow ?: TextOverflow.Clip,
      modifier = modifier,
    )
    return
  }
  // Structural key: AnnotatedString instances are never `==`, so animating on
  // the string itself would re-fire the transition on every recomposition.
  val animationKey = "$value\u0000${unit ?: ""}"
  AnimatedContent(
    targetState = animationKey,
    modifier = modifier,
    transitionSpec = {
      (fadeIn(tween(AppMotion.dataChange)) + slideInVertically(tween(AppMotion.dataChange)) { it / 6 }) togetherWith
        (fadeOut(tween(AppMotion.dataChange)) + slideOutVertically(tween(AppMotion.dataChange)) { -it / 6 })
    },
    label = "animatedValueText",
  ) { targetKey ->
    // Key by the structural target so a cross-fade shows the OLD text sliding
    // out and the NEW text sliding in (AnimatedContent keeps both composed
    // during the transition); the string is still built once per content
    // instance, not per parent recomposition.
    val contentText = remember(targetKey) { text }
    Text(
      text = contentText,
      style = style,
      textAlign = textAlign,
      maxLines = maxLines ?: Int.MAX_VALUE,
      overflow = overflow ?: TextOverflow.Clip,
    )
  }
}