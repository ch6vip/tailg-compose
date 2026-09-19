package com.tailg.plus.ui.screens

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.tailg.plus.R
import com.tailg.plus.data.preferences.AppPreferencesService
import com.tailg.plus.ui.navigation.Routes
import com.tailg.plus.ui.theme.LocalCyberPalette
import com.tailg.plus.ui.theme.NinebotDarkColorScheme
import com.tailg.plus.ui.theme.NinebotLightColorScheme
import com.tailg.plus.ui.theme.toCyberPalette
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
class SettingsScreenTest {
  @get:Rule val compose = createComposeRule()
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  @Test
  fun vehicleAndBatteryLeadTheHubAndKeepPreferenceRoutes() {
    val navigations = mutableListOf<String>()
    render(dark = false, onNavigate = { navigations += it })

    compose.onNodeWithText(context.getString(R.string.settings_core_section)).assertIsDisplayed()
    compose.onNodeWithText(context.getString(R.string.settings_vehicle_settings)).assertIsDisplayed()
    compose.onNodeWithText(context.getString(R.string.settings_battery_bms)).assertIsDisplayed()
    compose.onNodeWithText(context.getString(R.string.settings_preferences_section)).assertIsDisplayed()

    compose.onNodeWithText(context.getString(R.string.settings_vehicle_settings)).performClick()
    compose.onNodeWithText(context.getString(R.string.settings_battery_bms)).performClick()
    compose.onNodeWithText(context.getString(R.string.settings_my_vehicle)).performClick()
    compose.onNodeWithText(context.getString(R.string.settings_language_setting)).performScrollTo().performClick()
    compose.onNodeWithText(context.getString(R.string.settings_theme)).performScrollTo().performClick()

    assertEquals(
      listOf(
        Routes.vehicleSettings("vehicle-a"),
        Routes.batteryDetails("vehicle-a"),
        Routes.GARAGE,
        Routes.LANGUAGE_SETTINGS,
        Routes.THEME,
      ),
      navigations,
    )
  }

  @Test
  fun darkThemeKeepsCoreAndPreferenceCopyReadable() {
    render(dark = true)
    compose.onNodeWithText(context.getString(R.string.settings_core_section)).assertIsDisplayed()
    compose.onNodeWithText(context.getString(R.string.settings_vehicle_settings_short)).assertIsDisplayed()
    compose.onNodeWithText(context.getString(R.string.settings_battery_bms_short)).assertIsDisplayed()
    compose.onNodeWithText(context.getString(R.string.settings_follow_system_font)).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun tabRootHidesTheBackControl() {
    render(dark = false, showBack = false)
    compose.onNodeWithContentDescription(context.getString(R.string.common_back)).assertDoesNotExist()
  }

  private fun render(
    dark: Boolean,
    showBack: Boolean = true,
    onNavigate: (String) -> Unit = {},
  ) {
    val scheme = if (dark) NinebotDarkColorScheme else NinebotLightColorScheme
    val preferences = AppPreferencesService(context)
    compose.setContent {
      CompositionLocalProvider(LocalCyberPalette provides scheme.toCyberPalette()) {
        MaterialTheme(colorScheme = scheme) {
          SettingsScreen(
            vehicleRouteId = "vehicle-a",
            onBack = {},
            onNavigate = onNavigate,
            preferencesService = preferences,
            showBack = showBack,
          )
        }
      }
    }
  }
}
