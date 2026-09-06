# 全项目代码审查记录

审查日期：2026-09-07。

范围覆盖应用源码、现有测试、Android 权限与生命周期、BLE / MQTT / HTTP 控车链路、
账号和车辆状态、持久化、页面异步请求、地图与图片、日志及构建配置。
本次通过静态审查和本地回归测试确认问题，并直接在工作区修复。没有连接真实车辆或官方 MQTT 服务，
没有发送真实控车指令。

## P1：已修复的高优先级问题

| 问题与触发条件 | 修复 | 回归依据 |
| --- | --- | --- |
| 控车入口只检查页面快照；不可用命令仍可能执行，切车后还可能使用旧 BLE 连接或旧通道。 | 执行器首先拒绝不可用命令；发送前和异步前置检查后重新核对会话、车辆及通道；BLE 匹配实际设备地址、本地关联或 QGJ 身份。库仑计同样检查车辆，扫描页连接另一设备前释放旧连接。 | `ControlCommandExecutorBranchesTest`、`ControlCommandRouteTest`、`ControlChannelResolverTest`、`CoulombMeterServiceTest`。 |
| HTTP 控车请求在连接失败或丢失响应时可能自动重试，造成重复操作。 | 默认关闭自动重试和 OkHttp 连接重试；只有明确声明为读取的接口使用读取重试策略。调用取消时取消底层请求；在回调内有界读取并关闭响应，使每主机并发限制生效。 | `OfficialCloudApiClientTest` 覆盖修改请求不重试、读取重试、取消及并发限制。 |
| BLE 排队任务取消后仍执行，或旧 GATT 回调在重连后完成新连接的等待槽。 | 取消时移除或停止相应队列任务；每个任务隔离取消；回调携带源 GATT 并校验；执行前检查提交时的 GATT；等待槽仅由所属请求清理。 | `GattOperationQueueTest`、`ConnectionManagerCallbackTest`。 |
| 登录、退出、切车与后台请求重叠时，旧结果及旧存盘任务可能恢复已退出的会话、覆盖新账号或污染新车辆数据。 | 会话写入串行化；登录请求增加代次；更换会话时重置账号数据；StateFlow 更新内校验 token 和车辆；存盘时在会话锁中读取最新状态；过期认证错误不能退出新会话。 | `OfficialCloudSessionConcurrencyTest`、`OfficialCloudRefreshConcurrencyTest`、`OfficialCloudStorageTest`。 |
| MQTT 只按 IMEI 复用连接；同车更换账号、token、broker 或凭据时仍使用旧连接。连接或订阅期间切换会话也可能安装旧客户端。 | 连接身份包含账号、会话、车辆及 MQTT 配置；串行预连接，在等待锁、连接与订阅后校验身份；废弃的客户端执行清理。 | `OfficialMqttConnectionTest` 使用注入的 Paho mock，覆盖连接复用、凭据变更、云绑定及连接期间更换会话。 |
| MQTT 清理 pending 状态被当作成功 ACK；发送失败后切车可能将旧命令通过 HTTP 发到新车；旧客户端消息及失效重连任务仍影响当前会话。 | ACK 单独记录，只由有效推送设置；发送后与 HTTP 回退前校验会话和车辆；推送保留客户端身份；重连绑定会话并由代次控制，显式断开取消重连。 | `OfficialMqttServiceTest` 覆盖同步快速 ACK、断开不构成确认及切车后禁止 HTTP 回退。 |
| 感应模式切车时旧任务继续执行，使用新车偏好或影响新 RSSI 循环；手动模式快速开关可能丢失最后一次选择。 | 感应操作按车辆串行并可取消，捕获原车偏好键，操作边界核对代次和实际 BLE 身份；RSSI 状态属于各自循环。手动模式串行完成持久化和状态发布。 | `InductionModeServiceTest`、`ManualModeServiceTest`。 |

主要实现位于 [BLE 平台层](../app/src/main/java/com/tailg/plus/data/ble/platform/)、
[云服务](../app/src/main/java/com/tailg/plus/data/cloud/)、
[MQTT 服务](../app/src/main/java/com/tailg/plus/data/mqtt/OfficialMqttService.kt)、
[控车领域层](../app/src/main/java/com/tailg/plus/domain/control/) 和
[设备服务](../app/src/main/java/com/tailg/plus/service/)。

## P2：其他已修复问题

| 问题与触发条件 | 修复与验证 |
| --- | --- |
| Android 12+ 扫描权限请求遗漏所需权限组合；返回页面后权限或蓝牙状态陈旧。 | 扫描与权限服务使用共享权限列表；页面恢复时重查，暂停时停止扫描；无 adapter / scanner 时关闭 Flow。`PermissionServiceTest` 验证实际请求列表。 |
| 库仑计设置超时或收到无关 D001 帧也返回目标状态，页面随后提示成功；原测试的 `tryEmit` 可能没有发出 ACK。 | 仅接收可解析的 SOC 状态帧，使用有界缓冲；超时返回未知，页面提示重新读取；状态与目标不符不显示成功。测试改用真实 Flow 发射，并覆盖无回包、无关帧、相反状态及切车。 |
| 骑行统计静默切周期被自身 period 条件丢弃，还会使前台请求过期；仅靠 TTL 也无法恢复已切走周期的数据。 | 原子请求代次、前台优先、带周期的缓存判断；状态更新再次校验请求。回归覆盖静默 DAY → WEEK → DAY、前后台竞争、乱序及排队请求。 |
| 电池、BMS、位置、围栏、历史轨迹及其 loading / error 被旧车响应覆盖；月份乱序和详情缓存合并存在丢失更新。 | 按会话、车辆和月份保护写入；在原子更新内合并详情。`OfficialCloudRefreshConcurrencyTest` 覆盖各类晚到响应。 |
| 车库搜索、分页和电池规格查询重叠时旧响应覆盖新选择；页面或导航退出后协程仍更新旧状态。 | 车库请求增加代次并去重分页；电池规格使用以车型和电池类型为键的 effect；取消继续向上传播；扫描结束仅在当前页面仍有效时返回。控车通知使用页面作用域，结束或取消时清理忙碌状态及命令日志。 |
| 地图缓存把多条行程串成一条折线；轨迹内部坐标改变或重新进入轨迹模式时不更新视角。 | 仅绘制用户选择的行程，并按车辆及月份重置选择；地图 key 包含完整坐标，离开轨迹模式清理 key，延迟视角回调检查当前数据。通过代码审查及构建验证，显示效果待设备确认。 |
| Wi-Fi 切移动网络时旧网络的 lost 回调把当前可用网络标记为断网。 | 使用默认网络回调并核对网络身份，注册失败仍发出初始可用性。`NetworkAvailabilityServiceTest` 覆盖切网顺序。 |
| 定位协程取消或超时后底层定位任务仍运行，回调还可能再次恢复已结束的请求。 | 使用定位 CancellationToken，在协程取消时停止定位；回调检查 continuation 状态；静默定位节流表改为并发容器。 |
| 多页面各自创建消息已读存储，角标和隐藏状态不同步；并发修改会丢失结果。 | Hilt 共享单例，使用不可变 StateFlow 和 Mutex，持久化与发布统一完成。`MessageReadStoreTest` 覆盖共享状态及更新。 |
| 偏好初始化覆盖后续修改；损坏的缩放值导致无效布局密度。 | 初始化与修改串行；读取设超时；取消继续传播；缩放只接受有限值并约束在 0.5–2.0。 |
| 无 Content-Length 或分块传输的车辆图片和地图瓦片可无界读取。 | 读取过程中执行字节上限：车辆图片 5 MiB、瓦片 2 MiB；换图先重置加载状态。`BoundedInputStreamTest` 覆盖限额与边界。 |
| 加密 SharedPreferences 的 `commit()` 失败被忽略，迁移仍清除源数据。 | 失败抛出可处理的 IO 错误，仅在安全存储提交成功后清除迁移来源；保留安全存储无法初始化时的既有降级。测试覆盖凭据与车辆缓存迁移，以及保存和退出失败。 |
| 日志或请求路径漏脱敏验证码、MQTT / BLE 密钥等字段；Basic Authorization 只遮住 Basic 而保留完整凭据。 | 补全敏感字段，完整遮蔽 Basic 凭据，保留 Bearer 的既有脱敏格式；移除明文用户标识日志。`SensitiveTextRedactorTest` 覆盖日志和请求路径。 |
| OTA 分块大小、固件长度无有效边界，写入异常可能停留在处理中，字节值被按有符号解释。 | 校验分块及长度，写入异常进入 FAILED，取消继续传播，按无符号解析。`FirmwareOtaServiceTest` 覆盖边界与写入失败；生产固件下载仍保持禁用。 |
| 切换应用语言时 LocalizedContext 不能强转 Activity，导致感应设置权限宿主缺失；App Bundle 按语言拆包后可能缺少目标语言。 | 使用 `LocalActivity.current`；禁用 App Bundle 的语言拆分，随包保留语言资源。 |

## 验证

环境：Windows、JDK 17、本地 Android SDK、Gradle 9.7.1、AGP 9.4.0、Kotlin 2.4.10，
`compileSdk = 37`、`targetSdk = 36`、`minSdk = 26`。

| 检查 | 结果 |
| --- | --- |
| 全量 `:app:testDebugUnitTest` | 47 个测试类、307 项测试，0 失败、0 错误、0 跳过；此前执行的 58 项定向回归同样全部通过。 |
| `:app:lintDebug` | 0 Error / Fatal，264 项 Warning；其中 218 项为未使用资源提示。 |
| `:app:assembleDebug` | 构建成功。 |
| `:app:assembleRelease` / `:app:bundleRelease` | 构建成功，R8、资源收缩和 Release Lint 检查通过；产物未签名。 |
| `git diff --check` | 通过。 |

最终合并验证命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:bundleRelease --continue --console=plain
```

结果：`BUILD SUCCESSFUL in 9m 27s`。

本地输出（构建目录未纳入版本控制）：

- [Debug APK](../app/build/outputs/apk/debug/app-debug.apk)：50.61 MiB。
- [Release unsigned APK](../app/build/outputs/apk/release/app-release-unsigned.apk)：25.24 MiB。
- [Release App Bundle](../app/build/outputs/bundle/release/app-release.aab)：19.30 MiB。
- [单测报告](../app/build/reports/tests/testDebugUnitTest/index.html)。
- [Lint 报告](../app/build/reports/lint-results-debug.html)。

测试使用 mock、内存存储或本地测试数据。MQTT 连接生命周期测试注入 Paho mock，
不会连接真实 broker。Release 未配置签名密钥，因此产物为未签名包。
GitHub Actions 的现有构建流程已审查；上述结果来自本地执行，推送后的 CI 结果以 GitHub Actions 为准。

## 保留的兼容限制与设备验证

- MQTT 的固定官方 TLS 主机，以及官方云下发的 `mqHost` / `mqPort`，仍使用兼容信任策略，
  跳过证书链校验；这也适用于 Release，存在中间人攻击风险。KKS / YJ 官方协议仍使用明文 TCP。
  消除这些风险需要可验证的官方证书链或证书固定依据，以及兼容的服务端 TLS 端点；
  本次未凭空更换信任根或协议端口。README 已纠正此前“仅 Debug trust-all”的错误描述。
- BLE 的 AES/ECB 用于兼容现有车辆协议，不能单方面更改算法。Lint 的相关加密与信任管理器警告保留，
  没有降低 Lint 的错误检查规则。
- 仍需真机验证 Android 12+ 权限授予与撤销、BLE 断连和重连、车辆切换、感应模式、设备 ACK 时序、
  MQTT 网络恢复、相机扫码、位置权限与地图显示。JVM / Robolectric 测试不能替代车辆和系统蓝牙栈联调。
- 生产固件下载与真实 OTA 不在本次验证范围内，未启用或执行。
- 剩余 Lint 提示中的未使用资源、版本升级建议及 Compose 写法建议没有被批量删除或自动改写；
  它们与上述已修复的功能缺陷分开记录。
