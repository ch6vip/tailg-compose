# 控车首页性能优化(Perfetto 证据驱动)

基于三版本 Perfetto trace(debug / release 未门控 / release 已门控)的卡顿帧归因,
对控车首页做的针对性优化。门控改动的效果见文末对比;本文记录的是门控之后的
**剩余瓶颈**(布局/绘制/文本测量)的代码修复。

## Trace 归因(卡顿帧)

| 帧时 | 主线程调用栈 | 归因 |
| --- | --- | --- |
| 139 ms | `traversal` → `syncAndDrawFrame` → `postAndWait` 128 ms | RenderThread 同步阻塞,根因是主线程绘制阶段过重 |
| 51 ms | `Record View#draw()` 34 ms + `AndroidOwner:measureAndLayout` 28 ms | 绘制 + 布局重测 |
| 34 ms | `TextStringSimpleNode::measure` 5.4 + `TextLayout:initLayout` 5.0 + `Constructing StaticLayout` 5.0 ms | 大字号文本测量 |

结论:动画已经很便宜(门控版 animation 片均 1.05 ms),剩余开销集中在
**draw 阶段的每帧分配**与**无谓的组合期状态**。

## 已实施优化

### 1. 缓存舞台渐变画刷 — `NinebotControlHome.kt`

```kotlin
// Before: 每次重组都新建 Brush + listOf
Column(Modifier.background(Brush.verticalGradient(listOf(r.stageTop, r.stageBottom))))

// After: 仅在调色板翻转时重建
val stageGradient = remember(r) { Brush.verticalGradient(listOf(r.stageTop, r.stageBottom)) }
```

`r`(NbReplica)已被 `remember(dark)` 缓存,但画刷没有。BLE 心跳 / 云端刷新每秒
驱动首页重组,每次重组都新建 `Brush.verticalGradient` + `listOf`,draw 阶段还要为
新 brush 重建 shader。这直接贡献 `Record View#draw()` 热点帧。改后只在深/浅色切换
时重建一次。

### 2. AppPressable 跳过恒透明背景的颜色动画 — `AppPressable.kt`

```kotlin
val hasVisibleBackground = pressedBackground != null || background.alpha > 0f
val bg = if (hasVisibleBackground) {
  animateColorAsState(/* ... */).value
} else {
  Color.Transparent
}
```

控车首页约 15 个 `AppPressable` 使用默认透明背景且不传 `pressedBackground`。这种
情况下按压/非按压两态的动画目标都是 `Transparent`,颜色动画**零视觉效果**,但每个
实例仍然持有一个 `Animatable` 并参与组合期的帧订阅。跳过后首页一次性移除约 15 个
无用动画状态。

分支条件只依赖 `background` / `pressedBackground` 这两个稳定参数,不依赖 `pressed`,
因此组合结构不会在按压中途翻转。

## 已审查但**不需要**改动的热点

代码层面的高频路径此前已优化到位,本次确认后保持不变:

- `VehicleStage.kt`:所有 `Path` / `Brush` / `Paint` 均为文件级 `val` 单例,
  `drawVehicleStage` 只读复用。
- `CyberMapView.kt`:`MapOverlayState` 实例复用 + `MapDataKey` diff,
  overlay 列表只在可见性翻转时增删;相机仅在目标变化时移动。
- `ControlScreen.kt`:所有回调 `remember {}`,派生状态全部 `remember(key)`,
  无重组导致的 lambda 重建。
- `CyberHomeColors`(`Color.kt`):`staticCompositionLocalOf` + `@ReadOnlyComposable`
  读取,不触发重组,不创建组合组。
- `MiniMap`(`CyberMapStats.kt`):单张 256px 静态瓦片,`produceState` 异步加载 +
  `RGB_565` 采样解码,不是绘制热点。
- `AnimatedValueText.kt`:`AnnotatedString` 与动画 target 已用结构性 key 稳定化,
  无关重组是真正的 no-op。

## 门控改动(trace 验证有效,随本次一并提交)

前一次提交未携带的两个工作区改动,本次随优化一起提交:

- `AppChrome.kt`:AppSkeleton 脉冲动画增加生命周期门控,仅在 `RESUMED` 时运行。
- `NinebotControlHome.kt`:车辆头部悬浮动画增加生命周期门控 + 检查模式排除。

## 三版本帧率对比(优化前基线)

| 版本 | 帧数 | avg | p50 | p90 | p99 | max | >16ms | >33ms |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DEBUG | 538 | 9.41 ms | — | — | — | 255 ms | 33 (6.1%) | — |
| RELEASE 未门控 | 803 | 4.55 ms | 3.40 | 10.79 | 18.16 | 49 ms | 19 (2.37%) | 2 |
| RELEASE 已门控 | 1233 | 2.68 ms | 1.88 | 4.14 | 13.21 | 139 ms | 11 (0.89%) | 6 |

门控使平均帧时 -41%、p90 -62%、卡顿率 2.37% → 0.89%;不可见期的动画片数从
14–29/秒降到 2–14/秒,后台不再持续驱动帧时钟。
