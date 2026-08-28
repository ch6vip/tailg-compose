package com.tailg.plus.ui.components

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CyberMapStatsDecodeTest {

  @Test
  fun decodeMiniMapTileRejectsEmptyInput() {
    assertNull(decodeMiniMapTile(ByteArray(0)))
  }

  @Test
  fun decodeMiniMapTileDecodesPng() {
    // Minimal 1x1 transparent PNG (67 bytes).
    val png = Base64.getDecoder().decode(
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
    )
    val bitmap = decodeMiniMapTile(png)
    assertNotNull(bitmap)
    assertEquals(1, bitmap!!.width)
    assertEquals(1, bitmap.height)
  }
}
