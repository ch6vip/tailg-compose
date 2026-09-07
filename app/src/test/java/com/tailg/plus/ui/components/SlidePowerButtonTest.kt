package com.tailg.plus.ui.components

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tailg.plus.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

  @Test
  fun rtlKeepsThumbInsideTheTrackAndPreservesPhysicalPowerDirections() {
    val powered = mutableStateOf(false)
    val sentStates = mutableListOf<Boolean>()
    compose.setContent {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme {
          SlidePowerButton(
            isPowered = powered.value,
            trackWidth = 200.dp,
            onSlide = {
              val next = !powered.value
              sentStates += next
              powered.value = next
            },
          )
        }
      }
    }

    assertThumbAtPhysicalEnd(powered = false)
    compose.onNodeWithTag("slide-power-track").performTouchInput { swipeRight() }
    compose.mainClock.advanceTimeBy(1_200)
    compose.waitForIdle()
    assertEquals(listOf(true), sentStates)
    compose.onNodeWithContentDescription(context.getString(R.string.slide_power_slide_off)).assertExists()
    assertThumbAtPhysicalEnd(powered = true)

    compose.onNodeWithTag("slide-power-track").performTouchInput { swipeLeft() }
    compose.mainClock.advanceTimeBy(1_200)
    compose.waitForIdle()
    assertEquals(listOf(true, false), sentStates)
    compose.onNodeWithContentDescription(context.getString(R.string.slide_power_slide_on)).assertExists()
    assertThumbAtPhysicalEnd(powered = false)
  }

  private fun assertThumbAtPhysicalEnd(powered: Boolean) {
    val track = compose.onNodeWithTag("slide-power-track").fetchSemanticsNode().boundsInRoot
    val thumb = compose.onNodeWithTag("slide-power-thumb").fetchSemanticsNode().boundsInRoot
    assertTrue("RTL must not move the power thumb outside its track", thumb.left >= track.left - 0.5f && thumb.right <= track.right + 0.5f)
    if (powered) assertEquals("Power off begins at the physical right end", track.right, thumb.right, 0.5f)
    else assertEquals("Power on begins at the physical left end", track.left, thumb.left, 0.5f)
  }
}
