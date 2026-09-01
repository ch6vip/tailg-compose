# UI 一致性排查报告（VOID 深色系 vs Cyber 浅色系）

> 排查范围：`app/src/main/java/com/tailg/plus/ui/**`
> 结论一句话：**当前没有「白字隐形」级别的 P0 bug**；主要问题是「默认值绑定错了 token」「深色组件嵌浅色页面」和「一批 VOID 死代码」。

---

## 1. 两套颜色体系

| 体系 | 定义 | 页面背景 | 前景文字 | 来源 |
|---|---|---|---|---|
| **VOID 深色** | `AppColors` / `AppColorsDark` | `pageBg = 0xFF05070B`（近黑） | `textPrimary = 0xFFF4F6FA`（白） | Flutter replica 的 dark-first 设计 |
| **VOID 浅色伴生** | `AppColorsLight` | `pageBg = 0xFFF3F5F8` | `textPrimary = 0xFF0B1220` | 很少用 |
| **Cyber 浅色** | `CyberHomeColors` | `pageBg = 0xFFF4F5F7` | `ink = 0xFF15171C` | 2026 浅色 cockpit 重建 |

主题状态（`Theme.kt`）：
- `TailgTheme.darkTheme` 默认 **false**，永远用 `LightColorScheme`（`background = AppColorsLight.pageBg`）。
- `DarkColorScheme` 保留为参考，从不激活。
- `TailgTypography`（`Type.kt`）已全部迁到 `CyberHomeColors.ink*`，**不再泄露白色文字**。

---

## 2. 已修复（本次排查前即已闭环）

- **登录页手机号「隐形」**：`Type.kt` typography 曾硬编码 `AppColorsDark.textPrimary`（白），导致白字白底。已迁到 `CyberHomeColors`。
- **全项目输入框**：21 个输入框因 typography 迁移已全部修复，无需逐个补 `textStyle`（见 `docs/TEXT_COLOR_RULES.md`）。

---

## 3. 隐患清单（按优先级）

### P1 — `LucideIcon` 默认色绑定深色 token（活代码里唯一实质隐患）

`LucideIcon.kt:159`

```kotlin
color: Color = AppColorsDark.textSecondary,   // 0xFF8B93A7 灰蓝
```

- 全局 138 处 `LucideIcon(...)` 调用，绝大多数显式传了 `color = CyberHomeColors.*`，没问题。
- 但**任何漏传 `color` 的调用**会回落到这个 VOID 深色灰蓝，在浅色 `CyberHomeColors.pageBg/card` 上对比度仅约 **2.5:1**（WCAG 要求 ≥ 4.5:1），图标偏淡、接近隐形。
- **根治**：把默认值改成 `CyberHomeColors.inkMuted`，或改为 `Color.Unspecified` 让 `Icon` 走 `LocalContentColor`（跟随宿主）。改默认值即可一劳永逸，无需逐个调用点补参。

### P1/P2 — 深色 VOID 组件嵌在浅色 Cyber 页面（风格割裂，功能正常）

| 组件 | 位置 | 现象 |
|---|---|---|
| `VehicleSwitchSheet` | VehicleSwitchSheet.kt:77 | 切车弹窗 `containerColor = AppColorsDark.surfaceContainerHigh`（深色），在浅色控车页 / 我的页弹出，一浅一深割裂 |
| `VehicleControlGateBanner` | VehicleControlGate.kt:76 | 门禁 banner `background(AppColorsDark.surface.copy(alpha=0.85f))`（深色玻璃），显示在浅色控车页 gate overlay 里 |

两者**自绘深色背景 + 白字，不会隐形**，但和四周浅色 UI 明显不搭。若要统一浅色体系，需把这两处的 `AppColorsDark.surface*` / `AppColorsDark.text*` 迁到 `CyberHomeColors.card` / `CyberHomeColors.ink*`。

### P2 — `AppSnack` 是 VOID 彩色 snackbar（可选统一）

`AppSnack.kt` 全局 20+ 页面使用：错误=红底白字、成功=绿底深字、信息=深灰底白字。这是**刻意保留的 VOID 风格**，彩色 snackbar 属通用设计、非 bug；若追求纯浅色体系可迁，但非必须。

---

## 4. 死代码

按**实际导出符号**逐一验证（词边界排除定义文件，而非按文件名）：

- **整文件死代码（7 个，所有导出符号 0 引用）**：`VoidCanvas.kt` / `ControlAndUnlockCard.kt` / `VoidParticles.kt` / `VoidTypography.kt` / `AppToast.kt` / `StatusBadge.kt` / `VoidGlass.kt`。
- **半死文件（1 个）**：`AppChrome.kt` —— `AppSkeleton` 是活代码（18 处引用），其余 `AppPageHeader` / `AppSectionLabel` / `AppCard` / `AppHeaderAction` / `AppEmptyState` 是死符号。

> **关键坑**：这些文件的导出符号名 ≠ 文件名（如 `AppChrome.kt` 导出的是 `AppCard`/`AppPageHeader`/`AppSkeleton`）。按文件名词边界 grep 会把内部依赖误判。正确做法是逐一验证每个导出符号。
>
> `VehicleSettingsScreen` 里的 `StatusBadge` 是它自己定义的 `VehicleStatusBadge`，不是 `components/StatusBadge.kt`。

> 活代码（勿删）：`Lucide.kt` 图标映射、`AppPressable`、`AppSnack`、`LucideIcon`、`VehicleStage`、`SlidePowerButton`、`AppSkeleton` 等。

---

## 5. 修复建议（按性价比排序）

1. **【P1，1 行，强烈建议】** `LucideIcon.kt:159` 默认色 `AppColorsDark.textSecondary` → `CyberHomeColors.inkMuted`（或 `Color.Unspecified`）。 ✅ 已执行
2. **【P1/P2，2 文件】** `VehicleSwitchSheet`、`VehicleControlGateBanner` 迁到 `CyberHomeColors` 浅色 token，消除「深色弹窗/横幅在浅色页」的割裂。 ✅ 已执行
3. **【P2，清理】** 删除 7 个死代码 VOID 文件 + `AppChrome.kt` 内部 5 个死符号（`git` 可恢复）；`AppSkeleton` 骨架条底色一并迁浅色 token。 ✅ 已执行
4. **【P2，可选】** `AppSnack` 按需迁浅色；`Theme.kt` 的 `DarkColorScheme` 若确认永不需要可一并精简。 ⏳ 未执行（可选）

---

## 6. 执行记录（2026-09-02）

- `LucideIcon.kt`：默认 `color` → `CyberHomeColors.inkMuted`。
- `VehicleSwitchSheet.kt`：深色弹窗 → 浅色卡片（`card`/`ink`/`inkMuted`/`line`/`primary`/`primarySoft`）。
- `VehicleControlGate.kt`：深色玻璃横幅 → 浅色卡片 + 蓝色主按钮（`card`/`primary`/`ink`/`white`）。
- `AppChrome.kt`：仅保留 `AppSkeleton`，骨架条底色 `surfaceContainerHigh/Low` → `control/controlStrong`；删除 `AppPageHeader`/`AppSectionLabel`/`AppCard`/`AppHeaderAction`/`AppEmptyState`。
- 删除 7 个死代码文件（见第 4 节）。
- `compileDebugKotlin` + `testDebugUnitTest` 通过。

---

*生成时间：2026-09-02*
