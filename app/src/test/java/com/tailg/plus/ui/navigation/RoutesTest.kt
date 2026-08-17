package com.tailg.plus.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {

  @Test
  fun `vehicle home targets the control route for a selected vehicle`() {
    assertEquals("control/car-123", Routes.vehicleHome("car-123"))
  }

  @Test
  fun `vehicle home falls back to current when no vehicle is selected`() {
    assertEquals("control/current", Routes.vehicleHome(null))
    assertEquals("control/current", Routes.vehicleHome("   "))
  }
}
