package com.tailg.plus.ui.components

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppPressableTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun labelledControlKeepsClickAndSelectionWithoutInventingALongClick() {
    var clicks = 0
    compose.setContent {
      AppPressable(
        onClick = { clicks++ },
        semanticsLabel = "Choose day",
        semanticsSelected = true,
        haptic = false,
      ) { Text("Day") }
    }

    compose.onNodeWithContentDescription("Choose day")
      .assertIsSelected()
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
      .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
      .performSemanticsAction(SemanticsActions.OnClick) { it() }
    assertEquals(1, clicks)
    compose.onNodeWithText("Day").assertDoesNotExist()
  }

  @Test
  fun disabledLabelledControlRejectsAccessibilityAndTouchActions() {
    var clicks = 0
    var longClicks = 0
    compose.setContent {
      AppPressable(
        onClick = { clicks++ },
        onLongPress = { longClicks++ },
        modifier = Modifier.size(64.dp),
        enabled = false,
        semanticsLabel = "Disabled action",
        haptic = false,
      ) {}
    }

    compose.onNodeWithContentDescription("Disabled action")
      .assertIsNotEnabled()
      .performSemanticsAction(SemanticsActions.OnClick) { it() }
      .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
      .performTouchInput { click() }
    assertEquals(0, clicks)
    assertEquals(0, longClicks)
  }

  @Test
  fun unlabelledControlPublishesItsSelectionAndDisabledSemantics() {
    compose.setContent {
      AppPressable(
        onClick = {},
        semanticsSelected = true,
        semanticsEnabled = false,
        haptic = false,
      ) { Text("Selected period") }
    }

    compose.onNodeWithText("Selected period")
      .assertIsSelected()
      .assertIsNotEnabled()
      .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
  }
}
