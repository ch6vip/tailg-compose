# Agent Note: 帧预算清理（全库热点普查后的定向修复）

Status: implemented

## Problem

有人问「用什么语言/框架重写能提升流畅度」。在回答之前需要先确认瓶颈到底在哪：如果瓶颈在
语言或运行时，重写是对的；如果在帧内的具体开销，重写只是把同样的开销搬到另一个运行时，
还会把已经跑在原生线程上的能力（CameraX/ML Kit 扫码、osmdroid、前台服务、DataStore、
Paho 证书固定）重新塞回 platform channel。

一次全库普查（279 个 Kotlin 文件 / 主源码约 46k 行）给出的答案是后者，且相当干净：

| 类别 | 结论 |
| --- | --- |
| 主线程阻塞 | 0 处 `runBlocking` / `Thread.sleep` / 主线程 `BitmapFactory.decode*` / 每次新建 `SimpleDateFormat` / 主线程 File 读写 |
| 非 lifecycle 状态收集 | 0 处 `collectAsState()`，22 个文件全部 `collectAsStateWithLifecycle` |
| BLE 线程模型 | 事件循环在 `Dispatchers.IO`，GATT 回调只入队，不阻塞主线程 |
| 图片 | LRU + 两阶段采样解码 + `RGB_565` + URL 在途去重 |
| 地图 | overlay 实例复用 + `MapDataKey` diff，无主线程同步加载瓦片 |
| 无限动画 | 仅 4 处，全部有生命周期/可见性门控 |

于是剩下的问题不是"语言不够快"，而是十处**帧预算内的具体开销**。

## Decision

按开销类型修，不动运行时与架构。

**composition 期阻塞（两类，最直接）**

1. `QrImage`：512×512 的 `QRCodeWriter` + 262144 次逐像素填充 + 2 MB `ARGB_8888` 分配原先写在
   `remember { }` 里 —— 首次组合即在主线程同步执行，卡住车辆码弹窗入场。改为
   `produceState` + `withContext(Dispatchers.Default)`，就绪前不绘制（白卡兜底）。
   作用域只限本次组合，关掉重开会重新栅格化（与原先 `remember` 生命周期一致，未退化）。
2. `DiagnosticScreen.loadHistory`：`LaunchedEffect` 默认在主调度器，DataStore 读取之后的
   JSON 解析与排序也落在主线程。整段包进 `withContext(Dispatchers.Default)`。

**状态扇出被打穿**

3. `ThemeExt.animateAsState()` 给 48 个 M3 role 各建一个 `animateColorAsState`，原先用不带
   damping ratio 的 `spring()`：每个 role 持续重定向数百毫秒，而这 48 个动画都在 `TailgTheme`
   的组合里，于是这段时间**每一帧都重组整棵读 `MaterialTheme.colorScheme` 的子树**。改为
   300ms `tween`（到点即停，帧时钟随之停下），并接入既有 `MotionPolicy.reduceMotion()`
   （系统动效关闭时 duration 取 0）。
4. `ConnectionManager.matchesDebounced` 原先比较 `signalStrength`，而 QGJ 心跳每秒轮询 feb3。
   `BikeState.signalStrength` 全库**无消费者**（只被写入和比较；真正被消费的 RSSI 走 GATT
   回调，不经过 `BikeState`），于是一个无人读取的字段每秒发一次新状态对象，把整个控车页
   —— 含首页 osmdroid 迷你地图 —— 重组一遍。移除该比较。

**缺失的虚拟化**

5. `ScanScreen` 的扫描结果原先用 `Column + forEach` 渲染，且整页套在 `verticalScroll` 里 ——
   全库唯一一处数据驱动却没有虚拟化的长列表。改为让 `LazyColumn` 成为滚动容器本身，静态
   区块（页头/雷达/提示/尾距）变成前置 item，卡片用 `itemsIndexed(key = …, contentType = …)`。
   `LazyColumn` 嵌在 `verticalScroll` 内会抛异常，所以外层容器必须一起改。

**长列表 / 高频信号**

6. `LogService.changes` 从 `SharedFlow<Unit>` 改为 `StateFlow<Long>` 代次计数器。原先 ping 不带值，
   每个收集方都得自己再维护一个计数器才能得到可用的 `remember` 键，防抖也被各处重实现一遍；
   现在代次值本身就是键。`LogScreen` 的 `log.all`（上锁复制最多 2000 条）因此可以
   `remember(log, logGeneration, listGeneration)`，不再被每次无关重组拖着复制。
7. `ReplicaRideTab` 原先 `log.changes.collect { logGeneration++ }` 没有防抖，后面紧跟
   `byCategory(...)` 全表扫描 —— BLE 握手期每来一行日志就在主线程扫一遍。按
   `LogService.REFRESH_DEBOUNCE_MS`（120ms）防抖，与 `LogScreen` 共用同一常量。

**绘制期每帧分配**

8. `NinebotBatteryVisual.drawBatteryBox` 原先每帧构造两个 `List`、8 个 `BatteryPoint`、一个
   比较器（`sortedBy`）和 6 个 `Path`。改为角点存 `FloatArray`、面深度/排序存原始数组、
   六面复用同一个 `Path`；遮挡排序改用**稳定插入排序** —— 等深时保持模板顺序，与原先
   `sortedBy` 等价但不分配。另加 `BatteryProjection.project(x, y, z)` 免分配重载。
9. `NinebotBatteryContent.BatteryRange` 六条弧线复用同一个 `Path` 与 `FloatArray`，渐变改 `remember`。
10. `RideStatsScreen` 的环形光晕与进度条渐变改 `remember`（按调色板色与 `highlighted` 键）。

**地图跟随**

11. `CyberMapView` 的 `center` / `trackKey` / `dataKey` 原先在 `AndroidView.update` 内构造 ——
    每条重组都要把几百点的轨迹拷三份。改为在 `AndroidView` 之外按输入 `remember`。
    相机跟随原先对每个坐标微变都 `animateTo`，GPS 抖动会让平移动画反复重启、瓦片反复重载；
    现在要求位移 ≥ 8m **且**距上次跟随 ≥ 1s，锚点 latch，小幅移动累积而不是被丢弃。

**连接层**

12. `gattEvents` 从 `Channel.UNLIMITED` 改为有界 + `DROP_OLDEST`，并附注：`trySend` 在
    DROP_OLDEST 下只在通道关闭时失败，所以**没有**溢出计数可加（曾加过一个永远不命中的
    诊断计数，已删除）。有界化本身安全，因为所有 deferred 都有独立时限兜底。

**死依赖**

13. `lottie-compose` 声明了依赖并写了 ProGuard 规则，但 `app/src/main` 下零引用。删除依赖与
    规则；`assets/official_tailg/lottie/` 是官方资产，保留。

**量化手段（本次一并新增）**

14. 新增 `:macrobenchmark` 模块（此前仓库没有任何帧率/启动耗时基线，"流畅度"无法验收）：
    `StartupBenchmark`（`StartupTimingMetric` + 冷启动）与 `FrameTimingBenchmark`
    （`FrameTimingMetric` + 通用滚动，标注为粗粒度基线）。`:app` 增加 `benchmark` build type
    （继承 release 的 R8/资源压缩，debug 签名以便安装，`isProfileable = true`）。

## Alternatives considered

- **换 Flutter / RN / Compose Multiplatform（即用户最初的提问）**：把 CameraX + ML Kit 扫码、
  osmdroid、前台服务、DataStore + 加密、Paho 证书固定全部改成跨 channel 桥接。当前这些能力
  已经跑在原生线程上，重写只会新增 jank 面，不减少任何一处已定位的开销。否掉。
- **用 Rust/C++ 重写协议解析**：BLE 解析本来就在 `Dispatchers.IO` 的事件循环里，不是热点。
  零收益，否定。
- **主题动画：改成整个 `ColorScheme` 一次 `Crossfade`**：观感会从"颜色渐变"变成"两层交叉淡入"，
  中途会出现两套表面叠在一起（卡片描边发灰）。改为有限时长 tween 后已能停下帧时钟，
  不需要牺牲观感。否掉。
- **`animateColorAsState` 只保留实际被读取的 role**：全库确实只读了 81 处、涉及 18 个 role，
  但收敛白名单会让"以后新增一处 `colorScheme.x`"静默失去过渡。选有限时长 tween，保留全部 role。
- **`NinebotBatteryVisual` 的路径缓存做成实例级 `remember`**：更干净（无全局可变状态），
  但 `drawBatterySculpture` 在 `Canvas` lambda 内、不是可组合上下文，改它要穿透参数。
  最终对 `drawBatteryBox` 用了实例级 `remember`（在 composable 里建好传进 Canvas），
  对 `BatteryRange` 用了文件级 scratch 并注明单实例假设 —— 两处取舍不同，因为前者调用点多、
  后者只有一个屏。
- **`ScanScreen` 保留 `Column` 只加 `key`**：`Column` 没有回收机制，加 key 并不能避免
  新增设备时全量重组。必须换成 `LazyColumn`，而这又强制外层滚动容器一起改。
- **GATT 队列保持 `UNLIMITED`**：改动有语义风险（可能丢事件）。最终保留有界化，
  因为"溢出即过期"论证成立且有独立时限兜底 —— 但删掉了那个不成立的溢出计数器。

## Consequences

收益：上面每一条都消除了一个可指认的开销 —— 主线程阻塞点从 2 处降到 0，未虚拟化的数据驱动
长列表从 1 处降到 0，最热绘制函数（电池盒）从每帧 ~30 次分配降到 0，主题切换与控车页 1 Hz
的无效重组消失。最终 `:app:assembleDebug`/`assembleRelease`/`testDebugUnitTest`/`lintDebug`
与 `:macrobenchmark:assemble` 全部通过（475 个单测、0 失败；Lint 无新增错误）。

代价与限制，必须如实记下：

- **所有收益目前只有代码级证据，没有帧率数字**。宏基准只验证到"能编译、能装配"，
  没有在真机上跑过 `connectedBenchmarkAndroidTest`（环境无设备）。上表每一条的**实际**
  帧时收益都需要以宏基准输出为准，不能拿这份 Note 当性能已提升的证明。
- **`isProfileable = true` 只加在 `:app` 的 `benchmark` 变体上**，`release`/`debug` 清单未动，
  但这也意味着发布构建仍不可 profile；将来若要对 release 采样需要单独评估。
- **文件级可变 scratch（`BoxCornerScratch`、`BatteryRangeCurves`/`BatteryRangePath`）隐含
  "同屏单实例且单帧串行绘制"假设**。当前成立；若将来同一屏幕出现两个实例或进入并行绘制，
  会互相踩踏 —— 修法是改成实例级 `remember`（`drawBatteryBox` 已是该形态）。
- **`ScanDevice` 在权限被撤销时 `id` 回退为 `""`**，所以 `LazyColumn` 的 key 必须用
  `device.id.ifEmpty { "unknown-$index" }`：重复 key 会在组合期直接崩。这是虚拟化带来的
  新不变量，改动该处时必须保留。
- **扫描结果列表的垂直间距**由原来的 `spacedBy(10.dp)` 改为每卡 `padding(vertical = 5.dp)`，
  项间距离等价，但首尾各多 5dp（前后本有 16/20/80dp spacer，视觉不可见）。
- **`:macrobenchmark` 的 R8 keep 规则必须放在 `src/main/keepRules/*.keep`**：AGP 9 只认这个
  目录，且强制 `.keep` 扩展名；`proguardFiles { }` 对 `com.android.test` 模块被静默忽略。
  同一机制也意味着**测试模块必须开 `isMinifyEnabled`**（`checkTestedAppObfuscationBenchmark`
  会因被测 app 已压缩而拒绝启动未压缩的测试 APK），而测试 APK 自身必须是 `isDebuggable = true`
  （与 AndroidX 模板一致，instrumentation 需要附着；影响数值的是被测 app 的开关，不是这个）。
- **`macrobenchmark` 模块同时存在 `benchmark` 与 `debug` 两个变体**，故
  `connectedBenchmarkAndroidTest` 打 `:app:benchmark`，而 `connectedDebugAndroidTest` 打
  `:app:debug`。CI 若用聚合的 `connectedAndroidTest` 会跑两遍，应显式调用前者。
- 未改动但值得留意：`OfficialMqttService` 在 Paho 回调线程上同步处理 pending 窗口
  （不落主线程，改动会牵涉 ACK 时序）；`ScanScreen` 的 `isTailg` 用 `name.contains("tl")`
  判断，任何含 "tl" 的品牌名都会命中（非本次改动）。

## Verification

- `.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :macrobenchmark:assemble`
  → `BUILD SUCCESSFUL`；单测 81 个类 / 475 个用例 / 0 失败 0 错误；Lint 通过。
- 产物：`app-debug.apk`、`app-release-unsigned.apk`、`macrobenchmark-benchmark.apk`。
- 施工过程中被编译器抓出并修掉的真实缺陷（记录以免重犯）：误删 `ConnectionManager._model`
  字段与其两个 import、`NinebotBatteryVisual` 少一个闭合花括号、`ReplicaRideTab` 缺
  `@OptIn(FlowPreview)`、`Channel(N) { onBufferOverflow = … }` 尾随 lambda 实际绑定到
  `onUndeliveredElement`（必须用命名参数）。

相关：[PERF_CONTROL_HOME.md](../../../../docs/PERF_CONTROL_HOME.md)（控车首页，Perfetto 证据驱动）、
[PERF_FRAME_BUDGET.md](../../../../docs/PERF_FRAME_BUDGET.md)（本轮逐项清单）。
