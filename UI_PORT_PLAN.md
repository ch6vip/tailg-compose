# UI Port Plan (29 pages → Compose)

Source: `E:\ctf-aaa\tlddc\tailg-ble-app\lib\pages\*.dart` → `com.tailg.plus.ui.screens.<name>`
(one Kotlin file per screen, ViewModel per screen where stateful).

| # | Dart page | LOC | Kotlin target | Notes |
|---|-----------|-----|---------------|-------|
| 1 | login_page | | LoginScreen | cloud auth |
| 2 | garage_page | 1398 | GarageScreen | vehicle list + bind |
| 3 | cyber_vehicle_control_page_v2 | 1623 | ControlScreen | 六键控车、车型分支 — biggest, split into components |
| 4 | add_vehicle_page | | AddVehicleScreen | |
| 5 | bind_imei_page | | BindImeiScreen | |
| 6 | scan_page | | ScanScreen | CameraX + ML Kit barcode |
| 7 | garage_code_scanner_page | | GarageCodeScannerScreen | |
| 8 | location_page | 1310 | LocationScreen | 3 tabs (fence/map/travel) |
| 9 | location_fence_tab | | LocationFenceTab | |
| 10 | location_map_tab | | LocationMapTab | map SDK choice TODO |
| 11 | location_travel_tab | | LocationTravelTab | |
| 12 | battery_details_page | 1588 | BatteryDetailsScreen | |
| 13 | replace_battery_page | | ReplaceBatteryScreen | |
| 14 | ride_stats_page | | RideStatsScreen | |
| 15 | vehicle_message_page | 1044 | VehicleMessageScreen | |
| 16 | vehicle_settings_page | | VehicleSettingsScreen | |
| 17 | firmware_ota_page | | FirmwareOtaScreen | |
| 18 | diagnostic_page | | DiagnosticScreen | |
| 19 | qgj_settings_page | | QgjSettingsScreen | |
| 20 | induction_settings_page | | InductionSettingsScreen | |
| 21 | location settings (in induction file?) | | — | |
| 22 | profile_mine_page | 1038 | ProfileMineScreen | |
| 23 | settings_page | | SettingsScreen | |
| 24 | app_preferences_pages | | AppPreferencesScreens | |
| 25 | notification_prefs_page | | NotificationPrefsScreen | |
| 26 | cloud_token_page | | CloudTokenScreen | |
| 27 | log_page | | LogScreen | |
| 28 | official_cloud_page | 1072 | OfficialCloudScreen | |
| 29 | official_replica_pages | 1123 | OfficialReplicaScreens | |
| 30 | service_hub_page | | ServiceHubScreen | |

Widgets: `lib/widgets/*` (25 files, ~5.7k LOC) → `com.tailg.plus.ui.components`:
VoidOrbitalNav, cards, list tiles, dialogs, lucide icon wrapper (Material icons interim),
Lottie wrappers, bike painter (CustomPainter port), control grid, etc.

Theme: done (`ui/theme`). Navigation: `TailgNavHost` grows as screens land.
