package com.tailg.plus.ui.components

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import com.tailg.plus.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "zh-rCN-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SlidePowerButtonTest {
  @get:Rule val compose = createComposeRule()
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  @Test
  fun failedCallbackRestoresTheButtonForAnotherAttempt() {
    var calls = 0
    compose.setContent {
      MaterialTheme {
        SlidePowerButton(isPowered = false, onSlide = { calls++; error("send failed") })
      }
    }
    val label = context.getString(R.string.slide_power_slide_on)
    compose.onNodeWithContentDescription(label).performSemanticsAction(SemanticsActions.OnClick) { it() }
    compose.waitForIdle()
    compose.onNodeWithContentDescription(label).performSemanticsAction(SemanticsActions.OnClick) { it() }
    compose.waitForIdle()
    assertEquals(2, calls)
  }

  @Test
  fun dragUsesTheLatestCallbackAfterRecomposition() {
    var oldCalls = 0
    var newCalls = 0
    val callback = mutableStateOf<suspend () -> Unit>({ oldCalls++ })
    compose.setContent {
      MaterialTheme {
        SlidePowerButton(isPowered = false, onSlide = callback.value, modifier = Modifier.testTag("power"))
      }
    }
    compose.runOnIdle { callback.value = { newCalls++ } }
    compose.onNodeWithTag("power").performTouchInput { swipeRight() }
    compose.waitForIdle()
    assertEquals(0, oldCalls)
    assertEquals(1, newCalls)
  }
}
