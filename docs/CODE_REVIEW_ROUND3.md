# 全项目代码审查（第三轮）

日期：2026-09-07。基线提交：`601e2b0`。
本轮接续 [CODE_REVIEW.md](CODE_REVIEW.md) 与
[CODE_REVIEW_FOLLOWUP_2026-09-07.md](CODE_REVIEW_FOLLOWUP_2026-09-07.md)，
只记录**新发现**的问题，不复述前两轮已修复项。

## 方法

1. 将主源码（约 180 个 Kotlin 文件）按子系统分为 5 组（BLE / 云+MQTT / 领域+服务 /
   UI 页面+导航 / 组件+存储+工具），由 5 个子代理并行做缺陷发现，要求给出
   file:line 证据与触发条件，并显式排除前两轮已记录项。
2. 每条候选缺陷交由**另一个**子代理独立复核（对抗式，尽力反驳），
   判定 CONFIRMED / REFUTED / UNCERTAIN。
3. 对 CONFIRMED 项实施修复，再由 3 个子代理按文件分组交叉验证修复的正确性、
   安全性与可执行性，并由 test-runner 执行 Lint 与构建。
4. 对复核中发现的**残留竞态**再次修复并二次验证。

本轮未连接真实车辆、官方 broker 或发送真实控车指令；验证基于静态调用链、
并发故障注入、JVM/Robolectric 回归与 Android 构建。

## 确认的问题与修复

| # | 级别 | 问题 | 证据 | 修复 |
| --- | --- | --- | --- | --- |
| 1 | P1 | 选择非 System 语言后 `LocalContext` 变为非 Activity 的本地化 `ContextImpl`；三处 `startActivity` 未加 `FLAG_ACTIVITY_NEW_TASK`，仅捕获 `ActivityNotFoundException`，实际抛出的 `AndroidRuntimeException` 逃逸 → 崩溃（打开地图 / 查看源码） | `ui/navigation/TailgNavHost.kt:70-76`；`ui/screens/LocationScreen.kt`、`AppPreferencesScreen.kt`、`ReplicaFenceTab.kt` | 三处 intent 增加 `FLAG_ACTIVITY_NEW_TASK` |
| 2 | P2 | `TailgTypography` 把浅色 `LightCyberPalette` 颜色写死，却对明/暗两套 scheme 都生效；深色模式下 `Text(style = MaterialTheme.typography.X)` 渲染深色墨字于深色底（如 Lucide 日期选择器月份标签） | `ui/theme/Type.kt:18-77`、`Theme.kt:283` | 改为 `fun tailgTypography(palette)`，`Theme` 传 `remember(scheme) { tailgTypography(scheme.toCyberPalette()) }` |
| 3 | P2 | `ensureConnectionCollector` 仅在 `dispose()` 置空 `_connJob`（生产从不调用）；切车取消 `activeOperations` 时，取消会经 `withVehicleOperation` 的 `coroutineScope` 冒泡进 `collect`，永久杀死该长期收集器，之后 BLE 连接变化不再触发感应刷新 | `service/InductionModeService.kt:242-249,797-821,167-175` | 捕获操作级 `CancellationException`，仅当收集器自身不活跃时才重抛 |
| 4 | P2 | ready 握手看门狗在 `setState(CONNECTED)` 即启动（8s），早于 MTU（≤5s）与发现（15s）；有效但较慢的发现会被误杀 | `data/ble/platform/ConnectionManager.kt:601-606,2148-2183`；`Constants.kt:154,164` | 改为在 `discoverAndSetup()` 之后显式 `armReadyWatchdog()`，并加 `state==CONNECTED` 守卫；`setState` 只负责离开 CONNECTED 时解除 |
| 5 | P2 | 日志页 LazyColumn key = `time+message.hashCode()`，突发日志同毫秒同消息时重复 key → `IllegalArgumentException` | `ui/screens/LogScreen.kt:204` | 改用 `itemsIndexed(key = { index, _ -> index })` |
| 6 | P2 | 登录成功提示在 LOGIN 组合项的 `rememberCoroutineScope` 上启动，而同回调的 `popUpTo(LOGIN){inclusive=true}` 会取消该 scope，提示不显示 | `ui/navigation/AuthNavGraph.kt:25-36` | `authNavGraph` 增参 `snackbarScope`（导航宿主 scope），由 `TailgNavHost` 提供 |
| 7 | P2 | 空闲窗口的（conflated）状态帧若在发令前入队、发令后才被消费，会被当作该指令的 ACK（无时序关联） | `data/mqtt/OfficialMqttService.kt:761-855` | 引入 `_commandGeneration`：入队时打戳、`setPending` 自增、消费者与确认分支按代次过滤；确认判定与 `pending` 读取同处 `lock` 内，关闭 TOCTOU |
| 8 | P2 | 订阅循环先于 `_client = client`；clean session 下 broker 在 SUBACK 后立即下发 retained 状态，被 `_client !== client` 丢弃，首帧 ACC/设防状态丢失 | `data/mqtt/OfficialMqttService.kt:690-716` | 连接成功后、订阅前即发布 `_client`/`_connectedIdentity`；订阅失败时回滚 |
| 9 | P3 | 位置页 `localError` 失败时设置、成功时不清除，错误横幅常驻 | `ui/screens/LocationScreen.kt:160,243,375` | 成功分支置 `localError = null` |
| 10 | P3 | 骑行记录页 `remember(log)` 以 app 级单例为 key，日志列表首次组合后不再更新 | `ui/screens/ReplicaRideTab.kt:79-81` | 订阅 `log.changes` 并以其代次为 key |
| 11 | P3 | `AnimatedValueText` 的 `remember(targetKey)` 未含 style/unitStyle，仅样式变化时单位 span 保持旧样式 | `ui/components/AnimatedValueText.kt:75,89` | key 增加 `style, unitStyle` |
| 12 | P3 | `AppSnack` 用进程级全局变量传递视觉样式，两个宿主并存时互相覆盖 | `ui/components/AppSnack.kt:167,199` | 改为随消息携带 `AppSnackVisuals : SnackbarVisuals` |
| 13 | P3 | `BitmapMemoryCache.evictAll()` 注释称用于退出登录，实际无调用点，车辆照片/瓦片跨账号保留 | `util/BitmapMemoryCache.kt:77-78` | `TailgApplication` 注册 `afterLogout` 效果调用之 |
| 14 | P3 | 验证码 WebView 允许混合内容且注册 JS 桥，HTTPS 页面可被注入 HTTP 子资源触达桥 | `ui/components/CaptchaSliderDialog.kt:82` | 改为 `MIXED_CONTENT_COMPATIBILITY_MODE` |
| 15 | P3 | `setTlinkInductionDistance` 替换等待槽时未完成前一个 deferred（与同类方法及 Dart 原版不一致），并发调用会误报 false | `data/ble/platform/ConnectionManager.kt:833-845` | 替换前 `previous?.complete(false)` |
| 16 | P3 | BMS 硬件/软件版本同源于单字段 `batteryVersion`，两行显示相同值且同时存在时丢失硬件版本 | `data/model/OfficialBmsInfo.kt:65,95-96`；`BatterySnapshot.kt:280,299-300` | 新增独立 `hwVer`/`swVer` 字段并分别解析、映射 |
| 17 | P3 | 非 2xx 且 `msg` 为空串时生成空异常消息（elvis 不触发） | `data/cloud/OfficialCloudApiClient.kt:155-158` | `takeIf { it.isNotBlank() }` |
| 18 | P3 | `logout` 在持有会话写锁时执行 `mqttService.disconnect()`（需 `lifecycleMutex`，可被在途连接占用约 15s），阻塞后续会话写 | `data/cloud/OfficialCloudServiceOperations.kt:152-179` | 通道清理效果移出 `withSessionWrite`，在锁释放后执行 |
| 19 | P3 | 死代码：`ApiCallTemplate.apiCall`、`OfficialCloudRetryPolicy.TRANSPORT_ONLY` 无引用 | `data/cloud/ApiCallTemplate.kt`、`OfficialCloudApi.kt:67` | 删除文件与常量 |
| 20 | P3 | 手动模式持久化失败时 `_manual.setEnabled(false)` 在 `try` 之外，异常逃逸出 UI 协程 | `service/InductionModeService.kt:391-393` | 纳入 try/catch，失败经快照发布并返回 false |
| 21 | P3 | 位置行程列表 key 可能因重复 `travelDate` 冲突（UNCERTAIN，防御性） | `ui/screens/LocationTravelTab.kt:138` | key 追加 index 后缀 |

## 复核判定为误报（未修改）

- **`ServiceHubScreen` 的 `remember` 被新 lambda 击穿**：Kotlin 2.4 强跳过下，
  `VehicleNavGraph` 传入的 lambda 被编译器记忆化且捕获的 `navController` 引用稳定，
  `remember` 不会被击穿。REFUTED。
- **`connectGattOnce` 中 `_gatt` 赋值晚于回调的竞态**：`connectGatt` 同步返回后
  `STATE_CONNECTED` 才经回调投递，赋值必然先完成；该门控用于丢弃过期 GATT 事件。
  REFUTED。

## 保留项（已确认但本轮不改，附理由）

- **`setRssiEnabled(false)` 与开启路径不对称**（`_cloud == null` 时返回 ok=true）：
  生产环境 `_cloud` 恒由 Hilt 注入，仅测试/误配可达，无生产影响；改动会改变
  “云端缺失时能否本地关闭感应”的语义，故保留并记录。
- **`ensureKksBond` 不等待配对完成**（Dart 原版 await）：无真机无法验证配对时序，
  且 `createBond()` 可能需用户交互；改动可能给每次 KKS 连接引入最长数秒延迟，
  故保留，待真机联调。
- **MQTT 发布异常后 HTTP 回退可能重复下发指令**（UNCERTAIN）：无法从代码证明
  “已投递但抛异常”的窗口，且受影响指令（开/关/锁/解锁）实际幂等，未改。

## 验证

环境：Windows、JDK 17、本地 Android SDK、Gradle 9.7.1、AGP 9.4.0、Kotlin 2.4.10，
`compileSdk = 37`、`targetSdk = 36`、`minSdk = 26`。

| 检查 | 结果 |
| --- | --- |
| `:app:testDebugUnitTest` | 79 个测试类、465 项测试，0 失败、0 错误、0 跳过（基线 461 项，本轮新增 4 项） |
| `:app:lintDebug` | 0 Error / Fatal，291 Warning，3 Hint（`abortOnError=true` 未触发；含死代码清理后新孤立的部分字符串资源） |
| `:app:assembleDebug` | 成功，`app-debug.apk` 约 49.37 MB |

本轮新增/调整的回归测试：

- `data/model/OfficialBmsVersionTest.kt`（新）：`hwVer`/`swVer` 分别解析、BmsSnapshot 两行保持独立、
  旧字段回退。
- `data/ble/platform/ConnectionManagerLifecycleTest.kt`：`handshakeWatchdogReleasesTheGattConnection`
  改为按 `connect()` 的方式显式布防；新增 `setStateConnectedDoesNotArmTheWatchdog`，
  断言裸 CONNECTED 不再被 8s 握手定时器拆除（对修复前代码会失败）。

修复后的交叉验证结论（3 个子代理按文件分组 + test-runner）：

- UI/主题/组件 11 项修复：全部 CORRECT；无新缺陷。
- BLE/服务/模型 6 项修复：全部 CORRECT；指出 2 处测试覆盖缺口（已补）。
- 云/MQTT 5 项修复：4 项 CORRECT；第 2 项（代次过滤）指出残留 TOCTOU，
  已按“代次先于 pending 读取 + 确认判定置于同一锁内”再次修复并二次复核为 CORRECT。
- Lint 0 error、Debug 构建通过。

## 边界

- 本轮为静态审查 + JVM/Robolectric 回归，未做真机联调；BLE 配对/断连重连、
  MQTT 网络恢复、相机、地图宿主切换、权限撤销等系统行为仍需真机验证。
- BLE AES/ECB 属既有兼容约束，未改动（见前两轮记录）；官方 MQTT TLS 已由兼容信任改为证书固定（见下）。
- 本次结论针对检查范围与可复现场景，不代表项目不存在其他缺陷。

## 死代码清理（本轮追加）

在“保留项”之外，另做了一次全项目未引用声明扫描（统计每个 `fun` / 顶层 `val` / `var`
在 `app/src` 的引用次数，排除框架回调 `override` 与 Hilt `@Provides`），删除确认无引用的代码：

| 删除对象 | 说明 |
| --- | --- |
| `data/ble/platform/BleScanner.kt` + `ConnectionManager.scanDevices` / `bleScanner` 字段 | 两套并行扫描实现，均无调用点；真实扫描在 `ScanScreen` |
| `ui/components/CyberControlGrid.kt`、`ControlLottieAnimation.kt`、`CyberVehicleHeader.kt` | 整体无引用的组件文件（含 `OfficialBleChipState`、`CyberHeaderExpandedHeight`） |
| `ui/components/CyberMapStats.kt` 的 `CyberMapStatsRow` | 该文件其余声明仍在使用 |
| `ui/screens/OfficialCloudServiceFactory.kt` 的 `rememberOfficialCloudService` | 同文件 `VehicleStoreCloudAdapter` 仍在用 |
| `ConnectionTypes.completeIfSame`、`MessageReadStore.replaceState`、`CachedTileProvider.requestHeaders`、`PermissionService.openSystemSettings`、`AppPressable.roundedPressableShape`、`PersistenceValue.parsePersistedStringList`、`ControlScreenHelpers.officialBleChipState`、`OfficialCloudScreen.DetailLine`、`Type.TailgTypography` | 无引用的函数/属性 |

保留（有意不删）：`ui/components/material/SegmentedList.kt`（KernelSU 组件库移植，整体未被引用但
属成套组件）、`Color.DarkCyberPalette` 与 `Theme.CyberLightColorScheme`（主题 token 回退）、
`Routes.firmwareOta` / `qgjSettings`（见下）。删除后 `compileDebugKotlin`、465 项单测与
`lintDebug`（0 error）均通过。

## 功能观察（未改）

- **OTA 与 QGJ 设置页面已注册但无导航入口**：`VehicleNavGraph` 注册了
  `Routes.FIRMWARE_OTA` / `Routes.QGJ_SETTINGS` 两个目的地，但构建器
  `Routes.firmwareOta(...)` / `Routes.qgjSettings(...)` 无任何调用点，因此两页不可达。
  OTA 属有意禁用（生产固件下载未启用）；`QgjSettingsScreen` 本身是带 TODO 的 stub。
  需要时再补入口或删除目的地。

## MQTT 证书固定（本轮追加）

原实现对官方主机（硬编码 `www.tailgdd.com` 与云下发 `mqHost`）采用 trust-all，Release 同样生效，
存在中间人风险。本轮改为**严格证书固定**：

- 抓取官方 C18 broker（`www.tailgdd.com:6668`）证书：`CN=c18_ex_base_pro.tailgdd.com`，
  RSA-2048，自签，有效期 2023-09-11 → 2053-09-03，DER SHA-256
  `7ed944d07afebf76c0f72a8779da2800737f51777681b2c0589b7f4b2ee16438`。
- 新增 `data/mqtt/OfficialMqttPinning.kt`：内置该证书，`PinnedTrustManager` **只接受**
  公钥（SPKI）与内置证书一致的服务器证书，其余一律拒绝。**无 trust-all，也不回退系统信任链**
  —— Paho 的 `ssl://` 不校验主机名，任何「平台可信」回退都会让攻击者用任意公网证书绕过 pin
  （该缺陷在实现初稿中被对抗式复核发现并已修正）。
- `OfficialMqttService.tlsSocketFactoryFor`：官方主机（硬编码或云下发）改用该 pinning 工厂；
  Debug `ALLOW_INSECURE_MQTT_TLS` 开关仍可对任意非官方主机启用 trust-all（Release 恒为 false）。
- 证书不匹配时拒绝连接，调用方回退 HTTP 控车。

交叉验证结论：修复后 pin 不可绕过（唯一接受路径是持有官方私钥），安全上可发布。
已知取舍（非安全）：若某个云下发 `mqHost` 使用**不同**的官方私钥，其 MQTT 会被拒绝并回退 HTTP，
需把该证书 DER 加入内置集合。测试：`OfficialMqttPinningTest`（7 项，含 DER 指纹、拒绝任意非固定证书、
空链与客户端证书 fail-closed）。