package com.tailg.plus.ui.regression

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "zh-rCN-w411dp-h891dp")
abstract class ComposeRegressionTest {
    @get:Rule val compose = createComposeRule()
    internal lateinit var environment: UiTestEnvironment
    protected val viewModels = ViewModelStore()
    private var visible by mutableStateOf(true)

    @Before
    fun createEnvironment() {
        environment = UiTestEnvironment()
    }

    protected fun <T : ViewModel> retain(viewModel: T): T = viewModel.also {
        viewModels.put(viewModel.javaClass.name, viewModel)
    }

    protected fun render(content: @Composable () -> Unit) {
        compose.setContent { MaterialTheme { if (visible) content() } }
    }

    protected fun leaveScreen() {
        compose.runOnIdle { visible = false }
        compose.waitForIdle()
    }

    protected fun showScreen() {
        compose.runOnIdle { visible = true }
        compose.waitForIdle()
    }

    protected fun string(@StringRes id: Int): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id)

    protected fun button(@StringRes id: Int) = compose.onNode(hasText(string(id)) and hasClickAction())

    @After
    fun closeEnvironment() {
        try {
            leaveScreen()
            compose.runOnIdle { viewModels.clear() }
            assertEquals(
                "Unexpected API calls must fail even when a background refresh catches their exceptions",
                emptyList<ScriptedCloudApi.Request>(), environment.api.unexpectedRequests,
            )
        } finally {
            environment.close()
        }
    }
}
