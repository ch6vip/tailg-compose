package com.tailg.plus.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Port of the Dart `map_tile_config` expectations — template selection and
 * tile URL resolution (mirrors `test/map_tile_config_test.dart` behavior).
 */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class MapTileConfigTest {

  @Test
  fun `autonavi template without token`() {
    val template = MapTileConfig.baseUrlTemplate(token = "")
    assertEquals(
      "https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}",
      template,
    )
    assertNull(MapTileConfig.annotationUrlTemplate(token = ""))
    assertEquals(listOf("1", "2", "3", "4"), MapTileConfig.subdomains(token = ""))
  }

  @Test
  fun `tianditu template with token`() {
    val template = MapTileConfig.baseUrlTemplate(token = " demo-token ")
    assertTrue(template.startsWith("https://t{s}.tianditu.gov.cn/DataServer?T=vec_w&"))
    assertTrue(template.endsWith("tk=demo-token"))
    val annotation = MapTileConfig.annotationUrlTemplate(token = "demo-token")
    assertNotNull(annotation)
    assertTrue(annotation!!.contains("T=cva_w"))
    assertEquals(
      listOf("0", "1", "2", "3", "4", "5", "6", "7"),
      MapTileConfig.subdomains(token = "demo-token"),
    )
  }

  @Test
  fun `resolves x y z and subdomain placeholders`() {
    val template = "https://webrd0{s}.is.autonavi.com/appmaptile?x={x}&y={y}&z={z}"
    // Default seed is x+y, so x=3,y=4 -> seed 7 -> subdomains[7 % 4] = "4".
    assertEquals(
      "https://webrd04.is.autonavi.com/appmaptile?x=3&y=4&z=12",
      MapTileConfig.resolveTileUrl(template, x = 3, y = 4, zoom = 12, subdomains = listOf("1", "2", "3", "4")),
    )
  }

  @Test
  fun `subdomain rotates with seed`() {
    val template = "https://t{s}.example.com/{z}/{x}/{y}"
    val subdomains = listOf("1", "2", "3", "4")
    fun sub(seed: Int): String =
      MapTileConfig.resolveTileUrl(template, 0, 0, 5, subdomainSeed = seed, subdomains = subdomains)
        .replace(Regex("^https://t(.)\\.example.*$"), "$1")
    assertEquals("1", sub(0))
    assertEquals("2", sub(1))
    assertEquals("4", sub(3))
    assertEquals("1", sub(4))
  }

  @Test
  fun `circle geo points stay within radius bounds and close the loop`() {
    val center = org.osmdroid.util.GeoPoint(30.2741, 120.1551)
    val points = circleGeoPoints(center, 500.0, segments = 64)
    assertEquals(64, points.size)
    points.forEach { p ->
      val d = center.distanceToAsDouble(p)
      assertTrue("point $d outside circle", d in 480.0..520.0)
    }
    assertFalse(points[0] == points[1])
  }
}
