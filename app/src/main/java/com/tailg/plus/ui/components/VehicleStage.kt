package com.tailg.plus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tailg.plus.ui.theme.AppColors
import com.tailg.plus.ui.theme.AppColorsLight
import kotlin.math.min

/**
 * Port of `lib/widgets/vehicle_stage.dart` — fallback vehicle illustration.
 *
 * The Dart `CustomPainter` is ported 1:1 to a [DrawScope] extension
 * ([drawVehicleStage]) drawing the 340×172 viewBox with Compose [Path]s,
 * brushes and native blur-free strokes.
 *
 * **Color policy**: the painter uses the Dart illustration palette
 * (file-local constants, the same way the Dart file declares its own
 * `_vehicleFrameColor` etc. and the theme declares `ReplicaBikeColors`).
 * UI chrome tokens are used where the Dart referenced theme tokens:
 * - battery track `AppColors.card3` → [AppColorsLight.surfaceContainerHigh]
 * - dashboard `AppColors.inkBtn` → [AppColors.inkBtn]
 * - dashboard screen `AppColors.energyGreen` → [AppColors.energyGreen]
 * - wheel hub `AppColors.pageBgBot` → [AppColors.pageBgBot]
 *
 * **Deferred**: the official asset `assets/official_tailg/iv_control_evbike.png`
 * (Dart `VehicleStage.fallbackAsset`) is not copied into this project (task
 * scope copied only `lottie/`); the painter draws instead. Remote `carPhoto`
 * loading needs an image loader (Coil) that is not yet a dependency — the
 * `imageUrl` parameter is accepted for API parity and ignored for now.
 */

// Painter palette (mirror of Dart `_vehicleFrameColor` etc.).
private val VehicleFrameColor = Color(0xFF2A313D)
private val VehicleBodyLight = Color(0xFFDFE5EC)
private val VehicleTireShadow = Color(0xFFC2CAD4)
private val VehicleAccentDark = Color(0xFF3A434F)
private val VehicleBodyTop = Color(0xFFFCFDFE)
private val VehicleSeatStitch = Color(0x4D4A5563)
private val VehicleHubStroke = Color(0xFF9AA3B0)
private val VehicleSpoke = Color(0xFFB6BEC9)
private val VehicleHeadlight = Color(0xFFFCE7B8)
private val VehicleBatteryBorder = Color(0xFF04231A).copy(alpha = 0.08f)

/** Draws the 340×172 vehicle illustration, centered and aspect-scaled. */
fun DrawScope.drawVehicleStage(batteryLevel: Float) {
  val s = min(size.width / 340f, size.height / 172f)
  val ox = (size.width - 340f * s) / 2f
  val oy = (size.height - 172f * s) / 2f

  translate(left = ox, top = oy) {
    scale(scaleX = s, scaleY = s) {
      drawFloor()
      drawBrandMark()
      drawVehicle(batteryLevel)
    }
  }
}

private fun DrawScope.drawFloor() {
  drawOval(
    brush = Brush.radialGradient(
      colors = listOf(Color(0x18BFCAD6), Color(0x00BFCAD6)),
      center = Offset(170f, 145f),
      radius = 110f,
    ),
    topLeft = Offset(80f, 135f),
    size = Size(180f, 20f),
  )
}

private fun DrawScope.drawBrandMark() {
  val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    color = VehicleBodyLight.toArgb()
    textSize = 32f
    letterSpacing = 8f / 32f // Dart letterSpacing 8 at fontSize 32
    typeface = android.graphics.Typeface.create("Arial", android.graphics.Typeface.BOLD)
  }
  drawContext.canvas.nativeCanvas.drawText("TAILG", 110f, -18f + 32f, paint)
}

private fun DrawScope.drawVehicle(batteryLevel: Float) {
  val bodyGradient = Brush.linearGradient(
    colors = listOf(VehicleBodyTop, VehicleBodyLight),
    start = Offset(193f, 50f), // topCenter of rect(46,50,340,130)
    end = Offset(193f, 130f),
  )
  val tireGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFE3E8EF), VehicleTireShadow),
    start = Offset(170f, 100f), // topCenter of rect(0,100,340,172)
    end = Offset(170f, 172f),
  )
  val enGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF00E0A6), Color(0xFF00A57C)),
    start = Offset(108f, 98.5f), // centerLeft of battery bar
    end = Offset(148f, 98.5f),
  )

  val bodyStroke = Stroke(width = 4.5f, join = StrokeJoin.Round)

  // --- Rear mudguard ---
  drawPath(
    path = quadPath(46f, 116f, 80f, 80f, 118f, 104f),
    color = VehicleTireShadow,
    style = Stroke(width = 9f, cap = StrokeCap.Round),
  )
  // --- Front mudguard ---
  drawPath(
    path = quadPath(232f, 112f, 266f, 80f, 304f, 110f),
    color = VehicleTireShadow,
    style = Stroke(width = 9f, cap = StrokeCap.Round),
  )

  // --- Rear body (seat/battery compartment) ---
  val rearBody = Path().apply {
    moveTo(92f, 72f)
    lineTo(150f, 68f)
    quadraticBezierTo(160f, 70f, 162f, 82f)
    lineTo(168f, 120f)
    lineTo(104f, 124f)
    quadraticBezierTo(90f, 104f, 88f, 84f)
    quadraticBezierTo(88f, 75f, 92f, 72f)
    close()
  }
  drawPath(rearBody, brush = bodyGradient)
  drawPath(rearBody, color = VehicleFrameColor, style = bodyStroke)

  // --- Battery energy bar (dynamic: scales with batteryLevel) ---
  val barX = 108f
  val barY = 92f
  val barW = 40f
  val barH = 13f
  val fillW = barW * batteryLevel.coerceIn(0f, 1f)
  drawRoundRect(
    color = AppColorsLight.surfaceContainerHigh, // Dart AppColors.card3
    topLeft = Offset(barX, barY),
    size = Size(barW, barH),
    cornerRadius = CornerRadius(4f),
  )
  if (fillW > 0f) {
    drawRoundRect(
      brush = enGradient,
      topLeft = Offset(barX, barY),
      size = Size(fillW, barH),
      cornerRadius = CornerRadius(4f),
    )
  }
  drawRoundRect(
    color = VehicleBatteryBorder,
    topLeft = Offset(barX, barY),
    size = Size(barW, barH),
    cornerRadius = CornerRadius(4f),
    style = Stroke(width = 1f),
  )

  // --- Footboard (low step-through) ---
  drawLine(
    color = VehicleFrameColor,
    start = Offset(150f, 124f),
    end = Offset(214f, 124f),
    strokeWidth = 9f,
    cap = StrokeCap.Round,
  )
  drawLine(
    color = VehicleAccentDark.copy(alpha = 0.5f), // Dart ColorFilter srcOver 0x803A434F
    start = Offset(156f, 124f),
    end = Offset(210f, 124f),
    strokeWidth = 3f,
    cap = StrokeCap.Round,
  )

  // --- Front leg shield + stem ---
  val frontShield = Path().apply {
    moveTo(214f, 124f)
    quadraticBezierTo(214f, 104f, 224f, 90f)
    quadraticBezierTo(236f, 70f, 250f, 60f)
    lineTo(266f, 54f)
    quadraticBezierTo(272f, 60f, 268f, 74f)
    quadraticBezierTo(260f, 96f, 244f, 112f)
    quadraticBezierTo(232f, 122f, 224f, 124f)
    close()
  }
  drawPath(frontShield, brush = bodyGradient)
  drawPath(frontShield, color = VehicleFrameColor, style = bodyStroke)

  // --- Seat ---
  val seat = Path().apply {
    moveTo(60f, 70f)
    quadraticBezierTo(54f, 58f, 72f, 55f)
    lineTo(150f, 50f)
    quadraticBezierTo(164f, 50f, 160f, 63f)
    lineTo(154f, 70f)
    quadraticBezierTo(150f, 73f, 142f, 72f)
    lineTo(70f, 73f)
    quadraticBezierTo(62f, 73f, 60f, 70f)
    close()
  }
  drawPath(seat, color = VehicleFrameColor)
  drawLine(
    color = VehicleSeatStitch,
    start = Offset(74f, 58f),
    end = Offset(148f, 54f),
    strokeWidth = 2f,
    cap = StrokeCap.Round,
  )

  // --- Handlebar stem + handlebar ---
  drawLine(
    color = VehicleFrameColor,
    start = Offset(258f, 60f),
    end = Offset(278f, 32f),
    strokeWidth = 6.5f,
    cap = StrokeCap.Round,
  )
  drawLine(
    color = VehicleFrameColor,
    start = Offset(266f, 32f),
    end = Offset(296f, 27f),
    strokeWidth = 8f,
    cap = StrokeCap.Round,
  )

  // --- Dashboard ---
  drawRoundRect(
    color = AppColors.inkBtn,
    topLeft = Offset(252f, 40f),
    size = Size(22f, 15f),
    cornerRadius = CornerRadius(4f),
  )
  drawRoundRect(
    color = AppColors.energyGreen.copy(alpha = 0.9f),
    topLeft = Offset(256f, 44f),
    size = Size(14f, 7f),
    cornerRadius = CornerRadius(2f),
  )

  // --- Headlight ---
  val headlight = Path().apply {
    moveTo(244f, 96f)
    lineTo(266f, 102f)
    lineTo(263f, 116f)
    lineTo(241f, 110f)
    close()
  }
  drawPath(headlight, color = VehicleAccentDark)
  drawOval(
    color = VehicleHeadlight,
    topLeft = Offset(249f, 100f),
    size = Size(10f, 11f),
  )

  // --- Wheels ---
  drawWheel(82f, 130f, tireGradient)
  drawWheel(268f, 130f, tireGradient)
}

private fun DrawScope.drawWheel(cx: Float, cy: Float, tireBrush: Brush) {
  // Tire
  drawCircle(brush = tireBrush, radius = 30f, center = Offset(cx, cy))
  drawCircle(
    color = VehicleFrameColor,
    radius = 30f,
    center = Offset(cx, cy),
    style = Stroke(width = 5.5f),
  )
  // Hub
  drawCircle(color = AppColors.pageBgBot, radius = 13.5f, center = Offset(cx, cy))
  drawCircle(
    color = VehicleHubStroke,
    radius = 13.5f,
    center = Offset(cx, cy),
    style = Stroke(width = 2f),
  )
  // Axle
  drawCircle(color = VehicleFrameColor, radius = 3.6f, center = Offset(cx, cy))
  // Spokes
  drawLine(VehicleSpoke, Offset(cx, cy - 12f), Offset(cx, cy + 12f), strokeWidth = 2f)
  drawLine(VehicleSpoke, Offset(cx - 12f, cy), Offset(cx + 12f, cy), strokeWidth = 2f)
  drawLine(VehicleSpoke, Offset(cx - 8.5f, cy - 8.5f), Offset(cx + 8.5f, cy + 8.5f), strokeWidth = 2f)
  drawLine(VehicleSpoke, Offset(cx - 8.5f, cy + 8.5f), Offset(cx + 8.5f, cy - 8.5f), strokeWidth = 2f)
}

/** Dart `_cubicPath` — quadratic curve from (x1,y1) via (cx,cy) to (x2,y2). */
private fun quadPath(x1: Float, y1: Float, cx: Float, cy: Float, x2: Float, y2: Float): Path =
  Path().apply {
    moveTo(x1, y1)
    quadraticBezierTo(cx, cy, x2, y2)
  }

/**
 * Wrapper for the vehicle stage with proper sizing (Dart `VehicleStage`).
 * The painter is always drawn; when the official asset is copied later,
 * swap the Canvas for an `Image(painterResource(...))`.
 */
@Composable
fun VehicleStage(
  modifier: Modifier = Modifier,
  batteryLevel: Float = 0.84f,
  height: Dp = 200.dp,
  imageUrl: String? = null,
) {
  // Dart officialHorizontalPadding = 20.0
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(height)
      .padding(horizontal = 20.dp),
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      drawVehicleStage(batteryLevel)
    }
  }
}
