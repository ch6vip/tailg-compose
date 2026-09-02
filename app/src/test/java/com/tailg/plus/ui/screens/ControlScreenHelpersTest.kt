package com.tailg.plus.ui.screens

import com.tailg.plus.data.model.OfficialRideStatistics
import com.tailg.plus.data.preferences.DistanceUnitPreference
import org.junit.Assert.assertEquals
import org.junit.Test

class ControlScreenHelpersTest {

  @Test
  fun totalMileageLabel_usesCumulativeRideStatisticsMeters() {
    val state = cloudState(
      vehicleMileage = 12.0, // control-header range; must not be used here
      statistics = OfficialRideStatistics(totalMileage = "123400"),
    )

    assertEquals("123.4 km", totalMileageLabel(state))
  }

  @Test
  fun totalMileageLabel_convertsCumulativeMetersToImperial() {
    val state = cloudState(
      statistics = OfficialRideStatistics(totalMileage = "10000"),
    )

    assertEquals("6.2 mi", totalMileageLabel(state, DistanceUnitPreference.Imperial))
  }

  @Test
  fun totalMileageLabel_returnsMissingWhenStatisticsAreUnavailable() {
    assertEquals("--", totalMileageLabel(cloudState(statistics = null)))
    assertEquals(
      "--",
      totalMileageLabel(cloudState(statistics = OfficialRideStatistics(totalMileage = "invalid"))),
    )
  }

  private fun cloudState(
    vehicleMileage: Double? = null,
    statistics: OfficialRideStatistics?,
  ): CloudScreenState = CloudScreenState(
    signedIn = true,
    selectedVehicle = com.tailg.plus.data.model.OfficialVehicle(mileage = vehicleMileage),
    selectedVehicleKey = null,
    vehicles = emptyList(),
    batteryInfo = null,
    vehicleLocation = null,
    localVehicleLinks = emptyMap(),
    travelDays = emptyList(),
    rideStatistics = statistics,
    loading = false,
    error = null,
  )
}
