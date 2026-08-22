package com.tailg.plus.ui.components

import kotlin.math.min

internal const val VEHICLE_STAGE_VIEWBOX_WIDTH = 340f
internal const val VEHICLE_STAGE_VIEWBOX_HEIGHT = 172f
internal const val VEHICLE_STAGE_MIN_SIZE_PX = 8f

/** Scale-to-fit transform for the 340x172 vehicle viewBox. */
internal data class VehicleStageTransform(
  val scale: Float,
  val originX: Float,
  val originY: Float,
)

/**
 * Flutter `canvas.translate(ox, oy); canvas.scale(s)` equivalent.
 * Returns null when the canvas is too small or non-finite so callers can skip
 * the frame instead of drawing a clipped fragment at the origin.
 */
internal fun vehicleStageTransform(width: Float, height: Float): VehicleStageTransform? {
  if (!width.isFinite() || !height.isFinite()) return null
  if (width < VEHICLE_STAGE_MIN_SIZE_PX || height < VEHICLE_STAGE_MIN_SIZE_PX) return null
  val scale = min(
    width / VEHICLE_STAGE_VIEWBOX_WIDTH,
    height / VEHICLE_STAGE_VIEWBOX_HEIGHT,
  )
  if (scale <= 0f || !scale.isFinite()) return null
  return VehicleStageTransform(
    scale = scale,
    originX = (width - VEHICLE_STAGE_VIEWBOX_WIDTH * scale) / 2f,
    originY = (height - VEHICLE_STAGE_VIEWBOX_HEIGHT * scale) / 2f,
  )
}

internal fun VehicleStageTransform.mapViewBox(x: Float, y: Float): Pair<Float, Float> =
  (originX + x * scale) to (originY + y * scale)

internal fun isRemoteVehicleImageUrl(url: String): Boolean =
  url.startsWith("https://", ignoreCase = true) ||
    url.startsWith("http://", ignoreCase = true)

/**
 * True when the stage box is a real laid-out slot, not leftover rotation
 * geometry. A landscape-width box shown in a portrait viewport clips to the
 * left half of a centered bike (the rear wheel flash).
 */
internal fun vehicleStageLayoutReady(
  widthPx: Float,
  heightPx: Float,
  viewportWidthPx: Float,
  expectedHeightPx: Float = 0f,
): Boolean {
  if (vehicleStageTransform(widthPx, heightPx) == null) return false
  if (expectedHeightPx >= VEHICLE_STAGE_MIN_SIZE_PX &&
    heightPx < expectedHeightPx * 0.55f
  ) {
    return false
  }
  if (viewportWidthPx >= VEHICLE_STAGE_MIN_SIZE_PX &&
    widthPx > viewportWidthPx * 1.08f
  ) {
    return false
  }
  return true
}
