package com.tailg.plus.data.cloud

import com.tailg.plus.data.model.parsePersistedMap
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialCloudMessageOperationsTest {
    private fun response(json: String = """{"code":200,"data":[]}""") =
        OfficialCloudApiResponse(200, emptyMap(), requireNotNull(parsePersistedMap(CloudJson.decode(json))))

    @Test
    fun numericNotificationFlagsSurviveTheRealJsonDecoder() = runTest {
        val api = mockk<OfficialCloudApiClientInterface>()
        coEvery { api.request(any(), any(), any(), any(), any()) } returns response(
            """{"code":200,"data":{"alarm":1,"marketing":0,"battery":"1","system":true,"ride":false}}""",
        )
        val service = OfficialCloudService(mockk(relaxed = true), api, mockk(relaxed = true), scope = backgroundScope)
        service.setStateForTest(OfficialCloudState.initial().copyWith(initialized = true, token = "session"))

        assertEquals(mapOf("alarm" to true, "marketing" to false, "battery" to true, "system" to true, "ride" to false),
            service.getMessageControl())
    }

    @Test
    fun notificationPreferencesFromPreviousAccountAreRejected() = runTest {
        val result = CompletableDeferred<OfficialCloudApiResponse>()
        val api = mockk<OfficialCloudApiClientInterface>()
        coEvery { api.request(any(), any(), any(), any(), any()) } coAnswers { result.await() }
        val service = OfficialCloudService(mockk(relaxed = true), api, mockk(relaxed = true), scope = backgroundScope)
        service.setStateForTest(OfficialCloudState.initial().copyWith(initialized = true, token = "old"))
        val request = async { runCatching { service.getMessageControl() } }
        testScheduler.runCurrent()
        service.setStateForTest(OfficialCloudState.initial().copyWith(initialized = true, token = "new"))
        result.complete(response("""{"code":200,"data":{"alarm":1}}"""))

        assertTrue(request.await().isFailure)
        assertEquals("new", service.currentState.token)
    }

    @Test
    fun queuedMessageRefreshCannotSetLoadingOrSendWithAnExpiredSession() = runTest {
        val firstResponses = CompletableDeferred<OfficialCloudApiResponse>()
        val laterResponses = CompletableDeferred<OfficialCloudApiResponse>()
        var requests = 0
        val api = mockk<OfficialCloudApiClientInterface>()
        coEvery { api.request(any(), any(), any(), any(), any()) } coAnswers {
            requests++
            if (requests <= 2) firstResponses.await() else laterResponses.await()
        }
        val service = OfficialCloudService(mockk(relaxed = true), api, mockk(relaxed = true), scope = backgroundScope)
        service.setStateForTest(OfficialCloudState.initial().copyWith(initialized = true, token = "old"))
        val first = async { service.refreshMessages() }
        testScheduler.runCurrent()
        val queued = async { service.refreshMessages() }
        testScheduler.runCurrent()
        val newSession = OfficialCloudState.initial().copyWith(initialized = true, token = "new", userId = "new-user")
        service.setStateForTest(newSession)
        firstResponses.complete(response())
        testScheduler.runCurrent()
        val stateWhileQueued = service.currentState
        laterResponses.complete(response())
        first.await()
        queued.await()

        assertEquals(newSession, stateWhileQueued)
        assertEquals(newSession, service.currentState)
        coVerify(exactly = 2) { api.request(any(), any(), any(), any(), any()) }
    }
}
