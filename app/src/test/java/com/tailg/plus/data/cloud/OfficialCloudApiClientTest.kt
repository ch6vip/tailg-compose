package com.tailg.plus.data.cloud

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OfficialCloudApiClientTest {
  private fun response(chain: Interceptor.Chain, status: Int = 200, body: String = """{"code":200}""") =
    Response.Builder()
      .request(chain.request())
      .protocol(Protocol.HTTP_1_1)
      .code(status)
      .message("test")
      .body(body.toResponseBody())
      .build()

  @Test
  fun mutationDoesNotRetryWhenResponseIsLost() = runBlocking {
    val attempts = AtomicInteger()
    val http = OkHttpClient.Builder().addInterceptor {
      attempts.incrementAndGet()
      throw IOException("response lost after server accepted command")
    }.build()
    val client = OfficialCloudApiClient(okHttpClient = http)
    try {
      try {
        client.request("app/device/cmd/unlock", method = "POST")
        fail("Expected a transport failure")
      } catch (_: OfficialCloudApiException) {
        assertEquals(1, attempts.get())
      }
    } finally {
      client.dispose()
    }
  }

  @Test
  fun readPolicyStillRetriesTemporaryServerFailure() = runBlocking {
    val attempts = AtomicInteger()
    val http = OkHttpClient.Builder().addInterceptor { chain ->
      if (attempts.incrementAndGet() == 1) response(chain, 503, "gateway unavailable") else response(chain)
    }.build()
    val client = OfficialCloudApiClient(
      config = OfficialCloudApiConfig(retryBaseDelay = kotlin.time.Duration.ZERO),
      okHttpClient = http,
    )
    try {
      val result = client.request("app/centralControl/carStatus", "POST", retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST)
      assertEquals(200, result.statusCode)
      assertEquals(2, attempts.get())
    } finally {
      client.dispose()
    }
  }

  @Test
  fun cancellationCancelsCallWithoutWaitingForResponse() = runBlocking {
    val entered = CompletableDeferred<Call>()
    val release = CountDownLatch(1)
    val http = OkHttpClient.Builder().addInterceptor { chain ->
      entered.complete(chain.call())
      check(release.await(5, TimeUnit.SECONDS))
      response(chain)
    }.build()
    val client = OfficialCloudApiClient(okHttpClient = http)
    try {
      val request = async { client.request("app/centralControl/carStatus", "POST") }
      val call = withTimeout(2.seconds) { entered.await() }
      withTimeout(1.seconds) { request.cancelAndJoin() }

      assertTrue(call.isCanceled())
    } finally {
      release.countDown()
      client.dispose()
    }
  }

  @Test
  fun simultaneousRequestsHonorDispatcherHostLimit() = runBlocking {
    val entered = CountDownLatch(2)
    val release = CountDownLatch(1)
    val dispatcher = okhttp3.Dispatcher().apply { maxRequestsPerHost = 2 }
    val http = OkHttpClient.Builder().dispatcher(dispatcher).addInterceptor { chain ->
      entered.countDown()
      check(release.await(5, TimeUnit.SECONDS))
      response(chain)
    }.build()
    val client = OfficialCloudApiClient(okHttpClient = http)
    try {
      val requests = List(4) {
        async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
          client.request("app/centralControl/carStatus", "POST")
        }
      }
      assertTrue(entered.await(2, TimeUnit.SECONDS))
      assertEquals(2, dispatcher.runningCallsCount())
      assertEquals(2, dispatcher.queuedCallsCount())
      release.countDown()
      requests.forEach { assertEquals(200, it.await().statusCode) }
    } finally {
      release.countDown()
      client.dispose()
    }
  }
}
