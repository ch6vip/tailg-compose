package com.tailg.plus.ui.screens

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tailg.plus.R
import com.tailg.plus.data.preferences.AppPreferencesService
import com.tailg.plus.ui.components.BottomNavigationScaffold
import com.tailg.plus.ui.components.TailgBottomNavigation
import com.tailg.plus.ui.navigation.Routes
import com.tailg.plus.ui.theme.ColorMode
import com.tailg.plus.ui.theme.LocalCyberPalette
import com.tailg.plus.ui.theme.LocalUiMode
import com.tailg.plus.ui.theme.NinebotDarkColorScheme
import com.tailg.plus.ui.theme.NinebotLightColorScheme
import com.tailg.plus.ui.theme.TailgTypography
import com.tailg.plus.ui.theme.UiMode
import com.tailg.plus.ui.theme.VectorDarkColorScheme
import com.tailg.plus.ui.theme.VectorLightColorScheme
import com.tailg.plus.ui.theme.VectorShapes
import com.tailg.plus.ui.theme.VectorTypography
import com.tailg.plus.ui.theme.toCyberPalette
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VectorSettingsTest {
  @get:Rule val compose = createComposeRule()
  private val context: Context get() = ApplicationProvider.getApplicationContext()
  private val destination = mutableStateOf(Routes.SETTINGS)
  private val navigations = mutableListOf<String>()

  @Before
  fun disableSystemAnimations() {
    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
  }

  @Test
  fun selectingBothStylesChangesThePageAndPreservesTheirStoredIdentifiers() {
    val preferences = preferences()
    render(preferences)
    compose.onNodeWithTag("vectorSettingsHeader").assertIsDisplayed()
    capture("settings-light")

    revealSetting(R.string.settings_ui_mode).performClick()
    compose.onNodeWithText(text(R.string.theme_ui_mode_ninebot)).assertIsDisplayed().performClick()
    compose.waitUntil { preferences.uiMode.value == UiMode.NINEBOT.value }
    compose.onNodeWithTag("vectorSettingsHeader").assertDoesNotExist()
    assertEquals("Choosing Ninebot must retain the existing persisted identifier", 2, restoredPreferences().uiMode.value)

    revealSetting(R.string.settings_ui_mode).performClick()
    compose.onNodeWithText(text(R.string.theme_ui_mode_vector)).assertIsDisplayed().performClick()
    compose.waitUntil { preferences.uiMode.value == UiMode.VECTOR.value }
    assertEquals("Choosing VECTOR must preserve the original skin's persisted identifier", 0, restoredPreferences().uiMode.value)
    scrollBy(-100_000f)
    compose.onNodeWithTag("vectorSettingsHeader").assertIsDisplayed()
    compose.onNodeWithTag("vectorAppearanceCard").assertIsDisplayed()
  }

  @Test
  fun appearanceCardOpensTheActualPreviewAndSavedChangesApplyWhenReturning() {
    val preferences = preferences()
    render(preferences)
    compose.onNodeWithTag("vectorAppearanceCard").assertIsDisplayed().performClick()
    assertEquals(listOf(Routes.THEME), navigations)
    compose.onNodeWithTag("floating-bottom-bar").assertDoesNotExist()
    compose.onNodeWithText("VECTOR").assertIsDisplayed()
    compose.onNodeWithTag("theme-preview-floating-bar").assertIsDisplayed()
    capture("theme-preview-light")

    compose.onNodeWithText(text(R.string.theme_mode_dark)).performScrollTo().performClick()
    compose.waitUntil { preferences.themeMode.value == ColorMode.DARK.value }
    assertEquals(ColorMode.DARK.value, restoredPreferences().themeMode.value)
    compose.onNodeWithTag("theme-floating-bottom-bar-toggle").performScrollTo().assertIsOn().performClick()
    compose.waitUntil { !preferences.floatingBottomBar.value }
    compose.onNodeWithTag("theme-floating-bottom-bar-toggle").assertIsOff()
    compose.onNodeWithTag("theme-preview-classic-bar").performScrollTo().assertIsDisplayed()
    scrollBy(-100_000f)
    compose.onNodeWithText("VECTOR").assertIsDisplayed()
    capture("theme-preview-dark")

    compose.onNodeWithContentDescription(text(R.string.common_back)).performClick()
    compose.onNodeWithTag("classic-bottom-bar").assertIsDisplayed()
    compose.onNodeWithTag("vectorSettingsHeader").assertIsDisplayed()
    val restored = restoredPreferences()
    assertFalse(restored.floatingBottomBar.value)
    assertEquals(UiMode.VECTOR.value, restored.uiMode.value)
    assertEquals(ColorMode.DARK.value, restored.themeMode.value)
    capture("settings-dark")
  }

  @Test
  @Config(qualifiers = "en-rUS-w320dp-h640dp-mdpi")
  fun finalSettingsActionFitsLargeTextAndReceivesTouchesAboveEitherBar() {
    val preferences = preferences(dark = true)
    render(preferences, fontScale = 1.5f)
    for (floatingStyle in listOf(false, true)) {
      runBlocking { preferences.setFloatingBottomBar(floatingStyle) }
      scrollBy(100_000f)
      val about = setting(R.string.settings_about_app)
        .assertIsDisplayed()
        .assertHeightIsAtLeast(48.dp)
        .assertWidthIsAtLeast(48.dp)
      val actionBounds = about.fetchSemanticsNode().boundsInRoot
      val barTag = if (floatingStyle) "floating-bottom-bar" else "classic-bottom-bar"
      val barBounds = compose.onNodeWithTag(barTag).fetchSemanticsNode().boundsInRoot
      assertTrue("The final settings action must fully clear the bottom navigation", actionBounds.bottom <= barBounds.top)
      capture(if (floatingStyle) "settings-compact-floating" else "settings-compact-classic")
      assertTextFits(text(R.string.settings_about_app))
      assertTextFits(text(R.string.settings_about_app_desc))
      about.performTouchInput { click() }
    }
    assertEquals(listOf(Routes.ABOUT_APP, Routes.ABOUT_APP), navigations)
  }

  private fun preferences(dark: Boolean = false): AppPreferencesService = AppPreferencesService(context).also {
    runBlocking {
      it.setUiMode(UiMode.VECTOR.value)
      it.setThemeMode(if (dark) ColorMode.DARK.value else ColorMode.LIGHT.value)
      it.setPageScale(1f)
      it.setRespectSystemTextScale(true)
      it.setFloatingBottomBar(true)
    }
  }

  private fun restoredPreferences(): AppPreferencesService = AppPreferencesService(context).also {
    runBlocking { it.init() }
  }

  private fun render(preferences: AppPreferencesService, fontScale: Float = 1f) {
    RuntimeEnvironment.setFontScale(fontScale)
    compose.setContent {
      val uiModeValue by preferences.uiMode.collectAsState()
      val themeModeValue by preferences.themeMode.collectAsState()
      val floatingBottomBar by preferences.floatingBottomBar.collectAsState()
      val uiMode = UiMode.fromValue(uiModeValue)
      val isDark = ColorMode.fromValue(themeModeValue).isDark
      val scheme = when (uiMode) {
        UiMode.VECTOR -> if (isDark) VectorDarkColorScheme else VectorLightColorScheme
        UiMode.NINEBOT -> if (isDark) NinebotDarkColorScheme else NinebotLightColorScheme
      }
      CompositionLocalProvider(
        LocalDensity provides Density(LocalDensity.current.density, fontScale),
        LocalUiMode provides uiMode,
        LocalCyberPalette provides scheme.toCyberPalette(),
      ) {
        MaterialTheme(
          colorScheme = scheme,
          typography = if (uiMode == UiMode.VECTOR) VectorTypography else TailgTypography,
          shapes = if (uiMode == UiMode.VECTOR) VectorShapes else Shapes(),
        ) {
          if (destination.value == Routes.THEME) {
            ThemeSettingsScreen(
              preferencesService = preferences,
              onBack = { destination.value = Routes.SETTINGS },
            )
          } else {
            BottomNavigationScaffold(
              modifier = Modifier.fillMaxSize(),
              bottomBar = {
                TailgBottomNavigation(currentIndex = 3, floating = floatingBottomBar, onSelected = {})
              },
            ) {
              SettingsScreen(
                vehicleRouteId = "vehicle-settings-test",
                preferencesService = preferences,
                showBack = false,
                onBack = {},
                onNavigate = { route ->
                  navigations += route
                  if (route == Routes.THEME) destination.value = route
                },
              )
            }
          }
        }
      }
    }
  }

  private fun setting(label: Int): SemanticsNodeInteraction =
    compose.onNode(hasText(text(label)) and hasClickAction())

  private fun revealSetting(label: Int): SemanticsNodeInteraction {
    val target = setting(label).performScrollTo()
    val bounds = target.fetchSemanticsNode().boundsInRoot
    val bar = compose.onNode(hasTestTag("floating-bottom-bar") or hasTestTag("classic-bottom-bar"))
      .fetchSemanticsNode().boundsInRoot
    val viewport = compose.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
    // Leave room below the row for its popup as well as the navigation overlay.
    val preferredBottom = viewport.top + (bar.top - viewport.top) * 0.55f
    if (bounds.bottom > preferredBottom) scrollBy(bounds.bottom - preferredBottom)
    target.assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    assertTrue("The settings action must clear the navigation bar before tapping", target.fetchSemanticsNode().boundsInRoot.bottom <= bar.top)
    return target
  }

  private fun scrollBy(distance: Float) {
    compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) {
      it(0f, distance)
    }
  }

  private fun assertTextFits(value: String) {
    val layouts = mutableListOf<TextLayoutResult>()
    compose.onNode(
      hasText(value) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult) and hasAnyAncestor(hasClickAction()),
      useUnmergedTree = true,
    )
      .assertIsDisplayed()
      .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    assertTrue("The visible setting must expose a measured text layout: $value", layouts.isNotEmpty())
    assertTrue(
      "The complete setting label must stay readable: $value; " + layouts.joinToString { layout ->
        "size=${layout.size}, paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}, " +
          "lines=${layout.lineCount}, widthOverflow=${layout.didOverflowWidth}, heightOverflow=${layout.didOverflowHeight}"
      },
      layouts.none { it.hasVisualOverflow },
    )
  }

  private fun text(id: Int): String = context.getString(id)

  private fun capture(name: String) {
    compose.waitForIdle()
    val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
    val directory = File("build/reports/vector-design").apply { mkdirs() }
    File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }
}
