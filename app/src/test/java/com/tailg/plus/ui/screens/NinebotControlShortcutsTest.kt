package com.tailg.plus.ui.screens

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tailg.plus.R
import com.tailg.plus.data.ble.CommandCode
import com.tailg.plus.data.model.NinebotShortcut
import com.tailg.plus.data.model.NinebotShortcutLayout
import com.tailg.plus.data.store.NinebotShortcutStore
import com.tailg.plus.data.store.ShortcutTestDataStore
import com.tailg.plus.domain.control.ControlChannelAvailability
import com.tailg.plus.domain.control.OfficialControlChannel
import com.tailg.plus.log.LogService
import com.tailg.plus.ui.theme.NinebotDarkColorScheme
import com.tailg.plus.ui.theme.NinebotLightColorScheme
import com.tailg.plus.ui.theme.LocalCyberPalette
import com.tailg.plus.ui.theme.toCyberPalette
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
class NinebotControlShortcutsTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val data = ShortcutTestDataStore()
    private val store = NinebotShortcutStore(data)
    private val vehicle = mutableStateOf("vehicle-a")
    private val busy = mutableStateOf(false)
    private val activeCommand = mutableStateOf<CommandCode?>(null)
    private val actions = mutableListOf<String>()

    @Before
    fun disableAnimations() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    @Test
    fun editingSwapsSlotsAndOnlySavingChangesTheRealControls() {
        render()
        openEditor()
        captureEditor("editor-light")
        choose("seat")
        compose.onNodeWithTag("shortcut-slot-0").assertIsSelected()
        assertEquals(NinebotShortcutLayout.Default, saved())
        assertTrue(actions.isEmpty())
        save()

        compose.onNodeWithTag("ninebot-shortcut-editor").assertDoesNotExist()
        assertSlot(0, R.string.control_card_seat).performClick()
        assertSlot(1, R.string.ninebot_tile_battery).performClick()
        assertSlot(2, R.string.ninebot_tile_induction).performClick()
        compose.onNodeWithContentDescription(text(R.string.ninebot_tile_horn)).performClick()
        assertEquals(listOf("seat", "battery", "induction", "find"), actions)
        assertEquals(listOf(NinebotShortcut.SEAT, NinebotShortcut.BATTERY, NinebotShortcut.INDUCTION), saved().slots)
        capture("configured-controls-light")
    }

    @Test
    fun closingDiscardsTheDraftAndEmptySlotsCanBeAddedBack() {
        render()
        openEditor()
        choose("empty")
        compose.onNodeWithContentDescription(text(R.string.nb_shortcuts_close)).performScrollTo().performClick()
        assertEquals(0, data.writes)
        assertSlot(0, R.string.ninebot_tile_induction).assertIsDisplayed()

        openEditor()
        choose("empty")
        save()
        assertEquals(null, saved().slots[0])
        compose.onNodeWithContentDescription(context.getString(
            R.string.nb_shortcuts_slot_description, text(R.string.nb_shortcuts_left), text(R.string.nb_shortcuts_add),
        )).performClick()
        choose("induction")
        save()
        assertEquals(NinebotShortcutLayout.Default, saved())
        assertTrue(actions.isEmpty())
    }

    @Test
    fun failedSaveKeepsTheDraftAndAllowsRetry() {
        render()
        openEditor()
        choose("battery")
        data.failWrites = true
        save()
        compose.onNodeWithText(text(R.string.nb_shortcuts_save_failed)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("shortcut-save").assertIsEnabled()
        assertEquals(NinebotShortcutLayout.Default, saved())
        data.failWrites = false
        save()
        assertSlot(0, R.string.ninebot_tile_battery).assertIsDisplayed()
        assertEquals(1, data.writes)
        assertTrue(actions.isEmpty())
    }

    @Test
    fun switchingVehiclesLoadsTheirOwnLayoutAndDiscardsAnOpenDraft() {
        val first = NinebotShortcutLayout.Default.assign(0, NinebotShortcut.SEAT)
        val second = NinebotShortcutLayout.Default.assign(1, null)
        runBlocking {
            store.save("vehicle-a", first)
            store.save("vehicle-b", second)
        }
        render()
        assertSlot(0, R.string.control_card_seat).assertIsDisplayed()
        openEditor()
        choose("empty")
        compose.runOnIdle { vehicle.value = "vehicle-b" }
        compose.onNodeWithTag("ninebot-shortcut-editor").assertDoesNotExist()
        assertSlot(0, R.string.ninebot_tile_induction).assertIsDisplayed()
        assertEquals(first, saved("vehicle-a"))
        assertEquals(second, saved("vehicle-b"))
        assertTrue(actions.isEmpty())
    }

    @Test
    fun switchingVehiclesCancelsAnInFlightSaveWithoutRetargetingIt() {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        data.beforeWrite = { entered.complete(Unit); release.await() }
        render()
        openEditor()
        choose("seat")
        save()
        compose.onNodeWithTag("shortcut-save").assertIsNotEnabled()
        compose.runOnIdle {
            assertTrue(entered.isCompleted)
            vehicle.value = "vehicle-b"
        }
        compose.waitForIdle()
        compose.runOnIdle { release.complete(Unit) }
        compose.waitForIdle()
        compose.onNodeWithTag("ninebot-shortcut-editor").assertDoesNotExist()
        assertEquals(0, data.writes)
        assertEquals(NinebotShortcutLayout.Default, saved("vehicle-a"))
        assertEquals(NinebotShortcutLayout.Default, saved("vehicle-b"))
    }

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h640dp-mdpi")
    fun narrowDarkEditorWithLargeTextCanRestoreDefaults() {
        runBlocking { store.save("vehicle-a", NinebotShortcutLayout.Default.assign(0, null)) }
        render(dark = true, fontScale = 1.3f)
        openEditor()
        captureEditor("editor-dark-large-text")
        compose.onNodeWithTag("shortcut-reset").performScrollTo().performClick()
        save()
        assertEquals(NinebotShortcutLayout.Default, saved())
        assertTrue(actions.isEmpty())
    }

    @Test
    fun readFailureDisablesEditingUntilRetryLoadsTheSavedLayout() {
        runBlocking { store.save("vehicle-a", NinebotShortcutLayout.Default.assign(0, NinebotShortcut.SEAT)) }
        data.failReads = true
        render()
        compose.onNodeWithTag("ninebot-shortcuts-edit").assertIsNotEnabled()
        data.failReads = false
        compose.onNodeWithText(text(R.string.nb_shortcuts_load_failed)).performClick()
        compose.onNodeWithTag("ninebot-shortcuts-edit").assertIsEnabled()
        assertSlot(0, R.string.control_card_seat).assertIsDisplayed()
        assertEquals(1, data.writes)
    }

    @Test
    fun busySeatFeedbackFollowsItsSlotAndCannotSendAgain() {
        runBlocking { store.save("vehicle-a", NinebotShortcutLayout.Default.assign(0, NinebotShortcut.SEAT)) }
        render()
        compose.runOnIdle {
            busy.value = true
            activeCommand.value = CommandCode.openSeat
        }
        compose.onNodeWithText(context.getString(R.string.control_grid_in_progress_format, text(R.string.control_card_seat))).assertIsDisplayed()
        assertSlot(0, R.string.control_card_seat).performClick()
        compose.onNodeWithTag("ninebot-shortcuts-edit").assertIsNotEnabled()
        assertTrue(actions.isEmpty())
        compose.runOnIdle {
            busy.value = false
            activeCommand.value = null
        }
        assertSlot(0, R.string.control_card_seat).performClick()
        assertEquals(listOf("seat"), actions)
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h800dp-mdpi")
    fun englishEditorKeepsOptionsAndSaveReachable() {
        render(fontScale = 1.2f)
        openEditor()
        captureEditor("editor-english")
        choose("battery")
        save()
        assertSlot(0, R.string.ninebot_tile_battery).assertIsDisplayed()
        assertTrue(actions.isEmpty())
    }

    private fun render(dark: Boolean = false, fontScale: Float = 1f) {
        RuntimeEnvironment.setFontScale(fontScale)
        val scheme = if (dark) NinebotDarkColorScheme else NinebotLightColorScheme
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalCyberPalette provides scheme.toCyberPalette(),
            ) {
                MaterialTheme(colorScheme = scheme) {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(vertical = 24.dp)) {
                        NinebotControlSection(
                            vehicleKey = vehicle.value,
                            shortcutStore = store,
                            log = LogService(),
                            powered = false,
                            busy = busy.value,
                            activeCommand = activeCommand.value,
                            findAvailability = available(),
                            powerAvailability = available(),
                            seatAvailability = available(),
                            onFind = { actions += "find" },
                            onPowerToggle = { actions += "power" },
                            onArmToggle = { actions += "arm" },
                            onSettings = { actions += "settings" },
                            onSeat = { actions += "seat" },
                            onBattery = { actions += "battery" },
                            onInduction = { actions += "induction" },
                        )
                    }
                }
            }
        }
    }

    private fun available() = ControlChannelAvailability(
        channel = OfficialControlChannel.AUTOMATIC, officialDecision = null,
        canUseBle = false, canUseCloud = true, enabled = true, willUseBle = false,
        vehicleAllowsCloudFallback = true, effectiveChannelLabel = "Cloud",
        bleUnavailableReason = "", cloudUnavailableReason = "", disabledReason = "",
    )

    private fun openEditor() = compose.onNodeWithTag("ninebot-shortcuts-edit").performScrollTo().performClick()
    private fun choose(value: String) = compose.onNodeWithTag("shortcut-choice-$value").performScrollTo().performClick()
    private fun save() = compose.onNodeWithTag("shortcut-save").performScrollTo().performClick().also { compose.waitForIdle() }
    private fun saved(key: String = vehicle.value) = runBlocking { store.observe(key).first() }
    private fun text(id: Int) = context.getString(id)
    private fun assertSlot(index: Int, label: Int) = compose.onNode(
        hasContentDescription(text(label)) and hasAnyAncestor(hasTestTag("ninebot-shortcut-$index")),
    ).assertIsDisplayed()

    private fun captureEditor(name: String) {
        compose.waitForIdle()
        writeImage(name, compose.onNodeWithTag("ninebot-shortcut-editor").captureToImage().asAndroidBitmap())
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        writeImage(name, compose.onRoot().captureToImage().asAndroidBitmap())
    }

    private fun writeImage(name: String, bitmap: Bitmap) {
        val directory = File("build/reports/ninebot-shortcuts").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
