package com.tailg.plus.ui.screens

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import com.tailg.plus.ui.components.BottomNavigationScaffold
import com.tailg.plus.ui.components.LocalBottomNavigationPadding
import com.tailg.plus.ui.components.TailgBottomNavigation
import com.tailg.plus.ui.navigation.Routes
import com.tailg.plus.ui.theme.LocalCyberPalette
import com.tailg.plus.ui.theme.LocalUiMode
import com.tailg.plus.ui.theme.UiMode
import com.tailg.plus.ui.theme.VectorDarkColorScheme
import com.tailg.plus.ui.theme.VectorLightColorScheme
import com.tailg.plus.ui.theme.VectorTypography
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
class VectorPagesTest {
  @get:Rule val compose = createComposeRule()
  private val context: Context get() = ApplicationProvider.getApplicationContext()
  private val dark = mutableStateOf(false)
  private val floating = mutableStateOf(true)
  private val page = mutableStateOf(Page.Services)
  private val vehicleId = mutableStateOf("vehicle-a")
  private val profile = mutableStateOf(ProfileFixture())
  private val routes = mutableListOf<String>()
  private val profileActions = mutableListOf<String>()

  @Before
  fun disableSystemAnimations() {
    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
  }

  @Test
  fun allServiceDestinationsRemainUsableAndVehicleChangesUpdateTheirRoutes() {
    render(Page.Services)
    compose.onNodeWithTag("floating-nav-tab-0").assertIsSelected()
    compose.onNodeWithText(text(R.string.vector_service_heading)).assertIsDisplayed()
    capture("service-light")

    val destinations = listOf(
      R.string.service_location to Routes.location("vehicle-a"),
      R.string.service_travel to Routes.location("vehicle-a", "travel"),
      R.string.service_fence to Routes.location("vehicle-a", "fence"),
      R.string.service_vehicle_settings to Routes.vehicleSettings("vehicle-a"),
      R.string.service_battery to Routes.batteryDetails("vehicle-a"),
      R.string.service_ride_stats to Routes.rideStats("vehicle-a"),
      R.string.service_fault_diag to Routes.diagnostic("vehicle-a"),
      R.string.service_official_account to Routes.OFFICIAL_CLOUD,
    )
    destinations.forEach { (label, _) ->
      reveal(label).assertHeightIsAtLeast(48.dp).performClick()
    }
    assertEquals(destinations.map { it.second }, routes)

    // A vehicle change must reach the existing composable's callbacks too.
    compose.runOnIdle {
      vehicleId.value = "vehicle-b"
      dark.value = true
    }
    reveal(R.string.service_battery).performClick()
    reveal(R.string.service_location).performClick()
    assertEquals(
      listOf(Routes.batteryDetails("vehicle-b"), Routes.location("vehicle-b")),
      routes.takeLast(2),
    )
    scrollToTop()
    compose.onNodeWithText(text(R.string.vector_service_heading)).assertIsDisplayed()
    capture("service-dark")
  }

  @Test
  fun signedInProfileDisplaysSuppliedReadingsAndRetainsEveryUserAction() {
    render(Page.Profile)
    compose.onNodeWithTag("floating-nav-tab-2").assertIsSelected()
    compose.onNodeWithText(profile.value.nickname).assertIsDisplayed()
    compose.onNodeWithText(profile.value.battery, useUnmergedTree = true).assertIsDisplayed()
    capture("profile-light")

    val editActions = compose.onAllNodesWithContentDescription(text(R.string.profile_edit_nickname))
    editActions.assertCountEquals(2)
    editActions[0].assertHeightIsAtLeast(48.dp).performClick()
    editActions[1].assertWidthIsAtLeast(48.dp).performClick()
    reveal(R.string.profile_switch_vehicle).performClick()
    reveal(R.string.profile_message_center)
    compose.onNodeWithText(
      context.getString(R.string.vector_profile_unread, profile.value.unread),
      useUnmergedTree = true,
    ).assertIsDisplayed()
    compose.onNodeWithText(profile.value.unread.toString(), useUnmergedTree = true).assertIsDisplayed()
    reveal(R.string.profile_message_center).performClick()
    reveal(R.string.profile_about_us).performClick()
    reveal(R.string.common_logout).performClick()

    assertEquals(listOf("avatar", "edit", "vehicle", "messages", "about", "logout"), profileActions)
  }

  @Test
  fun changedReadingsAndSignOutDoNotRetainOldBatteryOrUnreadState() {
    dark.value = true
    render(Page.Profile)
    compose.onNodeWithText(profile.value.battery, useUnmergedTree = true).assertIsDisplayed()

    compose.runOnIdle {
      profile.value = profile.value.copy(battery = "0%", unread = 0, online = false)
    }
    compose.onNodeWithText("0%", useUnmergedTree = true).assertIsDisplayed()
    compose.onNodeWithText("73%", useUnmergedTree = true).assertDoesNotExist()
    capture("profile-dark")
    reveal(R.string.profile_message_center)
    compose.onNodeWithText(text(R.string.vector_profile_messages_note), useUnmergedTree = true).assertIsDisplayed()
    compose.onNodeWithContentDescription(
      context.getString(R.string.vector_profile_unread, 7),
      substring = true,
    ).assertDoesNotExist()
    reveal(R.string.profile_message_center).performClick()

    compose.runOnIdle {
      profile.value = ProfileFixture(
        nickname = text(R.string.profile_login_now),
        phone = text(R.string.profile_login_sync),
        signedIn = false,
        vehicleName = text(R.string.profile_no_vehicle),
        online = false,
        battery = "--",
        // Late message state must never reintroduce a signed-out badge.
        unread = 99,
      )
    }
    scrollToTop()
    compose.onNodeWithText(text(R.string.profile_login_now)).assertIsDisplayed()
    compose.onNodeWithText("--", useUnmergedTree = true).assertIsDisplayed()
    compose.onNodeWithText("0%", useUnmergedTree = true).assertDoesNotExist()
    compose.onNodeWithContentDescription(text(R.string.common_logout)).assertDoesNotExist()
    val loginActions = compose.onAllNodesWithContentDescription(text(R.string.profile_login_now))
    loginActions.assertCountEquals(2)
    loginActions[0].performClick()
    loginActions[1].performClick()
    reveal(R.string.profile_message_center)
    compose.onNodeWithText("99", useUnmergedTree = true).assertDoesNotExist()
    compose.onNodeWithText(text(R.string.vector_profile_messages_note), useUnmergedTree = true).assertIsDisplayed()
    reveal(R.string.profile_message_center).performClick()
    assertEquals(listOf("messages", "avatar", "edit", "messages"), profileActions)
  }

  @Test
  @Config(qualifiers = "en-rUS-w320dp-h640dp-mdpi")
  fun lastServiceActionClearsEitherNavigationBarAtLargeFontScaleAndAcceptsTouches() {
    dark.value = true
    render(Page.Services, fontScale = 1.5f)
    for (floatingStyle in listOf(false, true)) {
      compose.runOnIdle { floating.value = floatingStyle }
      scrollToEnd()
      val action = action(R.string.service_official_account)
      assertActionClearsBar(action)
      capture(if (floatingStyle) "service-compact-floating" else "service-compact-classic")
      assertTextFits(text(R.string.service_official_account))
      assertTextFits(text(R.string.service_official_account_desc))
      action.performTouchInput { click() }
    }
    assertEquals(listOf(Routes.OFFICIAL_CLOUD, Routes.OFFICIAL_CLOUD), routes)
  }

  @Test
  @Config(qualifiers = "en-rUS-w320dp-h640dp-mdpi")
  fun profileLogoutClearsEitherNavigationBarAtLargeFontScaleAndAcceptsTouches() {
    profile.value = profile.value.copy(nickname = "Alex Morgan", vehicleName = "TAILG Super S")
    render(Page.Profile, fontScale = 1.5f)
    for (floatingStyle in listOf(false, true)) {
      compose.runOnIdle { floating.value = floatingStyle }
      scrollToEnd()
      val action = action(R.string.common_logout)
      assertActionClearsBar(action)
      assertTextFits(text(R.string.common_logout))
      action.performTouchInput { click() }
      capture(if (floatingStyle) "profile-compact-floating" else "profile-compact-classic")
    }
    assertEquals(listOf("logout", "logout"), profileActions)
  }

  @Test
  @Config(qualifiers = "en-rUS-w320dp-h640dp-mdpi")
  fun compactHeadingsAndFullChargeRemainReadableWhenChangingTabs() {
    profile.value = profile.value.copy(nickname = "Alex Morgan", vehicleName = "TAILG Super S", battery = "100%")
    render(Page.Services, fontScale = 1.5f)
    capture("service-compact-top")
    val headline = assertTextFits(text(R.string.vector_service_heading))
    assertEquals("The two-part heading must retain its intended line break", 2, headline.lineCount)
    compose.onNodeWithTag("floating-nav-tab-2").performClick().assertIsSelected()
    reveal(R.string.profile_switch_vehicle)
    capture("profile-compact-full-charge")
    val reading = assertTextFits("100%")
    assertEquals("A battery percentage must stay on one line", 1, reading.lineCount)
    reveal(R.string.profile_switch_vehicle).performClick()
    assertEquals(listOf("vehicle"), profileActions)
  }

  private fun render(initialPage: Page, fontScale: Float = 1f) {
    page.value = initialPage
    RuntimeEnvironment.setFontScale(fontScale)
    compose.setContent {
      val scheme = if (dark.value) VectorDarkColorScheme else VectorLightColorScheme
      CompositionLocalProvider(
        LocalDensity provides Density(LocalDensity.current.density, fontScale),
        LocalUiMode provides UiMode.VECTOR,
        LocalCyberPalette provides scheme.toCyberPalette(),
      ) {
        MaterialTheme(colorScheme = scheme, typography = VectorTypography) {
          BottomNavigationScaffold(
            modifier = Modifier.fillMaxSize().testTag("vector-page"),
            bottomBar = {
              TailgBottomNavigation(
                currentIndex = if (page.value == Page.Services) 0 else 2,
                floating = floating.value,
                onSelected = { index ->
                  if (index == 0) page.value = Page.Services
                  if (index == 2) page.value = Page.Profile
                },
              )
            },
          ) {
            Column(
              Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .testTag("vector-page-scroll")
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp + LocalBottomNavigationPadding.current),
            ) {
              if (page.value == Page.Services) {
                VectorServiceContent(vehicleRouteId = vehicleId.value, onNavigate = { routes += it })
              } else {
                val state = profile.value
                VectorProfileContent(
                  nickname = state.nickname,
                  phoneLine = state.phone,
                  memberLabel = text(if (state.signedIn) R.string.profile_logged_in else R.string.profile_guest),
                  signedIn = state.signedIn,
                  vehicleName = state.vehicleName,
                  vehicleOnline = state.online,
                  vehicleStatusLabel = text(
                    if (!state.signedIn) R.string.profile_not_logged_in
                    else if (state.online) R.string.common_online else R.string.common_offline,
                  ),
                  batteryLabel = state.battery,
                  unreadCount = state.unread,
                  onAvatarTap = { profileActions += "avatar" },
                  onEditTap = { profileActions += "edit" },
                  onVehicleTap = { profileActions += "vehicle" },
                  onMessages = { profileActions += "messages" },
                  onAbout = { profileActions += "about" },
                  onLogout = { profileActions += "logout" },
                )
              }
            }
          }
        }
      }
    }
  }

  private fun action(label: Int): SemanticsNodeInteraction =
    compose.onNodeWithContentDescription(text(label), substring = true)

  private fun reveal(label: Int): SemanticsNodeInteraction {
    val target = action(label).performScrollTo()
    val bounds = target.fetchSemanticsNode().boundsInRoot
    val viewport = compose.onNodeWithTag("vector-page-scroll").fetchSemanticsNode().boundsInRoot
    val barTag = if (floating.value) "floating-bottom-bar" else "classic-bottom-bar"
    val bar = compose.onNodeWithTag(barTag).fetchSemanticsNode().boundsInRoot
    // performScrollTo only understands the full viewport, which includes the
    // transparent bottom bar. Scroll farther just as a user would before tapping.
    val preferredBottom = viewport.top + (bar.top - viewport.top) * 0.65f
    if (bounds.bottom > preferredBottom) scrollBy(bounds.bottom - preferredBottom)
    assertActionClearsBar(target)
    return target
  }

  private fun scrollToTop() = scrollBy(-100_000f)

  private fun scrollToEnd() = scrollBy(100_000f)

  private fun scrollBy(distance: Float) {
    compose.onNodeWithTag("vector-page-scroll").performSemanticsAction(SemanticsActions.ScrollBy) {
      it(0f, distance)
    }
  }

  private fun assertActionClearsBar(action: SemanticsNodeInteraction) {
    action.assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
    val actionBounds = action.fetchSemanticsNode().boundsInRoot
    val barTag = if (floating.value) "floating-bottom-bar" else "classic-bottom-bar"
    val barBounds = compose.onNodeWithTag(barTag).fetchSemanticsNode().boundsInRoot
    assertTrue("The entire final action must scroll above the navigation bar", actionBounds.bottom <= barBounds.top)
  }

  private fun assertTextFits(value: String): TextLayoutResult {
    val layouts = mutableListOf<TextLayoutResult>()
    compose.onNode(
      hasText(value) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
      useUnmergedTree = true,
    )
      .assertIsDisplayed()
      .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    assertTrue("The visible text must expose a measured layout: $value", layouts.isNotEmpty())
    assertTrue(
      "The full text must remain readable: $value; " + layouts.joinToString { layout ->
        "size=${layout.size}, paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}, " +
          "lines=${layout.lineCount}, widthOverflow=${layout.didOverflowWidth}, heightOverflow=${layout.didOverflowHeight}"
      },
      layouts.none { it.hasVisualOverflow },
    )
    return layouts.single()
  }

  private fun text(id: Int): String = context.getString(id)

  /** Native renders are review artifacts only; behavior assertions are the tests. */
  private fun capture(name: String) {
    compose.waitForIdle()
    val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
    val directory = File("build/reports/vector-design").apply { mkdirs() }
    File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  private enum class Page { Services, Profile }

  private data class ProfileFixture(
    val nickname: String = "林间",
    val phone: String = "138****9027",
    val signedIn: Boolean = true,
    val vehicleName: String = "台铃 超能 S",
    val online: Boolean = true,
    val battery: String = "73%",
    val unread: Int = 7,
  )
}
