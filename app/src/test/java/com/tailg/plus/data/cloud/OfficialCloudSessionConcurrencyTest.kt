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
    fun mqttStatusKeepsTheSessionAndVehicleItWasValidatedAgainst() = runTest {
        val service = OfficialCloudService(storage, api, mockk(relaxed = true), scope = backgroundScope)
        val selected = OfficialVehicle(carId = "b", acc = 0, defenceStatus = 1)
        val state = OfficialCloudState.initial().copyWith(
            initialized = true, token = "new", vehicles = listOf(selected), selectedVehicleKey = selected.key,
        )
        service.setStateForTest(state)

        service.applyMqttVehicleStatus(1, 0, token = "old", vehicleKey = selected.key, sessionGeneration = state.sessionGeneration)
        assertEquals(state, service.currentState)
        service.applyMqttVehicleStatus(1, 0, token = "new", vehicleKey = "a", sessionGeneration = state.sessionGeneration)
        assertEquals(state, service.currentState)
        service.applyMqttVehicleStatus(1, 0, token = "new", vehicleKey = selected.key, sessionGeneration = state.sessionGeneration)
        assertEquals(1, service.currentState.selectedVehicle?.acc)
        assertEquals(0, service.currentState.selectedVehicle?.defenceStatus)
    }

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
        oldResult.completeExceptionally(OfficialCloudApiException("登录已失效", statusCode = 401))

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
        result.completeExceptionally(OfficialCloudApiException("登录已失效", statusCode = 401))
        request.await()

        assertEquals("new", service.currentState.token)
        coVerify(exactly = 0) { storage.clearCredentialsAndSelection() }
    }

    @Test
    fun sameTokenReloginRejectsOldAuthenticationFailure() = runTest {
        val oldResult = CompletableDeferred<OfficialCloudApiResponse>()
        coEvery { api.request(any(), any(), any(), any(), any()) } coAnswers {
            if (arg<String>(0) == "app/msg/getMessageControl") oldResult.await() else response()
        }
        val service = OfficialCloudService(storage, api, mockk(relaxed = true), scope = backgroundScope)
        service.setStateForTest(OfficialCloudState.initial().copyWith(initialized = true, token = "same-token"))
        val oldRequest = async { runCatching { service.getMessageControl() } }
        testScheduler.runCurrent()
        service.logout()
        service.loginWithToken("same-token")
        oldResult.completeExceptionally(OfficialCloudApiException("登录已失效", statusCode = 401))
        oldRequest.await()

        assertEquals("same-token", service.currentState.token)
        assertTrue(service.currentState.signedIn)
    }

    @Test
    fun sameTokenReloginRejectsOldProfileResponse() = runTest {
        val oldResult = CompletableDeferred<OfficialCloudApiResponse>()
        var profileReads = 0
        coEvery { api.request(any(), any(), any(), any(), any()) } coAnswers {
            if (arg<String>(0) == "app/getUserProfile") {
                if (++profileReads == 1) oldResult.await() else response(mapOf("nickName" to "current"))
            } else response()
        }
        val service = OfficialCloudService(storage, api, mockk(relaxed = true), scope = backgroundScope)
        service.setStateForTest(OfficialCloudState.initial().copyWith(initialized = true, token = "same-token"))
        val oldRequest = async { service.refreshUserProfile(force = true) }
        testScheduler.runCurrent()
        service.logout()
        service.loginWithToken("same-token")
        oldResult.complete(response(mapOf("nickName" to "stale")))
        oldRequest.await()

        assertEquals("current", service.currentState.userProfile?.nickName)
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
