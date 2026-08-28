package com.tailg.plus.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleStageTest {

  @Test
  fun transformIsNullForTinyOrNonFiniteSize() {
    assertNull(vehicleStageTransform(0f, 164f))
    assertNull(vehicleStageTransform(320f, 0f))
    assertNull(vehicleStageTransform(7f, 7f))
    assertNull(vehicleStageTransform(Float.NaN, 164f))
    assertNull(vehicleStageTransform(320f, Float.POSITIVE_INFINITY))
  }

  @Test
  fun identityViewBoxKeepsRearWheelInPlace() {
    val transform = vehicleStageTransform(
      VEHICLE_STAGE_VIEWBOX_WIDTH,
      VEHICLE_STAGE_VIEWBOX_HEIGHT,
    )
    assertNotNull(transform)
    assertEquals(1f, transform!!.scale, 0.0001f)
    assertEquals(0f, transform.originX, 0.0001f)
    assertEquals(0f, transform.originY, 0.0001f)
    val (x, y) = transform.mapViewBox(82f, 130f)
    assertEquals(82f, x, 0.0001f)
    assertEquals(130f, y, 0.0001f)
  }

  @Test
  fun xxhdpiCanvasKeepsRearWheelOnScreen() {
    // ~360dp screen minus header/stage padding, 164.dp tall, density 3.
    val transform = vehicleStageTransform(width = 840f, height = 492f)
    assertNotNull(transform)
    val (x, y) = transform!!.mapViewBox(82f, 130f)
    assertTrue("rear wheel x=$x", x in 100f..740f)
    assertTrue("rear wheel y=$y", y in 80f..460f)
    val (frontX, _) = transform.mapViewBox(268f, 130f)
    assertTrue("front wheel x=$frontX", frontX in 200f..820f)
    assertTrue(frontX > x)
  }

  @Test
  fun composeDefaultCenterPivotWouldPushRearWheelOffTheLeftEdge() {
    val width = 840f
    val height = 492f
    val transform = vehicleStageTransform(width, height)!!
    val (correctX, _) = transform.mapViewBox(82f, 130f)
    val centerX = width / 2f
    val wrongX = transform.originX + centerX + (82f - centerX) * transform.scale
    assertTrue("origin-pivot wheel should stay on-canvas, got $correctX", correctX > 100f)
    assertTrue("center-pivot wheel should go negative, got $wrongX", wrongX < 0f)
  }

  @Test
  fun layoutReadyRejectsLandscapeWidthInPortraitViewport() {
    assertFalse(
      vehicleStageLayoutReady(
        widthPx = 2280f,
        heightPx = 492f,
        viewportWidthPx = 1080f,
        expectedHeightPx = 492f,
      ),
    )
  }

  @Test
  fun layoutReadyAcceptsPortraitStageInPortraitViewport() {
    assertTrue(
      vehicleStageLayoutReady(
        widthPx = 960f,
        heightPx = 492f,
        viewportWidthPx = 1080f,
        expectedHeightPx = 492f,
      ),
    )
  }

  @Test
  fun layoutReadyAcceptsLandscapeStageInLandscapeViewport() {
    assertTrue(
      vehicleStageLayoutReady(
        widthPx = 2280f,
        heightPx = 492f,
        viewportWidthPx = 2400f,
        expectedHeightPx = 492f,
      ),
    )
  }

  @Test
  fun layoutReadyRejectsSqueezedHeight() {
    assertFalse(
      vehicleStageLayoutReady(
        widthPx = 960f,
        heightPx = 72f,
        viewportWidthPx = 1080f,
        expectedHeightPx = 492f,
      ),
    )
  }

  @Test
  fun remoteImageUrlDetection() {
    assertTrue(isRemoteVehicleImageUrl("https://cdn.example/car.png"))
    assertTrue(isRemoteVehicleImageUrl("HTTP://cdn.example/car.png"))
    assertFalse(isRemoteVehicleImageUrl(""))
    assertFalse(isRemoteVehicleImageUrl("drawable://bike"))
    assertFalse(isRemoteVehicleImageUrl("ftp://cdn.example/car.png"))
  }

  @Test
  fun imageSampleSizeBoundsDecodeBelowTarget() {
    // 4000x3000 source → sample to ≤ 960x720 without oversampling below it.
    val sample = vehicleImageSampleSize(4000, 3000)
    assertTrue("sample=$sample should be at least 4 for 4000x3000", sample >= 4)
    assertTrue("sampled width too coarse: ${4000 / sample}", 4000 / sample <= MAX_VEHICLE_IMAGE_WIDTH * 2)
    assertTrue("sampled height too coarse: ${3000 / sample}", 3000 / sample <= MAX_VEHICLE_IMAGE_HEIGHT * 2)
    // Small sources are never sampled.
    assertEquals(1, vehicleImageSampleSize(640, 480))
    assertEquals(1, vehicleImageSampleSize(MAX_VEHICLE_IMAGE_WIDTH, MAX_VEHICLE_IMAGE_HEIGHT))
  }

  @Test
  fun imageSampleSizeRejectsNonPositive() {
    assertEquals(1, vehicleImageSampleSize(0, 0))
    assertEquals(1, vehicleImageSampleSize(-10, 100))
  }

  @Test
  fun imageTargetSizeNeverUpscalesAndKeepsAspect() {
    // Small source stays untouched.
    val small = vehicleImageTargetSize(640, 480)
    assertEquals(640, small.first)
    assertEquals(480, small.second)
    // Large source scales down proportionally within the bounds.
    val (w, h) = vehicleImageTargetSize(4000, 3000)
    assertTrue(w <= MAX_VEHICLE_IMAGE_WIDTH && h <= MAX_VEHICLE_IMAGE_HEIGHT)
    assertEquals(w.toDouble() / h, 4.0 / 3.0, 0.02)
    // Portrait sources keep portrait.
    val (pw, ph) = vehicleImageTargetSize(2000, 3000)
    assertTrue("portrait should stay portrait: $pw x $ph", ph >= pw)
  }
}
