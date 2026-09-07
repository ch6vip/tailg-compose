package com.tailg.plus.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.tailg.plus.R
import com.tailg.plus.ui.components.MotionPolicy
import com.tailg.plus.ui.components.NinebotIcon
import com.tailg.plus.ui.components.NinebotLucide
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A small perspective renderer, not a photograph of a specific battery model.
 * Ten illuminated cells encode the reported charge. Frame state is read only
 * in the draw phase; idle motion stops while scrolling, offscreen or paused.
 */
@Composable
internal fun NinebotBatteryVisual(percent: Int?, palette: BatteryPalette, motionActive: Boolean, modifier: Modifier = Modifier) {
  val reduceMotion = MotionPolicy.reduceMotion()
  val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
  val animate = !reduceMotion && !LocalInspectionMode.current && motionActive &&
    lifecycle.isAtLeast(Lifecycle.State.RESUMED) && percent != null
  var drag by remember { mutableFloatStateOf(0f) }
  val rotation = animateFloatAsState(
    targetValue = drag,
    animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.68f, stiffness = 260f),
    label = "batteryPerspective",
  )
  val charge = animateFloatAsState(
    targetValue = (percent ?: 0).coerceIn(0, 100) / 100f,
    animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 1f, stiffness = 60f),
    label = "batteryCells",
  )
  val breath: State<Float> = if (animate) {
    rememberInfiniteTransition(label = "batteryFloat").animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
      label = "batteryFloatPhase",
    )
  } else {
    rememberUpdatedState(0.5f)
  }
  val description = if (percent == null) stringResource(R.string.nb_battery_visual_empty) else stringResource(R.string.nb_battery_visual, percent)
  val resetLabel = stringResource(R.string.nb_battery_reset_view)
  Box(modifier, contentAlignment = Alignment.Center) {
    Canvas(
      Modifier.fillMaxSize()
        .semantics {
          contentDescription = description
          customActions = listOf(CustomAccessibilityAction(resetLabel) { drag = 0f; true })
        }
        .pointerInput(Unit) {
          detectHorizontalDragGestures(
            onDragEnd = { drag = 0f },
            onDragCancel = { drag = 0f },
            onHorizontalDrag = { change, distance ->
              change.consume()
              drag = (drag + distance / size.width.coerceAtLeast(1) * 85f).coerceIn(-24f, 24f)
            },
          )
        },
    ) {
      drawBatterySculpture(charge.value, percent, rotation.value, breath.value, palette)
    }
    Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      NinebotIcon(NinebotLucide.moveHorizontal, size = 12.dp, color = palette.muted)
      Spacer(Modifier.width(5.dp))
      Text(stringResource(R.string.nb_battery_drag), color = palette.muted, fontSize = 9.sp)
    }
  }
}

private data class BatteryPoint(val x: Float, val y: Float, val z: Float)
private data class ProjectedBatteryPoint(val position: Offset, val depth: Float)

private class BatteryProjection(private val center: Offset, private val scale: Float, yaw: Float) {
  private val cosY = cos(yaw * Math.PI.toFloat() / 180f)
  private val sinY = sin(yaw * Math.PI.toFloat() / 180f)
  private val cosX = cos(-0.13f)
  private val sinX = sin(-0.13f)
  private val cosZ = cos(-0.105f)
  private val sinZ = sin(-0.105f)

  fun project(point: BatteryPoint): ProjectedBatteryPoint {
    val x = point.x * cosY + point.z * sinY
    val z = -point.x * sinY + point.z * cosY
    val y = point.y * cosX - z * sinX
    val depth = point.y * sinX + z * cosX
    val perspective = 500f / (500f - depth)
    return ProjectedBatteryPoint(
      center + Offset(x * cosZ - y * sinZ, x * sinZ + y * cosZ) * (scale * perspective),
      depth,
    )
  }

  fun at(x: Float, y: Float, z: Float): Offset = project(BatteryPoint(x, y, z)).position

  fun path(points: List<BatteryPoint>): Path = Path().apply {
    points.forEachIndexed { index, point ->
      val projected = project(point).position
      if (index == 0) moveTo(projected.x, projected.y) else lineTo(projected.x, projected.y)
    }
    close()
  }

  /** Beveled rectangles keep their perspective on the front plane. */
  fun panel(left: Float, top: Float, right: Float, bottom: Float, z: Float, bevel: Float = 2f): Path = path(listOf(
    BatteryPoint(left + bevel, top, z), BatteryPoint(right - bevel, top, z),
    BatteryPoint(right, top + bevel, z), BatteryPoint(right, bottom - bevel, z),
    BatteryPoint(right - bevel, bottom, z), BatteryPoint(left + bevel, bottom, z),
    BatteryPoint(left, bottom - bevel, z), BatteryPoint(left, top + bevel, z),
  ))
}

private fun DrawScope.drawBatterySculpture(charge: Float, percent: Int?, drag: Float, breath: Float, p: BatteryPalette) {
  val scale = min(size.width / 148f, size.height / 242f)
  val center = Offset(size.width * 0.52f, size.height * 0.46f - (breath - 0.5f) * 4f * scale)
  val projection = BatteryProjection(center, scale, -22f + drag)
  val energy = if (percent != null && percent <= 20) Color(0xFFFFB85C) else Color(0xFF69B9FF)

  // Soft studio light and two fine orbital construction lines.
  drawCircle(
    Brush.radialGradient(listOf(p.blue.copy(alpha = if (p.dark) 0.12f else 0.09f), Color.Transparent), center, size.width * 0.60f),
    radius = size.width * 0.60f,
    center = center,
  )
  rotate(-22f, center) {
    drawOval(p.line.copy(alpha = 0.7f), center - Offset(71f, 88f) * scale, Size(142f, 176f) * scale, style = Stroke(0.65.dp.toPx()))
    drawOval(p.line.copy(alpha = 0.35f), center - Offset(80f, 98f) * scale, Size(160f, 196f) * scale, style = Stroke(0.65.dp.toPx()))
  }
  val shadowCenter = Offset(center.x + 5f * scale, center.y + 89f * scale)
  drawOval(
    Brush.radialGradient(listOf(Color(0xFF08142A).copy(alpha = if (p.dark) 0.42f else 0.20f), Color.Transparent), shadowCenter, 65f * scale),
    shadowCenter - Offset(65f, 11f) * scale,
    Size(130f, 22f) * scale,
  )

  // Machined top cap, carry handle and graphite shell.
  drawBatteryBox(projection, -30f, -83f, -13f, 30f, -73f, 13f, Color(0xFF657080), Color(0xFF343D4B))
  drawBatteryBox(projection, -23f, -94f, -7f, -15f, -80f, 7f, Color(0xFF394454), Color(0xFF161E2A))
  drawBatteryBox(projection, 15f, -94f, -7f, 23f, -80f, 7f, Color(0xFF394454), Color(0xFF161E2A))
  drawBatteryBox(projection, -23f, -99f, -7f, 23f, -91f, 7f, Color(0xFF637185), Color(0xFF26303F))
  drawBatteryBox(projection, -43f, -74f, -21f, 43f, 75f, 21f, Color(0xFF596576), Color(0xFF171F2C))

  // The front panel is a translucent window into ten charge segments.
  val front = projection.panel(-41f, -72f, 41f, 73f, 21.1f, 5f)
  drawPath(front, Brush.linearGradient(listOf(Color(0xFF526174), Color(0xFF273140), Color(0xFF121C29)), projection.at(-43f, -74f, 22f), projection.at(43f, 75f, 22f)))
  drawPath(front, Color(0xFFA7B6C9).copy(alpha = 0.28f), style = Stroke(0.8f * scale))
  val window = projection.panel(-33f, -57f, 33f, 52f, 22f, 4f)
  drawPath(window, Brush.linearGradient(listOf(Color(0xFF0C1927), Color(0xFF163046)), projection.at(-33f, -57f, 22f), projection.at(33f, 52f, 22f)))
  drawPath(window, Color(0xFF7BADC5).copy(alpha = 0.34f), style = Stroke(0.8f * scale))

  repeat(10) { index ->
    val bottom = 45f - index * 10f
    val top = bottom - 7f
    val fill = (charge * 10f - index).coerceIn(0f, 1f)
    val cell = projection.panel(-27f, top, 27f, bottom, 22.2f, 1.1f)
    drawPath(cell, Color(0xFF304556).copy(alpha = 0.65f))
    if (fill > 0f) {
      val filledTop = bottom - 7f * fill
      val active = projection.panel(-27f, filledTop, 27f, bottom, 22.3f, min(1.1f, 3.5f * fill))
      drawPath(active, Brush.linearGradient(
        listOf(if (percent != null && percent <= 20) Color(0xFFFFE2B4) else Color(0xFFBDEEFF), energy, if (percent != null && percent <= 20) Color(0xFFB66A22) else Color(0xFF3072D7)),
        projection.at(-27f, filledTop, 23f),
        projection.at(27f, bottom, 23f),
      ))
      drawLine(Color.White.copy(alpha = 0.38f), projection.at(-25f, filledTop + 0.5f, 23f), projection.at(24f, filledTop + 0.5f, 23f), strokeWidth = 0.65f * scale, cap = StrokeCap.Round)
    }
  }
  // Glazing reflection: narrow highlights rather than a costly blur layer.
  val reflection = projection.path(listOf(BatteryPoint(-31f, -55f, 23f), BatteryPoint(-19f, -55f, 23f), BatteryPoint(13f, 50f, 23f), BatteryPoint(4f, 50f, 23f)))
  drawPath(reflection, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent), projection.at(-31f, -55f, 23f), projection.at(13f, 50f, 23f)))

  // Side cooling ribs and recessed fasteners give the object a physical scale.
  val side = if (-22f + drag < 0f) 43.1f else -43.1f
  repeat(11) { index ->
    val y = -43f + index * 9f
    drawLine(Color(0xFF080E18).copy(alpha = 0.7f), projection.at(side, y, -13f), projection.at(side, y, 12f), 2.2f * scale, StrokeCap.Round)
    drawLine(Color(0xFF93A4BC).copy(alpha = 0.20f), projection.at(side, y + 1.6f, -13f), projection.at(side, y + 1.6f, 12f), 0.6f * scale, StrokeCap.Round)
  }
  listOf(-35f, 35f).forEach { x ->
    listOf(-64f, 65f).forEach { y ->
      drawCircle(Color(0xFF111A26), 1.7f * scale, projection.at(x, y, 22f))
      drawCircle(Color(0xFF8D9AAC), 0.75f * scale, projection.at(x, y - 0.25f, 22.1f))
    }
  }
  drawLine(Color(0xFF90A4BA).copy(alpha = 0.6f), projection.at(-13f, 62f, 22f), projection.at(13f, 62f, 22f), 1.4f * scale, StrokeCap.Round)
  drawLine(energy.copy(alpha = if (percent == null) 0.15f else 0.75f), projection.at(-6f, -65f, 22f), projection.at(6f, -65f, 22f), 1.6f * scale, StrokeCap.Round)
}

private fun DrawScope.drawBatteryBox(
  projection: BatteryProjection,
  left: Float, top: Float, back: Float,
  right: Float, bottom: Float, front: Float,
  highlight: Color, shadow: Color,
) {
  val vertices = listOf(
    BatteryPoint(left, top, back), BatteryPoint(right, top, back),
    BatteryPoint(right, bottom, back), BatteryPoint(left, bottom, back),
    BatteryPoint(left, top, front), BatteryPoint(right, top, front),
    BatteryPoint(right, bottom, front), BatteryPoint(left, bottom, front),
  )
  val faces = listOf(
    listOf(0, 1, 5, 4), listOf(3, 2, 6, 7), listOf(0, 3, 7, 4),
    listOf(1, 2, 6, 5), listOf(0, 1, 2, 3), listOf(4, 5, 6, 7),
  ).sortedBy { indices -> indices.sumOf { projection.project(vertices[it]).depth.toDouble() } }
  faces.forEach { indices ->
    val points = indices.map { vertices[it] }
    val path = projection.path(points)
    drawPath(path, Brush.linearGradient(listOf(highlight, shadow), projection.project(points.first()).position, projection.project(points[2]).position))
    drawPath(path, highlight.copy(alpha = 0.24f), style = Stroke(0.6.dp.toPx()))
  }
}
