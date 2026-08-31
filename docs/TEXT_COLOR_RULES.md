# 文字与输入框颜色规则（Material3 1.3.2）

> 状态：**已修复**。记录「登录页手机号隐形」bug 的根因、修复与全项目输入框排查结论。
> 本文是**排查输入框颜色问题的第一手参考**——下次遇到「文字看不见」先读第 2 节。

相关文档：[CONVENTIONS.md](CONVENTIONS.md)（移植规范）、[UI_PORT_PLAN.md](UI_PORT_PLAN.md)（移植清单）。

---

## 1. 现象

登录页输入手机号 `18886120851` 后文字不可见；长按触发选中（系统「复制/剪切」菜单 + 绿色把手）
时，文字才在高亮反色下显现。确认环境为**浅色主题、白底输入框**。

## 2. 根因：`textStyle.color` 会覆盖 `TextFieldColors`

反编译 material3 **1.3.2**（BOM `2025.04.01`）的 `TextFieldKt.TextField` 字节码，文字颜色
解析逻辑如下：

```
1828: textStyle.getColor()                 // 读传入 textStyle 的 color
1845: 与 Color.Unspecified 比较
1860: 若 ≠ Unspecified → 直接用 textStyle 的 color   ← 关键
1899: 若 = Unspecified → 才用 colors.textColor(enabled, error, focused)
1953: textStyle.merge(...)
```

**规则**：只要传入的 `textStyle` 其 `color` 是显式值（非 `Color.Unspecified`），
就**完全忽略** `TextFieldColors` 里设置的 `focusedTextColor` / `unfocusedTextColor`
/ `disabledTextColor`。

而 `OutlinedTextField` 的 `textStyle` 参数默认值是 `LocalTextStyle.current`
（即 `MaterialTheme.typography.bodyLarge`）。因此：

> **输入框输入文字的真实颜色，实际由 typography 的 `bodyLarge.color` 决定，
> 而不是由 `cyberTextFieldColors()` 里的 `unfocusedTextColor` 决定。**

### 为什么会白字隐形

`Type.kt` 的 `TailgTypography` 在浅色主题迁移时**沿用了深色 VOID 调色板**：

| token | 旧值（深色） | 含义 |
|---|---|---|
| `AppColorsDark.textPrimary` | `0xFFF4F6FA` | 近白色 |
| `AppColorsDark.textSecondary` | `0xFF8B93A7` | 浅灰 |
| `AppColorsDark.textTertiary` | `0xFF5A6278` | 中灰 |

于是 `bodyLarge.color` = 白色；输入框容器 `CyberHomeColors.card = 0xFFFFFFFF`（白色）
→ **白字配白底**，文字隐形。选中后高亮反色才「浮出来」。

## 3. 修复

| 文件 | 改动 | 性质 |
|---|---|---|
| `ui/theme/Type.kt` | `TailgTypography` 颜色整体从 `AppColorsDark.*` → `CyberHomeColors.*` | **根治**（一处生效，覆盖全部输入框） |
| `ui/screens/LoginScreen.kt` | 手机号 / 验证码输入框显式 `textStyle = TextStyle(fontSize = 16.sp, color = CyberHomeColors.ink)` | 双保险 |
| `ui/components/CyberPageChrome.kt` | `cyberTextFieldColors()` 补 `selectionColors`（主题色把手/高亮） | 顺带修 |

映射关系：`textPrimary → ink`、`textSecondary → inkSecondary`、`textTertiary → inkMuted`。

### 为什么改 typography 不会误伤深色组件

- 所有 VOID 深色组件（`AppChrome` / `VoidCanvas` / `VoidGlass` / `ControlAndUnlockCard`
  / `VehicleControlGate` / `VehicleSwitchSheet` / `LucideIcon` 默认色）内部都
  **显式传了 `color = AppColorsDark.*`**，不依赖 typography 默认色。
- 唯一使用 `MaterialTheme.typography.*` 的 `AppSnack.kt` 也显式传了 `color`。

因此改色只影响「既无显式 color、又依赖 typography」的文字，正是修复目标。

## 4. 全项目输入框清单（21 个，`components/` 下无输入框）

**✅ 显式 `textStyle`（`color = ink`），自包含 —— 8 个**

| 文件 | 数量 | 说明 |
|---|---|---|
| `LoginScreen` | 3 | 2 个本次新增；Token 框原有 |
| `CloudTokenScreen` | 1 | 原有 |
| `GarageScreen` | 3 | 原有（搜索框 / 重命名 / 解绑验证） |
| `ProfileMineScreen` | 1 | 原有（昵称） |

**⚠️ 无显式 `textStyle`，靠 typography —— 14 个**

| 文件 | 输入框 | 数量 |
|---|---|---|
| `BindImeiScreen` | IMEI | 1 |
| `OfficialCloudScreen` | 手机号、验证码 | 2 |
| `ReplicaShareTab` | 成员姓名、手机号 | 2 |
| `ReplaceBatteryScreen` | 类型、电压、容量、规格 | 4 |
| `ReplicaFenceTab` | 纬度、经度、半径 | 3 |
| `ReplicaNfcTab` | 钥匙名、类型 | 2 |

> 这 14 个在 typography 迁移后已自动修复（`bodyLarge.color` 现为 `ink` 深色）。
> **不建议逐个补 `textStyle`**——补了需精确复刻默认字号（bodyLarge = 14.sp），
> 否则会意外改变字号，得不偿失。

## 5. 排查指南（重要）

### ⚠️ 死代码陷阱

在当前 typography 有显式 color 的前提下，
`cyberTextFieldColors()` 里的 `focusedTextColor` / `unfocusedTextColor` /
`disabledTextColor` **是死代码**——它们永远被 typography 的 color 覆盖，
仅在 `LocalTextStyle` 变为 `Unspecified` 时才生效。

### 下次遇到「输入框/文字颜色不对」

1. **先查 `Type.kt` 的 `TailgTypography`**（`bodyLarge.color`），不要先改
   `cyberTextFieldColors()`——改那里大概率无效。
2. 再确认该组件是否**显式传了 `color` 或 `textStyle`**（显式值优先级最高）。
3. 最后才看 `cyberTextFieldColors()` 的 `placeholderColor` / `labelColor` /
   `supportingTextColor` 等——这些**是生效的**（它们不参与 textStyle 的 color 竞争）。

### 未设置项的说明

`cyberTextFieldColors()` 未设 `errorTextColor`。这**不影响**功能：由于 typography
color 非 Unspecified，error 状态下不会走 `colors.textColor()` 分支，文字保持正常深色，
仅边框与 `supportingText` 变红——符合预期。

## 6. 遗留隐患（非本次范围）

项目并存两套 UI 体系：

- **VOID 深色**（旧）：`AppColorsDark` 白字，散落 `ui/components` 下多个组件
- **Cyber 浅色**（新，v8 Aurora Cockpit）：`CyberHomeColors` 深字

若某个 VOID 深色组件被放到浅色 `pageBg` 上渲染，其白字仍会隐形。属架构级重构，
需逐个确认组件宿主背景后迁移，与项目 P0「暗色主题硬编码」同源。
