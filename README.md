# tailg-compose

台铃智能官方 App（3.5.9）的 **Kotlin + Jetpack Compose + Material 3** 原生复刻，
是 `tailg-ble-app`（Flutter 复刻线）的姊妹工程。

- **包名**：`com.tailg.plus`
- **设计系统**：VOID COCKPIT（Lucide 风格图标、深色优先），令牌已映射为 Material 3 `ColorScheme` / `Typography` / `Shapes`
- **通道**：本地 BLE（TLink / QGJ + AES）、远程 MQTT、云 HTTP —— 与官方及 Flutter 复刻线对齐
- **验证**：GitHub Actions 编译 + 单测 + lint（本机不装 Android SDK）

## 目录结构

```
app/src/main/java/com/tailg/plus/
├── TailgApplication.kt / MainActivity.kt
├── ui/
│   ├── theme/        # VOID COCKPIT → M3 令牌
│   ├── navigation/   # 路由图（TailgNavHost + Auth/Vehicle/Settings 子图）
│   ├── screens/      # 29 个页面移植（见 docs/UI_PORT_PLAN.md）
│   └── components/   # 33 个共享组件（CyberControlGrid、CyberMapView、VoidNav…）
├── data/
│   ├── model/        # 数据模型
│   ├── ble/          # BLE 协议（TLink/QGJ + AES）
│   ├── cloud/        # 云 HTTP（Retrofit + Moshi）
│   ├── mqtt/         # MQTT（Paho）
│   ├── network/      # 网络层
│   ├── preferences/  # DataStore 偏好
│   └── store/        # 本地存储（DataStore/JSON）
├── domain/
│   └── control/      # 控车路由与状态机
├── di/               # Hilt 依赖注入
├── service/          # 前台服务（感应解锁等）
├── log/  util/  permission/  # 基础设施
└── (187 个 Kotlin 文件)
```

## 构建

需要 **JDK 17 或 21**（Gradle 8.12 的 Kotlin DSL 无法在 JDK 25 上解析版本号）。
CI 使用 Temurin 17；本机若默认是更新的 JDK，请设置 `JAVA_HOME` 后再构建。

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug
```

官方 C18 TLS broker（`www.tailgdd.com:6668`）使用私有 CA 自签证书（实测
CN=`c18_ex_base_pro.tailgdd.com` 且链不可验证），系统级校验必然失败——
官方 App 的 MqttUtil 正是为此安装了信任路径。本客户端对**官方 broker 主机**
默认对齐该行为（跳过系统证书校验，日志中显式记录警告），其他任何主机仍走
严格校验。如确需在 Debug 构建中连接任意自签名测试 Broker，可显式启用：

```bash
./gradlew assembleDebug -PallowInsecureMqttTls=true
```

该开关仅影响非官方主机的调试场景，不应在日常构建或不可信网络中使用。

注意：官方 KKS/YJ 车型协议固定使用 `tcp://www.tailgdd.com:1883`，当前客户端
无法仅靠本地改动把它升级为 TLS；连接日志和诊断报告会明确标记为
`plaintext-tcp`。只有服务端提供兼容 TLS 端点并确认车型协议支持后，才能切换，
不要直接替换端口以免破坏远程控车兼容性。

## 文档

| 文档 | 说明 |
|------|------|
| [docs/CONVENTIONS.md](docs/CONVENTIONS.md) | 移植契约（Dart → Kotlin），移植期间所有子代理必须遵循 |
| [docs/UI_PORT_PLAN.md](docs/UI_PORT_PLAN.md) | UI 移植清单（29 页 → Compose），含每页落地状态 |
| [docs/PORT_WAVE2.md](docs/PORT_WAVE2.md) | Wave 2 委托简报（cloud/mqtt/control routing/stores） |

## 进度

| 阶段 | 状态 |
|------|------|
| 工程骨架 + M3 主题 + CI | ✅ |
| 模型 / 平台层（BLE/MQTT/云） | ✅ |
| 服务层（auto-connect/induction/location/coulomb/ota/diagnostic/ble-nfc） | ✅ |
| UI 29 页 + 导航图 | ✅ |
| 测试移植 | ✅ (32 个测试文件,224 个测试,全部通过) |
| CI 全绿 | ✅ `assembleDebug` + `testDebugUnitTest` + `lintDebug` 全部成功 |
| Hilt DI 图 | ✅ (单例图 + EntryPoint；屏幕共用同一 graph，无双实例 factory) |
| Control ViewModel | ✅ 控车页会话状态迁入 Hilt ViewModel |
| MQTT TLS | ✅ 默认使用系统信任库；trust-all 仅 Debug 显式 opt-in |
| 真机能力 | ✅ BLE 扫描、CameraX + ML Kit 扫码、位置、MQTT |
| 地图 SDK | ✅ osmdroid（高德瓦片默认 / 天地图 token 可选）——位置/轨迹/围栏三 tab + ControlScreen 迷你图 |
| 性能优化 | ✅ 渲染与状态流双重削减、控车确认链路对齐官方推送模型、共享位图缓存+采样解码+动画开关+图片在途去重+列表 contentType+grain 降采样+地图 DPI/zoom 限制+云 client 连接池+baseline profile+Compose 编译器指标（对照 ComicPlus_Pure 管线） |
