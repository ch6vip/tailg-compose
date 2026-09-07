package com.tailg.plus.ui.components

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tailg.plus.R
import com.tailg.plus.data.ble.CommandCode
import com.tailg.plus.data.cloud.ResolvedVehicleLocation
import com.tailg.plus.domain.control.ControlChannelAvailability
import com.tailg.plus.domain.control.ControlTopBarChannel
import com.tailg.plus.domain.control.ControlTopBarChannelKind
import com.tailg.plus.domain.control.OfficialControlChannel
import com.tailg.plus.ui.theme.LocalCyberPalette
import com.tailg.plus.ui.theme.LocalUiMode
import com.tailg.plus.ui.theme.UiMode
import com.tailg.plus.ui.theme.VectorDarkColorScheme
import com.tailg.plus.ui.theme.VectorLightColorScheme
import com.tailg.plus.ui.theme.VectorShapes
import com.tailg.plus.ui.theme.VectorTypography
import com.tailg.plus.ui.theme.toCyberPalette
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class VectorControlHomeTest {
  @get:Rule val compose = createComposeRule()
  private val context: Context get() = ApplicationProvider.getApplicationContext()
  private val state = mutableStateOf(PanelState())
  private val dark = mutableStateOf(false)
  private val floating = mutableStateOf(true)
  private val events = mutableListOf<String>()
  private lateinit var pageScroll: ScrollState
  private var statusBarInsetPx = 0
  private var navigationBarInsetPx = 0

  @Before
  fun disableSystemAnimations() {
    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
  }

  @Test
  fun unknownBatteryAndVehicleStateNeverAppearAsZeroOrReady() {
    state.value = PanelState(
      range = "-- km",
      batteryKnown = false,
      batteryPercent = 0,
      powered = null,
      armed = null,
      online = false,
    )
    render()

    compose.onNodeWithTag("vectorBattery").assertContentDescriptionEquals(
      context.getString(
        R.string.vector_control_battery_description,
        "-- km",
        context.getString(R.string.vehicle_header_unknown),
      ),
    )
    compose.onNodeWithText(context.getString(R.string.vector_control_power_unknown)).assertIsDisplayed()
    compose.onNodeWithText(context.getString(R.string.vector_control_lock_unknown)).assertIsDisplayed()
    compose.onNodeWithText(context.getString(R.string.vehicle_header_powered)).assertDoesNotExist()
    compose.onNodeWithText(context.getString(R.string.vehicle_header_unpowered)).assertDoesNotExist()
    compose.onNodeWithText(context.getString(R.string.vehicle_header_armed)).assertDoesNotExist()
    val power = compose.onNodeWithTag("vectorPower").performScrollTo()
      .assertContentDescriptionEquals(context.getString(R.string.slide_power_unknown))
    assertNull("Unknown power must not expose a send action", power.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick))
    assertTrue(events.isEmpty())
    capture("vector-control-unknown")
  }

  @Test
  fun busyCommandsBlockMoreSendsButNavigationAndUnavailableExplanationsRemainReachable() {
    state.value = PanelState(
      busy = true,
      activeCommand = CommandCode.find,
      available = false,
      unavailableReason = "正在执行控车指令，请稍候",
    )
    render()
    for (tag in listOf("vectorFind", "vectorArm", "vectorSeat")) {
      expose(tag)
      compose.onNodeWithTag(tag).assertIsNotEnabled().performTouchInput { click() }
    }
    assertTrue("No second control command may be emitted while busy", events.isEmpty())
    expose("vectorSettings")
    compose.onNodeWithTag("vectorSettings").assertIsEnabled().performTouchInput { click() }
    assertEquals(listOf("settings"), events)

    compose.runOnIdle {
      state.value = state.value.copy(busy = false, activeCommand = null, unavailableReason = "蓝牙未连接")
    }
    expose("vectorFind")
    compose.onNodeWithTag("vectorFind")
      .assertIsEnabled()
      .assertContentDescriptionEquals(
        context.getString(R.string.control_grid_unavailable_reason_format, context.getString(R.string.control_card_find), "蓝牙未连接"),
      )
      .performTouchInput { click() }
    assertEquals("The existing unavailable callback supplies the reason UI", listOf("settings", "find"), events)
  }

  @Test
  fun headerAndControlKeysRouteToTheirOriginalCallbacks() {
    render()
    val routes = listOf(
      "vectorVehicle" to "vehicle",
      "vectorBattery" to "battery",
      "vectorBle" to "ble",
      "vectorMessages" to "messages",
      "vectorChannel" to "channel",
      "vectorFind" to "find",
      "vectorArm" to "arm",
      "vectorSeat" to "seat",
      "vectorSettings" to "settings",
      "vectorNfc" to "nfc",
      "vectorRideStats" to "ride",
      "vectorLocation" to "location",
    )
    for ((tag, _) in routes) {
      expose(tag)
      compose.onNodeWithTag(tag).performTouchInput { click() }
    }
    assertEquals(routes.map { it.second }, events)
  }

  @Test
  @Config(qualifiers = "en-w320dp-h640dp-mdpi")
  fun narrowScreenWithLargeTextKeepsEveryActionInsideTheViewportAndAboveTheBar() {
    dark.value = true
    render(fontScale = 1.5f)
    val routes = listOf(
      "vectorVehicle" to "vehicle",
      "vectorMessages" to "messages",
      "vectorBattery" to "battery",
      "vectorChannel" to "channel",
      "vectorArm" to "arm",
      "vectorSeat" to "seat",
      "vectorNfc" to "nfc",
      "vectorLocation" to "location",
    )
    for ((tag, _) in routes) {
      expose(tag)
      val action = compose.onNodeWithTag(tag).assertIsDisplayed()
      val bounds = action.fetchSemanticsNode().boundsInRoot
      val root = compose.onNodeWithTag("vectorTestRoot").fetchSemanticsNode().boundsInRoot
      assertTrue("$tag must keep a 48 dp touch target", bounds.width >= 48f && bounds.height >= 48f)
      assertTrue("$tag must stay within the 320 dp viewport", bounds.left >= root.left && bounds.right <= root.right)
      action.performTouchInput { click() }
    }
    assertEquals(routes.map { it.second }, events)
    capture("vector-control-dark-320-large-text")
    compose.onNodeWithTag("floating-nav-tab-2").performTouchInput { click() }
    assertEquals("tab:2", events.last())
  }

  @Test
  @Config(qualifiers = "zh-rCN-w360dp-h720dp-mdpi")
  fun firstScreenStartsPowerWithoutScrollingOn360WithClassicBar() {
    assertFirstScreenCanStartPower(floatingBar = false, fontScale = 1f, screenshot = "first-screen-360x720-classic-light")
  }

  @Test
  @Config(qualifiers = "zh-rCN-w360dp-h720dp-mdpi")
  fun firstScreenStartsPowerWithoutScrollingOn360WithFloatingBar() {
    assertFirstScreenCanStartPower(floatingBar = true, fontScale = 1f, screenshot = "first-screen-360x720-floating-light")
  }

  @Test
  @Config(qualifiers = "en-w320dp-h640dp-mdpi")
  fun firstScreenStartsPowerWithoutScrollingOn320WithLargeTextAndClassicBar() {
    dark.value = true
    assertFirstScreenCanStartPower(floatingBar = false, fontScale = 1.5f, screenshot = "first-screen-320x640-classic-dark-large-text")
  }

  @Test
  @Config(qualifiers = "en-w320dp-h640dp-mdpi")
  fun firstScreenStartsPowerWithoutScrollingOn320WithLargeTextAndFloatingBar() {
    dark.value = true
    assertFirstScreenCanStartPower(floatingBar = true, fontScale = 1.5f, screenshot = "first-screen-320x640-floating-dark-large-text")
  }

  @Test
  fun lightAndDarkScreensRenderTheCompleteScrollingPageWithRealNavigationClearance() {
    render()
    for (darkTheme in listOf(false, true)) {
      compose.runOnIdle { dark.value = darkTheme }
      scrollBy(-10_000f)
      compose.onNodeWithTag("vectorVehicle").assertIsDisplayed()
      compose.onNodeWithTag("vectorPower").assertIsDisplayed()
      compose.onNodeWithTag("slide-power-track").assertIsDisplayed()
      compose.onNodeWithTag("floating-bottom-bar").assertIsDisplayed()
      val mode = if (darkTheme) "dark" else "light"
      capture("vector-control-$mode-top")

      expose("vectorArtwork")
      compose.onNodeWithTag("vectorArtwork").assertIsDisplayed()
      capture("vector-control-$mode-display")

      scrollBy(10_000f)
      val location = compose.onNodeWithTag("vectorLocation").assertIsDisplayed()
        .fetchSemanticsNode().boundsInRoot
      val bar = compose.onNodeWithTag("floating-bottom-bar").fetchSemanticsNode().boundsInRoot
      assertTrue("The final location action must scroll completely clear of navigation", location.bottom <= bar.top)
      capture("vector-control-$mode-controls")
    }
  }

  private fun render(fontScale: Float = 1f, reserveSystemBars: Boolean = false) {
    RuntimeEnvironment.setFontScale(fontScale)
    compose.setContent {
      val scheme = if (dark.value) VectorDarkColorScheme else VectorLightColorScheme
      CompositionLocalProvider(
        LocalDensity provides Density(LocalDensity.current.density, fontScale),
        LocalUiMode provides UiMode.VECTOR,
        LocalCyberPalette provides scheme.toCyberPalette(),
      ) {
        MaterialTheme(colorScheme = scheme, typography = VectorTypography, shapes = VectorShapes) {
          // Real device insets are sometimes zero in Robolectric. The first-
          // screen cases reserve at least 24 dp above and 48 dp below, while
          // union/consumption avoids counting actual system insets twice.
          val statusInsets = if (reserveSystemBars) {
            WindowInsets.statusBars.union(WindowInsets(top = 24.dp))
          } else WindowInsets.statusBars
          val navigationInsets = if (reserveSystemBars) {
            WindowInsets.navigationBars.union(WindowInsets(bottom = 48.dp))
          } else WindowInsets(0, 0, 0, 0)
          val measuredStatusInset = statusInsets.getTop(LocalDensity.current)
          val measuredNavigationInset = navigationInsets.getBottom(LocalDensity.current)
          SideEffect {
            statusBarInsetPx = measuredStatusInset
            navigationBarInsetPx = measuredNavigationInset
          }
          BottomNavigationScaffold(
            modifier = Modifier.fillMaxSize().testTag("vectorTestRoot"),
            bottomBar = {
              Box(Modifier.windowInsetsPadding(navigationInsets)) {
                TailgBottomNavigation(currentIndex = 1, floating = floating.value, onSelected = { events += "tab:$it" })
              }
            },
          ) {
            Scaffold(containerColor = scheme.background, contentWindowInsets = statusInsets) { padding ->
              val scroll = rememberScrollState()
              SideEffect { pageScroll = scroll }
              Column(
                modifier = Modifier.fillMaxSize().padding(padding)
                  .background(scheme.background)
                  .verticalScroll(scroll)
                  .testTag("vectorTestScroll"),
              ) {
                val snapshot = state.value
                val availability = availability(snapshot.available, snapshot.unavailableReason)
                VectorVehicleHeader(
                  vehicleName = snapshot.vehicleName,
                  rangeText = snapshot.range,
                  carPhoto = "",
                  batteryPercent = snapshot.batteryPercent,
                  batteryKnown = snapshot.batteryKnown,
                  online = snapshot.online,
                  bluetoothConnected = false,
                  isLocked = snapshot.armed,
                  powered = snapshot.powered,
                  bleChip = OfficialBleChipState.ClickToConnect,
                  channelStatus = ControlTopBarChannel(ControlTopBarChannelKind.CLOUD_STANDBY, "云端待命"),
                  onTitleTap = { events += "vehicle" },
                  onBatteryTap = { events += "battery" },
                  onBleChipTap = { events += "ble" },
                  onMessages = { events += "messages" },
                  onChannelTap = { events += "channel" },
                  controls = {
                    VectorControlGrid(
                      powered = snapshot.powered,
                      armed = snapshot.armed,
                      busy = snapshot.busy,
                      activeCommand = snapshot.activeCommand,
                      findAvailability = availability,
                      powerAvailability = availability,
                      armAvailability = availability,
                      seatAvailability = availability,
                      onFind = { events += "find" },
                      onPowerToggle = {
                        events += "power"
                        state.value.powered?.let { current -> state.value = state.value.copy(powered = !current) }
                      },
                      onArmToggle = { events += "arm" },
                      onSettings = { events += "settings" },
                      onSeat = { events += "seat" },
                      onNfc = { events += "nfc" },
                    )
                  },
                )
                Spacer(Modifier.height(32.dp))
                VectorStatsRow(
                  location = ResolvedVehicleLocation(
                    latitude = 31.2317,
                    longitude = 121.5050,
                    accuracy = 10.0,
                    timeLabel = "09:41",
                    address = "滨江大道",
                    source = "test fixture",
                  ),
                  address = "滨江大道",
                  todayKm = "12.8 km",
                  totalKm = "1,286 km",
                  onMapTap = { events += "location" },
                  onRideStatsTap = { events += "ride" },
                )
                Spacer(Modifier.height(24.dp + LocalBottomNavigationPadding.current))
              }
            }
          }
        }
      }
    }
  }

  private fun expose(tag: String) {
    compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
    val action = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    val bar = compose.onNodeWithTag(barTag()).fetchSemanticsNode().boundsInRoot
    if (action.bottom > bar.top) scrollBy(action.bottom - bar.top + 12f)
    val visible = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    assertTrue("$tag must be physically reachable above the floating bar", visible.bottom <= bar.top + 0.5f)
  }

  private fun assertFirstScreenCanStartPower(floatingBar: Boolean, fontScale: Float, screenshot: String) {
    floating.value = floatingBar
    state.value = PanelState(
      vehicleName = "台铃超能旗舰 S95 长续航城市通勤版",
      range = "170 km",
      batteryPercent = 100,
      batteryKnown = true,
      powered = false,
    )
    render(fontScale = fontScale, reserveSystemBars = true)
    compose.runOnIdle {
      assertEquals("The page must first open at its top", 0, pageScroll.value)
      assertTrue("The fixture must remain a genuinely scrollable control page", pageScroll.maxValue > 0)
      assertTrue("The status bar must have reserved space", statusBarInsetPx >= 24)
      assertTrue("The system navigation area must have reserved space", navigationBarInsetPx >= 48)
    }
    assertFirstScreenPowerBounds(screenshot)
    capture(screenshot)
    compose.runOnIdle {
      assertEquals("No scroll may be used to expose the start action", 0, pageScroll.value)
      assertTrue(events.isEmpty())
    }

    // Exercise the real pointer gesture in the initial viewport. No expose(),
    // performScrollTo(), semantic click, or manual scroll is used in this test.
    compose.onNodeWithTag("slide-power-track").performTouchInput { swipeRight() }
    compose.waitForIdle()
    compose.runOnIdle {
      assertEquals(listOf("power"), events)
      assertEquals(true, state.value.powered)
      assertEquals("Starting power must not scroll the page", 0, pageScroll.value)
    }
    compose.onNodeWithTag("vectorPower")
      .assertContentDescriptionEquals(context.getString(R.string.slide_power_slide_off))
    assertFirstScreenPowerBounds("$screenshot-after-start")
  }

  private fun assertFirstScreenPowerBounds(caseName: String) {
    val root = actualBounds("vectorTestRoot")
    val bar = actualBounds(barTag())
    val safeTop = root.top + statusBarInsetPx
    val safeBottom = minOf(bar.top, root.bottom - navigationBarInsetPx)
    for (tag in listOf("vectorPower", "slide-power-track", "slide-power-thumb")) {
      compose.onNodeWithTag(tag).assertIsDisplayed()
      val bounds = actualBounds(tag)
      val detail = "$caseName: $tag=$bounds, root=$root, bar=$bar, safeTop=$safeTop, safeBottom=$safeBottom, scroll=${pageScroll.value}"
      assertTrue("Power must be wholly inside the horizontal viewport: $detail", bounds.left >= root.left - 0.5f && bounds.right <= root.right + 0.5f)
      assertTrue("Power must clear the status bar without scrolling: $detail", bounds.top >= safeTop - 0.5f)
      assertTrue("Power must clear the Tab and system navigation bars without scrolling: $detail", bounds.bottom <= safeBottom + 0.5f)
      assertTrue("The complete physical target must be present: $detail", bounds.width >= 48f && bounds.height >= 48f)
    }
    val track = actualBounds("slide-power-track")
    val thumb = actualBounds("slide-power-thumb")
    assertTrue("The actual thumb must remain wholly in the track: thumb=$thumb, track=$track", thumb.left >= track.left - 0.5f && thumb.right <= track.right + 0.5f && thumb.top >= track.top - 0.5f && thumb.bottom <= track.bottom + 0.5f)
  }

  /** Use the full node size so ancestor clipping cannot turn a partial target into a passing bound. */
  private fun actualBounds(tag: String): Rect {
    val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
    return Rect(node.positionInRoot, Size(node.size.width.toFloat(), node.size.height.toFloat()))
  }

  private fun barTag() = if (floating.value) "floating-bottom-bar" else "classic-bottom-bar"

  private fun scrollBy(pixels: Float) {
    compose.onNodeWithTag("vectorTestScroll").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, pixels) }
  }

  private fun capture(name: String) {
    compose.waitForIdle()
    val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
    val directory = File("build/reports/vector-design").apply { mkdirs() }
    File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  private fun availability(enabled: Boolean, reason: String) = ControlChannelAvailability(
    channel = OfficialControlChannel.AUTOMATIC,
    officialDecision = null,
    canUseBle = false,
    canUseCloud = enabled,
    enabled = enabled,
    willUseBle = false,
    vehicleAllowsCloudFallback = true,
    effectiveChannelLabel = "官方云端",
    bleUnavailableReason = "",
    cloudUnavailableReason = reason,
    disabledReason = reason,
  )

  private data class PanelState(
    val vehicleName: String = "城市漫游",
    val range: String = "86 km",
    val batteryPercent: Int = 78,
    val batteryKnown: Boolean = true,
    val online: Boolean = true,
    val powered: Boolean? = false,
    val armed: Boolean? = false,
    val busy: Boolean = false,
    val activeCommand: CommandCode? = null,
    val available: Boolean = true,
    val unavailableReason: String = "",
  )
}
