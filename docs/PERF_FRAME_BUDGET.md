# 帧预算清理（全库热点普查后的定向修复）

承接 [PERF_CONTROL_HOME.md](PERF_CONTROL_HOME.md)（控车首页、Perfetto 证据驱动）。
这一轮的目标不是继续打磨首页，而是**把"重写语言/框架"这个错误选项排除掉**：
先做一次全库普查，确认瓶颈位置，再只修真正在热路径上的东西。

## 结论先说：瓶颈不在语言与运行时

普查（279 个 Kotlin 文件 / 主源码约 46k 行）确认以下类别**全部干净**，
也就是说换成 Flutter / RN / Compose Multiplatform 不会带来任何收益，
反而要把这些已经跑在原生线程上的能力重新塞回 platform channel：

| 类别 | 结论 |
| --- | --- |
| 主线程阻塞 | 全库 **0 处** `runBlocking` / `Thread.sleep` / 主线程 `BitmapFactory.decode*` / 每次新建 `SimpleDateFormat` / 主线程 File 读写 |
| 非 lifecycle 状态收集 | **0 处** `collectAsState()`，22 个文件全部 `collectAsStateWithLifecycle` |
| BLE 线程模型 | `ConnectionManager` 事件循环在 `Dispatchers.IO`，GATT 回调只入队，不阻塞主线程 |
| 图片 | 自定义 LRU + 两阶段采样解码 + `RGB_565` + URL 在途去重，无列表项解码原图 |
| 地图 | overlay 实例复用 + `MapDataKey` diff，`isTilesScaledToDpi=false`，`maxZoomLevel=17`，无主线程同步加载瓦片 |
| 无限动画 | 仅 4 处，且全部有生命周期/可见性门控 |
| `Modifier.blur` / `renderEffect` | 0 处 |

因此本轮的修复都落在**帧预算内的具体开销**：composition 期阻塞、缺失的列表虚拟化、
每帧对象分配、以及被无关字段打穿的状态扇出。

## 已实施

### 1. 二维码栅格化移出主线程 — `ui/components/QrImage.kt`

512×512 的 `QRCodeWriter` + 262144 次逐像素填充 + 2 MB `ARGB_8888` 分配原先写在
`remember { }` 里，即首次组合时**在主线程同步执行**，直接卡住车辆码弹窗的入场动画。

改为 `produceState` + `withContext(Dispatchers.Default)`；位图就绪前不绘制，
弹窗白卡自然兜底。栅格化逻辑提到 `renderQrBitmap`，并注明必须在非主线程调用。
注意作用域只限本次组合：关掉再打开弹窗会重新栅格化（与原先 `remember` 的生命周期一致，并未退化）。

### 2. 主题 48 个颜色动画由 `spring()` 改有限时长 tween — `ui/theme/ThemeExt.kt`

`ColorScheme.animateAsState()` 为每个 M3 role 建一个 `animateColorAsState`，共 48 个。
原先的 `spring()`（未给 damping ratio）会让每个 role 持续重定向数百毫秒，而它们都在
`TailgTheme` 的组合里，于是**这段时间内每一帧都重组整棵读 `MaterialTheme.colorScheme` 的子树**。

改为 300ms 的 `tween`（到点即停，帧时钟随之停下），并接入既有 `MotionPolicy.reduceMotion()`：
系统动效关闭时 duration 取 0，颜色直接切换。

### 3. BLE 扫描结果列表虚拟化 — `ui/screens/ScanScreen.kt`

扫描结果原先用 `Column + results.forEach` 渲染，且整个页面套在 `verticalScroll` 里——
这是全库**唯一一处数据驱动却没有虚拟化的长列表**：BLE 每发现一台设备，
屏幕上所有已发现的卡片全部重组。

改为让 `LazyColumn` 成为滚动容器本身，静态区块（页头、雷达、提示、尾距）成为前置 item，
设备卡片用 `itemsIndexed(key = { _, d -> d.id })` + `contentType`。
（`LazyColumn` 嵌在 `verticalScroll` 内会抛异常，两者不能共存，所以外层容器必须一起改。）
每张卡片的 5dp 垂直内边距等价于原先的 `spacedBy(10.dp)`。

### 4. 日志列表：快照缓存 + 代次键 — `log/LogService.kt`、`ui/screens/LogScreen.kt`、`ui/screens/ReplicaRideTab.kt`

`LogService.changes` 从 `SharedFlow<Unit>` 改成 `StateFlow<Long>` 代次计数器。
原先的 ping 不带值，于是每个收集方都得自己再维护一个计数器才能得到可用的 `remember` 键，
防抖也被各处重新实现一遍；现在**代次值本身就是键**。

- `LogScreen`：`val entries = log.all` 原先直接写在组合里，而 `log.all` 要上锁复制最多 2000 条——
  每一次无关重组（调色板、snackbar、对话框状态）都要付这笔钱。改为
  `remember(log, logGeneration, listGeneration) { log.all }`，只在代次或手动刷新变化时复制。
- `ReplicaRideTab`：原先 `log.changes.collect { logGeneration++ }` **没有防抖**，
  后面紧跟 `byCategory(...)` 全表扫描——BLE 握手期每来一行日志就在主线程扫一遍。
  现按 `LogService.REFRESH_DEBOUNCE_MS`（120ms）防抖，与 `LogScreen` 共用同一常量。

### 5. 地图：跟随限流 + 派生值上提 — `ui/components/CyberMapView.kt`

- `AndroidView.update` 每次重组都重建 `GeoPoint`、`List<Pair>` 轨迹键和 `MapDataKey`——
  一条几百点的轨迹每次重组要拷三份。三者改为在 `AndroidView` 之外按输入
  `remember`（`center` / `trackKey` / `dataKey`）。
- 相机原先对每个坐标微变都调 `animateTo`，GPS 抖动会让平移动画反复重启、
  瓦片反复重载。现在要求**位移 ≥ 8m 且距上次跟随 ≥ 1s**才跟随，锚点latch，
  小幅移动会累积而不是被丢弃。

### 6. 绘制期每帧分配消除 — `NinebotBatteryVisual.kt`、`NinebotBatteryContent.kt`、`RideStatsScreen.kt`

- `drawBatteryBox`：原先每帧构造两个 `List`、8 个 `BatteryPoint`、一个比较器
  （`sortedBy`）和 6 个 `Path`。改为角点存 `FloatArray`、面深度/排序存原始数组、
  六个面复用同一个 `Path`；遮挡排序改用**稳定插入排序**，与原先 `sortedBy`
  在等深时保持模板顺序的行为一致，但不分配。
  另加 `BatteryProjection.project(x, y, z)` 免分配的 3-Float 重载（右侧焦点轴不变）。
- `BatteryRange`：渐变改 `remember`，六条弧线复用同一个 `Path` 与 `FloatArray`。
- `RideStatsScreen`：环形光晕的 `Brush.radialGradient` 与进度条的
  `Brush.horizontalGradient` 改 `remember`（后者按 `highlighted` 与调色板键）；
  原先每次重组都新建渐变，draw 期还要为新 brush 重建 shader。

> `CyberHomeColors` 是 `@Composable @ReadOnlyComposable` 读取，因此不能提升为文件级
> `val`——只能按"调色板值"做 `remember` 键，改动均按此处理。

### 7. 诊断历史解析移出主线程 — `ui/screens/DiagnosticScreen.kt`

`LaunchedEffect` 默认跑在主调度器上，而 `loadHistory` 在 DataStore 读取之后还要
JSON 解析并排序全部历史记录。整段包进 `withContext(Dispatchers.Default)`。

### 8. 控车页 1 Hz 无效重组 — `data/ble/platform/ConnectionManager.kt`

`publishBikeState` 的去抖条件包含 `signalStrength`，而 QGJ 心跳每秒轮询一次 feb3。
但 `BikeState.signalStrength` **在全库没有任何消费者**（只被写入和被比较），
于是一个无人读取的 RSSI 抖动每秒发一次新状态对象，把整个控车页——包括首页的
osmdroid 迷你地图——重组一遍。已从去抖条件中移除并加注释说明；
真正被消费的 RSSI（感应解锁）是直接从 GATT 回调读的，不经过 `BikeState`。

### 9. GATT 事件队列改为有界 — `data/ble/platform/ConnectionManager.kt`

`Channel.UNLIMITED` 在通知风暴且对端卡住时可以无上限增长。改为 512 深度 +
`DROP_OLDEST`，并把 `trySend` 失败计入诊断计数。溢出的事件按定义已过期
（它们丢失的 suspend deferred 另有时限兜底），所以丢弃最旧是安全且有界的。

### 10. 删除 Lottie 死依赖 — `app/build.gradle.kts`、`gradle/libs.versions.toml`、`app/proguard-rules.pro`

`lottie-compose` 已被声明并写了 ProGuard 规则，但 `app/src/main` 下**零处引用**
（`assets/official_tailg/lottie/` 只是官方资产，保留）。删除依赖与规则，纯减体积。

## 已审查但**不改**（确认无问题）

- `ControlScreen.kt` / `TailgNavHost.kt` / `BatteryDetailsScreen.kt` / `LocationScreen.kt` /
  `VehicleMessageScreen.kt` / `GarageScreen.kt` / `NotificationPrefsScreen.kt`：
  列表均已 `LazyColumn` + key/contentType；`remember(key)` 语义正确，无 `remember(Unit)`。
- `VideoStage` / `CyberMapStats`：采样解码与在途去重已到位。
- `InductionModeService`：200ms RSSI 轮询只在前台 + 绑定 watch 时进行，且不驱动 UI。
- `OfficialMqttService`：idle 帧走 `CONFLATED`，发布/pending 处理在 IO；Paho 回调线程上的
  同步处理不落主线程，暂不动（改动会牵涉 ACK 时序）。
- `RideStatsScreen` 的 `Column + verticalScroll`：内容是固定 6 个区块，不是数据驱动长列表。

## 测量

本轮的量化手段是随本次一并新增的 **`:macrobenchmark` 模块**
（`StartupBenchmark`：`StartupTimingMetric` + 冷启动；`FrameTimingBenchmark`：`FrameTimingMetric`）。
在此之前仓库没有任何帧率/启动耗时基线，"流畅度"无法验收——
上述每一条的收益都应以此为验收依据，而不是靠推断。

```bash
.\gradlew.bat :macrobenchmark:connectedCheck   # 需要真机/模拟器
```

> 注：benchmark 需要 `benchmark` build type（debug 签名、release 规则），
> 以便可安装且与发布构建同构；相关配置在 `app/build.gradle.kts` 与
> `macrobenchmark/build.gradle.kts`。
