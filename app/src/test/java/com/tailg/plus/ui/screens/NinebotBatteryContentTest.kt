package com.tailg.plus.ui.screens

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.tailg.plus.R
import com.tailg.plus.data.model.BatterySnapshot
import com.tailg.plus.data.model.OfficialBatteryInfo
import com.tailg.plus.data.model.OfficialBmsDetail
import com.tailg.plus.data.model.OfficialBmsInfo
import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.data.preferences.DistanceUnitPreference
import com.tailg.plus.ui.theme.LocalDistanceUnitPreference
import com.tailg.plus.ui.theme.NinebotDarkColorScheme
import com.tailg.plus.ui.theme.NinebotLightColorScheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NinebotBatteryContentTest {
  @get:Rule val compose = createComposeRule()
  private val context: Context get() = ApplicationProvider.getApplicationContext()
  private var refreshes = 0
  private var accountOpens = 0
  private var corrections = 0
  private val toggles = mutableListOf<Boolean>()

  @Before
  fun disableIdleMotion() {
    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
  }

  @Test
  fun signedOutShowsUnknownValuesAndOffersSignIn() {
    render(NinebotBatteryState(BatterySnapshot.fromSources(), signedIn = false))
    compose.onNodeWithText(text(R.string.nb_battery_waiting)).assertIsDisplayed()
    compose.onNodeWithText(text(R.string.nb_battery_ready)).assertDoesNotExist()
    capture("empty-light")
    compose.onNodeWithText(text(R.string.nb_battery_sign_in)).performClick()
    assertEquals(1, accountOpens)
    compose.onNodeWithContentDescription(text(R.string.nb_battery_sync)).assertIsNotEnabled()
    reveal(R.string.nb_battery_correct).assertIsNotEnabled()
    reveal(R.string.nb_battery_score_pending).assertIsDisplayed()
    assertEquals(0, refreshes)
  }

  @Test
  fun populatedOverviewAndBatteryDossierKeepAllActionsReachable() {
    render(sampleState())
    compose.onNodeWithText("86").assertIsDisplayed()
    compose.onNodeWithText("55.9").assertIsDisplayed()
    capture("overview-light")
    reveal(R.string.nb_battery_cycles).performClick()
    compose.onNodeWithText(text(R.string.nb_battery_cycles_title)).assertIsDisplayed()
    compose.onNodeWithText(text(R.string.nb_battery_cycles_body)).assertIsDisplayed()
    compose.onNodeWithContentDescription(text(R.string.nb_battery_close)).performClick()
    reveal(R.string.nb_battery_dossier).performClick()
    compose.onNode(hasText(text(R.string.nb_battery_dossier)) and hasClickAction()).assertIsSelected()
    compose.onNodeWithTag("ninebot-battery-list").performScrollToIndex(2)
    capture("dossier-light")
    compose.onNodeWithText("72V 24Ah").performScrollTo().assertIsDisplayed()
    reveal(R.string.nb_battery_bms).performClick()
    compose.onNodeWithText(text(R.string.nb_battery_software)).performScrollTo().assertIsDisplayed()
    compose.onAllNodesWithText("BMS-2026.09.01")[1].assertIsDisplayed()
    reveal(R.string.nb_battery_correct).performClick()
    assertEquals(1, corrections)
    reveal(R.string.nb_battery_care).performClick()
    compose.onNodeWithText(text(R.string.nb_battery_care_title)).assertIsDisplayed()
  }

  @Test
  fun partialSyncFailureRetainsReadingsAndBlocksRepeatedRefreshWhileLoading() {
    val state = mutableStateOf(sampleState().copy(bmsError = "BMS unavailable"))
    compose.setContent { Theme { Content(state.value) } }
    compose.onNodeWithText("86").assertIsDisplayed()
    compose.onNodeWithText(text(R.string.nb_battery_sync_error)).performClick()
    assertEquals(1, refreshes)
    compose.runOnIdle { state.value = state.value.copy(bmsLoading = true) }
    compose.onNodeWithText(text(R.string.nb_battery_syncing)).assertIsNotEnabled().performClick()
    assertEquals(1, refreshes)
    compose.onNodeWithText("86").assertIsDisplayed()
  }

  @Test
  fun darkThemeAndImperialRangeUseRealData() {
    render(sampleState(), dark = true, imperial = true)
    compose.onNodeWithText("34.7").assertIsDisplayed()
    compose.onNodeWithText("mi").assertIsDisplayed()
    capture("overview-dark")
    compose.onNodeWithText("53.4").performScrollTo().assertIsDisplayed()
    compose.onNodeWithTag("ninebot-battery-list").performScrollToIndex(2)
    capture("metrics-dark")
    reveal(R.string.nb_battery_score).performClick()
    compose.onNodeWithText(text(R.string.nb_battery_score_body)).assertIsDisplayed()
  }

  @Test
  fun fullChargeFitsBesideTheSculptureAndDailyUseCanExceedOneCharge() {
    val sample = sampleState()
    render(sample.copy(snapshot = sample.snapshot.copy(percent = 100, consumePowerPercent = "125")))
    val number = compose.onNodeWithText("100").fetchSemanticsNode().boundsInRoot
    val sculpture = compose.onNodeWithContentDescription(context.getString(R.string.nb_battery_visual, 100)).fetchSemanticsNode().boundsInRoot
    assertTrue("Full charge typography must not overlap the battery viewer", number.right <= sculpture.left)
    capture("full-charge-light")
    reveal(R.string.nb_battery_today)
    compose.onNodeWithText("125").performScrollTo().assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "en-rUS-w320dp-h700dp-mdpi")
  fun narrowScreenWithLargeTextKeepsNavigationAndZeroReadingsAvailable() {
    val sample = sampleState()
    render(sample.copy(snapshot = sample.snapshot.copy(percent = 0, temperature = 0.0, consumePowerPercent = "0", loopCount = "0", batteryScore = null)), fontScale = 1.6f)
    compose.onNodeWithText(text(R.string.nb_battery_low)).assertIsDisplayed()
    capture("compact-large-text")
    reveal(R.string.nb_battery_today).assertIsDisplayed()
    reveal(R.string.nb_battery_score_pending).assertIsDisplayed()
    reveal(R.string.nb_battery_care).performClick()
    compose.onNodeWithTag("ninebot-battery-help").performScrollToNode(hasText(text(R.string.nb_battery_care_service)))
    compose.onNodeWithText(text(R.string.nb_battery_care_service)).assertIsDisplayed()
  }

  @Test
  fun coulombControlRequiresBluetoothAndAKnownState() {
    val state = mutableStateOf(sampleState().copy(coulomb = NinebotCoulombState(false, null, null, false)))
    compose.setContent { Theme { Content(state.value) } }
    reveal(R.string.nb_battery_dossier).performClick()
    reveal(R.string.nb_battery_coulomb)
    compose.onNodeWithContentDescription(text(R.string.nb_battery_coulomb)).performScrollTo().assertIsNotEnabled().performClick()
    assertEquals(emptyList<Boolean>(), toggles)
    compose.runOnIdle { state.value = state.value.copy(coulomb = NinebotCoulombState(false, false, null, true)) }
    compose.onNodeWithContentDescription(text(R.string.nb_battery_coulomb)).performClick()
    assertEquals(listOf(true), toggles)
  }

  private fun render(state: NinebotBatteryState, dark: Boolean = false, imperial: Boolean = false, fontScale: Float = 1f) {
    compose.setContent {
      CompositionLocalProvider(
        LocalDensity provides Density(LocalDensity.current.density, fontScale),
        LocalDistanceUnitPreference provides if (imperial) DistanceUnitPreference.Imperial else DistanceUnitPreference.Metric,
      ) { Theme(dark) { Content(state) } }
    }
  }

  @Composable
  private fun Theme(dark: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) NinebotDarkColorScheme else NinebotLightColorScheme, content = content)
  }

  @Composable
  private fun Content(state: NinebotBatteryState) {
    NinebotBatteryContent(
      state = state,
      onBack = {},
      onRefresh = { refreshes++ },
      onCorrect = { corrections++ },
      onAccount = { accountOpens++ },
      onCoulombToggle = { toggles += it },
      onCoulombRefresh = {},
    )
  }

  private fun text(id: Int): String = context.getString(id)

  private fun reveal(id: Int): androidx.compose.ui.test.SemanticsNodeInteraction {
    val matcher = hasText(text(id))
    compose.onNodeWithTag("ninebot-battery-list").performScrollToNode(matcher)
    return compose.onNode(matcher)
  }

  /** Native Compose renders, written only to ignored build output for visual review. */
  private fun capture(name: String) {
    compose.waitForIdle()
    val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
    val directory = File("build/reports/ninebot-battery").apply { mkdirs() }
    File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  private fun sampleState(): NinebotBatteryState = NinebotBatteryState(
    snapshot = BatterySnapshot.fromSources(
      officialVehicle = OfficialVehicle(carId = "battery-preview", carName = "台铃 · 超能 S", raw = mapOf(
        "batterySpec" to "72V 24Ah", "batteryBindDate" to "2026-06-18", "batteryTypeId" to "208",
      )),
      officialBatteryInfo = OfficialBatteryInfo(
        dumpEnergyPercent = "86", remainingMileage = "55.9", mileage = "4128.6", capacitance = "24Ah",
        consumePowerPercent = "8", loopCount = "128", temperature = "28", batteryScore = "98", voltage = "53.4",
      ),
      officialBmsInfo = OfficialBmsInfo(details = listOf(OfficialBmsDetail(
        soc = "86", soh = "98", batteryCapacity = "24Ah", batteryTemperature = "28",
        batteryCurrent = "0.0", batteryType = "锂电池", batteryVersion = "BMS-2026.09.01",
      ))),
    ),
    signedIn = true,
    lastSync = "刚刚",
  )
}
