# tailg-compose

台铃智能官方 App（3.5.9）的 **Kotlin + Jetpack Compose + Material 3** 原生复刻，
是 `tailg-ble-app`（Flutter 复刻线）的姊妹工程。

- **包名**：`com.tailg.plus`
- **设计系统**：VOID COCKPIT（Lucide 风格图标、深色优先），令牌已映射为 Material 3 `ColorScheme` / `Typography` / `Shapes`
- **通道**：本地 BLE（TLink / QGJ + AES）、远程 MQTT、云 HTTP —— 与官方及 Flutter 复刻线对齐
- **验证**：本地 Android SDK 与 GitHub Actions 均可运行编译、单测和 Lint；本次审查结果见 [CODE_REVIEW.md](docs/CODE_REVIEW.md)

## 目录结构

```
app/src/main/java/com/tailg/plus/
├── TailgApplication.kt / MainActivity.kt
├── ui/
│   ├── theme/        # VOID COCKPIT → M3 令牌
│   ├── navigation/   # 路由图（TailgNavHost + Auth/Vehicle/Settings 子图）
│   ├── screens/      # 页面（历史移植清单见 docs/UI_PORT_PLAN.md）
│   └── components/   # 共享组件（CyberControlGrid、CyberMapView、VoidNav…）
├── data/
│   ├── model/        # 数据模型
│   ├── ble/          # BLE 协议（TLink/QGJ + AES）
│   ├── cloud/        # 云 HTTP（手写 OkHttp 传输 + Moshi）
│   ├── mqtt/         # MQTT（Paho）
│   ├── network/      # 网络层
│   ├── preferences/  # DataStore 偏好
│   └── store/        # 本地存储（DataStore/JSON）
├── domain/
│   └── control/      # 控车路由与状态机
├── di/               # Hilt 依赖注入
├── service/          # 前台服务（感应解锁等）
└── log/  util/  permission/  # 基础设施
```

## 构建

使用 **JDK 17** 和 Android SDK；当前 `compileSdk = 37`、`targetSdk = 36`。
CI 使用 Temurin 17，本地通过 `JAVA_HOME` 选择 JDK，通过 `ANDROID_HOME` 或
`local.properties` 的 `sdk.dir` 指定 SDK。

当前 Wrapper 为 Gradle 9.7.1，AGP 9.4.0、Kotlin 2.4.10；版本以
`gradle/wrapper/gradle-wrapper.properties`、`gradle/libs.versions.toml` 和
`app/build.gradle.kts` 为准。

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew assembleRelease
```

Windows 将 `./gradlew` 替换为 `.\gradlew.bat`。
未提供签名配置时，Release 构建生成 unsigned APK。

官方 C18 TLS broker（`www.tailgdd.com:6668`）使用私有 CA 自签证书（实测
CN=`c18_ex_base_pro.tailgdd.com` 且链不可验证），系统级校验必然失败——
官方 App 的 MqttUtil 正是为此安装了信任路径。本客户端对固定官方主机以及
**官方云下发的车辆 `mqHost` / `mqPort`** 默认采用兼容信任策略，跳过证书链校验，
并记录警告；该兼容策略在 Release 中同样存在，具有中间人攻击风险。
其他主机使用系统信任库。如确需在 Debug 构建中连接任意自签名测试 Broker，可显式启用：

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
| [docs/CODE_REVIEW.md](docs/CODE_REVIEW.md) | 本次全项目审查、已修复问题、验证结果与剩余限制 |
| [docs/CONVENTIONS.md](docs/CONVENTIONS.md) | 移植契约（Dart → Kotlin），移植期间所有子代理必须遵循 |
| [docs/UI_PORT_PLAN.md](docs/UI_PORT_PLAN.md) | UI 移植清单（29 页 → Compose），含每页落地状态 |
| [docs/PORT_WAVE2.md](docs/PORT_WAVE2.md) | Wave 2 委托简报（cloud/mqtt/control routing/stores） |

## 进度

| 阶段 | 状态 |
|------|------|
| 工程骨架 + M3 主题 + CI | ✅ |
| 模型 / 平台层（BLE/MQTT/云） | ✅ |
| 服务层（induction/location/coulomb/ota/diagnostic/ble-nfc） | ✅ |
| 自动连接（auto-connect，Flutter 线有） | ⛔ 移植后从未接线（无 UI 入口/零引用），已删除；需要时从 git 历史找回 |
| UI 29 页 + 导航图 | ✅ |
| 单测与回归 | 已覆盖协议、控车路由、会话并发、存储与页面数据逻辑；本次结果见审查记录 |
| 构建检查 | CI 配置 Debug / Release 构建、单测和 Lint；本地执行结果见审查记录 |
| Hilt DI 图 | ✅ (单例图 + EntryPoint；屏幕共用同一 graph，无双实例 factory) |
| Control ViewModel | ✅ 控车页会话状态迁入 Hilt ViewModel |
| MQTT TLS | 官方固定端点和云下发端点采用兼容信任；其他端点使用系统信任库，详见上文 |
| 设备功能 | 已实现 BLE 扫描、CameraX + ML Kit 扫码、位置与 MQTT；本次审查未执行真机联调 |
| 地图 SDK | ✅ osmdroid（高德瓦片默认 / 天地图 token 可选）——位置/轨迹/围栏三 tab + ControlScreen 迷你图 |
| 性能优化 | ✅ 渲染与状态流双重削减、控车确认链路对齐官方推送模型、共享位图缓存+采样解码+动画开关+图片在途去重+列表 contentType+grain 降采样+地图 DPI/zoom 限制+云 client 连接池+baseline profile+Compose 编译器指标（对照 ComicPlus_Pure 管线） |
