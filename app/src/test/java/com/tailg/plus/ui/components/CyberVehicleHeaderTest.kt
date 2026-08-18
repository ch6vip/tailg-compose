package com.tailg.plus.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class CyberVehicleHeaderTest {

  @Test
  fun expandedAndCollapsedHeightsMatchFlutterHeaderExtents() {
    assertEquals(376.dp, cyberHeaderHeight(0f))
    assertEquals(152.dp, cyberHeaderHeight(1f))
  }

  @Test
  fun headerHeightClampsAndInterpolatesCollapseProgress() {
    assertEquals(376.dp, cyberHeaderHeight(-1f))
    assertEquals(264.dp, cyberHeaderHeight(0.5f))
    assertEquals(152.dp, cyberHeaderHeight(2f))
  }
}
