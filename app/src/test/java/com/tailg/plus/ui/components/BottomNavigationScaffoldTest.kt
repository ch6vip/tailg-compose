package com.tailg.plus.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tailg.plus.R
import com.tailg.plus.ui.navigation.Routes
import com.tailg.plus.ui.screens.ServiceHubScreen
import com.tailg.plus.ui.theme.CyberDarkColorScheme
import com.tailg.plus.ui.theme.LocalCyberPalette
import com.tailg.plus.ui.theme.NinebotLightColorScheme
import com.tailg.plus.ui.theme.toCyberPalette
import java.io.File
import org.junit.Assert.assertEquals
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
@Config(application = Application::class, sdk = [35], qualifiers = "zh-rCN-w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BottomNavigationScaffoldTest {
    @get:Rule val compose = createComposeRule()
    private val floating = mutableStateOf(true)
    private val visible = mutableStateOf(true)
    private val dark = mutableStateOf(false)
    private val selectedTab = mutableStateOf(1)
    private var bottomPadding = 0.dp

    @Before
    fun disableSystemAnimations() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    @Test
    fun pageExtendsBehindBothBarsAndItsColorShowsThrough() {
        val backdrop = mutableStateOf(Color.Red)
        render {
            Box(Modifier.fillMaxSize().background(backdrop.value))
        }

        for (darkTheme in listOf(false, true)) {
            for (floatingStyle in listOf(false, true)) {
                compose.runOnIdle {
                    dark.value = darkTheme
                    floating.value = floatingStyle
                    backdrop.value = Color.Red
                }
                val page = compose.onNodeWithTag("page-viewport").fetchSemanticsNode().boundsInRoot
                val root = compose.onNodeWithTag("navigation-root").fetchSemanticsNode().boundsInRoot
                assertEquals("The page viewport must extend beneath the bar", root.bottom, page.bottom, 0.5f)
                val redBackdrop = sampleBarBackground()
                compose.runOnIdle { backdrop.value = Color.Blue }
                val blueBackdrop = sampleBarBackground()

                assertTrue("The red page must show through the bar", redBackdrop.red - blueBackdrop.red > 0.08f)
                assertTrue("The blue page must show through the bar", blueBackdrop.blue - redBackdrop.blue > 0.08f)
            }
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h560dp-mdpi")
    fun lastServiceActionScrollsAboveEitherBarAndAcceptsRealTouches() {
        val navigations = mutableListOf<String>()
        val context = ApplicationProvider.getApplicationContext<Application>()
        val lastActionLabel = context.getString(R.string.service_official_account)
        render {
            ServiceHubScreen(vehicleRouteId = "vehicle-a", onNavigate = { navigations += it })
        }

        for (floatingStyle in listOf(false, true)) {
            compose.runOnIdle { floating.value = floatingStyle }
            compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) {
                it(0f, 10_000f)
            }
            val action = compose.onNodeWithContentDescription(lastActionLabel, substring = true).assertIsDisplayed()
            val actionBounds = action.fetchSemanticsNode().boundsInRoot
            val barBounds = compose.onNodeWithTag(barTag()).fetchSemanticsNode().boundsInRoot
            assertTrue("The whole last action must clear the navigation bar", actionBounds.bottom <= barBounds.top)
            action.performTouchInput { click() }
            capture(if (floatingStyle) "service-floating-light" else "service-classic-light")
        }

        assertEquals(listOf(Routes.OFFICIAL_CLOUD, Routes.OFFICIAL_CLOUD), navigations)
    }

    @Test
    @Config(qualifiers = "en-w320dp-h640dp-mdpi")
    fun bothBarsKeepLargeEnglishLabelsOnOneLineAndAllFourRoutesTouchable() {
        RuntimeEnvironment.setFontScale(1.5f)
        val selections = mutableListOf<Int>()
        val context = ApplicationProvider.getApplicationContext<Application>()
        val labels = listOf(R.string.nav_service, R.string.nav_control, R.string.nav_mine, R.string.nav_settings)
            .map(context::getString)
        render(fontScale = 1.5f, onSelected = { selections += it }) {
            Box(Modifier.fillMaxSize().background(Color.White))
        }

        for (floatingStyle in listOf(false, true)) {
            compose.runOnIdle { floating.value = floatingStyle }
            for (label in labels) {
                assertNavigationLabelFits(label)
                val tab = compose.onNode(hasText(label) and hasClickAction()).assertIsDisplayed()
                val bounds = tab.fetchSemanticsNode().boundsInRoot
                assertTrue("$label must retain a 48 dp touch target", bounds.width >= 48f && bounds.height >= 48f)
                tab.performTouchInput { click() }
                // Selecting a tab changes its weight; the selected label must
                // also fit, particularly the longer Services and Settings.
                assertNavigationLabelFits(label)
            }
        }

        assertEquals(listOf(0, 1, 2, 3, 0, 1, 2, 3), selections)
    }

    @Test
    fun nestedSnackbarClearsTheBarAndLeavingTabsRemovesTheirPadding() {
        val snackbar = SnackbarHostState()
        render {
            LaunchedEffect(Unit) {
                snackbar.showSnackbar("设置已保存", duration = SnackbarDuration.Indefinite)
            }
            Scaffold(snackbarHost = { AppSnackbarHost(snackbar) }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding))
            }
        }

        val message = compose.onNodeWithText("设置已保存").assertIsDisplayed()
        val before = message.fetchSemanticsNode().boundsInRoot
        val bar = compose.onNodeWithTag(barTag()).fetchSemanticsNode().boundsInRoot
        assertTrue("Snackbar text must stay above the navigation bar", before.bottom < bar.top)
        compose.runOnIdle {
            assertTrue(bottomPadding > 0.dp)
            visible.value = false
        }
        compose.onNodeWithTag("floating-bottom-bar").assertDoesNotExist()
        val after = message.fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { assertEquals(0.dp, bottomPadding) }
        assertTrue("A full-screen destination must not keep the old bar clearance", after.bottom > before.bottom)
    }

    private fun render(
        fontScale: Float = 1f,
        onSelected: (Int) -> Unit = {},
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            val scheme = if (dark.value) CyberDarkColorScheme else NinebotLightColorScheme
            CompositionLocalProvider(
                LocalCyberPalette provides scheme.toCyberPalette(),
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                MaterialTheme(colorScheme = scheme) {
                    BottomNavigationScaffold(
                        modifier = Modifier.testTag("navigation-root"),
                        bottomBar = {
                            if (visible.value) {
                                TailgBottomNavigation(
                                    currentIndex = selectedTab.value,
                                    floating = floating.value,
                                    onSelected = { index ->
                                        selectedTab.value = index
                                        onSelected(index)
                                    },
                                )
                            }
                        },
                    ) {
                        val measuredPadding = LocalBottomNavigationPadding.current
                        SideEffect { bottomPadding = measuredPadding }
                        Box(Modifier.fillMaxSize().testTag("page-viewport")) {
                            content()
                        }
                    }
                }
            }
        }
    }

    private fun barTag() = if (floating.value) "floating-bottom-bar" else "classic-bottom-bar"

    private fun assertNavigationLabelFits(label: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        // Read the actual BasicText leaf, not the tab's merged semantics node.
        compose.onNodeWithText(label, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("$label must resolve to exactly one text leaf", 1, layouts.size)
        assertTrue("$label must not clip or ellipsize", !layouts.single().hasVisualOverflow)
        assertEquals("$label must remain on one line", 1, layouts.single().lineCount)
    }

    private fun sampleBarBackground(): Color {
        val bar = compose.onNodeWithTag(barTag()).fetchSemanticsNode().boundsInRoot
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        // Clear space above the unselected third tab's icon, away from the border.
        return pixels[(bar.left + bar.width * 0.625f).toInt(), (bar.top + 8f).toInt()]
    }

    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File("build/reports/translucent-navigation").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
