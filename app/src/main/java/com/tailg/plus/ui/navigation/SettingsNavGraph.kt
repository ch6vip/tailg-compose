package com.tailg.plus.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.preferences.AppPreferencesService
import com.tailg.plus.data.store.VehicleStore
import com.tailg.plus.log.LogService
import com.tailg.plus.ui.screens.AboutAppScreen
import com.tailg.plus.ui.screens.CloudTokenScreen
import com.tailg.plus.ui.screens.LanguageSettingsScreen
import com.tailg.plus.ui.screens.LogScreen
import com.tailg.plus.ui.screens.NotificationPrefsScreen
import com.tailg.plus.ui.screens.OfficialCloudScreen
import com.tailg.plus.ui.screens.OfficialReplicaScreen
import com.tailg.plus.ui.screens.ProfileMineScreen
import com.tailg.plus.ui.screens.SettingsScreen
import com.tailg.plus.ui.screens.ThemeSettingsScreen
import com.tailg.plus.ui.screens.UnitSettingsScreen

/**
 * Settings and profile navigation graph.
 */
fun NavGraphBuilder.settingsNavGraph(
    navController: NavController,
    cloudService: OfficialCloudService,
    preferencesService: AppPreferencesService,
    logService: LogService,
    vehicleRouteId: String,
    vehicleStore: VehicleStore,
    connectionManager: ConnectionManager,
) {
    composable(Routes.PROFILE_MINE) {
        ProfileMineScreen(
            cloudService = cloudService,
            onNavigate = { route -> navController.navigate(route) },
            onBack = { navController.popBackStack() },
            onSignedOut = {
                navController.navigate(Routes.LOGIN) {
                    launchSingleTop = true
                    popUpTo(Routes.CONTROL) { inclusive = true }
                }
            },
        )
    }
    composable(Routes.SETTINGS) {
        SettingsScreen(
            vehicleRouteId = vehicleRouteId,
            preferencesService = preferencesService,
            onBack = { navController.popBackStack() },
            onNavigate = { route -> navController.navigate(route) },
            showBack = false,
        )
    }
    composable(Routes.APP_PREFERENCES) {
        LanguageSettingsScreen(
            preferencesService = preferencesService,
            onBack = { navController.popBackStack() },
        )
    }
    composable(Routes.UNIT_SETTINGS) {
        UnitSettingsScreen(
            preferencesService = preferencesService,
            onBack = { navController.popBackStack() },
        )
    }
    composable(Routes.ABOUT_APP) {
        AboutAppScreen(
            vehicleRouteId = vehicleRouteId,
            onBack = { navController.popBackStack() },
            onNavigate = { route -> navController.navigate(route) },
        )
    }
    composable(Routes.THEME) {
        ThemeSettingsScreen(
            preferencesService = preferencesService,
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
        )
    }
    composable(Routes.LOG) {
        LogScreen(
            cloudService = cloudService,
            logService = logService,
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
            vehicleStore = vehicleStore,
            connectionManager = connectionManager,
            onBack = { navController.popBackStack() },
        )
    }
}