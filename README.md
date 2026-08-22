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
├── ui/theme/       # VOID COCKPIT → M3 令牌
├── ui/navigation/  # 路由图
├── data/           # 模型 + 仓库（cloud / mqtt / ble / storage）
├── domain/         # 用例与状态机
└── service/        # 前台服务（感应解锁等）
```

## 构建

需要 **JDK 17 或 21**（Gradle 8.12 的 Kotlin DSL 无法在 JDK 25 上解析版本号）。
CI 使用 Temurin 17；本机若默认是更新的 JDK，请设置 `JAVA_HOME` 后再构建。

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug
```

如确需兼容证书异常的测试 MQTT Broker，可仅在 Debug 构建中显式启用：

```bash
./gradlew assembleDebug -PallowInsecureMqttTls=true
```

该开关会跳过 MQTT 服务端证书校验，不应在日常构建或不可信网络中使用。

注意：官方 KKS/YJ 车型协议固定使用 `tcp://www.tailgdd.com:1883`，当前客户端
无法仅靠本地改动把它升级为 TLS；连接日志和诊断报告会明确标记为
`plaintext-tcp`。只有服务端提供兼容 TLS 端点并确认车型协议支持后，才能切换，
不要直接替换端口以免破坏远程控车兼容性。

## 进度

| 阶段 | 状态 |
|------|------|
| 工程骨架 + M3 主题 + CI | ✅ |
| 模型 / 平台层（BLE/MQTT/云） | ✅ |
| 服务层（auto-connect/induction/location/coulomb/ota/diagnostic/ble-nfc） | ✅ |
| UI 30 页 + 导航图 | ✅ |
| 测试移植 | ✅ (32 个测试文件,224 个测试,全部通过) |
| CI 全绿 | ✅ `assembleDebug` + `testDebugUnitTest` + `lintDebug` 全部成功 |
| Hilt DI 图 | ✅ (单例图 + EntryPoint；屏幕共用同一 graph，无双实例 factory) |
| Control ViewModel | ✅ 控车页会话状态迁入 Hilt ViewModel |
| MQTT TLS | ✅ 默认使用系统信任库；trust-all 仅 Debug 显式 opt-in |
| 真机能力 | ✅ BLE 扫描、CameraX + ML Kit 扫码、位置、MQTT |
| 地图 SDK | ✅ osmdroid（高德瓦片默认 / 天地图 token 可选）——位置/轨迹/围栏三 tab + ControlScreen 迷你图 |
