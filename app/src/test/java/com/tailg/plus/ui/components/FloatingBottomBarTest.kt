package com.tailg.plus.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
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
@Config(application = Application::class, sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FloatingBottomBarTest {
    @get:Rule val compose = createComposeRule()
    private val selection = mutableIntStateOf(1)
    private val floating = mutableStateOf(true)
    private val navigations = mutableListOf<Int>()

    @Before
    fun disableSystemAnimations() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    @Test
    fun tappingSelectsOneDestinationAndRetappingDoesNotNavigateAgain() {
        render()
        compose.onNodeWithTag("floating-nav-tab-1").assertIsSelected()
        compose.onNodeWithTag("floating-nav-tab-3").performClick().assertIsSelected()
        compose.onNodeWithTag("floating-nav-tab-3").performClick()
        assertEquals(listOf(3), navigations)
        capture("floating-ninebot-light")
    }

    @Test
    fun draggingCommitsOnlyOnReleaseWithoutOpeningIntermediateTabs() {
        render()
        compose.onNodeWithTag("floating-bottom-bar").performTouchInput {
            down(Offset(width * 0.375f, centerY))
            moveTo(Offset(width * 0.875f, centerY), delayMillis = 240)
        }
        compose.runOnIdle {
            assertTrue(navigations.isEmpty())
            assertEquals(1, selection.intValue)
        }
        compose.onNodeWithTag("floating-bottom-bar").performTouchInput { up() }
        compose.onNodeWithTag("floating-nav-tab-3").assertIsSelected()
        assertEquals(listOf(3), navigations)
    }

    @Test
    fun cancellingADragRestoresTheCurrentTab() {
        render()
        compose.onNodeWithTag("floating-bottom-bar").performTouchInput {
            down(Offset(width * 0.375f, centerY))
            moveTo(Offset(width * 0.875f, centerY), delayMillis = 200)
            cancel()
        }
        compose.onNodeWithTag("floating-nav-tab-1").assertIsSelected()
        assertTrue(navigations.isEmpty())
    }

    @Test
    fun rtlDraggingUsesTheMirroredTabPositions() {
        selection.intValue = 0
        render(rtl = true)
        compose.onNodeWithTag("floating-bottom-bar").performTouchInput {
            swipe(Offset(width * 0.875f, centerY), Offset(width * 0.125f, centerY), durationMillis = 240)
        }
        compose.onNodeWithTag("floating-nav-tab-3").assertIsSelected()
        assertEquals(listOf(3), navigations)
    }

    @Test
    fun changingAppearanceAndExternalSelectionKeepsNavigationState() {
        render()
        compose.runOnIdle {
            selection.intValue = 2
            floating.value = false
        }
        compose.onNodeWithTag("classic-bottom-bar").assertIsDisplayed()
        compose.onNodeWithTag("floating-bottom-bar").assertDoesNotExist()
        compose.runOnIdle { floating.value = true }
        compose.onNodeWithTag("floating-nav-tab-2").assertIsSelected()
        assertTrue(navigations.isEmpty())
    }

    @Test
    @Config(qualifiers = "en-w320dp-h640dp-mdpi")
    fun narrowDarkBarWithLargeTextKeepsEveryTabReachable() {
        render(dark = true, fontScale = 1.5f, pageScale = 1.1f)
        BottomNavDestination.entries.indices.forEach { index ->
            compose.onNodeWithTag("floating-nav-tab-$index")
                .assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
                .performClick()
                .assertIsSelected()
            val layouts = mutableListOf<TextLayoutResult>()
            val label = ApplicationProvider.getApplicationContext<Application>().getString(BottomNavDestination.entries[index].labelRes)
            compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("The complete navigation label must fit: $label", layouts.isNotEmpty() && layouts.none { it.hasVisualOverflow })
        }
        capture("floating-cyber-dark-large-text")
    }

    private fun render(dark: Boolean = false, fontScale: Float = 1f, pageScale: Float = 1f, rtl: Boolean = false) {
        RuntimeEnvironment.setFontScale(fontScale)
        val scheme = if (dark) CyberDarkColorScheme else NinebotLightColorScheme
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density * pageScale, fontScale),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalCyberPalette provides scheme.toCyberPalette(),
            ) {
                MaterialTheme(colorScheme = scheme) {
                    Box(Modifier.fillMaxSize().background(scheme.background), contentAlignment = Alignment.BottomCenter) {
                        TailgBottomNavigation(
                            currentIndex = selection.intValue,
                            floating = floating.value,
                            onSelected = {
                                navigations += it
                                selection.intValue = it
                            },
                        )
                    }
                }
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag("floating-bottom-bar").captureToImage().asAndroidBitmap()
        val directory = File("build/reports/floating-bottom-bar").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
