package com.tailg.plus.ui.navigation

/**
 * Route constants for the Tailg Plus navigation graph.
 *
 * One object per destination; the [Route] string is the NavHost route key.
 * Named arguments use the `{arg}` placeholder syntax expected by Navigation-Compose.
 */
object Routes {

    // ---- Auth flow ----
    const val LOGIN = "login"

    // ---- Bottom-nav destinations ----
    const val GARAGE = "garage"
    const val CONTROL = "control/{vehicleId}"
    const val SERVICE_HUB = "service_hub"
    const val PROFILE_MINE = "profile_mine"

    // ---- Vehicle management ----
    const val ADD_VEHICLE = "add_vehicle"
    const val BIND_IMEI = "bind_imei"
    const val SCAN = "scan"
    const val GARAGE_CODE_SCANNER = "garage_code_scanner"

    // ---- Vehicle detail screens ----
    const val LOCATION = "location/{vehicleId}"
    const val BATTERY_DETAILS = "battery_details/{vehicleId}"
    const val REPLACE_BATTERY = "replace_battery/{vehicleId}"
    const val RIDE_STATS = "ride_stats/{vehicleId}"
    const val VEHICLE_MESSAGE = "vehicle_message/{vehicleId}"
    const val VEHICLE_SETTINGS = "vehicle_settings/{vehicleId}"
    const val FIRMWARE_OTA = "firmware_ota/{vehicleId}"
    const val DIAGNOSTIC = "diagnostic/{vehicleId}"
    const val QGJ_SETTINGS = "qgj_settings/{vehicleId}"
    const val INDUCTION_SETTINGS = "induction_settings/{vehicleId}"

    // ---- Settings & profile ----
    const val SETTINGS = "settings"
    const val APP_PREFERENCES = "app_preferences"
    const val NOTIFICATION_PREFS = "notification_prefs"
    const val CLOUD_TOKEN = "cloud_token"
    const val LOG = "log"
    const val OFFICIAL_CLOUD = "official_cloud"
    const val OFFICIAL_REPLICA = "official_replica"

    // ---- Helpers ----
    fun control(vehicleId: String) = "control/$vehicleId"
    fun location(vehicleId: String) = "location/$vehicleId"
    fun batteryDetails(vehicleId: String) = "battery_details/$vehicleId"
    fun replaceBattery(vehicleId: String) = "replace_battery/$vehicleId"
    fun rideStats(vehicleId: String) = "ride_stats/$vehicleId"
    fun vehicleMessage(vehicleId: String) = "vehicle_message/$vehicleId"
    fun vehicleSettings(vehicleId: String) = "vehicle_settings/$vehicleId"
    fun firmwareOta(vehicleId: String) = "firmware_ota/$vehicleId"
    fun diagnostic(vehicleId: String) = "diagnostic/$vehicleId"
    fun qgjSettings(vehicleId: String) = "qgj_settings/$vehicleId"
    fun inductionSettings(vehicleId: String) = "induction_settings/$vehicleId"

    /** Argument key extracted from routes that carry a `{vehicleId}` segment. */
    const val ARG_VEHICLE_ID = "vehicleId"
}
