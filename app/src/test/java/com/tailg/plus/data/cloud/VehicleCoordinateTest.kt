package com.tailg.plus.data.cloud

import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.data.model.OfficialVehicleLocation
import com.tailg.plus.data.model.isValidCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleCoordinateTest {
  @Test
  fun coordinatesMustBeFiniteAndInRange() {
    assertTrue(isValidCoordinate(-90.0, -180.0))
    assertTrue(isValidCoordinate(90.0, 180.0))
    assertTrue(isValidCoordinate(0.0, 0.0))
    for ((lat, lng) in listOf(
      Double.NaN to 120.0, 30.0 to Double.POSITIVE_INFINITY,
      90.1 to 120.0, 30.0 to -180.1, null to 120.0,
    )) {
      assertFalse("$lat, $lng", isValidCoordinate(lat, lng))
    }
  }

  @Test
  fun invalidParkingPinFallsBackToValidVehicleCoordinates() {
    val location = resolveVehicleLocation(
      vehicleLocation = OfficialVehicleLocation(bleConnectLat = "NaN", bleConnectLng = "120"),
      officialVehicle = OfficialVehicle(latitude = "30", longitude = "120"),
      localVehicle = null,
    )
    assertEquals(30.0, location?.latitude)
    assertTrue(location!!.hasCoordinate)
  }

  @Test
  fun invalidParkingPinCanStillSupplyAddressMetadataWithoutAPin() {
    val location = resolveVehicleLocation(
      vehicleLocation = OfficialVehicleLocation(bleConnectLat = "91", bleConnectLng = "120", bleConnectAddress = "address"),
      officialVehicle = null,
      localVehicle = null,
      allowCloudMetadataWithoutCoordinate = true,
    )
    assertNotNull(location)
    assertEquals("address", location?.address)
    assertFalse(location!!.hasCoordinate)
    assertFalse(ResolvedVehicleLocation(Double.NaN, 120.0, 0.0, "", "", "").hasCoordinate)
  }

  @Test
  fun invalidTrackPointsAreRemovedBeforeReachingTheMap() {
    val points = OfficialCloudDataParser.travelPoints(listOf(
      mapOf("lat" to "30", "lng" to "120"),
      mapOf("lat" to "NaN", "lng" to "120"),
      mapOf("lat" to "30", "lng" to "Infinity"),
      mapOf("lat" to "91", "lng" to "120"),
      mapOf("lat" to "30", "lng" to "181"),
    ))
    assertEquals(1, points.size)
    assertEquals(30.0, points.single().latitude)
  }
}
