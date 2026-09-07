# 悬浮底栏

在「设置 → 主题设置」开启「悬浮底栏」，主页面的服务、控车、我的、设置使用带滑动选中底托的胶囊导航。默认关闭，关闭时使用原有底栏。主题页的小屏预览会同步显示所选样式。

- 点击切换页面；重复点击当前页面不会重复导航。
- 横向拖动时底托跟手，松手后切换到目标页面，取消拖动则回到当前页面。拖动经过其他入口时不会打开它们。
- 选项通过现有 AppPreferencesService / DataStore 保存，对 VECTOR、九号及深浅主题生效。
- 底栏避让系统导航区和横向安全区，大屏居中显示，沿用系统字体缩放和动效设置；图标使用 Lucide。

外形与交互参考 KernelSU Manager 的 `ui/component/FloatingBottomBar.kt` 非模糊模式，使用项目现有 Compose API 实现。主体位于 `FloatingBottomBar.kt`，通过 `TailgNavHost` 接入原有导航逻辑。

回归测试覆盖开关与预览、配置恢复、取消拖动、松手切换、RTL、切换样式后保持选中页面，以及窄屏大字体。截图输出到被 Git 忽略的 `app/build/reports/floating-bottom-bar/`。
