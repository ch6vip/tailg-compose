package com.tailg.plus.data.cloud

import com.tailg.plus.data.model.AffirmBatteryInfoRequest
import com.tailg.plus.data.model.OfficialVehicle
import io.mockk.mockk
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialCloudMutationRetryTest {
  @Test
  fun serviceMutationsAreSentOnceWhenTheServerResponseIsLost() = runBlocking {
    val mutations: List<suspend (OfficialCloudService) -> Unit> = listOf(
      { it.affirmBatteryInfo(AffirmBatteryInfoRequest("car")) },
      { it.updateFenceData(true, 500, "00:00", "23:59") },
      { it.updateCarNickName("car", "new name") },
      { it.setMessagePushConfig(mapOf("alarm" to true)) },
      { it.deleteMessages() },
    )
    for (mutation in mutations) {
      val attempts = AtomicInteger()
      val http = OkHttpClient.Builder().addInterceptor {
        attempts.incrementAndGet()
        throw IOException("response lost after server accepted the mutation")
      }.build()
      val client = OfficialCloudApiClient(
        config = OfficialCloudApiConfig(retryBaseDelay = Duration.ZERO),
        okHttpClient = http,
      )
      val vehicle = OfficialVehicle(carId = "car", imei = "imei")
      val service = OfficialCloudService(mockk(relaxed = true), client, mockk(relaxed = true), scope = this)
      service.setStateForTest(OfficialCloudState.initial().copyWith(
        initialized = true, token = "session", vehicles = listOf(vehicle), selectedVehicleKey = vehicle.key,
      ))
      try {
        assertTrue(runCatching { mutation(service) }.isFailure)
        assertEquals(1, attempts.get())
      } finally {
        client.dispose()
      }
    }
  }
}
