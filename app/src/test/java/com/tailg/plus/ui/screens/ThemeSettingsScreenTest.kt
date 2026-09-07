package com.tailg.plus.ui.screens

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import com.tailg.plus.R
import com.tailg.plus.data.preferences.AppPreferencesService
import com.tailg.plus.ui.theme.ColorMode
import com.tailg.plus.ui.theme.LocalCyberPalette
import com.tailg.plus.ui.theme.NinebotDarkColorScheme
import com.tailg.plus.ui.theme.NinebotLightColorScheme
import com.tailg.plus.ui.theme.UiMode
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
class ThemeSettingsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun disableSystemAnimations() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    @Test
    fun floatingBarSwitchUpdatesPreviewAndPersistsBothChoices() {
        val preferences = preferences()
        render(preferences)
        compose.onNodeWithTag("theme-floating-bottom-bar-toggle").performScrollTo().assertIsOff().performClick()
        compose.waitUntil { preferences.floatingBottomBar.value }
        compose.onNodeWithTag("theme-floating-bottom-bar-toggle").assertIsOn()
        compose.onNodeWithTag("theme-preview-floating-bar").performScrollTo().assertIsDisplayed()
        capture("theme-ninebot-light")
        assertTrue(restoredPreferences().floatingBottomBar.value)

        compose.onNodeWithTag("theme-floating-bottom-bar-toggle").performScrollTo().performClick()
        compose.waitUntil { !preferences.floatingBottomBar.value }
        compose.onNodeWithTag("theme-preview-classic-bar").performScrollTo().assertIsDisplayed()
        assertFalse(restoredPreferences().floatingBottomBar.value)
    }

    @Test
    @Config(qualifiers = "en-w320dp-h640dp-mdpi")
    fun englishDarkThemeKeepsTheSwitchReachableWithLargeText() {
        RuntimeEnvironment.setFontScale(1.5f)
        val preferences = preferences(dark = true)
        render(preferences, dark = true)
        compose.onNodeWithTag("theme-floating-bottom-bar-toggle").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil { preferences.floatingBottomBar.value }
        compose.onNodeWithTag("theme-floating-bottom-bar-toggle").assertIsOn()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(context.getString(R.string.theme_mode_system))
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty() && layouts.none { it.hasVisualOverflow })
        capture("theme-dark-english-large-text")
    }

    @Test
    fun savingBeforeInitPreservesOtherAppearancePreferences() = runBlocking {
        val preferences = preferences(dark = true)
        preferences.setPageScale(1.05f)
        preferences.setFloatingBottomBar(true)
        val uninitialized = AppPreferencesService(context)
        uninitialized.setFloatingBottomBar(false)

        val restored = restoredPreferences()
        assertFalse(restored.floatingBottomBar.value)
        assertEquals(ColorMode.DARK.value, restored.themeMode.value)
        assertEquals(UiMode.NINEBOT.value, restored.uiMode.value)
        assertEquals(1.05f, restored.pageScale.value, 0f)
    }

    private fun preferences(dark: Boolean = false): AppPreferencesService = AppPreferencesService(context).also {
        runBlocking {
            it.setUiMode(UiMode.NINEBOT.value)
            it.setThemeMode(if (dark) ColorMode.DARK.value else ColorMode.LIGHT.value)
            it.setPageScale(1f)
            it.setFloatingBottomBar(false)
        }
    }

    private fun restoredPreferences(): AppPreferencesService = AppPreferencesService(context).also {
        runBlocking { it.init() }
    }

    private fun render(preferences: AppPreferencesService, dark: Boolean = false) {
        val scheme = if (dark) NinebotDarkColorScheme else NinebotLightColorScheme
        compose.setContent {
            CompositionLocalProvider(LocalCyberPalette provides scheme.toCyberPalette()) {
                MaterialTheme(colorScheme = scheme) {
                    ThemeSettingsScreen(preferencesService = preferences, onBack = {})
                }
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File("build/reports/floating-bottom-bar").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
