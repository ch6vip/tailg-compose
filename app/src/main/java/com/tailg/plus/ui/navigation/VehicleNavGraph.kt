package com.tailg.plus.ui.navigation

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.data.ble.platform.OfficialBleConnectionContext
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.mqtt.OfficialMqttService
import com.tailg.plus.data.store.VehicleStore
import com.tailg.plus.ui.screens.AddVehicleScreen
import com.tailg.plus.ui.screens.BatteryDetailsScreen
import com.tailg.plus.ui.screens.BindImeiScreen
import com.tailg.plus.ui.screens.ControlScreen
import com.tailg.plus.ui.screens.DiagnosticScreen
import com.tailg.plus.ui.screens.FirmwareOtaScreen
import com.tailg.plus.ui.screens.GarageCodeScannerScreen
import com.tailg.plus.ui.screens.GarageScreen
import com.tailg.plus.ui.screens.InductionSettingsScreen
import com.tailg.plus.ui.screens.LocationInitialTab
import com.tailg.plus.ui.screens.LocationScreen
import com.tailg.plus.ui.screens.QgjSettingsScreen
import com.tailg.plus.ui.screens.ReplaceBatteryScreen
import com.tailg.plus.ui.screens.RideStatsScreen
import com.tailg.plus.ui.screens.ScanScreen
import com.tailg.plus.ui.screens.ServiceHubScreen
import com.tailg.plus.ui.screens.VehicleMessageScreen
import com.tailg.plus.ui.screens.VehicleSettingsScreen
import timber.log.Timber
import kotlinx.coroutines.launch

/**
 * Vehicle-related navigation graph — garage, control, and vehicle detail screens.
 */
fun NavGraphBuilder.vehicleNavGraph(
    navController: NavController,
    cloudService: OfficialCloudService,
    connectionManager: ConnectionManager,
    mqttService: OfficialMqttService,
    vehicleStore: VehicleStore,
    vehicleRouteId: String,
    context: Context,
) {
    // ---- Bottom-nav: 服务 ----
    composable(Routes.SERVICE_HUB) {
        ServiceHubScreen(
            vehicleRouteId = vehicleRouteId,
            onNavigate = { route -> navController.navigate(route) },
        )
    }

    // ---- Bottom-nav: 控车 ----
    composable(
        Routes.CONTROL,
        arguments = listOf(navArgument(Routes.ARG_VEHICLE_ID) { type = NavType.StringType }),
    ) {
        ControlScreen(
            cloudService = cloudService,
            connectionManager = connectionManager,
            mqttService = mqttService,
            vehicleStore = vehicleStore,
            onBack = { navController.popBackStack() },
            onNavigate = { route -> navController.navigate(route) },
        )
    }

    // ---- Vehicle management ----
    composable(Routes.GARAGE) { entry ->
        val scannedCode by entry.savedStateHandle.getStateFlow<String?>("scanned_vehicle_code", null)
            .collectAsState()
        GarageScreen(
            cloudService = cloudService,
            onBack = { navController.popBackStack() },
            onNavigate = { route -> navController.navigate(route) },
            scannedCode = scannedCode,
            onConsumeScan = { entry.savedStateHandle.remove<String>("scanned_vehicle_code") },
            mqttService = mqttService,
            connectionManager = connectionManager,
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
            onBack = { navController.popBackStack() },
        )
    }
    composable(Routes.SCAN) {
        val scanScope = rememberCoroutineScope()
        ScanScreen(
            onBack = { navController.popBackStack() },
            onConnectDevice = { deviceId, deviceName ->
                scanScope.launch {
                    try {
                        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
                        val device = manager?.adapter?.getRemoteDevice(deviceId)
                        if (device != null) {
                            val state = cloudService.currentState
                            val ctx = state.selectedVehicle?.let {
                                OfficialBleConnectionContext.fromVehicle(it, state.userId)
                            }
                            connectionManager.connect(device, ctx)
                            if (deviceName.isNotEmpty()) {
                                Timber.tag("TailgNavHost").i("BLE connected: $deviceName")
                            }
                        }
                    } catch (e: SecurityException) {
                        Timber.tag("TailgNavHost").w(e, "BLE connect missing permission")
                    } catch (e: Exception) {
                        Timber.tag("TailgNavHost").w(e, "BLE connect failed")
                    } finally {
                        navController.popBackStack()
                    }
                }
            },
        )
    }
    composable(Routes.GARAGE_CODE_SCANNER) {
        GarageCodeScannerScreen(
            onBack = { navController.popBackStack() },
            onScanned = { value ->
                navController.previousBackStackEntry?.savedStateHandle?.set("scanned_vehicle_code", value)
                navController.popBackStack()
            },
        )
    }

    // ---- Vehicle detail screens ----
    composable(
        Routes.LOCATION,
        arguments = listOf(
            navArgument(Routes.ARG_VEHICLE_ID) { type = NavType.StringType },
            navArgument("tab") {
                type = NavType.StringType
                defaultValue = "map"
            },
        ),
    ) { entry ->
        LocationScreen(
            cloudService = cloudService,
            vehicleStore = vehicleStore,
            initialTab = when (entry.arguments?.getString("tab")) {
                "travel" -> LocationInitialTab.TRAVEL
                "fence" -> LocationInitialTab.FENCE
                else -> LocationInitialTab.MAP
            },
            onBack = { navController.popBackStack() },
        )
    }
    composable(
        Routes.BATTERY_DETAILS,
        arguments = listOf(navArgument(Routes.ARG_VEHICLE_ID) { type = NavType.StringType }),
    ) { entry ->
        val batteryChanged by entry.savedStateHandle.getStateFlow<Boolean?>("battery_changed", null)
            .collectAsState()
        BatteryDetailsScreen(
            cloudService = cloudService,
            connectionManager = connectionManager,
            onBack = { navController.popBackStack() },
            onNavigate = { route -> navController.navigate(route) },
            batteryChanged = batteryChanged,
            onConsumeBatteryChanged = { entry.savedStateHandle.remove<Boolean>("battery_changed") },
        )
    }
    composable(
        Routes.REPLACE_BATTERY,
        arguments = listOf(navArgument(Routes.ARG_VEHICLE_ID) { type = NavType.StringType }),
    ) {
        ReplaceBatteryScreen(
            cloudService = cloudService,
            onBack = { changed ->
                if (changed) {
                    navController.previousBackStackEntry?.savedStateHandle?.set("battery_changed", true)
                }
                navController.popBackStack()
            },
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
            onNavigate = { route -> navController.navigate(route) },
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
            onNavigate = { route -> navController.navigate(route) },
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
    ) {
        FirmwareOtaScreen(onBack = { navController.popBackStack() })
    }
    composable(
        Routes.DIAGNOSTIC,
        arguments = listOf(navArgument(Routes.ARG_VEHICLE_ID) { type = NavType.StringType }),
    ) {
        DiagnosticScreen(onBack = { navController.popBackStack() })
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
            connectionManager = connectionManager,
            onBack = { navController.popBackStack() },
        )
    }
}