package com.tailg.plus.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import kotlin.math.min
import kotlin.math.roundToInt

/** Compose equivalent of Flutter's `FittedBox(fit: BoxFit.scaleDown)`. */
@Composable
fun ScaleToFit(
  modifier: Modifier = Modifier,
  contentAlignment: Alignment = Alignment.Center,
  content: @Composable () -> Unit,
) {
  Layout(
    content = content,
    modifier = modifier,
  ) { measurables, constraints ->
    val measurable = measurables.firstOrNull()
    if (measurable == null) {
      layout(constraints.minWidth, constraints.minHeight) {}
    } else {
      val placeable = measurable.measure(
        Constraints(
          minWidth = 0,
          minHeight = 0,
          maxWidth = Constraints.Infinity,
          maxHeight = Constraints.Infinity,
        ),
      )
      val width = placeable.width
        .coerceAtLeast(constraints.minWidth)
        .let { if (constraints.hasBoundedWidth) it.coerceAtMost(constraints.maxWidth) else it }
      val height = placeable.height
        .coerceAtLeast(constraints.minHeight)
        .let { if (constraints.hasBoundedHeight) it.coerceAtMost(constraints.maxHeight) else it }
      val widthScale = if (placeable.width > 0) width.toFloat() / placeable.width else 1f
      val heightScale = if (placeable.height > 0) height.toFloat() / placeable.height else 1f
      val scale = min(1f, min(widthScale, heightScale))
      val scaledSize = IntSize(
        width = (placeable.width * scale).roundToInt(),
        height = (placeable.height * scale).roundToInt(),
      )
      val offset = contentAlignment.align(
        size = scaledSize,
        space = IntSize(width, height),
        layoutDirection = layoutDirection,
      )

      layout(width, height) {
        placeable.placeRelativeWithLayer(offset.x, offset.y) {
          scaleX = scale
          scaleY = scale
          transformOrigin = TransformOrigin(0f, 0f)
        }
      }
    }
  }
}
