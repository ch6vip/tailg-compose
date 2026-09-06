package com.tailg.plus.ui.regression

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.tailg.plus.R
import com.tailg.plus.data.cloud.OfficialCloudApiResponse
import com.tailg.plus.ui.screens.CloudTokenScreen
import com.tailg.plus.ui.screens.CloudTokenViewModel
import io.mockk.coVerify
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CloudTokenScreenTest : ComposeRegressionTest() {
    private lateinit var viewModel: CloudTokenViewModel

    @Before
    fun setUp() {
        viewModel = retain(CloudTokenViewModel(environment.cloud, environment.log, environment.clipboard))
        render { CloudTokenScreen(onBack = {}, viewModel = viewModel) }
    }

    private fun submit() = compose.onNode(
        (hasText(string(R.string.token_paste_login)) or hasText(string(R.string.token_relogin))) and hasClickAction(),
    )

    @Test
    fun emptyTokenShowsValidationMessageWithoutSendingRequest() {
        submit().performScrollTo().performClick()

        compose.onNodeWithText(string(R.string.token_vm_paste_first)).assertIsDisplayed()
        assertTrue(environment.api.requests.isEmpty())
    }

    @Test
    fun rejectedTokenShowsServerErrorAndAllowsRetry() {
        environment.api.respond = {
            OfficialCloudApiResponse(200, emptyMap(), mapOf("code" to 500, "msg" to "测试服务暂不可用"))
        }
        compose.onNode(hasSetTextAction()).performTextInput("rejected-token")
        submit().performScrollTo().performClick()

        compose.onNodeWithText("测试服务暂不可用").assertIsDisplayed()
        submit().assertIsEnabled()
        assertFalse(environment.cloud.currentState.signedIn)
        coVerify(exactly = 0) { environment.storage.saveCredentials(any(), any(), any()) }
    }

    @Test
    fun tokenIsNormalizedAndPersistedOnlyAfterVerification() {
        val verification = CompletableDeferred<OfficialCloudApiResponse>()
        environment.api.respond = { request ->
            when (request.path) {
                "app/centralControl/carStatus" -> verification.await()
                "app/getUserProfile" -> cloudResponse(mapOf("id" to "test-user"))
                "app/mine/batteryInfo" -> cloudResponse()
                else -> environment.api.unexpected(request)
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("Authorization: Bearer verified-token")
        submit().performScrollTo().performClick()
        submit().assertIsNotEnabled().performClick()
        assertEquals(listOf("verified-token"), environment.api.requests.map { it.token })
        coVerify(exactly = 0) { environment.storage.saveCredentials(any(), any(), any()) }

        compose.runOnIdle { verification.complete(cloudResponse(emptyList<Any>())) }

        compose.onNodeWithText(string(R.string.token_vm_login_success)).assertIsDisplayed()
        submit().assertIsEnabled()
        assertEquals("verified-token", environment.cloud.currentState.token)
        assertEquals("test-user", environment.cloud.currentState.userId)
        coVerify(exactly = 1) { environment.storage.saveCredentials("verified-token", "", "test-user") }
        assertEquals(1, environment.api.requests.count { it.path == "app/centralControl/carStatus" })
    }

    @Test
    fun clearingViewModelCancelsLoginWithoutReportingAnError() {
        val verification = CompletableDeferred<OfficialCloudApiResponse>()
        environment.api.respond = { verification.await() }
        compose.onNode(hasSetTextAction()).performTextInput("pending-token")
        submit().performScrollTo().performClick()
        submit().assertIsNotEnabled()

        leaveScreen()
        compose.runOnIdle { viewModels.clear() }
        compose.waitForIdle()

        assertEquals(1, environment.api.cancelledRequests.size)
        assertFalse(environment.cloud.currentState.signedIn)
        assertFalse(viewModel.uiState.value.busy)
        assertTrue(viewModel.messages.value.isEmpty())
        assertFalse(environment.log.all.any { it.message == "Token 登录失败" })
        coVerify(exactly = 0) { environment.storage.saveCredentials(any(), any(), any()) }
    }
}
