# UI 移植清单（29 页 → Compose）

来源：`tailg-ble-app/lib/pages/*.dart` → `com.tailg.plus.ui.screens.<name>`
（每屏一个 Kotlin 文件，有状态屏配一个 ViewModel）。

> 状态：**已完成**（2025）。本文档从移植计划演进为落地清单，
> 每行的目标文件即为当前代码中的实际落点。

| # | Dart 页面 | 状态 | Kotlin 目标（实际） | 备注 |
|---|-----------|------|---------------------|------|
| 1 | login_page | ✅ | LoginScreen.kt | 云认证 |
| 2 | garage_page | ✅ | GarageScreen.kt | 车辆列表 + 绑定（Dart 1398 LOC） |
| 3 | cyber_vehicle_control_page_v2 | ✅ | ControlScreen.kt + ControlScreenHelpers.kt / ControlScreenSkeleton.kt | 六键控车、车型分支；拆为组件（Dart 1623 LOC） |
| 4 | add_vehicle_page | ✅ | AddVehicleScreen.kt | |
| 5 | bind_imei_page | ✅ | BindImeiScreen.kt | |
| 6 | scan_page | ✅ | ScanScreen.kt | CameraX + ML Kit 扫码 |
| 7 | garage_code_scanner_page | ✅ | GarageCodeScannerScreen.kt | |
| 8 | location_page | ✅ | LocationScreen.kt | 3 tab（围栏/地图/轨迹） |
| 9 | location_fence_tab | ✅ | LocationFenceTab.kt | |
| 10 | location_map_tab | ✅ | （并入 LocationScreen.kt） | 地图已接 osmdroid（CyberMapView） |
| 11 | location_travel_tab | ✅ | LocationTravelTab.kt | |
| 12 | battery_details_page | ✅ | BatteryDetailsScreen.kt | （Dart 1588 LOC） |
| 13 | replace_battery_page | ✅ | ReplaceBatteryScreen.kt | |
| 14 | ride_stats_page | ✅ | RideStatsScreen.kt | |
| 15 | vehicle_message_page | ✅ | VehicleMessageScreen.kt | （Dart 1044 LOC） |
| 16 | vehicle_settings_page | ✅ | VehicleSettingsScreen.kt | |
| 17 | firmware_ota_page | ✅ | FirmwareOtaScreen.kt + FirmwareOtaViewModel.kt | |
| 18 | diagnostic_page | ✅ | DiagnosticScreen.kt | |
| 19 | qgj_settings_page | ✅ | QgjSettingsScreen.kt | |
| 20 | induction_settings_page | ✅ | InductionSettingsScreen.kt | |
| 21 | location settings | ✅ | （并入相关设置页） | 位于 induction 文件内 |
| 22 | profile_mine_page | ✅ | ProfileMineScreen.kt | （Dart 1038 LOC） |
| 23 | settings_page | ✅ | SettingsScreen.kt | |
| 24 | app_preferences_pages | ✅ | AppPreferencesScreen.kt + AppPreferenceLabels.kt | |
| 25 | notification_prefs_page | ✅ | NotificationPrefsScreen.kt | |
| 26 | cloud_token_page | ✅ | CloudTokenScreen.kt + CloudTokenViewModel.kt | |
| 27 | log_page | ✅ | LogScreen.kt | |
| 28 | official_cloud_page | ✅ | OfficialCloudScreen.kt + OfficialCloudServiceFactory.kt | （Dart 1072 LOC） |
| 29 | official_replica_pages | ✅ | OfficialReplicaScreen.kt + ReplicaFenceTab / ReplicaNfcTab / ReplicaRideTab / ReplicaShareTab / ReplicaShared | （Dart 1123 LOC） |
| 30 | service_hub_page | ✅ | ServiceHubScreen.kt | |

Widgets：`lib/widgets/*`（25 文件，约 5.7k LOC）→ `com.tailg.plus.ui.components`
（33 个组件）：VoidNav、卡片、列表项、对话框、图标封装（Material 图标过渡期）、
Lottie 封装、车辆插画 CustomPainter、控车网格等。

主题：✅ `ui/theme`。导航：✅ `TailgNavHost` + `AuthNavGraph` / `VehicleNavGraph` / `SettingsNavGraph`。

相关文档：[CONVENTIONS.md](CONVENTIONS.md)（移植契约）、[PORT_WAVE2.md](PORT_WAVE2.md)（Wave 2 简报）。
