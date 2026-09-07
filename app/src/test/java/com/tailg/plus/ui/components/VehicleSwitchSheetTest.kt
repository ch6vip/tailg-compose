package com.tailg.plus.ui.components

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tailg.plus.data.model.OfficialVehicle
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VehicleSwitchSheetTest {
  @get:Rule val compose = createComposeRule()
  private val first = OfficialVehicle(carId = "first", carName = "First vehicle")
  private val second = OfficialVehicle(carId = "second", carName = "Second vehicle")

  @Test
  fun choosingAVehicleInvokesSelectionAndDismissesAfterSuccess() {
    val selected = mutableListOf<String>()
    var dismissals = 0
    render(onSelect = { selected += it.key; true }, onDismiss = { dismissals++ })

    compose.onNodeWithText(first.displayName).assertIsSelected()
    compose.onNodeWithText(second.displayName).performClick()
    compose.runOnIdle {
      assertEquals(listOf(second.key), selected)
      assertEquals(1, dismissals)
    }
  }

  @Test
  fun failedSelectionCanBeRetriedWithoutDismissingTheSheet() {
    var attempts = 0
    var dismissals = 0
    render(
      onSelect = { if (++attempts == 1) error("selection failed") else true },
      onDismiss = { dismissals++ },
    )

    compose.onNodeWithText(second.displayName).performClick()
    compose.runOnIdle {
      assertEquals(1, attempts)
      assertEquals(0, dismissals)
    }
    compose.onNodeWithText(second.displayName).assertIsEnabled().performClick()
    compose.runOnIdle {
      assertEquals(2, attempts)
      assertEquals(1, dismissals)
    }
  }

  @Test
  fun pendingSelectionBlocksOtherVehiclesUntilItFinishes() {
    val result = CompletableDeferred<Boolean>()
    val selected = mutableListOf<String>()
    render(onSelect = { selected += it.key; result.await() }, onDismiss = {})

    compose.onNodeWithText(second.displayName).performClick()
    compose.onNodeWithText(first.displayName).assertIsNotEnabled().performClick()
    compose.runOnIdle {
      assertEquals(listOf(second.key), selected)
      result.complete(false)
    }
    compose.onNodeWithText(first.displayName).assertIsEnabled()
    compose.onNodeWithText(second.displayName).assertIsEnabled()
  }

  private fun render(onSelect: suspend (OfficialVehicle) -> Boolean, onDismiss: () -> Unit) {
    compose.setContent {
      MaterialTheme {
        VehicleSwitchSheet(
          vehicles = listOf(first, second),
          selectedKey = first.key,
          onSelect = onSelect,
          onDismiss = onDismiss,
        )
      }
    }
  }
}
