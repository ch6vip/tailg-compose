package com.tailg.plus.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tailg.plus.ui.components.AppSnackbarHost
import com.tailg.plus.ui.components.VoidOrbitalNav
import com.tailg.plus.ui.screens.AddVehicleScreen
import com.tailg.plus.ui.screens.AppPreferencesScreen
import com.tailg.plus.ui.screens.BatteryDetailsScreen
import com.tailg.plus.ui.screens.BindImeiScreen
import com.tailg.plus.ui.screens.CloudTokenScreen
import com.tailg.plus.ui.screens.ControlScreen
import com.tailg.plus.ui.screens.DiagnosticScreen
import com.tailg.plus.ui.screens.FirmwareOtaScreen
import com.tailg.plus.ui.screens.GarageCodeScannerScreen
import com.tailg.plus.ui.screens.GarageScreen
import com.tailg.plus.ui.screens.InductionSettingsScreen
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
import com.tailg.plus.ui.screens.VehicleMessageScreen
import com.tailg.plus.ui.screens.VehicleSettingsScreen
import com.tailg.plus.ui.theme.AppColors
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars

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
      startDestination = Routes.LOGIN,
      modifier = Modifier.fillMaxSize(),
    ) {
      // ---- Auth ----
      composable(Routes.LOGIN) {
        LoginScreen(
          onSignedIn = {
            navController.navigate(Routes.SERVICE_HUB) {
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
          vehicleId = entry.arguments?.getString(Routes.ARG_VEHICLE_ID) ?: "",
          onBack = { navController.popBackStack() },
          onNavigate = { route -> navController.navigate(route) },
        )
      }

      // ---- Bottom-nav: 我的 ----
      composable(Routes.PROFILE_MINE) {
        ProfileMineScreen(
          onNavigate = { route -> navController.navigate(route) },
          onBack = { navController.popBackStack() },
        )
      }

      // ---- Vehicle management ----
      composable(Routes.GARAGE) {
        GarageScreen(
          onBack = { navController.popBackStack() },
          onNavigate = { route -> navController.navigate(route) },
        )
      }
      composable(Routes.ADD_VEHICLE) {
        AddVehicleScreen(
          onBack = { navController.popBackStack() },
          onNavigate = { route -> navController.navigate(route) },
        )
      }
      composable(Routes.BIND_IMEI) {
        BindImeiScreen(
          onBack = { navController.popBackStack() },
          onNavigate = { route -> navController.navigate(route) },
        )
      }
      composable(Routes.SCAN) {
        ScanScreen(
          onBack = { navController.popBackStack() },
          onNavigate = { route -> navController.navigate(route) },
        )
      }
      composable(Routes.GARAGE_CODE_SCANNER) {
        GarageCodeScannerScreen(
          onBack = { navController.popBackStack() },
          onNavigate = { route -> navController.navigate(route) },
        )
      }

      // ---- Vehicle detail screens ----
      composable(
        Routes.LOCATION,
        arguments = listOf(navArgument(Routes.ARG_VEHICLE_ID) { type = NavType.StringType }),
      ) { entry ->
        LocationScreen(
          vehicleId = entry.arguments?.getString(Routes.ARG_VEHICLE_ID) ?: "",
          onBack = { navController.popBackStack() },
        )
      }
      composable(
        Routes.BATTERY_DETAILS,
        arguments = listOf(navArgument(Routes.ARG_VEHICLE_ID) { type = NavType.StringType }),
      ) { entry ->
        BatteryDetailsScreen(
          vehicleId = entry.arguments?.getString(Routes.ARG_VEHICLE_ID) ?: "",
          onBack = { navController.popBackStack() },
        )
      }
      composable(
        Routes.REPLACE_BATTERY,
        arguments = listOf(navArgument(Routes.ARG_VEHICLE_ID) { type = NavType.StringType }),
      ) { entry ->
        ReplaceBatteryScreen(
          vehicleId = entry.arguments?.getString(Routes.ARG_VEHICLE_ID) ?: "",
          onBack = { navController.popBackStack() },
        )
      }
      composable(
        Routes.RIDE_STATS,
        arguments = listOf(navArgument(Routes.ARG_VEHICLE_ID) { type = NavType.StringType }),
      ) { entry ->
        RideStatsScreen(
          vehicleId = entry.arguments?.getString(Routes.ARG_VEHICLE_ID) ?: "",
          onBack = { navController.popBackStack() },
        )
      }
      composable(
        Routes.VEHICLE_MESSAGE,
        arguments = listOf(navArgument(Routes.ARG_VEHICLE_ID) { type = NavType.StringType }),
      ) { entry ->
        VehicleMessageScreen(
          vehicleId = entry.arguments?.getString(Routes.ARG_VEHICLE_ID) ?: "",
          onBack = { navController.popBackStack() },
        )
      }
      composable(
        Routes.VEHICLE_SETTINGS,
        arguments = listOf(navArgument(Routes.ARG_VEHICLE_ID) { type = NavType.StringType }),
      ) { entry ->
        VehicleSettingsScreen(
          vehicleId = entry.arguments?.getString(Routes.ARG_VEHICLE_ID) ?: "",
          onBack = { navController.popBackStack() },
        )
      }
      composable(
        Routes.FIRMWARE_OTA,
        arguments = listOf(navArgument(Routes.ARG_VEHICLE_ID) { type = NavType.StringType }),
      ) { entry ->
        FirmwareOtaScreen(
          vehicleId = entry.arguments?.getString(Routes.ARG_VEHICLE_ID) ?: "",
          onBack = { navController.popBackStack() },
        )
      }
      composable(
        Routes.DIAGNOSTIC,
        arguments = listOf(navArgument(Routes.ARG_VEHICLE_ID) { type = NavType.StringType }),
      ) { entry ->
        DiagnosticScreen(
          vehicleId = entry.arguments?.getString(Routes.ARG_VEHICLE_ID) ?: "",
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
        AppPreferencesScreen(
          onBack = { navController.popBackStack() },
        )
      }
      composable(Routes.NOTIFICATION_PREFS) {
        NotificationPrefsScreen(
          onBack = { navController.popBackStack() },
        )
      }
      composable(Routes.CLOUD_TOKEN) {
        CloudTokenScreen(
          onBack = { navController.popBackStack() },
        )
      }
      composable(Routes.LOG) {
        LogScreen(
          onBack = { navController.popBackStack() },
        )
      }
      composable(Routes.OFFICIAL_CLOUD) {
        OfficialCloudScreen(
          onBack = { navController.popBackStack() },
        )
      }
      composable(Routes.OFFICIAL_REPLICA) {
        OfficialReplicaScreen(
          onBack = { navController.popBackStack() },
        )
      }
    }
  }
}
