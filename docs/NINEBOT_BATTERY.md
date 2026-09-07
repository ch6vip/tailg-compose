# 九号风格 · 电池信息

在设置中选择「九号」界面风格，再从车控页面打开「电池信息」。页面使用原生 Jetpack Compose，没有 WebView 或运行时网络素材。

## 视觉与交互

- 雾灰／墨黑双主题，电光蓝强调色；Space Grotesk 用于电量、续航及参数数字。
- 透视投影绘制石墨电池外壳、金属提手、玻璃反射与十段电芯。电芯按实际电量填充，支持水平拖动和弹簧回弹。这是电量示意图，不代表特定电池的外观。
- 顶部保留返回与同步操作。「能量概览」展示运行参数和评分；「电池档案」展示绑定规格、容量、总里程、BMS 明细与受车型支持限制的库仑计。
- 循环次数、评分、养护和换电服务均有可关闭的说明面板；更正电池仍使用原有规格选择流程。
- 页面、说明面板和同步提示统一使用真正的 Lucide 描边矢量，无表情符号。

## 数据与适配

数据仍由 `BatterySnapshot`、官方车辆／电池／BMS 服务提供。空值显示待读取，保留真实零值，不将缺失故障数据解释为安全或健康结论。官方续航缺失时，明确标注电量估算；距离遵循公制／英制设置。

刷新期间阻止重复提交，BMS 部分失败会显示失败状态；异步刷新继续使用发起时的车辆和登录会话校验。更换车辆后不会显示旧请求的成功提示。

支持中英文、大字体和窄屏。交互目标至少 48dp；分页提供选中状态，BMS 提供展开状态，电量图提供文字描述。浮动效果在滚动、不可见、说明面板打开和应用暂停时停止；系统关闭动画时使用静态状态。

## 文件

| 文件 | 职责 |
| --- | --- |
| `ui/screens/BatteryDetailsScreen.kt` | 现有数据及操作逻辑，按 `UiMode.NINEBOT` 接入新版 |
| `ui/screens/NinebotBatteryContent.kt` | 页面、状态、参数、档案、说明面板与同步提示 |
| `ui/screens/NinebotBatteryVisual.kt` | 电池透视绘制与触摸动效 |
| `res/values/battery_ninebot.xml`、`res/values-en/battery_ninebot.xml` | 中英文文案 |
| `ui/screens/NinebotBatteryContentTest.kt`（test） | 原生渲染和交互回归 |

## 验证与预览

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.tailg.plus.ui.screens.NinebotBatteryContentTest --max-workers=2
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug --max-workers=2
```

渲染测试使用 Robolectric Native Graphics 和独立示例数据，不连接真实车辆；PNG 写入被 Git 忽略的 `app/build/reports/ninebot-battery/`。包括深浅主题、未登录、满电、大字体窄屏、参数概览和电池档案。

回归覆盖登录入口、同步失败及刷新禁用、档案切换、BMS 展开、更正入口、说明面板、库仑计连接限制、公英制转换、零值和满电排版。

## 素材来源

- Lucide Static 0.525.0：官方 SVG 路径转换为 Android VectorDrawable，保留 24×24 视口、2px 描边和圆角端点。许可位于 `app/src/main/assets/licenses/lucide.txt`。
- Space Grotesk：Google Fonts 的 `ofl/spacegrotesk/SpaceGrotesk[wght].ttf`，随应用本地打包。OFL 许可位于 `app/src/main/assets/licenses/space_grotesk.txt`。
