# VECTOR · 矢量

在「设置 → 界面风格」选择 VECTOR。原风格的存储值 `0` 继续使用，九号的值仍为 `2`；切换风格不会重置账户、车辆或主题偏好。

VECTOR 将控车、服务、我的和设置统一为大号数字、编号章节、切角面板与原生线条绘制。浅色使用纸白背景与碳黑文字，深色使用近黑背景；酸橙色用于重点面板与选中项。主题集中在 `VectorTheme.kt`，使用现有 Space Grotesk 字体，并由系统字体补齐中文。

## 页面与交互

- 控车页突出真实续航、电量与通道状态；车辆插画为装饰，车辆照片仍使用已有缓存加载器。滑动电源、六项控车、NFC、位置和里程保留原业务回调。未知读数和状态明确标为未知。
- 服务页保留位置、轨迹、围栏、车辆设置、电池、骑行统计、故障诊断和官方账户八个入口。
- 我的页保留登录、昵称编辑、车辆切换、消息、关于与退出登录操作；消息数、电量和在线状态均来自现有账户状态。
- 设置页提供外观入口与编号分区；主题预览跟随风格、浅深色及底栏选择。
- 两种底栏保持半透明，页面可从其后方滚动，末尾操作与 Snackbar 使用实际测得的底栏避让距离。
- 按压使用弹簧反馈，入场遵循减少动态效果设置。大字体和窄屏下允许内容增高或纵向排列，操作区域保留可触达尺寸。

## 图标

界面不使用表情符号作为图标或文案。`LucideIcon` 和 `NinebotIcon` 均使用官方 `lucide-static@0.525.0` 图形，保留 24 × 24 坐标、2 单位描边与圆角端点；资源文件位于 `res/drawable/ic_lucide_*.xml`，许可证随应用打包在 `assets/licenses/lucide.txt`。

开关、选择菜单、下拉箭头、返回键、日期选择器和前台通知同样使用 Lucide。日期选择器保留 Material `DatePickerState` 的 UTC 日期契约、年份范围和不可选日期规则，并支持按本地顺序输入年月日。

## 验证

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --continue --console=plain
```

`VectorControlHomeTest`、`VectorPagesTest` 与 `VectorSettingsTest` 覆盖业务回调、风格和外观偏好持久化、未知状态、浅深色，以及 320 dp / 1.5 倍字体下的实际触摸和底栏避让。`LucideDatePickerTest` 覆盖 UTC、闰年、范围限制、不可选日期、点击及输入。

VECTOR 的 Robolectric 原生渲染截图输出到 `app/build/reports/vector-design/`。截图使用测试数据，验证过程中不会向真实车辆发送命令。
