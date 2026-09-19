# Agent Note: 设置页核心/普通分层与 SettingTile

Status: implemented

## Problem

设置页仍是 Flutter 官方页的直译：五个等权分组、同一套青色图标井、车辆/BMS 和语言/字号挤在同一视觉层级。底栏经典样式还绑死浅色 Cyber token，深色主题下描边发白。行组件写在屏幕文件里，关于页无法复用。

## Decision

设置页改成 Material 3 枢纽结构，视觉跟九号控车页对齐：雾灰页底、28dp 白卡、圆形线描图标井、墨色标题。车辆设置和电池/BMS 用 `SettingFeatureTile` 双卡打头；车库行保留 Core 强调；语言、单位、字号、主题、关于收成一组普通行。共享组件落在 `SettingTile.kt`。经典底栏用 `surfaceContainer` + `primary` 选中态，和悬浮底栏对齐。

## Alternatives considered

- 继续扩 `CyberCard` + `CyberSectionLabel`：能少动文件，但浅色 token 在深色主题里发灰，且无法把车辆/BMS 从普通行里拉开。
- 把设置页做成 Preference 库列表：层级清楚，但会丢掉现有 Lucide 图标井和主题页已经用的 Expressive 组件。
- 只改设置页、底栏仍走 CyberHomeColors：页面和导航会在深色主题下各画一套。

## Consequences

设置页不再镜像官方分组顺序。关于页的诊断/Token 行也切到 `SettingsGroup`，视觉会跟枢纽页一致。经典底栏选中色从墨色改成 primary，截图像素会变。代价是设置页依赖 Experimental Material3 TopAppBar。
