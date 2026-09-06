# UI／集成回归测试

这组测试通过真实 Compose 页面输入、点击和移除页面，验证 UI、ViewModel、云会话与 MQTT 状态处理的协作。
源码位于 `app/src/test/java/com/tailg/plus/ui/regression/`，共 25 项测试。

## 覆盖范围

| 测试类 | 数量 | 行为 |
| --- | --- | --- |
| `LoginScreenTest` | 5 | 手机号／验证码校验、短信登录请求、Token 双击只提交一次、验证完成后登录回调只执行一次、拒绝登录提示、离页取消及迟到响应。 |
| `CloudTokenScreenTest` | 4 | 空 Token 提示、服务端错误可见并可重试、规范化 Authorization、验证后才保存凭据、请求中禁用提交、销毁 ViewModel 不误报登录失败。 |
| `GarageScreenTest` | 9 | 确认／取消切车、MQTT → BLE → 切车请求顺序、选中状态更新、两种断连期间离页取消、不可取消的清理结束后仍禁止切车、旧查询成功／失败不覆盖新结果、清空查询、离页取消搜索。 |
| `ControlScreenTest` | 7 | 发送成功仍等待回执、忽略其他车辆回执、设防状态确认及按钮更新、断连清 pending 不算成功、离页后完成确认并恢复显示、销毁 ViewModel 取消及清 busy、发送前切车／换账号禁止发令。 |

## 运行

需要与应用构建相同的 JDK 17 和 Android SDK。Windows 使用 `gradlew.bat`，Linux／macOS 使用 `./gradlew`。

```powershell
# 仅运行 UI／集成回归
.\gradlew.bat :app:testDebugUnitTest --tests 'com.tailg.plus.ui.regression.*'

# 全量测试、静态检查和 Debug 构建
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --continue
```

现有 GitHub Actions `build` 工作流的 `testDebugUnitTest` 会自动执行这组测试，失败会阻断构建。
HTML 报告位于 `app/build/reports/tests/testDebugUnitTest/index.html`，JUnit XML 位于
`app/build/test-results/testDebugUnitTest/`；CI 无论成功还是失败都会尝试上传 `reports` artifact。

## 测试环境与生命周期

- Robolectric 固定 API 35，加载真实 Android 资源；Compose 使用 JUnit4 v2 规则，界面配置为中文、411 × 891 dp。
- 全局 `robolectric.properties` 使用普通 `android.app.Application`，避免启用资源后通过 manifest 启动生产 Hilt、MQTT 或其他应用服务。原有 Robolectric 测试也使用这一默认配置。
- 保留真实 `OfficialCloudService`、ViewModel 和控车流程中的 `OfficialMqttService`。HTTP 使用可编排的内存 API，持久化使用替身；不创建生产 HTTP 客户端。未配置的 API 请求即使被后台刷新捕获，也会使测试失败。
- MQTT 禁用真实连接，并用会拒绝创建客户端的工厂兜底；只替换 publish 边界，回执仍经过真实解析、车辆身份校验和状态处理。BLE 不连接设备，定位使用替身。测试车辆不提供网络图片或地图坐标。
- 用 `CompletableDeferred` 控制请求返回与取消顺序；控车延时分别推进 Compose 时钟和 Android 主 Looper，不依赖真实睡眠。每个测试清理页面、ViewModelStore、服务及协程。
- 登录和车库请求由页面拥有，离页应取消。控车请求由 ViewModel 拥有，移除页面但保留 ViewModel 时应继续确认；清理 ViewModel 时才取消，且必须结束活动记录并清除 busy。

## 本轮测试复现并修复的问题

1. Token 页面创建了 `SnackbarHostState` 却没有挂载提示栏：空输入和服务端错误不可见。已挂载 `AppSnackbarHost`。
2. 登录页面／Token ViewModel 把 `CancellationException` 当作登录失败记录或排入提示队列。已让取消继续传播；短信登录及发送验证码的同类路径也按此处理。
3. 车库切车时的断连异常处理吞掉取消，离页后仍继续后续步骤。已传播取消，并在清理完成后、发送切车请求前检查协程仍然有效；包含 `NonCancellable` 清理的回归用例。

这组测试不替代真机上的权限弹窗、第三方验证码、完整导航图、真实服务器或 BLE／MQTT 设备联调，也不执行真实控车与 OTA。

## 验证记录（2026-09-07）

执行 `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug --continue` 成功：

| 检查 | 结果 |
| --- | --- |
| 全量测试 | 51 个测试类、332 项测试，0 失败、0 错误、0 跳过。 |
| 其中 UI／集成回归 | 4 个测试类、25 项，全部通过。 |
| Lint | 0 Error / Fatal，264 项 Warning。 |
| Debug APK | 构建成功。 |
