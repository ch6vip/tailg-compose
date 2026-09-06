package com.tailg.plus.data.cloud

import com.tailg.plus.data.model.OfficialBatteryInfo
import com.tailg.plus.data.model.OfficialCloudMessage
import com.tailg.plus.data.model.OfficialVehicle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OfficialCloudSessionConcurrencyTest {
    private val storage = mockk<OfficialCloudStorage>(relaxed = true)
    private val api = mockk<OfficialCloudApiClientInterface>(relaxed = true).apply {
        every { config } returns OfficialCloudApiConfig()
    }
    private fun response(data: Any = emptyMap<String, Any>()) =
        OfficialCloudApiResponse(200, emptyMap(), mapOf("code" to 200, "data" to data))

    @Test
    fun logoutDuringTokenVerificationCannotRestoreCredentials() = runTest {
        val result = CompletableDeferred<OfficialCloudApiResponse>()
        coEvery { api.request(any(), any(), any(), any(), any()) } coAnswers { result.await() }
        val service = OfficialCloudService(storage, api, mockk(relaxed = true), scope = backgroundScope)
        val login = async { runCatching { service.loginWithToken("old-token") } }
        testScheduler.runCurrent()
        service.logout()
        result.complete(response())

        assertTrue(login.await().isFailure)
        assertFalse(service.currentState.signedIn)
        coVerify(exactly = 0) { storage.saveCredentials(any(), any(), any()) }
    }

    @Test
    fun oldVerificationFailureDoesNotClearNewLogin() = runTest {
        val oldResult = CompletableDeferred<OfficialCloudApiResponse>()
        coEvery { api.request(any(), any(), any(), any(), any()) } coAnswers {
            if (arg<String?>(2) == "old-token") oldResult.await() else response()
        }
        val service = OfficialCloudService(storage, api, mockk(relaxed = true), scope = backgroundScope)
        val oldLogin = async { runCatching { service.loginWithToken("old-token") } }
        testScheduler.runCurrent()
        service.loginWithToken("new-token")
        oldResult.completeExceptionally(OfficialCloudApiException("登录已失效"))

        assertTrue(oldLogin.await().isFailure)
        assertEquals("new-token", service.currentState.token)
        coVerify(exactly = 1) { storage.saveCredentials("new-token", "", "") }
        coVerify(exactly = 0) { storage.saveCredentials("old-token", any(), any()) }
    }

    @Test
    fun cancelledVerificationClearsOnlyItsCandidate() = runTest {
        coEvery { api.request(any(), any(), any(), any(), any()) } coAnswers {
            CompletableDeferred<OfficialCloudApiResponse>().await()
        }
        val service = OfficialCloudService(storage, api, mockk(relaxed = true), scope = backgroundScope)
        val login = async { service.loginWithToken("candidate") }
        testScheduler.runCurrent()
        login.cancelAndJoin()

        assertFalse(service.currentState.signedIn)
        assertFalse(service.currentState.loading)
        coVerify(exactly = 0) { storage.saveCredentials(any(), any(), any()) }
    }

    @Test
    fun stagingNewAccountRemovesOldVehicleAndMessageData() = runTest {
        coEvery { api.request(any(), any(), any(), any(), any()) } coAnswers {
            CompletableDeferred<OfficialCloudApiResponse>().await()
        }
        val service = OfficialCloudService(storage, api, mockk(relaxed = true), scope = backgroundScope)
        service.setStateForTest(OfficialCloudState.initial().copyWith(
            initialized = true, token = "old", batteryInfo = OfficialBatteryInfo(dumpEnergyPercent = "90"),
            messagesError = "old account error", localVehicleLinks = mapOf("official" to "local"),
        ))
        val login = async { service.loginWithToken("candidate") }
        testScheduler.runCurrent()

        assertNull(service.currentState.batteryInfo)
        assertNull(service.currentState.messagesError)
        assertEquals(mapOf("official" to "local"), service.currentState.localVehicleLinks)
        login.cancelAndJoin()
    }

    @Test
    fun authErrorFromPreviousAccountCannotLogOutCurrentAccount() = runTest {
        val result = CompletableDeferred<OfficialCloudApiResponse>()
        coEvery { api.request(any(), any(), any(), any(), any()) } coAnswers { result.await() }
        val service = OfficialCloudService(storage, api, mockk(relaxed = true), scope = backgroundScope)
        service.setStateForTest(OfficialCloudState.initial().copyWith(initialized = true, token = "old"))
        val request = async { runCatching { service.getMessageControl() } }
        testScheduler.runCurrent()
        service.setStateForTest(OfficialCloudState.initial().copyWith(initialized = true, token = "new"))
        result.completeExceptionally(OfficialCloudApiException("登录已失效"))
        request.await()

        assertEquals("new", service.currentState.token)
        coVerify(exactly = 0) { storage.clearCredentialsAndSelection() }
    }

    @Test
    fun concurrentInitializationLoadsStorageOnce() = runTest {
        val stored = CompletableDeferred<OfficialCloudStoredSession>()
        coEvery { storage.loadSession() } coAnswers { stored.await() }
        val service = OfficialCloudService(storage, api, mockk(relaxed = true), scope = backgroundScope)
        val first = async { service.initForTest() }
        val second = async { service.initForTest() }
        testScheduler.runCurrent()
        stored.complete(OfficialCloudStoredSession("", "", "", null, emptyList(), emptyMap(), null))
        first.await()
        second.await()

        assertTrue(service.currentState.initialized)
        coVerify(exactly = 1) { storage.loadSession() }
    }
}
