package com.tailg.plus.data.cloud

import com.tailg.plus.data.model.OfficialBatteryInfo
import com.tailg.plus.data.model.OfficialBmsInfo
import com.tailg.plus.data.model.OfficialFenceData
import com.tailg.plus.data.model.OfficialRidePeriod
import com.tailg.plus.data.model.OfficialRideStatistics
import com.tailg.plus.data.model.OfficialTravelDay
import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.data.model.OfficialVehicleLocation
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialCloudRefreshConcurrencyTest {
  private fun vehicle(id: String) = OfficialVehicle(carId = id, imei = id, frame = "frame-$id")

  private fun state(id: String) = OfficialCloudState.initial().copyWith(
    initialized = true,
    token = "session",
    userId = "user",
    vehicles = listOf(vehicle(id)),
    selectedVehicleKey = vehicle(id).key,
  )

  private fun response(code: Int = 200, data: Any = emptyMap<String, Any>()) =
    OfficialCloudApiResponse(200, emptyMap(), mapOf("code" to code, "data" to data))

  private fun service(api: OfficialCloudApiClientInterface, scope: CoroutineScope) =
    OfficialCloudService(
      storage = mockk(relaxed = true),
      apiClient = api,
      vehicleStore = mockk(relaxed = true),
      scope = scope,
    ).apply { setStateForTest(state("a")) }

  @Test
  fun lateVehicleResponsesCannotOverwriteNewSelectionOrLoadingFlags() = runTest {
    val refreshes: List<Pair<String, suspend (OfficialCloudService) -> Unit>> = listOf(
      "battery" to { it.refreshBatteryInfo() },
      "bms" to { it.refreshBmsInfo() },
      "location" to { it.refreshVehicleLocation() },
      "fence" to { it.refreshFenceData() },
      "travel" to { it.refreshTravelHistory(month = "2026-08") },
      "travel detail" to { it.refreshTravelDetail("old-trip") },
      "ride statistics" to { it.refreshRideStatistics(OfficialRidePeriod.DAY) },
    )
    for ((name, refresh) in refreshes) {
      val result = CompletableDeferred<OfficialCloudApiResponse>()
      val api = mockk<OfficialCloudApiClientInterface>()
      coEvery { api.request(any(), any(), any(), any(), any()) } coAnswers { result.await() }
      val service = service(api, backgroundScope)
      val request = async { refresh(service) }
      testScheduler.runCurrent()
      val selected = state("b").copyWith(
        batteryInfo = OfficialBatteryInfo(dumpEnergyPercent = "82"),
        bmsInfo = OfficialBmsInfo(soc = "82"),
        vehicleLocation = OfficialVehicleLocation(carId = "b"),
        fenceData = OfficialFenceData(fenceRadius = "5"),
        travelDays = listOf(OfficialTravelDay(travelDate = "2026-09-07")),
        travelMonth = "2026-09",
        batteryInfoLoading = true,
        bmsInfoLoading = true,
        vehicleLocationLoading = true,
        fenceLoading = true,
        travelLoading = true,
        travelDetailLoading = true,
        rideStatistics = OfficialRideStatistics(dayMileage = "8200"),
        rideStatisticsLoading = true,
      )
      service.setStateForTest(selected)
      result.complete(response())
      request.await()

      assertEquals(name, selected, service.currentState)
    }
  }

  @Test
  fun oldNoBmsResponseCannotEraseCurrentBattery() = runTest {
    val result = CompletableDeferred<OfficialCloudApiResponse>()
    val api = mockk<OfficialCloudApiClientInterface>()
    coEvery { api.request(any(), any(), any(), any(), any()) } coAnswers { result.await() }
    val service = service(api, backgroundScope)
    val request = async { service.refreshBmsInfo() }
    testScheduler.runCurrent()
    val selected = state("b").copyWith(bmsInfo = OfficialBmsInfo(soc = "92"))
    service.setStateForTest(selected)
    result.complete(response(code = 100))
    request.await()

    assertEquals(selected, service.currentState)
  }

  @Test
  fun olderMonthCannotReplaceMoreRecentTravelRequest() = runTest {
    val august = CompletableDeferred<OfficialCloudApiResponse>()
    val september = CompletableDeferred<OfficialCloudApiResponse>()
    val api = mockk<OfficialCloudApiClientInterface>()
    coEvery { api.request(any(), any(), any(), any(), any()) } coAnswers {
      if (arg<Map<String, Any?>>(3)["queryMonth"] == "2026-08") august.await() else september.await()
    }
    val service = service(api, backgroundScope)
    val old = async { service.refreshTravelHistory(month = "2026-08") }
    testScheduler.runCurrent()
    val current = async { service.refreshTravelHistory(month = "2026-09") }
    testScheduler.runCurrent()
    september.complete(response(data = listOf(mapOf("travelDate" to "2026-09-07"))))
    current.await()
    val selected = service.currentState
    august.complete(response(data = emptyList<Any>()))
    old.await()

    assertEquals("2026-09", selected.travelMonth)
    assertEquals(selected, service.currentState)
  }

  @Test
  fun returningToRecentMonthStillLoadsItsData() = runTest {
    var requests = 0
    val api = mockk<OfficialCloudApiClientInterface>()
    coEvery { api.request(any(), any(), any(), any(), any()) } answers {
      requests++
      response(data = listOf(mapOf("travelDate" to arg<Map<String, Any?>>(3)["queryMonth"])))
    }
    val service = service(api, backgroundScope)
    service.refreshTravelHistory(month = "2026-08", silent = true)
    service.refreshTravelHistory(month = "2026-09", silent = true)
    service.refreshTravelHistory(month = "2026-08", silent = true)

    assertEquals(3, requests)
    assertEquals("2026-08", service.currentState.travelDays.single().travelDate)
  }

  @Test
  fun silentRideRefreshCanReturnToAPreviouslyLoadedPeriod() = runTest {
    var requests = 0
    val api = mockk<OfficialCloudApiClientInterface>()
    coEvery { api.request(any(), any(), any(), any(), any()) } answers {
      requests++
      response(data = mapOf("dayMileage" to arg<Map<String, Any?>>(3)["model"]))
    }
    val service = service(api, backgroundScope)

    service.refreshRideStatistics(OfficialRidePeriod.DAY, silent = true)
    service.refreshRideStatistics(OfficialRidePeriod.WEEK)
    service.refreshRideStatistics(OfficialRidePeriod.DAY, silent = true)

    assertEquals(3, requests)
    assertEquals(OfficialRidePeriod.DAY, service.currentState.ridePeriod)
    assertEquals("days", service.currentState.rideStatistics?.dayMileage)
    assertFalse(service.currentState.rideStatisticsLoading)
  }

  @Test
  fun silentRideRefreshDoesNotInvalidateForegroundSelection() = runTest {
    val week = CompletableDeferred<OfficialCloudApiResponse>()
    var requests = 0
    val api = mockk<OfficialCloudApiClientInterface>()
    coEvery { api.request(any(), any(), any(), any(), any()) } coAnswers {
      requests++
      week.await()
    }
    val service = service(api, backgroundScope)
    val foreground = async { service.refreshRideStatistics(OfficialRidePeriod.WEEK) }
    testScheduler.runCurrent()

    service.refreshRideStatistics(OfficialRidePeriod.DAY, silent = true)

    assertEquals(1, requests)
    assertEquals(OfficialRidePeriod.WEEK, service.currentState.ridePeriod)
    assertTrue(service.currentState.rideStatisticsLoading)
    week.complete(response(data = mapOf("weekMileage" to "7000")))
    foreground.await()
    assertEquals("7000", service.currentState.rideStatistics?.weekMileage)
    assertFalse(service.currentState.rideStatisticsLoading)
  }

  @Test
  fun lateBackgroundRideStatisticsCannotReplaceForegroundResult() = runTest {
    val day = CompletableDeferred<OfficialCloudApiResponse>()
    val api = mockk<OfficialCloudApiClientInterface>()
    coEvery { api.request(any(), any(), any(), any(), any()) } coAnswers {
      if (arg<Map<String, Any?>>(3)["model"] == "days") day.await()
      else response(data = mapOf("weekMileage" to "7000"))
    }
    val service = service(api, backgroundScope)
    val background = async { service.refreshRideStatistics(OfficialRidePeriod.DAY, silent = true) }
    testScheduler.runCurrent()
    service.refreshRideStatistics(OfficialRidePeriod.WEEK)
    val selected = service.currentState

    day.complete(response(data = mapOf("dayMileage" to "1000")))
    background.await()

    assertEquals(selected, service.currentState)
  }

  @Test
  fun queuedRideRefreshCannotOvertakeANewerPeriodSelection() = runTest {
    val day = CompletableDeferred<OfficialCloudApiResponse>()
    var dayRequests = 0
    val api = mockk<OfficialCloudApiClientInterface>()
    coEvery { api.request(any(), any(), any(), any(), any()) } coAnswers {
      if (arg<Map<String, Any?>>(3)["model"] == "days") {
        dayRequests++
        day.await()
      } else {
        response(data = mapOf("monthsMileage" to "30000"))
      }
    }
    val service = service(api, backgroundScope)
    val first = async { service.refreshRideStatistics(OfficialRidePeriod.DAY) }
    testScheduler.runCurrent()
    val queued = async { service.refreshRideStatistics(OfficialRidePeriod.DAY, force = true) }
    testScheduler.runCurrent()
    service.refreshRideStatistics(OfficialRidePeriod.MONTH)
    val selected = service.currentState

    day.complete(response(data = mapOf("dayMileage" to "1000")))
    first.await()
    queued.await()

    assertEquals(1, dayRequests)
    assertEquals(OfficialRidePeriod.MONTH, selected.ridePeriod)
    assertEquals(selected, service.currentState)
  }

  @Test
  fun silentRefreshPropagatesCancellationWithoutRecordingAnError() = runTest {
    val api = mockk<OfficialCloudApiClientInterface>()
    coEvery { api.request(any(), any(), any(), any(), any()) } throws CancellationException("cancelled")
    val service = service(api, backgroundScope)
    val request = async { service.refreshBatteryInfo(silent = true) }
    try {
      request.await()
    } catch (_: CancellationException) {
      // Cancellation must remain cancellation, rather than a successful refresh.
    }

    assertTrue(request.isCancelled)
    assertNull(service.currentState.batteryInfoError)
  }
}
