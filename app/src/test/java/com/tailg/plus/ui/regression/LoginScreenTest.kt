package com.tailg.plus.ui.regression

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.tailg.plus.R
import com.tailg.plus.data.cloud.OfficialCloudApiResponse
import com.tailg.plus.ui.screens.LoginScreen
import io.mockk.coVerify
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LoginScreenTest : ComposeRegressionTest() {
    private val navigations = mutableListOf<String?>()

    @Before
    fun setUp() {
        render {
            LoginScreen(
                cloudService = environment.cloud,
                log = environment.log,
                clipboard = environment.clipboard,
                onSignedIn = { navigations += it },
            )
        }
    }

    @Test
    fun invalidPhoneAndSmsCannotSubmit() {
        button(R.string.login_button).assertIsNotEnabled()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("138")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("12")
        button(R.string.login_button).performScrollTo().assertIsNotEnabled().performClick()
        button(R.string.login_get_code).assertIsNotEnabled()

        assertTrue(environment.api.requests.isEmpty())
        assertTrue(navigations.isEmpty())

        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("13800000000")
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("123456")
        button(R.string.login_button).assertIsEnabled()
    }

    @Test
    fun smsLoginSubmitsEnteredCredentialsAndNavigatesOnce() {
        val login = CompletableDeferred<OfficialCloudApiResponse>()
        environment.api.respond = { request ->
            when (request.path) {
                "app/login" -> login.await()
                "app/centralControl/carStatus" -> cloudResponse(emptyList<Any>())
                "app/getUserProfile" -> cloudResponse(mapOf("id" to "sms-user"))
                "app/mine/batteryInfo" -> cloudResponse()
                else -> environment.api.unexpected(request)
            }
        }
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("13800000000")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("123456")
        button(R.string.login_button).performScrollTo().performClick()
        button(R.string.login_use_token).assertIsNotEnabled()
        assertTrue(navigations.isEmpty())
        val request = environment.api.requests.single()
        assertEquals("app/login", request.path)
        assertEquals("POST", request.method)
        assertEquals("13800000000", request.body?.get("phone"))
        assertEquals("123456", request.body?.get("smsCode"))

        compose.runOnIdle {
            login.complete(OfficialCloudApiResponse(
                200, mapOf("authorization" to "sms-token"), mapOf("code" to 200, "data" to mapOf("id" to "sms-user")),
            ))
        }
        compose.waitForIdle()

        assertEquals(listOf(string(R.string.login_success)), navigations)
        assertEquals("sms-token", environment.cloud.currentState.token)
        coVerify(exactly = 1) { environment.storage.saveCredentials("sms-token", "13800000000", any()) }
    }

    @Test
    fun doubleTapTokenLoginWaitsForVerificationAndNavigatesOnce() {
        val verification = CompletableDeferred<OfficialCloudApiResponse>()
        environment.api.respond = { request ->
            when (request.path) {
                "app/centralControl/carStatus" -> verification.await()
                "app/getUserProfile" -> cloudResponse(mapOf("id" to "token-user"))
                "app/mine/batteryInfo" -> cloudResponse()
                else -> environment.api.unexpected(request)
            }
        }
        openTokenLogin("Authorization: Bearer test-token")
        button(R.string.login_token_button).performScrollTo().performTouchInput {
            click()
            click()
        }
        button(R.string.login_use_sms).assertIsNotEnabled()
        assertEquals(1, environment.api.requests.size)
        assertTrue(navigations.isEmpty())
        coVerify(exactly = 0) { environment.storage.saveCredentials(any(), any(), any()) }

        compose.runOnIdle { verification.complete(cloudResponse(emptyList<Any>())) }
        compose.waitForIdle()

        assertEquals(listOf(string(R.string.login_token_success)), navigations)
        assertEquals("test-token", environment.cloud.currentState.token)
        assertFalse(environment.cloud.currentState.loading)
    }

    @Test
    fun rejectedTokenStaysOnLoginWithVisibleError() {
        environment.api.respond = {
            OfficialCloudApiResponse(200, emptyMap(), mapOf("code" to 500, "msg" to "测试登录被拒绝"))
        }
        openTokenLogin("rejected-token")
        button(R.string.login_token_button).performScrollTo().performClick()

        compose.onNodeWithText("测试登录被拒绝").assertIsDisplayed()
        assertTrue(navigations.isEmpty())
        assertFalse(environment.cloud.currentState.signedIn)
    }

    @Test
    fun leavingLoginCancelsVerificationAndIgnoresLateResponse() {
        val verification = CompletableDeferred<OfficialCloudApiResponse>()
        environment.api.respond = { verification.await() }
        openTokenLogin("pending-token")
        button(R.string.login_token_button).performScrollTo().performClick()
        button(R.string.login_use_sms).assertIsNotEnabled()

        leaveScreen()
        compose.runOnIdle { verification.complete(cloudResponse(emptyList<Any>())) }
        compose.waitForIdle()

        assertEquals(1, environment.api.cancelledRequests.size)
        assertFalse(environment.cloud.currentState.signedIn)
        assertFalse(environment.cloud.currentState.loading)
        assertTrue(navigations.isEmpty())
        assertFalse(environment.log.all.any { it.message == "Token 登录失败" })
        coVerify(exactly = 0) { environment.storage.saveCredentials(any(), any(), any()) }
    }

    private fun openTokenLogin(token: String) {
        button(R.string.login_use_token).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput(token)
    }
}
