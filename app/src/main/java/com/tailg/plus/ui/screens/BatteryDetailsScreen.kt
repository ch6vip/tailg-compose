package com.tailg.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.data.cloud.OfficialCloudMessages
import com.tailg.plus.data.cloud.OfficialCloudRedactor
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.cloud.OfficialCloudState
import com.tailg.plus.data.model.BatteryDataSource
import com.tailg.plus.data.model.BatterySnapshot
import com.tailg.plus.data.model.BmsField
import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService
import com.tailg.plus.service.CoulombMeterService
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.components.AppSnackbarHost
import com.tailg.plus.ui.components.AppSnack
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.navigation.Routes
import com.tailg.plus.ui.components.cyberButtonShape
import com.tailg.plus.ui.components.cyberFilledButtonColors
import com.tailg.plus.ui.components.cyberOutlinedButtonBorder
import com.tailg.plus.ui.components.cyberOutlinedButtonColors
import com.tailg.plus.ui.theme.AppIconSizes
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppTouchTargets
import com.tailg.plus.ui.theme.CyberHomeColors
import com.tailg.plus.util.BatteryHelpCopy
import com.tailg.plus.util.formatRelativeSyncText
import kotlinx.coroutines.launch

private val batteryCardDecoration: Modifier
  get() = Modifier
    .clip(RoundedCornerShape(AppRadii.tile))
    .background(CyberHomeColors.card)
    .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))

/**
 * Port of `lib/pages/battery_details_page.dart` — battery details screen.
 *
 * Shows battery hero, source strip, sync card, vehicle meta, coulomb meter,
 * official summary, metric grid, fault card, BMS details, route hint, actions,
 * and read-only notice.
 */
@Composable
fun BatteryDetailsScreen(
  cloudService: OfficialCloudService,
  connectionManager: ConnectionManager,
  onBack: () -> Unit,
  onNavigate: (String) -> Unit,
  modifier: Modifier = Modifier,
  batteryChanged: Boolean? = null,
  onConsumeBatteryChanged: () -> Unit = {},
) {
  val scope = rememberCoroutineScope()
  val log = remember { LogService() }
  val cloudState by cloudService.stateFlow.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  val bleState by connectionManager.stateFlow.collectAsState()

  val vehicle = if (cloudState.signedIn) cloudState.selectedVehicle else null
  val data = remember(cloudState) {
    BatterySnapshot.fromSources(
      officialVehicle = vehicle,
      officialBatteryInfo = cloudState.batteryInfo,
      officialBmsInfo = cloudState.bmsInfo,
    )
  }
  val loading = cloudState.batteryInfoLoading || cloudState.bmsInfoLoading

  val coulombMeterService = remember(connectionManager) { CoulombMeterService(connectionManager) }
  val coulombSupported = remember(vehicle) {
    vehicle != null && CoulombMeterService.isSupported(
      modelType = vehicle.modelType,
      bmsTlvType = vehicle.bmsTlvType,
    )
  }
  val isLithium = vehicle?.bmsTlvType?.trim() == "208"
  val bleReady = connectionManager.isProtocolLoggedIn

  var coulombBusy by remember { mutableStateOf(false) }
  var coulombEnabled by remember { mutableStateOf<Boolean?>(null) }
  var coulombMessage by remember { mutableStateOf<String?>(null) }

  fun refreshAllBatteryData() {
    if (!cloudService.currentState.signedIn) {
      scope.launch { AppSnack.info(snackbarHostState, OfficialCloudMessages.SIGN_IN_REQUIRED) }
      return
    }
    scope.launch {
      try {
        cloudService.refreshBatteryInfo(force = true)
        cloudService.refreshBmsInfo(force = true, silent = true)
        val info = cloudService.currentState.batteryInfo
        val bms = cloudService.currentState.bmsInfo
        if (info?.hasData == true || bms?.hasData == true) {
          AppSnack.success(snackbarHostState, "电池信息已同步")
        } else {
          AppSnack.info(snackbarHostState, "已同步，当前暂无电池明细")
        }
      } catch (e: Exception) {
        log.operation("官方电池信息刷新失败", detail = e.toString(), level = LogLevel.WARNING)
        AppSnack.error(snackbarHostState, OfficialCloudRedactor.errorMessage(e))
      }
    }
  }

  fun queryCoulombMeter(silent: Boolean = false) {
    if (!coulombSupported || coulombBusy) return
    if (!bleReady) {
      if (!silent) scope.launch { AppSnack.info(snackbarHostState, "请先连接车辆蓝牙后再操作库仑计") }
      coulombMessage = "需 BLE 已协议登录"
      coulombEnabled = null
      return
    }
    coulombBusy = true
    coulombMessage = null
    scope.launch {
      try {
        val on = coulombMeterService.queryStatus()
        coulombEnabled = on
        coulombMessage = if (on == null) "请点「刷新状态」：车辆上电后获取开关" else null
      } catch (e: Exception) {
        coulombMessage = if (e is IllegalStateException) e.message else "查询失败"
        if (!silent) AppSnack.error(snackbarHostState, coulombMessage ?: "查询失败")
      } finally {
        coulombBusy = false
      }
    }
  }

  fun toggleCoulombMeter(value: Boolean) {
    if (!coulombSupported || coulombBusy) return
    if (!bleReady) {
      scope.launch { AppSnack.info(snackbarHostState, "请先连接车辆蓝牙后再操作库仑计") }
      return
    }
    coulombBusy = true
    coulombMessage = null
    scope.launch {
      try {
        val on = coulombMeterService.setEnabled(value)
        coulombEnabled = on
        coulombMessage = null
        AppSnack.success(snackbarHostState, if (value) "库仑计已开启" else "库仑计已关闭")
      } catch (e: Exception) {
        coulombMessage = if (e is IllegalStateException) e.message else "设置失败"
        AppSnack.error(snackbarHostState, coulombMessage ?: "设置失败")
      } finally {
        coulombBusy = false
      }
    }
  }

  // Auto-query coulomb meter when BLE is ready.
  LaunchedEffect(bleReady) {
    if (bleReady && coulombSupported && coulombEnabled == null && !coulombBusy) {
      queryCoulombMeter(silent = true)
    }
  }

  // Dart `.then((changed) { if (changed) refreshAllBatteryData() })`.
  LaunchedEffect(batteryChanged) {
    if (batteryChanged == true) {
      onConsumeBatteryChanged()
      refreshAllBatteryData()
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = CyberHomeColors.pageBg,
    snackbarHost = { AppSnackbarHost(snackbarHostState) },
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 32.dp),
    ) {
      item {
        BatteryHeader(
          loading = loading,
          canRefresh = cloudState.signedIn,
          canCorrect = cloudState.signedIn,
          onBack = onBack,
          onRefresh = { refreshAllBatteryData() },
          onCorrect = {
            if (!cloudService.currentState.signedIn) {
              scope.launch { AppSnack.info(snackbarHostState, OfficialCloudMessages.SIGN_IN_REQUIRED) }
              return@BatteryHeader
            }
            if (cloudService.currentState.selectedVehicle == null) {
              scope.launch { AppSnack.info(snackbarHostState, "请先选择车辆") }
              return@BatteryHeader
            }
            onNavigate(Routes.replaceBattery("current"))
          },
        )
      }
      item { Spacer(Modifier.height(12.dp)) }
      item { BatteryHero(snapshot = data) }
      item { Spacer(Modifier.height(14.dp)) }
      item { SourceStrip(snapshot = data, cloudState = cloudState) }
      item { Spacer(Modifier.height(14.dp)) }
      if (cloudState.signedIn) {
        item { BatterySyncCard(cloudService = cloudService) }
        item { Spacer(Modifier.height(14.dp)) }
      }
      if (vehicle != null) {
        item { VehicleBatteryMetaCard(vehicle = vehicle) }
        item { Spacer(Modifier.height(14.dp)) }
      }
      if (coulombSupported && !isLithium) {
        item {
          CoulombMeterCard(
            busy = coulombBusy,
            enabled = coulombEnabled,
            message = coulombMessage,
            bleReady = bleReady,
            onToggle = { toggleCoulombMeter(it) },
            onRefresh = { queryCoulombMeter(silent = false) },
          )
        }
        item { Spacer(Modifier.height(14.dp)) }
      }
      item { OfficialSummaryRow(snapshot = data) }
      item { Spacer(Modifier.height(14.dp)) }
      item {
        OfficialMetricGrid(
          snapshot = data,
          onCycleHelp = {
            scope.launch { AppSnack.info(snackbarHostState, BatteryHelpCopy.CYCLE_TITLE) }
          },
          onScoreHelp = {
            scope.launch { AppSnack.info(snackbarHostState, BatteryHelpCopy.SCORE_TITLE) }
          },
        )
      }
      item { Spacer(Modifier.height(14.dp)) }
      item { FaultCard(snapshot = data) }
      item { Spacer(Modifier.height(14.dp)) }
      item {
        BmsDetailsCard(
          snapshot = data,
          loading = cloudState.bmsInfoLoading,
          error = cloudState.bmsInfoError,
        )
      }
      item { Spacer(Modifier.height(14.dp)) }
      item { BatteryRouteHintCard(vehicle = vehicle) }
      item { Spacer(Modifier.height(14.dp)) }
      item {
        BatteryActionsCard(
          signedIn = cloudState.signedIn,
          shareCar = vehicle?.shareCarFlag == true,
          onSwapService = {
            scope.launch { AppSnack.info(snackbarHostState, BatteryHelpCopy.SWAP_SERVICE_TITLE) }
          },
          onCorrectBattery = {
            if (!cloudService.currentState.signedIn) {
              scope.launch { AppSnack.info(snackbarHostState, OfficialCloudMessages.SIGN_IN_REQUIRED) }
              return@BatteryActionsCard
            }
            onNavigate(Routes.replaceBattery("current"))
          },
        )
      }
      item { Spacer(Modifier.height(14.dp)) }
      item { BatteryReadOnlyCard() }
    }
  }
}

@Composable
private fun BatteryHeader(
  loading: Boolean,
  canRefresh: Boolean,
  canCorrect: Boolean,
  onBack: () -> Unit,
  onRefresh: () -> Unit,
  onCorrect: () -> Unit,
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
      Box(modifier = Modifier.size(AppTouchTargets.min), contentAlignment = Alignment.Center) {
        LucideIcon(icon = Lucide.arrowLeft, size = 20.dp, color = CyberHomeColors.inkSecondary)
      }
    }
    Spacer(Modifier.width(12.dp))
    Text(
      text = "电池信息",
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      modifier = Modifier.weight(1f),
    )
    AppPressable(
      onClick = { if (canRefresh && !loading) onRefresh() },
      enabled = canRefresh && !loading,
      shape = CircleShape,
      semanticsLabel = "刷新",
    ) {
      Box(modifier = Modifier.size(AppTouchTargets.min), contentAlignment = Alignment.Center) {
        if (loading) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 1.8.dp, color = CyberHomeColors.primary)
        } else {
          LucideIcon(icon = Lucide.refresh, size = 20.dp, color = if (canRefresh) CyberHomeColors.inkSecondary else CyberHomeColors.inkFaint)
        }
      }
    }
    AppPressable(
      onClick = { if (canCorrect) onCorrect() },
      enabled = canCorrect,
      shape = CircleShape,
      semanticsLabel = "更正电池",
    ) {
      Box(modifier = Modifier.size(AppTouchTargets.min), contentAlignment = Alignment.Center) {
        LucideIcon(icon = Lucide.edit, size = 20.dp, color = if (canCorrect) CyberHomeColors.inkSecondary else CyberHomeColors.inkFaint)
      }
    }
  }
}

@Composable
private fun BatteryHero(snapshot: BatterySnapshot) {
  val percent = snapshot.percent
  val color = batteryColor(percent)
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .height(300.dp)
      .then(batteryCardDecoration)
      .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 20.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
      Spacer(Modifier.width(8.dp))
      Text(
        text = snapshot.officialVehicle?.displayName ?: "当前车辆",
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.inkSecondary),
        modifier = Modifier.weight(1f),
      )
      Text(
        text = snapshot.healthLabel,
        style = androidx.compose.ui.text.TextStyle(
          fontSize = 12.sp,
          fontWeight = FontWeight.W700,
          color = if (snapshot.faults.isEmpty()) CyberHomeColors.success else CyberHomeColors.danger,
        ),
      )
    }
    Spacer(Modifier.weight(1f))
    // Battery glyph placeholder.
    Box(
      modifier = Modifier
        .size(width = 148.dp, height = 74.dp)
        .align(Alignment.CenterHorizontally),
      contentAlignment = Alignment.Center,
    ) {
      LucideIcon(
        icon = if (percent != null && percent > 20) Lucide.batteryFull else Lucide.batteryWarning,
        size = 48.dp,
        color = color,
      )
    }
    Spacer(Modifier.height(18.dp))
    Row(
      modifier = Modifier.align(Alignment.CenterHorizontally),
      verticalAlignment = Alignment.Top,
    ) {
      Text(
        text = percent?.toString() ?: "--",
        style = androidx.compose.ui.text.TextStyle(
          fontSize = 68.sp,
          fontWeight = FontWeight.W300,
          color = if (percent == null) CyberHomeColors.inkFaint else CyberHomeColors.ink,
          lineHeight = 62.sp,
        ),
      )
      Text(
        text = "%",
        style = androidx.compose.ui.text.TextStyle(
          fontSize = 21.sp,
          color = if (percent == null) CyberHomeColors.inkFaint else CyberHomeColors.ink,
          fontWeight = FontWeight.W500,
        ),
        modifier = Modifier.padding(top = if (percent == null) 4.dp else 7.dp),
      )
    }
    Spacer(Modifier.height(5.dp))
    Text(
      text = "当前电量",
      style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkMuted),
      modifier = Modifier.align(Alignment.CenterHorizontally),
    )
  }
}

private fun batteryColor(percent: Int?): Color {
  if (percent == null) return CyberHomeColors.inkFaint
  if (percent > 60) return CyberHomeColors.success
  if (percent > 20) return CyberHomeColors.warning
  return CyberHomeColors.danger
}

@Composable
private fun BatterySyncCard(cloudService: OfficialCloudService) {
  val sync = formatRelativeSyncText(cloudService.lastBatteryRefreshAt)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    LucideIcon(icon = Lucide.refresh, size = AppIconSizes.sm, color = CyberHomeColors.inkFaint)
    Spacer(Modifier.width(8.dp))
    Text(text = "最后同步", style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkMuted))
    Spacer(Modifier.weight(1f))
    Text(
      text = sync,
      style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.inkMuted),
    )
  }
}

@Composable
private fun SourceStrip(snapshot: BatterySnapshot, cloudState: OfficialCloudState) {
  val signedIn = cloudState.signedIn
  val loading = cloudState.batteryInfoLoading
  val error = cloudState.batteryInfoError
  val title = when {
    loading -> "正在刷新电池信息"
    error != null -> "电池信息刷新失败"
    signedIn -> "电池数据已同步"
    else -> "登录官方账号后可同步更多电池数据"
  }
  val subtitle = error ?: when {
    loading -> "正在向官方电池服务请求最新数据"
    signedIn -> "电量、电压、温度来自官方电池接口；维护、校准请前往官方服务渠道"
    else -> "登录后可读取电量、电压、温度与 BMS 明细"
  }
  val color = when {
    error != null -> CyberHomeColors.warning
    loading -> CyberHomeColors.primary
    else -> CyberHomeColors.success
  }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(color.copy(alpha = 0.08f))
      .border(1.dp, color.copy(alpha = 0.18f), RoundedCornerShape(AppRadii.tile))
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (loading) {
      CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = color)
    } else {
      LucideIcon(icon = if (error == null) Lucide.badgeCheck else Lucide.info, color = color, size = AppIconSizes.md)
    }
    Spacer(Modifier.width(10.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W700, color = color),
      )
      Spacer(Modifier.height(2.dp))
      Text(
        text = subtitle,
        style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkMuted),
      )
    }
  }
}

@Composable
private fun OfficialSummaryRow(snapshot: BatterySnapshot) {
  val bms = snapshot.bms
  val voltage = snapshot.voltage
  val items = listOf(
    Metric("预估里程", withUnit(snapshot.remainingMileage, "km")),
    Metric("总里程", withUnit(snapshot.totalMileage, "km")),
    Metric("电压", if (voltage == null) "待读取" else "${"%.1f".format(voltage)}V"),
    Metric("电池容量", bms.batteryCapacity ?: "待读取"),
  )
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .then(batteryCardDecoration)
      .padding(vertical = 14.dp),
  ) {
    items.forEachIndexed { index, metric ->
      if (index > 0) {
        VerticalDivider(modifier = Modifier.height(48.dp), color = CyberHomeColors.line)
      }
      CompactMetric(metric = metric, modifier = Modifier.weight(1f))
    }
  }
}

@Composable
private fun CompactMetric(metric: Metric, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = metric.value,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = androidx.compose.ui.text.TextStyle(
        fontSize = if (metric.value.length > 8) 14.sp else 16.sp,
        fontWeight = FontWeight.W700,
        color = if (metric.value == "待读取") CyberHomeColors.inkFaint else CyberHomeColors.ink,
      ),
    )
    Spacer(Modifier.height(4.dp))
    Text(
      text = metric.label,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkFaint),
    )
  }
}

@Composable
private fun OfficialMetricGrid(
  snapshot: BatterySnapshot,
  onCycleHelp: () -> Unit,
  onScoreHelp: () -> Unit,
) {
  val items = listOf(
    Metric("今日耗电", BatterySnapshot.displayMetric(snapshot.consumePowerPercent, unit = "%"), Lucide.zap),
    Metric("循环次数", BatterySnapshot.displayMetric(snapshot.loopCount), Lucide.rotateCcw, onCycleHelp),
    Metric("当前温度", temperatureDisplay(snapshot), Lucide.thermometer),
    Metric("电池评分", BatterySnapshot.displayMetric(snapshot.batteryScore, unit = "分"), Lucide.gauge, onScoreHelp),
  )
  Column {
    items.chunked(2).forEach { row ->
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        row.forEach { item ->
          MetricTile(metric = item, modifier = Modifier.weight(1f))
        }
        if (row.size == 1) Spacer(Modifier.weight(1f))
      }
      Spacer(Modifier.height(10.dp))
    }
  }
}

@Composable
private fun MetricTile(metric: Metric, modifier: Modifier = Modifier) {
  val hasValue = metric.value != "待读取"
  Row(
    modifier = modifier
      .height(112.dp)
      .then(batteryCardDecoration)
      .padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(CyberHomeColors.primarySoft),
      contentAlignment = Alignment.Center,
    ) {
      LucideIcon(icon = metric.icon, color = CyberHomeColors.primary, size = AppIconSizes.md)
    }
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = metric.label,
          style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
          modifier = Modifier.weight(1f),
        )
        if (metric.onHelp != null) {
          AppPressable(
            onClick = metric.onHelp,
            shape = CircleShape,
            semanticsLabel = "${metric.label}说明",
          ) {
            Box(modifier = Modifier.size(AppTouchTargets.min), contentAlignment = Alignment.Center) {
              LucideIcon(icon = Lucide.help, size = 16.dp, color = CyberHomeColors.inkFaint)
            }
          }
        }
      }
      Spacer(Modifier.height(5.dp))
      if (hasValue) {
        Text(
          text = metric.value,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          style = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
        )
      } else {
        Box(
          modifier = Modifier
            .width(56.dp)
            .height(16.dp)
            .clip(RoundedCornerShape(AppRadii.pill))
            .background(CyberHomeColors.controlStrong),
        )
      }
    }
  }
}

@Composable
private fun FaultCard(snapshot: BatterySnapshot) {
  val faults = snapshot.faults
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .then(batteryCardDecoration)
      .padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    LucideIcon(
      icon = if (faults.isEmpty()) Lucide.checkCircle else Lucide.alertCircle,
      color = if (faults.isEmpty()) CyberHomeColors.success else CyberHomeColors.danger,
    )
    Spacer(Modifier.width(12.dp))
    Text(
      text = if (faults.isEmpty()) "未发现电池相关故障" else faults.joinToString("、"),
      style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = CyberHomeColors.ink),
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun BmsDetailsCard(
  snapshot: BatterySnapshot,
  loading: Boolean,
  error: String?,
) {
  val fields = snapshot.bms.fields
  val hasBms = snapshot.hasOfficialBmsInfo
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .then(batteryCardDecoration),
  ) {
    Row(
      modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LucideIcon(icon = Lucide.list, color = CyberHomeColors.primary, size = AppIconSizes.md)
      Spacer(Modifier.width(8.dp))
      Text(
        text = "BMS 详情",
        style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
        modifier = Modifier.weight(1f),
      )
      if (loading) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
      } else {
        Text(
          text = if (hasBms) "已同步" else (if (error == null) "待同步" else "同步失败"),
          style = androidx.compose.ui.text.TextStyle(
            fontSize = 12.sp,
            color = if (hasBms) CyberHomeColors.success else (if (error == null) CyberHomeColors.inkFaint else CyberHomeColors.warning),
            fontWeight = FontWeight.W600,
          ),
        )
      }
    }
    if (error != null && !hasBms) {
      Text(
        text = error,
        style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.warning),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
      )
    }
    fields.forEachIndexed { index, field ->
      BmsFieldRow(field = field)
      if (index != fields.lastIndex) {
        HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = CyberHomeColors.line)
      }
    }
  }
}

@Composable
private fun BmsFieldRow(field: BmsField) {
  val color = if (field.hasValue) CyberHomeColors.ink else CyberHomeColors.inkFaint
  val source = sourceDisplay(field)
  Row(
    modifier = Modifier.padding(start = 16.dp, top = 11.dp, end = 16.dp, bottom = 11.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = field.label,
        style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.ink),
      )
      Spacer(Modifier.height(4.dp))
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(AppRadii.pill))
          .background(source.color.copy(alpha = 0.1f))
          .padding(horizontal = 8.dp, vertical = 3.dp),
      ) {
        Text(
          text = source.label,
          style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontWeight = FontWeight.W600, color = source.color),
        )
      }
    }
    Spacer(Modifier.width(12.dp))
    Text(
      text = field.displayValue,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.End,
      style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W700, color = color),
    )
  }
}

private data class SourceChip(val label: String, val color: Color)

private fun sourceDisplay(field: BmsField): SourceChip {
  if (!field.hasValue) return SourceChip("待同步", CyberHomeColors.warning)
  return when (field.source) {
    BatteryDataSource.OFFICIAL_VEHICLE -> SourceChip("车辆状态", CyberHomeColors.success)
    BatteryDataSource.OFFICIAL_BATTERY -> SourceChip("电池服务", CyberHomeColors.success)
    BatteryDataSource.OFFICIAL_BMS -> SourceChip("BMS 服务", CyberHomeColors.success)
    BatteryDataSource.BMS_RESERVED -> SourceChip("待同步", CyberHomeColors.warning)
  }
}

@Composable
private fun CoulombMeterCard(
  busy: Boolean,
  enabled: Boolean?,
  message: String?,
  bleReady: Boolean,
  onToggle: (Boolean) -> Unit,
  onRefresh: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .then(batteryCardDecoration)
      .padding(16.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      LucideIcon(icon = Lucide.battery, color = CyberHomeColors.primary, size = AppIconSizes.md)
      Spacer(Modifier.width(8.dp))
      Text(
        text = "库仑计",
        style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
        modifier = Modifier.weight(1f),
      )
      if (busy) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
      } else {
        Switch(
          checked = enabled == true,
          onCheckedChange = onToggle,
          enabled = bleReady && enabled != null,
          colors = androidx.compose.material3.SwitchDefaults.colors(
            checkedThumbColor = CyberHomeColors.white,
            checkedTrackColor = CyberHomeColors.primary,
            uncheckedThumbColor = CyberHomeColors.white,
            uncheckedTrackColor = CyberHomeColors.controlStrong,
          ),
        )
      }
    }
    Spacer(Modifier.height(6.dp))
    Text(
      text = "开启后可自学习电量（锂电不可用）",
      style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 13.sp * 1.45f, color = CyberHomeColors.inkMuted),
    )
    Spacer(Modifier.height(8.dp))
    if (!bleReady) {
      Text(
        text = "需先近场连接并完成协议登录",
        style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.warning),
      )
    } else if (message != null) {
      Text(
        text = message,
        style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.warning),
      )
    }
    Spacer(Modifier.height(8.dp))
    TextButton(
      onClick = onRefresh,
      enabled = !busy && bleReady,
    ) {
      LucideIcon(icon = Lucide.refresh, size = 18.dp)
      Spacer(Modifier.width(6.dp))
      Text(text = "刷新状态", color = CyberHomeColors.primary)
    }
  }
}

@Composable
private fun VehicleBatteryMetaCard(vehicle: OfficialVehicle) {
  val spec = vehicle.batterySpecLabel.trim()
  val bind = vehicle.batteryBindDate.trim()
  val typeId = vehicle.batteryTypeId.trim()
  val tlv = vehicle.bmsTlvType.trim()
  if (spec.isNotEmpty() || bind.isNotEmpty() || typeId.isNotEmpty() || tlv.isNotEmpty()) {
    val bindLabel = if (bind.length >= 10) bind.substring(0, 10) else bind
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .then(batteryCardDecoration)
        .padding(16.dp),
    ) {
      Text(
        text = "电池绑定信息",
        style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      )
      Spacer(Modifier.height(10.dp))
      if (spec.isNotEmpty()) {
        MetaLine(label = "当前使用", value = if (spec.startsWith("当前使用")) spec else "当前使用：$spec")
      }
      if (bindLabel.isNotEmpty()) {
        MetaLine(label = "绑定日期", value = "$bindLabel 绑定")
      }
      if (typeId.isNotEmpty()) {
        MetaLine(label = "电池类型 ID", value = typeId)
      }
      if (tlv.isNotEmpty()) {
        MetaLine(label = "BMS TLV", value = tlv)
      }
    }
  }
}

@Composable
private fun MetaLine(label: String, value: String) {
  Row(modifier = Modifier.padding(bottom = 8.dp)) {
    Text(
      text = label,
      style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
      modifier = Modifier.width(88.dp),
    )
    Text(
      text = value,
      style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.ink),
    )
  }
}

@Composable
private fun BatteryRouteHintCard(vehicle: OfficialVehicle?) {
  val modelType = vehicle?.modelType
  val tlv = vehicle?.bmsTlvType?.trim() ?: ""
  val isGps = vehicle?.isGps == 1 || vehicle?.hasGpsService == true
  val route = officialBatteryRoute(modelType = modelType, isGps = isGps, bmsTlvType = tlv)
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .then(batteryCardDecoration)
      .padding(16.dp),
  ) {
    Text(
      text = "官方页面分流",
      style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
    )
    Spacer(Modifier.height(8.dp))
    Text(
      text = "当前机型 modelType=${modelType ?: "--"} · isGps=${if (isGps) "1" else "0"}" +
        if (tlv.isEmpty()) "" else " · bmsTlvType=$tlv",
      style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
    )
    Spacer(Modifier.height(6.dp))
    Text(
      text = route,
      style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.ink),
    )
    Spacer(Modifier.height(6.dp))
    Text(
      text = "本页合并展示官方通用电池信息 + BMS 明细；C39 / TLV 专页 UI 后续按需补齐。",
      style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 13.sp * 1.45f, color = CyberHomeColors.inkMuted),
    )
  }
}

private fun officialBatteryRoute(modelType: Int?, isGps: Boolean, bmsTlvType: String): String {
  if (modelType == 1 || modelType == 2) return "官方路由：BatteryInfoActivity（KKS/YJ）"
  if (modelType == 10 || modelType == 14) return "官方路由：BatteryInfoC39Activity（C39）"
  if (isGps && (bmsTlvType == "176" || bmsTlvType == "208" || bmsTlvType == "6000")) {
    return if (bmsTlvType == "176") "官方路由：BmsBatteryTlvActivity" else "官方路由：BatteryInfoTlvActivity"
  }
  if (isGps) return "官方路由：BatteryInfoActivity（GPS 通用）"
  return "官方路由：可能进入换电/绑定流程（无 GPS）"
}

@Composable
private fun BatteryActionsCard(
  signedIn: Boolean,
  shareCar: Boolean,
  onSwapService: () -> Unit,
  onCorrectBattery: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .then(batteryCardDecoration)
      .padding(16.dp),
  ) {
    Text(
      text = "电池服务",
      style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
    )
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      OutlinedButton(
        onClick = { if (signedIn) onCorrectBattery() },
        enabled = signedIn,
        shape = cyberButtonShape,
        colors = cyberOutlinedButtonColors(),
        border = cyberOutlinedButtonBorder,
        modifier = Modifier
          .weight(1f)
          .height(48.dp),
      ) {
        Text(text = "更正电池")
      }
      OutlinedButton(
        onClick = { if (signedIn && !shareCar) onSwapService() },
        enabled = signedIn && !shareCar,
        shape = cyberButtonShape,
        colors = cyberOutlinedButtonColors(),
        border = cyberOutlinedButtonBorder,
        modifier = Modifier
          .weight(1f)
          .height(48.dp),
      ) {
        Text(text = if (shareCar) "共享车不可换电" else "换电服务")
      }
    }
  }
}

@Composable
private fun BatteryReadOnlyCard() {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .then(batteryCardDecoration)
      .padding(16.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      LucideIcon(icon = Lucide.lock, size = AppIconSizes.sm, color = CyberHomeColors.inkMuted)
      Spacer(Modifier.width(8.dp))
      Text(
        text = "电池服务说明",
        style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      )
    }
    Spacer(Modifier.height(8.dp))
    Text(
      text = "当前页面用于查看电量、电压、温度、健康状态和 BMS 信息。涉及电池校准、更换和升级的操作，请通过官方服务渠道完成。",
      style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 13.sp * 1.45f, color = CyberHomeColors.inkMuted),
    )
  }
}

private data class Metric(
  val label: String,
  val value: String,
  val icon: androidx.compose.ui.graphics.vector.ImageVector = Lucide.info,
  val onHelp: (() -> Unit)? = null,
)

private fun withUnit(value: String?, unit: String): String =
  BatterySnapshot.displayMetric(value, unit = unit)

private fun temperatureDisplay(snapshot: BatterySnapshot): String {
  val parsed = snapshot.temperature
  if (parsed != null) {
    val rounded = kotlin.math.round(parsed)
    val text = if (parsed == rounded) {
      rounded.toInt().toString()
    } else {
      "%.1f".format(parsed)
    }
    return "$text°C"
  }
  val raw = snapshot.officialBatteryInfo?.temperature?.trim() ?: ""
  if (raw.isEmpty() || raw == "--") return "待读取"
  if (raw.contains("°") || raw.contains("℃") || raw.contains("C")) {
    return raw.replace("℃", "°C")
  }
  return "$raw°C"
}
