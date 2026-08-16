package com.tailg.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.permission.AppPermissionService
import com.tailg.plus.service.DataStoreInductionPrefs
import com.tailg.plus.service.InductionModeService
import com.tailg.plus.service.InductionModeSnapshot
import com.tailg.plus.service.InductionStack
import com.tailg.plus.service.ManualModeService
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.components.AppSnackbarHost
import com.tailg.plus.ui.components.AppSnack
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppTouchTargets
import com.tailg.plus.ui.theme.CyberHomeColors
import kotlinx.coroutines.launch

/**
 * Port of `lib/pages/induction_settings_page.dart` — unified induction /
 * proximity unlock settings (QGJ / TLink / RSSI).
 *
 * The Dart `StatefulWidget` subscribes to `inductionModeService.snapshotStream`
 * and `manualModeService.enabledStream`, mirrors the latest snapshot into UI
 * state, and drives `setEnabled` / `setDistance` / `refresh` from button +
 * slider callbacks. Here the same flow is reproduced with `StateFlow`
 * collection (`collectAsState`) and a coroutine scope.
 *
 * Service access: [InductionModeService] needs a [ConnectionManager] (Android
 * BLE wrapper) and an [com.tailg.plus.service.InductionPrefs]; both are
 * constructable from the current [android.content.Context]. [OfficialCloudService]
 * is obtained via [rememberOfficialCloudService] and supplies the
 * selected vehicle for `bindVehicle`. [AppPermissionService] is used only on the
 * RSSI path to request notification permission.
 *
 * Token mapping (Dart → Compose): `CyberHomeColors.*` 1:1; `AppRadii.tile` for
 * card radius; `Lucide.sensors/pointer/bluetooth/alertCircle/refresh` for
 * icons. The Dart `SegmentedButton` is rendered as two side-by-side
 * [AppPressable] segments (Compose M3 `SegmentedButton` is experimental and
 * its API differs; a manual pair keeps the visual contract without opting into
 * experimental APIs).
 */
@Composable
fun InductionSettingsScreen(
  vehicleId: String,
  onBack: () -> Unit,
  cloudService: OfficialCloudService? = null,
) {
  val context = LocalContext.current
  val cloudService = cloudService ?: rememberOfficialCloudService()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  // Construct collaborators once per composition (rule #12: remember { ... }).
  val connectionManager = remember { ConnectionManager(context) }
  val prefs = remember { DataStoreInductionPrefs(context) }
  val manualModeService = remember { ManualModeService(prefs) }
  val inductionService = remember {
    InductionModeService(
      cm = connectionManager,
      context = context,
      manual = manualModeService,
      cloud = cloudService,
      prefs = prefs,
    )
  }
  val permissionService = remember { AppPermissionService(context) }

  val snapshot by inductionService.snapshotFlow.collectAsState()
  val manualEnabled by manualModeService.enabledFlow.collectAsState()

  var busy by remember { mutableStateOf(false) }
  var distanceDraft by remember {
    mutableFloatStateOf(InductionModeService.DEFAULT_DISTANCE_LEVEL.toFloat())
  }

  // Dart `initState`: bind vehicle, seed snapshot, init manual mode, refresh.
  LaunchedEffect(Unit) {
    val vehicle = cloudService.currentState.selectedVehicle
    inductionService.bindVehicle(
      modelType = vehicle?.modelType,
      carId = vehicle?.carId,
      vehicleRaw = vehicle?.raw,
    )
    manualModeService.init()
    inductionService.refresh(force = true)
  }

  // Keep the distance draft in sync when the snapshot reports a new distance.
  LaunchedEffect(snapshot.distance) {
    val d = snapshot.distance
    if (d != null) {
      distanceDraft = d.toFloat()
    }
  }

  val supportsInduction = snapshot.stack != InductionStack.NONE
  val showDistanceSlider =
    snapshot.stack == InductionStack.QGJ || snapshot.stack == InductionStack.TLINK
  val maxDistanceLevel = if (snapshot.stack == InductionStack.QGJ) 10
    else InductionModeService.MAX_DISTANCE_LEVEL
  val canWrite = snapshot.bleReady && supportsInduction
  val anyBusy = busy || snapshot.busy

  // true = induction, false = manual, null = unknown / reading.
  val unlockSelection: Boolean? = when {
    manualEnabled -> false
    !supportsInduction -> false
    else -> snapshot.unlockSelection
  }

  val helpText = helpTextFor(snapshot.stack)
  val statusLine = unlockStatusLine(
    snapshot = snapshot,
    supportsInduction = supportsInduction,
    manualEnabled = manualEnabled,
    unlockSelection = unlockSelection,
  )

  Scaffold(
    containerColor = CyberHomeColors.pageBg,
    snackbarHost = { AppSnackbarHost(snackbarHostState) },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
    ) {
      InductionHeader(
        busy = anyBusy,
        onBack = onBack,
        onRefresh = {
          if (busy) return@InductionHeader
          scope.launch {
            busy = true
            inductionService.refresh(force = true)
            busy = false
            val err = inductionService.snapshot.lastError
            if (err != null) {
              AppSnack.error(snackbarHostState, err)
            } else {
              AppSnack.success(snackbarHostState, "状态已刷新")
            }
          }
        },
      )
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
          start = 20.dp,
          end = 20.dp,
          top = 12.dp,
          bottom = 32.dp,
        ),
      ) {
        item {
          SectionLabel("当前能力")
        }
        item {
          CapabilityCard(
            stack = snapshot.stack,
            helpText = helpText,
            statusLine = statusLine,
            bondIncomplete = snapshot.bondIncomplete,
          )
        }
        if (!snapshot.bleReady && supportsInduction) {
          item {
            Spacer(Modifier.height(10.dp))
            ConnectionNotice(
              protocolLoggedIn = connectionManager.isProtocolLoggedIn,
            )
          }
        }
        item {
          Spacer(Modifier.height(22.dp))
          SectionLabel("解锁模式")
        }
        item {
          UnlockModeCard(
            selection = unlockSelection,
            supportsInduction = supportsInduction,
            anyBusy = anyBusy,
            showDistanceSlider = showDistanceSlider,
            canWrite = canWrite,
            distanceDraft = distanceDraft,
            maxDistanceLevel = maxDistanceLevel,
            onSelect = { induction ->
              scope.launch {
                selectUnlockMode(
                  induction = induction,
                  busy = busy,
                  snapshot = snapshot,
                  supportsInduction = supportsInduction,
                  manualEnabled = manualEnabled,
                  inductionService = inductionService,
                  manualModeService = manualModeService,
                  permissionService = permissionService,
                  snackbarHostState = snackbarHostState,
                  setBusy = { busy = it },
                )
              }
            },
            onDistanceChange = { value -> distanceDraft = value },
            onDistanceCommit = { level ->
              scope.launch {
                if (busy) return@launch
                busy = true
                val ok = inductionService.setDistance(level)
                busy = false
                if (!ok) {
                  AppSnack.error(snackbarHostState, inductionService.snapshot.lastError ?: "距离设置失败")
                } else {
                  AppSnack.success(snackbarHostState, "感应距离已更新")
                }
              }
            },
          )
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

/** Dart `_InductionHeader` — back circle + title + refresh action. */
@Composable
private fun InductionHeader(
  busy: Boolean,
  onBack: () -> Unit,
  onRefresh: () -> Unit,
) {
  Row(
    modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AppPressable(
      onClick = onBack,
      shape = CircleShape,
      background = CyberHomeColors.card,
      shadowElevation = 4.dp,
      shadowColor = CyberHomeColors.actionShadow,
      semanticsLabel = "返回",
    ) {
      Box(
        modifier = Modifier.size(AppTouchTargets.min),
        contentAlignment = Alignment.Center,
      ) {
        LucideIcon(icon = Lucide.arrowLeft, size = 20.dp, color = CyberHomeColors.ink)
      }
    }
    Spacer(Modifier.width(12.dp))
    Text(
      text = "感应解锁",
      modifier = Modifier.weight(1f),
      maxLines = 1,
      overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
      style = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.W700,
        color = CyberHomeColors.ink,
      ),
    )
    AppPressable(
      onClick = if (busy) null else onRefresh,
      enabled = !busy,
      shape = CircleShape,
      semanticsLabel = "刷新状态",
    ) {
      Box(
        modifier = Modifier.size(AppTouchTargets.min),
        contentAlignment = Alignment.Center,
      ) {
        if (busy) {
          CircularProgressIndicator(
            color = CyberHomeColors.primary,
            strokeWidth = 1.8.dp,
            modifier = Modifier.size(18.dp),
          )
        } else {
          LucideIcon(icon = Lucide.refresh, size = 20.dp, color = CyberHomeColors.inkSecondary)
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Section label
// ---------------------------------------------------------------------------

/** Dart `_SectionLabel`. */
@Composable
private fun SectionLabel(label: String) {
  Text(
    text = label,
    modifier = Modifier.padding(start = 2.dp, bottom = 9.dp),
    style = TextStyle(
      fontSize = 12.sp,
      fontWeight = FontWeight.W700,
      color = CyberHomeColors.inkMuted,
    ),
  )
}

// ---------------------------------------------------------------------------
// Capability card
// ---------------------------------------------------------------------------

/** Dart `_CapabilityCard`. */
@Composable
private fun CapabilityCard(
  stack: InductionStack,
  helpText: String,
  statusLine: String,
  bondIncomplete: Boolean,
) {
  val title = when (stack) {
    InductionStack.QGJ, InductionStack.TLINK -> "车辆感应"
    InductionStack.RSSI -> "蓝牙信号感应"
    InductionStack.NONE -> "手动控车"
  }
  val icon = when (stack) {
    InductionStack.QGJ, InductionStack.TLINK -> Lucide.sensors
    InductionStack.RSSI -> Lucide.bluetooth
    InductionStack.NONE -> Lucide.pointer
  }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(16.dp),
  ) {
    Row(verticalAlignment = Alignment.Top) {
      Box(
        modifier = Modifier
          .size(42.dp)
          .clip(CircleShape)
          .background(CyberHomeColors.primarySoft),
        contentAlignment = Alignment.Center,
      ) {
        LucideIcon(icon = icon, size = 21.dp, color = CyberHomeColors.primary)
      }
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = TextStyle(
            fontSize = 16.sp,
            fontWeight = FontWeight.W700,
            color = CyberHomeColors.ink,
          ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
          text = statusLine,
          style = TextStyle(
            fontSize = 12.sp,
            lineHeight = 12.sp * 1.4f,
            color = if (bondIncomplete) CyberHomeColors.warning else CyberHomeColors.inkMuted,
          ),
        )
      }
    }
    Spacer(Modifier.height(14.dp))
    HorizontalDivider(thickness = 1.dp, color = CyberHomeColors.line)
    Spacer(Modifier.height(12.dp))
    Text(
      text = helpText,
      style = TextStyle(
        fontSize = 12.sp,
        lineHeight = 12.sp * 1.55f,
        color = CyberHomeColors.inkMuted,
      ),
    )
  }
}

// ---------------------------------------------------------------------------
// Connection notice
// ---------------------------------------------------------------------------

/** Dart `_ConnectionNotice`. */
@Composable
private fun ConnectionNotice(protocolLoggedIn: Boolean) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.warning.copy(alpha = 0.08f))
      .border(1.dp, CyberHomeColors.warning.copy(alpha = 0.2f), RoundedCornerShape(AppRadii.tile))
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.Top,
  ) {
    LucideIcon(icon = Lucide.alertCircle, size = 18.dp, color = CyberHomeColors.warning)
    Spacer(Modifier.width(9.dp))
    Text(
      text = if (protocolLoggedIn) "蓝牙已连接，正在同步状态…"
        else "当前未完成蓝牙协议登录，请返回爱车页连接车辆。",
      modifier = Modifier.weight(1f),
      style = TextStyle(
        fontSize = 12.sp,
        lineHeight = 12.sp * 1.45f,
        color = CyberHomeColors.inkMuted,
      ),
    )
  }
}

// ---------------------------------------------------------------------------
// Unlock mode card (segmented button + distance slider)
// ---------------------------------------------------------------------------

/** Dart unlock-mode card with `SegmentedButton` + distance slider. */
@Composable
private fun UnlockModeCard(
  selection: Boolean?,
  supportsInduction: Boolean,
  anyBusy: Boolean,
  showDistanceSlider: Boolean,
  canWrite: Boolean,
  distanceDraft: Float,
  maxDistanceLevel: Int,
  onSelect: (Boolean) -> Unit,
  onDistanceChange: (Float) -> Unit,
  onDistanceCommit: (Int) -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(16.dp),
  ) {
    Row(modifier = Modifier.fillMaxWidth()) {
      SegmentButton(
        label = "感应",
        icon = Lucide.sensors,
        selected = selection == true,
        enabled = supportsInduction && !anyBusy,
        modifier = Modifier.weight(1f),
        onClick = { onSelect(true) },
      )
      Spacer(Modifier.width(8.dp))
      SegmentButton(
        label = "手动",
        icon = Lucide.pointer,
        selected = selection == false || (selection == null && !supportsInduction),
        enabled = !anyBusy,
        modifier = Modifier.weight(1f),
        onClick = { onSelect(false) },
      )
    }
    if (showDistanceSlider && selection == true) {
      Spacer(Modifier.height(18.dp))
      HorizontalDivider(thickness = 1.dp, color = CyberHomeColors.line)
      Spacer(Modifier.height(16.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = "感应距离",
          modifier = Modifier.weight(1f),
          style = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.W700,
            color = CyberHomeColors.ink,
          ),
        )
        DistanceBadge(level = distanceDraft.toInt())
      }
      Spacer(Modifier.height(4.dp))
      Text(
        text = "档位越高，越远就能触发解锁",
        style = TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
      )
      Spacer(Modifier.height(6.dp))
      val clamped = distanceDraft.coerceIn(0f, maxDistanceLevel.toFloat())
      Slider(
        value = clamped,
        onValueChange = onDistanceChange,
        enabled = !anyBusy && canWrite,
        valueRange = 0f..maxDistanceLevel.toFloat(),
        steps = if (maxDistanceLevel > 0) maxDistanceLevel - 1 else 0,
        colors = SliderDefaults.colors(
          thumbColor = CyberHomeColors.primary,
          activeTrackColor = CyberHomeColors.primary,
          inactiveTrackColor = CyberHomeColors.controlStrong,
        ),
        onValueChangeFinished = {
          if (!anyBusy && canWrite) {
            onDistanceCommit(clamped.toInt())
          }
        },
      )
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "近",
          style = TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkFaint),
        )
        Text(
          text = "远",
          style = TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkFaint),
        )
      }
    }
  }
}

/** One segment of the Dart `SegmentedButton` pair. */
@Composable
private fun SegmentButton(
  label: String,
  icon: ImageVector,
  selected: Boolean,
  enabled: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val bg = if (selected) CyberHomeColors.primary else CyberHomeColors.control
  val fg = if (selected) CyberHomeColors.white else CyberHomeColors.inkMuted
  val border = if (selected) CyberHomeColors.primary else CyberHomeColors.line
  AppPressable(
    onClick = if (enabled) onClick else null,
    enabled = enabled,
    modifier = modifier,
    shape = RoundedCornerShape(AppRadii.xs),
    background = bg,
    borderWidth = 1.dp,
    borderColor = border,
    semanticsLabel = label,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(44.dp)
        .padding(horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      LucideIcon(icon = icon, size = 16.dp, color = fg)
      Spacer(Modifier.width(6.dp))
      Text(
        text = label,
        style = TextStyle(
          fontSize = 13.sp,
          fontWeight = FontWeight.W600,
          color = fg,
        ),
      )
    }
  }
}

/** Dart `_DistanceBadge`. */
@Composable
private fun DistanceBadge(level: Int) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(AppRadii.pill))
      .background(CyberHomeColors.primarySoft)
      .padding(horizontal = 9.dp, vertical = 5.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = "档位 $level",
      style = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.W700,
        color = CyberHomeColors.primary,
      ),
    )
  }
}

// ---------------------------------------------------------------------------
// Business logic helpers (port of the Dart `_selectUnlockMode` / `_setDistance`
// / `_read` methods, lifted out of the composable for testability)
// ---------------------------------------------------------------------------

/**
 * Port of Dart `_selectUnlockMode`.
 *
 * The activity is required only on the RSSI path to request notification
 * permission; it is resolved from the current context at call time.
 */
private suspend fun selectUnlockMode(
  induction: Boolean,
  busy: Boolean,
  snapshot: InductionModeSnapshot,
  supportsInduction: Boolean,
  manualEnabled: Boolean,
  inductionService: InductionModeService,
  manualModeService: ManualModeService,
  permissionService: AppPermissionService,
  snackbarHostState: SnackbarHostState,
  setBusy: (Boolean) -> Unit,
) {
  if (busy || snapshot.busy) return

  if (!induction) {
    // Switch to manual mode.
    if (manualEnabled && snapshot.enabled != true) return
    val vehicleManaged =
      snapshot.stack == InductionStack.QGJ || snapshot.stack == InductionStack.TLINK
    if (vehicleManaged && snapshot.enabled == null) {
      AppSnack.info(snackbarHostState, "请先连接车辆蓝牙并读取感应状态")
      return
    }
    if (vehicleManaged && snapshot.enabled == true && !snapshot.bleReady) {
      AppSnack.info(snackbarHostState, "请先连接车辆蓝牙，确认关闭感应后再切换手动模式")
      return
    }
    setBusy(true)
    if (snapshot.enabled == true) {
      val closed = inductionService.setEnabled(false)
      if (!closed) {
        setBusy(false)
        AppSnack.error(snackbarHostState, inductionService.snapshot.lastError ?: "关闭感应失败")
        return
      }
    }
    manualModeService.setEnabled(true)
    setBusy(false)
    val err = inductionService.snapshot.lastError
    if (err != null) {
      AppSnack.info(snackbarHostState, err)
    } else {
      AppSnack.success(snackbarHostState, "已切换为手动模式")
    }
    return
  }

  // Switch to induction mode.
  if (!supportsInduction) {
    AppSnack.info(
      snackbarHostState,
      if (snapshot.bleReady) "当前车型不支持感应解锁" else "连接蓝牙后识别车型",
    )
    return
  }
  if (!snapshot.bleReady) {
    AppSnack.info(snackbarHostState, "请先连接车辆蓝牙后再开启感应")
    return
  }
  if (snapshot.enabled == true && !manualEnabled) {
    if (snapshot.bondIncomplete) {
      AppSnack.info(snackbarHostState, "请在系统弹窗中允许蓝牙配对，否则靠近解锁可能无效")
    }
    return
  }

  if (snapshot.stack == InductionStack.RSSI) {
    // TODO: request notification permission via the host ComponentActivity.
    // The Dart path calls permissionService.requestNotificationPermission();
    // AppPermissionService.requestNotificationPermission needs a ComponentActivity
    // which is not available inside this suspend helper. Wire it from the
    // composable scope once the activity is injected (LocalContext as
    // ComponentActivity). For now, proceed without the gate; the foreground
    // service will still start and the system will prompt for POST_NOTIFICATIONS
    // when the service posts its notification.
  }

  setBusy(true)
  val ok = inductionService.setEnabled(true, clearManualMode = true)
  setBusy(false)
  if (!ok) {
    AppSnack.error(snackbarHostState, inductionService.snapshot.lastError ?: "开启感应失败")
    return
  }
  val err = inductionService.snapshot.lastError
  when {
    err != null -> AppSnack.info(snackbarHostState, err)
    inductionService.snapshot.bondIncomplete ->
      AppSnack.info(snackbarHostState, inductionService.snapshot.lastError ?: "感应已开启，请允许系统蓝牙配对")
    else -> AppSnack.success(snackbarHostState, "感应解锁已开启")
  }
}

/** Dart `_helpText` getter. */
private fun helpTextFor(stack: InductionStack): String = when (stack) {
  InductionStack.QGJ, InductionStack.TLINK ->
    "开启后，手机靠近车辆会自动解锁，离开后自动上锁。" +
      "首次开启可能弹出系统蓝牙配对请求，请点允许。" +
      "距离档越大，越容易触发感应。"
  InductionStack.RSSI ->
    "开启后，App 会根据蓝牙信号强弱自动解防或上锁。" +
      "请保持手机蓝牙已连接车辆；Android 后台运行时会显示常驻通知。" +
      "手动模式开启时不会自动控车。"
  InductionStack.NONE ->
    "当前车辆暂不支持本地感应解锁，请使用手动控车。"
}

/** Dart `_unlockStatusLine` getter. */
private fun unlockStatusLine(
  snapshot: InductionModeSnapshot,
  supportsInduction: Boolean,
  manualEnabled: Boolean,
  unlockSelection: Boolean?,
): String {
  if (!supportsInduction) {
    return if (snapshot.bleReady) "当前车型仅支持手动控车" else "连接蓝牙后识别车型"
  }
  if (unlockSelection == null) {
    return if (snapshot.bleReady) "正在读取解锁模式…" else "连接蓝牙后可开启感应"
  }
  if (unlockSelection == false) {
    return "手动控车 · 已关闭自动连接与感应"
  }
  if (!snapshot.bleReady) return "开启感应前请先连接车辆蓝牙"
  if (snapshot.bondIncomplete) {
    return "感应已开 · 请允许系统蓝牙配对"
  }
  val dist = snapshot.distance?.let { " · 距离档 $it" } ?: ""
  return "靠近自动解防，离开自动上锁$dist"
}
