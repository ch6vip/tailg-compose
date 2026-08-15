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
import androidx.compose.ui.Modifier
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
  val text = buildAnnotatedString {
    append(value)
    if (unit != null) {
      withStyle(unitStyle?.toSpanStyle() ?: style.toSpanStyle()) {
        append(unit)
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
  AnimatedContent(
    targetState = text,
    modifier = modifier,
    transitionSpec = {
      (fadeIn(tween(AppMotion.dataChange)) + slideInVertically(tween(AppMotion.dataChange)) { it / 6 }) togetherWith
        (fadeOut(tween(AppMotion.dataChange)) + slideOutVertically(tween(AppMotion.dataChange)) { -it / 6 })
    },
    label = "animatedValueText",
  ) { target ->
    Text(
      text = target,
      style = style,
      textAlign = textAlign,
      maxLines = maxLines ?: Int.MAX_VALUE,
      overflow = overflow ?: TextOverflow.Clip,
    )
  }
}
