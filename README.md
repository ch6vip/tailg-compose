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

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug
```

## 进度

| 阶段 | 状态 |
|------|------|
| 工程骨架 + M3 主题 + CI | ✅ |
| 模型 / 平台层（BLE/MQTT/云） | ✅ |
| 服务层（auto-connect/induction/location/coulomb/ota/diagnostic/ble-nfc） | ✅ |
| UI 30 页 + 导航图 | ✅ |
| 测试移植 | ✅ (19 个测试文件) |
| CI 编译通过 | 🔧 修复中 |
