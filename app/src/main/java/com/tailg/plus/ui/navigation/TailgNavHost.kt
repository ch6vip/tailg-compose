package com.tailg.plus.ui.navigation

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tailg.plus.ui.components.AppSnackbarHost
import com.tailg.plus.ui.components.VoidOrbitalNav
import com.tailg.plus.ui.screens.MainViewModel
import com.tailg.plus.ui.theme.CyberHomeColors
import com.tailg.plus.ui.theme.LocalDistanceUnitPreference
import com.tailg.plus.data.cloud.OfficialCloudState
import com.tailg.plus.data.preferences.AppLanguagePreference
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.Locale

/**
 * Root navigation host — wires all screens into a single NavHost through
 * sub-graphs:
 * - [authNavGraph] — login
 * - [vehicleNavGraph] — garage, control, vehicle detail screens
 * - [settingsNavGraph] — profile, settings, about screens
 *
 * Bottom-nav destinations (服务 / 控车 / 我的) share the [VoidOrbitalNav] bar;
 * all other routes are full-screen pushes without the bottom bar.
 */
@Composable
fun TailgNavHost() {
  val vm: MainViewModel = hiltViewModel()
  val preferences = vm.appPreferences
  val respectTextScale by preferences.respectSystemTextScale.collectAsStateWithLifecycle()
  val language by preferences.language.collectAsStateWithLifecycle()
  val distanceUnit by preferences.distanceUnit.collectAsStateWithLifecycle()
  LaunchedEffect(Unit) {
    preferences.init()
  }
  val baseContext = LocalContext.current
  val baseConfiguration = LocalConfiguration.current
  val localizedConfiguration = remember(baseConfiguration, language) {
    baseConfiguration.withAppLanguage(language)
  }
  val localizedContext = remember(baseContext, localizedConfiguration, language) {
    if (language == AppLanguagePreference.System) {
      baseContext
    } else {
      baseContext.createConfigurationContext(localizedConfiguration)
    }
  }
  val baseDensity = LocalDensity.current
  val effectiveDensity = remember(baseDensity, respectTextScale) {
    Density(
      density = baseDensity.density,
      fontScale = resolveAppFontScale(baseDensity.fontScale, respectTextScale),
    )
  }

  CompositionLocalProvider(
    LocalContext provides localizedContext,
    LocalConfiguration provides localizedConfiguration,
    LocalDensity provides effectiveDensity,
    LocalDistanceUnitPreference provides distanceUnit,
  ) {
    TailgNavHostContent(vm)
  }
}

private fun Configuration.withAppLanguage(language: AppLanguagePreference): Configuration =
  Configuration(this).apply {
    if (language != AppLanguagePreference.System) {
      val locale = Locale.forLanguageTag(language.value)
      setLocale(locale)
      setLayoutDirection(locale)
    }
  }

internal fun resolveAppFontScale(systemFontScale: Float, respectSystemTextScale: Boolean): Float =
  if (respectSystemTextScale) systemFontScale.coerceIn(0.85f, 1.5f) else 1f

@Composable
private fun TailgNavHostContent(vm: MainViewModel) {
  val navController = rememberNavController()
  val context = androidx.compose.ui.platform.LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  val cloudService = vm.cloudService
  // Narrow cloud projection — the nav scaffold only needs signed-in status and
  // the selected vehicle key. Collecting the whole `stateFlow` here used to
  // recompose the entire scaffold / bottom bar / NavHost on EVERY cloud
  // emission (messages, travel, loading flips, battery refreshes). The
  // remembered `map`+`distinctUntilChanged` chain (per ControlScreen) drops
  // emissions that leave these two fields unchanged.
  val navCloudSlice by remember(cloudService) {
    cloudService.stateFlow
      .map { state -> NavCloudSlice(state.signedIn, state.selectedVehicle?.key) }
      .distinctUntilChanged()
  }.collectAsStateWithLifecycle(initialValue = NavCloudSlice(
    cloudService.currentState.signedIn,
    cloudService.currentState.selectedVehicle?.key,
  ))

  // The nav scaffold consumes three fields only. Reading them through
  // derivedStateOf keeps an unrelated cloudState emission (messages, travel,
  // loading flags, battery refreshes …) from recomposing the whole Scaffold /
  // bottom bar / NavHost — that double recomposition amplified every state
  // change on the control page into a full-tree pass.
  val navSignedIn by remember { derivedStateOf { navCloudSlice.signedIn } }
  val navSelectedVehicleKey by remember { derivedStateOf { navCloudSlice.selectedVehicleKey } }
  val navVehicleRouteId by remember {
    derivedStateOf { navSelectedVehicleKey?.takeIf { it.isNotBlank() } ?: "current" }
  }

  // Bootstrap once: restore the persisted session, bind MQTT to the cloud state.
  var bootstrapped by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    vm.mqttService.attachToCloud(cloudService)
    try {
      cloudService.init()
    } catch (e: Exception) {
      Timber.tag("TailgNavHost").w(e, "cloud session restore failed")
    }
    bootstrapped = true
  }
  if (!bootstrapped) {
    Box(modifier = Modifier.fillMaxSize().background(CyberHomeColors.pageBg))
    return
  }

  val startDestination = remember(bootstrapped) {
    if (navSignedIn) Routes.vehicleHome(navSelectedVehicleKey) else Routes.LOGIN
  }

  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route

  val showBottomBar = currentRoute in setOf(
    Routes.SERVICE_HUB,
    Routes.CONTROL,
    Routes.PROFILE_MINE,
    Routes.SETTINGS,
  )
  val vehicleRouteId = navVehicleRouteId

  fun navigateBottomTab(route: String) {
    navController.navigate(route) {
      launchSingleTop = true
      popUpTo(Routes.CONTROL) { saveState = true }
      restoreState = true
    }
  }

  Scaffold(
    containerColor = CyberHomeColors.pageBg,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    snackbarHost = { AppSnackbarHost(snackbarHostState) },
    bottomBar = {
      if (showBottomBar) {
        val index = when (currentRoute) {
          Routes.SERVICE_HUB -> 0
          Routes.CONTROL -> 1
          Routes.PROFILE_MINE -> 2
          Routes.SETTINGS -> 3
          else -> 0
        }
        VoidOrbitalNav(
          currentIndex = index,
          onService = { navigateBottomTab(Routes.SERVICE_HUB) },
          onVehicle = { navigateBottomTab(Routes.vehicleHome(vehicleRouteId)) },
          onMine = { navigateBottomTab(Routes.PROFILE_MINE) },
          onSettings = { navigateBottomTab(Routes.SETTINGS) },
        )
      }
    },
  ) { innerPadding ->
    NavHost(
      navController = navController,
      startDestination = startDestination,
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(CyberHomeColors.pageBg),
    ) {
      // ---- Auth graph ----
      authNavGraph(
        navController = navController,
        cloudService = cloudService,
        snackbarHostState = snackbarHostState,
      )

      // ---- Vehicle graph ----
      vehicleNavGraph(
        navController = navController,
        cloudService = cloudService,
        connectionManager = vm.connectionManager,
        mqttService = vm.mqttService,
        vehicleStore = vm.vehicleStore,
        inductionService = vm.inductionModeService,
        manualModeService = vm.manualModeService,
        vehicleRouteId = vehicleRouteId,
        context = context,
      )

      // ---- Settings & profile graph ----
      settingsNavGraph(
        navController = navController,
        cloudService = cloudService,
        preferencesService = vm.appPreferences,
        logService = vm.logService,
        vehicleRouteId = vehicleRouteId,
        vehicleStore = vm.vehicleStore,
        connectionManager = vm.connectionManager,
      )
    }
  }
}

/**
 * The slice of [OfficialCloudState] the nav scaffold reads. A plain data
 * class so `distinctUntilChanged` compares by value: any emission that leaves
 * signed-in status and the selected vehicle key unchanged is dropped before
 * the scaffold / bottom bar / NavHost recompose.
 */
private data class NavCloudSlice(
  val signedIn: Boolean,
  val selectedVehicleKey: String?,
)