# 全项目代码审查补充记录

日期：2026-09-07。工作区基线：`bb1277c`。

本记录接续本轮全项目审查，保留 [之前的审查记录](CODE_REVIEW.md) 及其历史验证数据。
以下修复均已在本地工作区完成并通过验证。审查结合静态调用链检查、可控的并发故障注入、
JVM / Robolectric 回归和 Android 构建；没有连接真实车辆或官方 broker，没有发送真实控车指令。

## 覆盖范围

检查覆盖 BLE 协议和 Android GATT 生命周期、HTTP / MQTT、账号与车辆切换、控车执行器、
感应服务与前台服务、DataStore 和本地缓存、相机与验证码 WebView、地图与位置、
Compose 页面和交互组件、数据模型与格式化辅助函数、资源与清单、Gradle / R8 / CI。
当前主源码共 199 个 Kotlin 文件；另对 60 个资源 XML 做了解析检查，中英文字符串文件没有重复资源名。
纯布局和无修改的兼容代码以静态检查为主，新增回归集中在真实故障及异步边界。

本轮开始时的本地基线为 56 个测试类、362 项测试通过；这不是下文最终验证的统计。

## P1：会话、控车与连接问题

| 触发条件与影响 | 修复 | 主要回归依据 |
| --- | --- | --- |
| 退出后用相同 Token 重新登录，旧资料响应仍可覆盖新会话；旧 401 还可退出新登录，旧 MQTT 失败可进入 HTTP 回退。 | 为每次本地登录增加会话代次，状态写入、缓存键、合并请求、认证失败及 MQTT 连接/回退均携带会话身份；身份日志不输出 Token。 | `OfficialCloudSessionConcurrencyTest`、`OfficialCloudRefreshConcurrencyTest`、`OfficialMqttServiceTest`。 |
| 云端切车和本地车辆映射写入重叠，旧操作覆盖新默认车辆或车辆关联。 | 选择、关联及本地同步在会话写锁内按序执行，读取当前选择后再提交。 | `OfficialCloudLocalVehicleSyncTest`。 |
| MQTT 收到其他 IMEI 的控车回包，或旧客户端消息晚到，影响当前车辆状态和待确认命令。 | 按命令 IMEI、源客户端、车辆及会话身份核对推送，并在云状态原子写入处再次验证。 | `OfficialMqttServiceTest`、`OfficialCloudSessionConcurrencyTest`。 |
| 控车执行器把调用者取消当成自身超时，继续运行失败处理；部分修改接口声明了读取重试策略。 | 仅将执行器自身超时转成超时结果，保留调用者取消；五处云端修改调用显式使用 `NO_RETRY`。 | `ControlCommandExecutorBranchesTest`、`OfficialCloudMutationRetryTest`。 |
| 已提交的 GATT 读写等待被取消或超时，同一连接的晚到 Android 回调可能满足下一次同类操作。 | 退休并关闭存在未完成操作的 GATT，后续任务拒绝该连接；保留原调用的超时/取消结果。 | `ConnectionManagerOperationsTest`、`ConnectionManagerCallbackTest`。 |
| 主动断开未停止初始连接重试；重连在服务发现或协议握手失败后提前退出；仅完成服务设置就报告重连成功。 | 主动断开取消并等待初始连接；连接超时作为可重试失败；每次重连重新设置断连保护，沿用同一轮重试预算并等待实际 LOGIN；成功和下一次断线的所有权交接受锁保护。 | `ConnectionManagerLifecycleTest` 覆盖失败预算耗尽、无 LOGIN、主动取消及登录后再次断线。 |
| 系统配对广播先于接收器注册，或其他设备的配对广播完成当前请求；TLink 协议处理脱离来源 GATT。 | 先注册再配对，检查目标设备，使用适合蓝牙系统广播的接收模式并清理接收器；TLink 回包在来源身份锁内处理，登录写入保留来源。 | `ConnectionManagerBondTest`、`ConnectionManagerCallbackTest`。 |
| 感应任务跨车辆/校准/手动模式变化继续执行，或前台服务尚未真正启动就允许自动操作。 | RSSI 循环捕获绑定和校准，在执行边界校验车辆、手动模式及前台服务存活；`startForeground` 成功后才确认启动，调用者有界等待。 | `InductionModeServiceTest`、`InductionForegroundServiceTest`。 |
| 本地 NFC 记录与物理车辆/槽位没有映射，但删除任何记录都会发送槽位 `01` 的删除指令。 | 本地记录增删改只更新本机数据，补充中英文说明；物理 NFC 服务保持独立，并仅允许 TLink LOGIN。 | 本地页面调用链审查、`ReplicaFeatureStoreTest`、`BleNfcServiceTest`。 |
| NFC 增删帧填充错误，追加四字节 TLink Token 后不满足 AES 块长度。 | 按官方 `TailgBleUtils.HEADER_SEND_CUSHION_SET_BODY` 改为 `56789ABCDE`，校验槽位和类型参数。 | `BleProtocolEdgeTest` 验证帧及加密长度；参考本地官方 3.5.9 反编译源码。 |
| 车库只监听 Token，同 Token 重登不刷新；旧切车操作在不可取消的通道清理完成后仍继续影响新会话。 | 车库内容、记忆状态、弹窗和协程绑定登录代次；查询结果及每个切车边界验证会话，MQTT 清理后先检查取消再处理 BLE。 | `GarageScreenTest` 覆盖同 Token 重载及等待清理期间换会话。 |

GATT 退休解决的是 Android 回调没有操作编号的问题。协议层在一次写入完成后晚到的业务 ACK
仍受协议本身约束，不能据此声称所有协议 ACK 都已有唯一关联。

## P2：持久化、页面与数据问题

| 触发条件与影响 | 修复与验证 |
| --- | --- |
| DataStore 读取封装吞掉调用者取消；车辆内存状态在磁盘写入失败后仍显示成功，默认车辆变化无法单独订阅。 | 区分自身读取超时与外部取消；车辆列表和默认项使用已提交快照，失败回滚，新增默认车辆 Flow。`DataStoreReadTest`、`VehicleStoreTest`。 |
| 多页面编辑本地 NFC / 共享记录时，各自旧列表覆盖其他页面更新；保存失败仍关闭弹窗。 | 使用 Hilt 共享存储，在 DataStore 事务中更新最新列表；读取等待已开始的提交，页面成功落盘后才发布结果；支持加载失败重试，保存期间禁止再次提交和编辑。`ReplicaFeatureStoreTest` 与页面检查。 |
| NFC / 共享页面标题操作没有实际功能。 | 标题操作接入添加弹窗；移除围栏标题无效操作，保留页面内有效的位置操作。 |
| 消息订阅字段为数字时，字符串判断把有效状态识别错误；消息修改缺少会话保护。 | 统一解析布尔/数字标记，修改操作验证登录和目标会话。`OfficialCloudMessageOperationsTest`。 |
| 地图接收非有限或越界坐标，跨极区/日期变更线的围栏生成无效经纬度；生命周期宿主替换时销毁仍在复用的地图。 | 共用坐标有效性检查，过滤无效轨迹；使用球面距离生成围栏并规范经度；地图销毁与宿主观察分离，延迟回调检查释放状态。`VehicleCoordinateTest`、`CyberMapGeometryTest`。 |
| 切换行程月份后显示旧数据，旧月份响应还会改写新月份的 loading/error。 | 切月时清理旧数据，并在原子写入内验证请求月份。`OfficialCloudRefreshConcurrencyTest`。 |
| 带空格的消息时间解析不一致，接口的无时区时间被作为 UTC 展示。 | 统一时间分隔符解析，消息按设备时区解释；持久化时间保留既有 UTC 默认值。`OfficialCloudMessageTest`。 |
| Token 含非 ASCII 字符时按字符码编码，生成错误的 Authorization 值。 | 按 UTF-8 字节执行百分号编码。`OfficialCloudAuthParserTest`。 |
| 骑行模式设置把无效/不匹配读回当成成功，TLink 感应写失败遗留 ACK 槽。 | 只有有效且匹配的模式读回才确认成功；写失败和取消时清理所属等待槽。BLE 回调与操作回归。 |
| 偏好、消息、本地设置或日志导出写入失败，异常逃出 UI 协程。 | 使用统一 `AppSnack.runAction` 呈现错误并保留取消语义；诊断历史读取有超时且继续传播取消。页面检查、既有主题设置回归。 |
| 扫码页面释放后仍接受 ML Kit 结果，处理失败漏关图像，返回页面后使用旧权限状态。 | 忽略已释放和晚到结果，失败路径关闭帧，在恢复时重查权限。`BarcodeAnalyzerTest`。 |
| 验证码 JS 重复/晚到结果触发重复提交，关闭弹窗后 Handler / WebView 仍有活动。 | 只接受一次有效结果，释放时清理回调、桥接和 WebView；通过具有具体参数类型的函数注册接口，使 Lint 正确识别 JS 接口注解。`CaptchaJsInterfaceTest`。 |
| 滑动按钮重组后调用旧回调，发送异常后一直忙碌，未知电源状态被当成成功确认。 | 获取最新回调，统一清理等待状态，禁止重复激活和无拖动范围；确认必须有明确状态变化。`SlidePowerButtonTest` 覆盖真实滑动与失败重试。 |
| 车辆切换列表接收 `onTap` 却没有绑定点击，失败后选择状态也可能不复位。 | 行使用可选择语义并绑定点击，异步选择在 `finally` 复位。`VehicleSwitchSheetTest` 覆盖选择、失败重试和重复点击。 |
| `AppPressable` 的选中/禁用语义参数无效，无长按行为也暴露长按动作。 | 同步点击及无障碍动作、选中/禁用状态，只在存在长按回调时暴露该动作。`AppPressableTest`；文本字段行同时传递禁用状态。 |
| 菜单手势捕获旧回调；围栏输入受本地化小数格式影响且接受非法范围。 | 手势按回调更新，坐标使用固定小数格式，严格校验坐标和半径。代码路径检查和构建验证。 |
| CI 未构建 AAB，缺少签名时 Release APK 上传路径不匹配。 | 构建加入 `bundleRelease`，上传通配匹配签名/未签名 APK，并增加 AAB 产物；相关构建任务已在本地通过。 |

## 最终验证

环境：Windows、JDK 17、本地 Android SDK、Gradle 9.7.1、AGP 9.4.0、Kotlin 2.4.10，
`compileSdk = 37`、`targetSdk = 36`、`minSdk = 26`。

最终执行命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:bundleRelease --continue --console=plain
```

结果：`BUILD SUCCESSFUL in 7m 39s`。以下统计来自项目默认检查配置下的完整执行。

| 检查 | 最终结果 |
| --- | --- |
| `:app:testDebugUnitTest` | 75 个测试类、440 项测试，0 失败、0 错误、0 跳过。 |
| `:app:lintDebug` | 0 Error / Fatal，272 项 Warning，3 项 Hint。 |
| `:app:assembleDebug` | 成功。 |
| `:app:assembleRelease` | 成功，R8、资源压缩及 Release Lint 检查通过；APK 未签名。 |
| `:app:bundleRelease` | 成功，AAB 未签名。 |
| `git diff --check` | 通过。 |

剩余 Lint 项目分布：226 项未使用资源、18 项 `VisibleForTests`、6 项依赖版本建议、
5 项屏幕尺寸 API 建议、5 项协议加密/TLS 信任警告、4 项 KTX 建议、
2 项 Modifier 参数顺序、2 项过时 SDK 条件、2 项 offset 写法、
1 项 targetSdk 提示、1 项跨版本清单属性提示；另有 3 项原始类型状态容器建议。
TLS 和协议加密的限制见下节。

本地产物及报告（构建目录未纳入版本控制）：

| 文件 | 大小 |
| --- | --- |
| [Debug APK](../app/build/outputs/apk/debug/app-debug.apk) | 52.03 MiB |
| [Release unsigned APK](../app/build/outputs/apk/release/app-release-unsigned.apk) | 25.48 MiB |
| [Release AAB](../app/build/outputs/bundle/release/app-release.aab) | 19.69 MiB |

- [完整单测报告](../app/build/reports/tests/testDebugUnitTest/index.html)。
- [完整 Lint 报告](../app/build/reports/lint-results-debug.html)。
- [验证统计与产物 SHA-256](../app/build/reports/code-review-verification.json)。

GitHub Actions 工作流已纳入 AAB 构建和上传；本记录中的结果均来自本地执行。

## 保留的兼容限制与验证边界

- 官方 MQTT TLS 兼容路径仍使用宽松证书信任，KKS / YJ 仍使用明文 TCP。消除这些风险需要可验证的官方证书链及兼容端点，未凭空修改服务端配置；详见原审查记录。
- BLE AES/ECB 属于现有车辆协议兼容要求。真实协议登录、ACK 时序、掉线重连、配对及感应动作仍需真机和车辆联调。
- 本地 NFC / 共享记录和围栏草稿没有被升级为云端或车辆功能；记录页已明确说明本机保存范围。生产固件下载和 OTA 仍未启用。
- 相机、WebView、地图宿主切换、权限撤销及 Android 前台服务限制的系统行为，尚未在真实设备验证。
- Release 未配置签名密钥；构建成功也不代表已经签名、安装或发布。剩余非阻断 Lint 提示按类别记录，不通过批量删除资源或关闭检查掩盖。
- 本次结论针对检查范围和可复现场景，不代表项目不存在其他缺陷。
