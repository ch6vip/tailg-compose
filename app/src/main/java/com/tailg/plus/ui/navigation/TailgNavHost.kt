package com.tailg.plus.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tailg.plus.ui.components.AppSnackbarHost
import com.tailg.plus.ui.components.VoidOrbitalNav
import com.tailg.plus.ui.screens.AboutAppScreen
import com.tailg.plus.ui.screens.AddVehicleScreen
import com.tailg.plus.ui.screens.BatteryDetailsScreen
import com.tailg.plus.ui.screens.BindImeiScreen
import com.tailg.plus.ui.screens.CloudTokenScreen
import com.tailg.plus.ui.screens.ControlScreen
import com.tailg.plus.ui.screens.DiagnosticScreen
import com.tailg.plus.ui.screens.FirmwareOtaScreen
import com.tailg.plus.ui.screens.GarageCodeScannerScreen
import com.tailg.plus.ui.screens.GarageScreen
import com.tailg.plus.ui.screens.InductionSettingsScreen
import com.tailg.plus.ui.screens.LanguageSettingsScreen
import com.tailg.plus.ui.screens.LocationScreen
import com.tailg.plus.ui.screens.LoginScreen
import com.tailg.plus.ui.screens.LogScreen
import com.tailg.plus.ui.screens.NotificationPrefsScreen
import com.tailg.plus.ui.screens.OfficialCloudScreen
import com.tailg.plus.ui.screens.OfficialReplicaScreen
import com.tailg.plus.ui.screens.ProfileMineScreen
import com.tailg.plus.ui.screens.QgjSettingsScreen
import com.tailg.plus.ui.screens.ReplaceBatteryScreen
import com.tailg.plus.ui.screens.RideStatsScreen
import com.tailg.plus.ui.screens.ScanScreen
import com.tailg.plus.ui.screens.ServiceHubScreen
import com.tailg.plus.ui.screens.SettingsScreen
import com.tailg.plus.ui.screens.UnitSettingsScreen
import com.tailg.plus.ui.screens.VehicleMessageScreen
import com.tailg.plus.ui.screens.VehicleSettingsScreen
import com.tailg.plus.ui.theme.AppColors
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import com.tailg.plus.di.rememberTailgEntryPoint

/**
 * Root navigation host — wires all 30 screens into a single NavHost.
 *
 * Bottom-nav destinations (服务 / 控车 / 我的) share the [VoidOrbitalNav] bar;
 * all other routes are full-screen pushes without the bottom bar.
 */
@Composable
fun TailgNavHost() {
  val navController = rememberNavController()
  val snackbarHostState = remember { SnackbarHostState() }
  val entryPoint = rememberTailgEntryPoint()
  val cloudService = entryPoint.cloudService()

  // Bootstrap once (Dart `main()`): restore the persisted session, bind MQTT
  // to the cloud state and register the logout channel teardown. The first
  // frame stays a blank page until the session settles so the start
  // destination is chosen with the restored sign-in state.
  var bootstrapped by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    val mqttService = entryPoint.mqttService()
    val connectionManager = entryPoint.connectionManager()
    mqttService.attachToCloud(cloudService)
    if (cloudService.afterLogoutSideEffects.isEmpty()) {
      cloudService.afterLogoutSideEffects += {
        mqttService.disconnect()
        connectionManager.disconnect()
      }
    }
    cloudService.init()
    bootstrapped = true
  }
  if (!bootstrapped) {
    Box(modifier = Modifier.fillMaxSize().background(AppColors.pageBg))
    return
  }
  val startDestination = if (cloudService.currentState.signedIn) Routes.SERVICE_HUB else Routes.LOGIN

  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route

  val showBottomBar = currentRoute in setOf(
    Routes.SERVICE_HUB,
    Routes.CONTROL,
    Routes.PROFILE_MINE,
  )

  Scaffold(
    containerColor = AppColors.pageBg,
    contentWindowInsets = WindowInsets.systemBars,
    snackbarHost = { AppSnackbarHost(snackbarHostState) },
    bottomBar = {
      if (showBottomBar) {
        val index = when (currentRoute) {
          Routes.SERVICE_HUB -> 0
          Routes.CONTROL -> 1
          Routes.PROFILE_MINE -> 2
          else -> 0
        }
        VoidOrbitalNav(
          currentIndex = index,
          onService = { navController.navigate(Routes.SERVICE_HUB) { launchSingleTop = true } },
          onVehicle = { navController.navigate(Routes.CONTROL) { launchSingleTop = true } },
          onMine = { navController.navigate(Routes.PROFILE_MINE) { launchSingleTop = true } },
        )
      }
    },
  ) { innerPadding ->
    NavHost(
      navController = navController,
      startDestination = startDestination,
      modifier = Modifier.fillMaxSize(),
    ) {
      // ---- Auth ----
      composable(Routes.LOGIN) {
        LoginScreen(
          cloudService = cloudService,
          onSignedIn = {
            navController.navigate(Routes.SERVICE_HUB) {
              launchSingleTop = true
              popUpTo(Routes.LOGIN) { inclusive = true }
            }
          },
        )
      }

      // ---- Bottom-nav: 服务 ----
      composable(Routes.SERVICE_HUB) {
        ServiceHubScreen(
          onNavigate = { route -> navController.navigate(route) },
        )
      }

      // ---- Bottom-nav: 控车 ----
      composable(
        Routes.CONTROL,
        arguments = listOf(navArgument(Routes.ARG_VEHICLE_ID) { type = NavType.StringType }),
      ) { entry ->
        ControlScreen(
          cloudService = cloudService,
          connectionManager = entryPoint.connectionManager(),
          mqttService = entryPoint.mqttService(),
          vehicleStore = entryPoint.vehicleStore(),
          onBack = { navController.popBackStack() },
          onNavigate = { route -> navController.navigate(route) },
        )
      }

      // ---- Bottom-nav: 我的 ----
      composable(Routes.PROFILE_MINE) {
        ProfileMineScreen(
          cloudService = cloudService,
          onNavigate = { route -> navController.navigate(route) },
          onBack = { navController.popBackStack() },
          onSignedOut = {
            navController.navigate(Routes.LOGIN) {
              launchSingleTop = true
              popUpTo(Routes.SERVICE_HUB) { inclusive = true }
            }
          },
        )
      }

      // ---- Vehicle management ----
      composable(Routes.GARAGE) {
        GarageScreen(
          cloudService = cloudService,
          onBack = { navController.popBackStack() },
          onNavigate = { route -> navController.navigate(route) },
        )
      }
      composable(Routes.ADD_VEHICLE) {
        AddVehicleScreen(
          onBack = { navController.popBackStack() },
          onOpenOfficialVehicles = { navController.navigate(Routes.OFFICIAL_CLOUD) },
          onOpenImeiBind = { navController.navigate(Routes.BIND_IMEI) },
          onOpenBleScan = { navController.navigate(Routes.SCAN) },
        )
      }
      composable(Routes.BIND_IMEI) {
        BindImeiScreen(
          cloudService = cloudService,
          onBack = { _ -> navController.popBackStack() },
        )
      }
      composable(Routes.SCAN) {
        ScanScreen(
          onBack = { navController.popBackStack() },
          onConnectDevice = { _, _ -> navController.popBackStack() },
        )
      }
      composable(Routes.GARAGE_CODE_SCANNER) {
        GarageCodeScannerScreen(
          onBack = { navController.popBackStack() },
          onScanned = { _ -> navController.popBackStack() },
        )
      }

      // ---- Vehicle detail screens ----
      composable(
        Routes.LOCATION,
        arguments = listOf(navArgument(Routes.ARG_VEHICLE_ID) { type = NavType.StringType }),
      ) { entry ->
        LocationScreen(
          cloudService = cloudService,
          vehicleStore = entryPoint.vehicleStore(),
          onBack = { navController.popBackStack() },
        )
      }
      composable(
        Routes.BATTERY_DETAILS,
        arguments = listOf(navArgument(Routes.ARG_VEHICLE_ID) { type = NavType.StringType }),
      ) { entry ->
        BatteryDetailsScreen(
          cloudService = cloudService,
          connectionManager = entryPoint.connectionManager(),
          onBack = { navController.popBackStack() },
          onNavigate = { route -> navController.navigate(route) },
        )
      }
      composable(
        Routes.REPLACE_BATTERY,
        arguments = listOf(navArgument(Routes.ARG_VEHICLE_ID) { type = NavType.StringType }),
      ) { entry ->
        ReplaceBatteryScreen(
          cloudService = cloudService,
          onBack = { _ -> navController.popBackStack() },
        )
      }
      composable(
        Routes.RIDE_STATS,
        arguments = listOf(navArgument(Routes.ARG_VEHICLE_ID) { type = NavType.StringType }),
      ) { entry ->
        RideStatsScreen(
          vehicleId = entry.arguments?.getString(Routes.ARG_VEHICLE_ID) ?: "",
          cloudService = cloudService,
          onBack = { navController.popBackStack() },
        )
      }
      composable(
        Routes.VEHICLE_MESSAGE,
        arguments = listOf(navArgument(Routes.ARG_VEHICLE_ID) { type = NavType.StringType }),
      ) { entry ->
        VehicleMessageScreen(
          vehicleId = entry.arguments?.getString(Routes.ARG_VEHICLE_ID) ?: "",
          cloudService = cloudService,
          onBack = { navController.popBackStack() },
        )
      }
      composable(
        Routes.VEHICLE_SETTINGS,
        arguments = listOf(navArgument(Routes.ARG_VEHICLE_ID) { type = NavType.StringType }),
      ) { entry ->
        VehicleSettingsScreen(
          cloudService = cloudService,
          onBack = { navController.popBackStack() },
          onOpenNotificationPrefs = { navController.navigate(Routes.NOTIFICATION_PREFS) },
          onOpenInductionSettings = {
            val vid = entry.arguments?.getString(Routes.ARG_VEHICLE_ID) ?: ""
            navController.navigate(Routes.inductionSettings(vid))
          },
          onAddVehicle = { navController.navigate(Routes.ADD_VEHICLE) },
        )
      }
      composable(
        Routes.FIRMWARE_OTA,
        arguments = listOf(navArgument(Routes.ARG_VEHICLE_ID) { type = NavType.StringType }),
      ) { entry ->
        FirmwareOtaScreen(
          onBack = { navController.popBackStack() },
          cloudService = cloudService,
        )
      }
      composable(
        Routes.DIAGNOSTIC,
        arguments = listOf(navArgument(Routes.ARG_VEHICLE_ID) { type = NavType.StringType }),
      ) { entry ->
        DiagnosticScreen(
          onBack = { navController.popBackStack() },
        )
      }
      composable(
        Routes.QGJ_SETTINGS,
        arguments = listOf(navArgument(Routes.ARG_VEHICLE_ID) { type = NavType.StringType }),
      ) { entry ->
        QgjSettingsScreen(
          vehicleId = entry.arguments?.getString(Routes.ARG_VEHICLE_ID) ?: "",
          onBack = { navController.popBackStack() },
        )
      }
      composable(
        Routes.INDUCTION_SETTINGS,
        arguments = listOf(navArgument(Routes.ARG_VEHICLE_ID) { type = NavType.StringType }),
      ) { entry ->
        InductionSettingsScreen(
          vehicleId = entry.arguments?.getString(Routes.ARG_VEHICLE_ID) ?: "",
          cloudService = cloudService,
          onBack = { navController.popBackStack() },
        )
      }

      // ---- Settings & profile ----
      composable(Routes.SETTINGS) {
        SettingsScreen(
          onBack = { navController.popBackStack() },
          onNavigate = { route -> navController.navigate(route) },
        )
      }
      composable(Routes.APP_PREFERENCES) {
        LanguageSettingsScreen(
          onBack = { navController.popBackStack() },
        )
      }
      composable(Routes.NOTIFICATION_PREFS) {
        NotificationPrefsScreen(
          cloudService = cloudService,
          onBack = { navController.popBackStack() },
        )
      }
      composable(Routes.CLOUD_TOKEN) {
        CloudTokenScreen(
          onBack = { navController.popBackStack() },
          cloudService = cloudService,
        )
      }
      composable(Routes.LOG) {
        LogScreen(
          cloudService = cloudService,
          onBack = { navController.popBackStack() },
        )
      }
      composable(Routes.OFFICIAL_CLOUD) {
        OfficialCloudScreen(
          cloudService = cloudService,
          onBack = { navController.popBackStack() },
          onNavigate = { route -> navController.navigate(route) },
        )
      }
      composable(Routes.OFFICIAL_REPLICA) {
        OfficialReplicaScreen(
          cloudService = cloudService,
          vehicleStore = entryPoint.vehicleStore(),
          connectionManager = entryPoint.connectionManager(),
          onBack = { navController.popBackStack() },
        )
      }
    }
  }
}
