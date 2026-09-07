package com.tailg.plus.ui.components

import com.tailg.plus.data.model.isValidCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.util.GeoPoint
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CyberMapGeometryTest {
  @Test
  fun fenceRemainsGeographicAcrossPolesAndTheDateLine() {
    for (center in listOf(GeoPoint(89.99, 179.99), GeoPoint(-89.99, -179.99), GeoPoint(30.0, 179.99))) {
      val points = circleGeoPoints(center, 5_000.0)
      assertEquals(64, points.size)
      for (point in points) {
        assertTrue(isValidCoordinate(point.latitude, point.longitude))
        assertEquals(5_000.0, center.distanceToAsDouble(point), 5.0)
      }
    }
  }

  @Test
  fun invalidFenceDataDoesNotGenerateAnOverlay() {
    val center = GeoPoint(30.0, 120.0)
    assertTrue(circleGeoPoints(center, Double.POSITIVE_INFINITY).isEmpty())
    assertTrue(circleGeoPoints(center, Double.NaN).isEmpty())
    assertTrue(circleGeoPoints(center, -1.0).isEmpty())
    assertTrue(circleGeoPoints(center, 500.0, segments = 0).isEmpty())
  }
}
