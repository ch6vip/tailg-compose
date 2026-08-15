package com.tailg.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.data.ble.BikeState
import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.data.ble.platform.ConnectionState
import com.tailg.plus.data.cloud.OfficialCloudRedactor
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.cloud.OfficialCloudState
import com.tailg.plus.data.cloud.OfficialCloudMessages
import com.tailg.plus.data.cloud.resolveVehicleLocation
import com.tailg.plus.data.model.BatterySnapshot
import com.tailg.plus.data.model.CommandCode
import com.tailg.plus.data.model.ControlCommandActivityLog
import com.tailg.plus.data.model.ControlCommandActivityStatus
import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.data.mqtt.OfficialMqttService
import com.tailg.plus.data.store.VehicleStore
import com.tailg.plus.domain.control.ControlChannelAvailability
import com.tailg.plus.domain.control.ControlChannelResolver
import com.tailg.plus.domain.control.ControlCloudState
import com.tailg.plus.domain.control.ControlCommandExecutor
import com.tailg.plus.domain.control.ControlCommandPolicy
import com.tailg.plus.domain.control.ControlCommandResult
import com.tailg.plus.domain.control.ControlCommandRoute
import com.tailg.plus.domain.control.ControlTopBarChannel
import com.tailg.plus.domain.control.OfficialControlChannel
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService
import com.tailg.plus.ui.components.AppSnackbarHost
import com.tailg.plus.ui.components.AppSnack
import com.tailg.plus.ui.components.CyberControlGrid
import com.tailg.plus.ui.components.CyberMapStatsRow
import com.tailg.plus.ui.components.CyberRecentCommands
import com.tailg.plus.ui.components.CyberVehicleHeader
import com.tailg.plus.ui.components.OfficialBleChipState
import com.tailg.plus.ui.components.VehicleControlGateBanner
import com.tailg.plus.ui.components.VehicleControlHomeGate
import com.tailg.plus.ui.components.VehicleControlHomeGateKind
import com.tailg.plus.ui.components.rememberCyberCollapseFraction
import com.tailg.plus.ui.theme.CyberHomeColors
import com.tailg.plus.util.formatCompactDecimal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CONTROL_CONFIRM_TIMEOUT_MS = 8_000L
private const val CONTROL_CONFIRM_POLL_DELAY_MS = 800L
private const val CONTROL_COMMAND_DEBOUNCE_MS = 1_000L
private const val CONTROL_COMMAND_SEND_DELAY_MS = 500L

/**
 * Port of `lib/pages/cyber_vehicle_control_page_v2.dart` — Cyber control home.
 *
 * State and command channel:
 * - vehicle / battery / location: [OfficialCloudService.currentState]
 * - control: [ControlCommandExecutor] + [ControlCommandPolicy] + state confirmation
 * - near-field BLE: auto link + top-right chip / banner
 * - pull-to-refresh: refreshVehicles + battery / location
 */
@Composable
fun ControlScreen(
  cloudService: OfficialCloudService,
  connectionManager: ConnectionManager,
  mqttService: OfficialMqttService,
  vehicleStore: VehicleStore,
  onBack: () -> Unit,
  onNavigate: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  val log = remember { LogService() }
  val cloudState by cloudService.stateFlow.collectAsState()
  val bleState by connectionManager.stateFlow.collectAsState()
  val bleBikeState by connectionManager.bikeStateFlow.collectAsState()
  val mqttLinkState by mqttService.linkState.collectAsState()

  var busy by remember { mutableStateOf(false) }
  var activeCommand by remember { mutableStateOf<CommandCode?>(null) }
  var controlChannel by remember { mutableStateOf(OfficialControlChannel.AUTOMATIC) }
  var networkReady by remember { mutableStateOf(true) }
  val commandLog = remember { ControlCommandActivityLog() }
  var commandVersion by remember { mutableStateOf(0) }

  val commandExecutor = remember {
    ControlCommandExecutor(
      sendBleCommand = { command -> connectionManager.sendCommand(command.toBleCommandCode()) },
      sendCloudCommand = { command -> mqttService.sendCommandPreferMqtt(command, cloudService) },
    )
  }

  val cloudVehicle = cloudState.selectedVehicle
  val battery = remember(cloudState) {
    BatterySnapshot.fromSources(
      officialVehicle = if (cloudState.signedIn) cloudVehicle else null,
      officialBatteryInfo = cloudState.batteryInfo,
    )
  }
  val location = remember(cloudState) {
    resolveVehicleLocation(
      cloudState = cloudState,
      localVehicle = vehicleStore.defaultVehicle,
      allowCloudMetadataWithoutCoordinate = true,
    )
  }
  val isPowerOn = currentPowerState(bleBikeState, cloudVehicle)
  val isArmed = currentLockState(bleBikeState, cloudVehicle)
  val percent = battery.percent ?: 0
  val signedIn = cloudState.signedIn
  val hasVehicle = cloudVehicle != null

  val controlAvailability = remember(cloudState, bleState, busy, controlChannel, networkReady) {
    ControlChannelResolver.resolve(
      cloudState = cloudState.asControlCloudState(),
      bleReady = connectionManager.isProtocolLoggedIn,
      bleNotReadyReason = connectionManager.protocolLoginUnavailableReason,
      defaultVehicleId = vehicleStore.defaultVehicle?.id,
      channel = controlChannel,
      busy = busy,
      networkReady = networkReady,
    )
  }

  val controlChannelStatus = remember(controlAvailability, bleState, mqttLinkState) {
    ControlTopBarChannel.resolve(
      availability = controlAvailability,
      bleState = bleState,
      bleProtocolLoggedIn = connectionManager.isProtocolLoggedIn,
      mqttLinkState = mqttLinkState,
      mqttPreconnectInFlight = mqttService.preconnectInFlight,
      mqttLastPreconnectError = mqttService.lastPreconnectError,
    )
  }

  val findAvailability = remember(cloudState, controlChannel) {
    ControlCommandRoute.resolve(
      base = ControlChannelResolver.resolve(
        cloudState = cloudState.asControlCloudState(),
        bleReady = connectionManager.isProtocolLoggedIn,
        bleNotReadyReason = connectionManager.protocolLoginUnavailableReason,
        defaultVehicleId = vehicleStore.defaultVehicle?.id,
        channel = controlChannel,
        busy = false,
        networkReady = networkReady,
      ),
      command = CommandCode.FIND,
      vehicle = cloudVehicle,
    )
  }

  val powerAvailability = remember(cloudState, controlChannel, isPowerOn) {
    val cmd = if (isPowerOn == true) CommandCode.POWER_OFF else CommandCode.POWER_ON
    ControlCommandRoute.resolve(
      base = ControlChannelResolver.resolve(
        cloudState = cloudState.asControlCloudState(),
        bleReady = connectionManager.isProtocolLoggedIn,
        bleNotReadyReason = connectionManager.protocolLoginUnavailableReason,
        defaultVehicleId = vehicleStore.defaultVehicle?.id,
        channel = controlChannel,
        busy = false,
        networkReady = networkReady,
      ),
      command = cmd,
      vehicle = cloudVehicle,
    )
  }

  val armAvailability = remember(cloudState, controlChannel, isArmed) {
    val cmd = if (isArmed == true) CommandCode.UNLOCK else CommandCode.LOCK
    ControlCommandRoute.resolve(
      base = ControlChannelResolver.resolve(
        cloudState = cloudState.asControlCloudState(),
        bleReady = connectionManager.isProtocolLoggedIn,
        bleNotReadyReason = connectionManager.protocolLoginUnavailableReason,
        defaultVehicleId = vehicleStore.defaultVehicle?.id,
        channel = controlChannel,
        busy = false,
        networkReady = networkReady,
      ),
      command = cmd,
      vehicle = cloudVehicle,
    )
  }

  val seatAvailability = remember(cloudState, controlChannel) {
    ControlCommandRoute.resolve(
      base = ControlChannelResolver.resolve(
        cloudState = cloudState.asControlCloudState(),
        bleReady = connectionManager.isProtocolLoggedIn,
        bleNotReadyReason = connectionManager.protocolLoginUnavailableReason,
        defaultVehicleId = vehicleStore.defaultVehicle?.id,
        channel = controlChannel,
        busy = false,
        networkReady = networkReady,
      ),
      command = CommandCode.OPEN_SEAT,
      vehicle = cloudVehicle,
    )
  }

  val bleChipState = remember(cloudVehicle, bleState, busy) {
    officialBleChipState(cloudVehicle, connectionManager, bleState, busy)
  }

  val lastRideVisuals = remember(cloudState) { lastRideVisuals(cloudState) }
  val commandActivities = remember(commandVersion) { commandLog.entries }

  // Silent refresh on first composition.
  LaunchedEffect(Unit) {
    if (cloudService.currentState.signedIn) {
      try {
        cloudService.refreshVehicles(silent = true, refreshReplicaDetails = true)
        cloudService.refreshMessages(silent = true)
      } catch (e: Exception) {
        log.operation("Cyber 首页静默刷新失败", detail = e.toString(), level = LogLevel.WARNING)
      }
    }
    mqttService.preconnectForCloud(cloudService)
  }

  fun handleRefresh() {
    if (!cloudService.currentState.signedIn) {
      scope.launch { AppSnack.info(snackbarHostState, OfficialCloudMessages.SIGN_IN_REQUIRED) }
      return
    }
    scope.launch {
      try {
        cloudService.refreshVehicles(force = true, refreshReplicaDetails = true)
        cloudService.refreshBatteryInfo(force = true, silent = true)
        cloudService.refreshVehicleLocation(force = true, silent = true)
        cloudService.refreshTodayRideMileage(force = true, silent = true)
        cloudService.refreshMessages(force = true, silent = true)
      } catch (e: Exception) {
        log.operation("Cyber 首页下拉刷新失败", detail = e.toString(), level = LogLevel.WARNING)
        AppSnack.error(snackbarHostState, OfficialCloudRedactor.errorMessage(e))
      }
    }
  }

  fun sendCommand(cmd: CommandCode) {
    if (busy) {
      scope.launch { AppSnack.error(snackbarHostState, "正在执行控车指令，请稍候") }
      return
    }
    val policy = ControlCommandPolicy.evaluate(command = cmd, isPowerOn = isPowerOn == true)
    if (!policy.allowed) {
      scope.launch { AppSnack.error(snackbarHostState, policy.disabledReason ?: "${cmd.label}不可用") }
      return
    }
    val availability = ControlCommandRoute.resolve(
      base = controlAvailability,
      command = cmd,
      vehicle = cloudVehicle,
    )
    if (!availability.enabled) {
      scope.launch { AppSnack.error(snackbarHostState, availability.disabledReason.ifEmpty { "当前不可控车，请检查蓝牙或网络" }) }
      return
    }
    busy = true
    activeCommand = cmd
    val activityId = commandLog.start(cmd, "${cmd.label}中…", "指令已发送，等待回执")
    commandVersion++
    scope.launch {
      try {
        delay(CONTROL_COMMAND_SEND_DELAY_MS)
        val result = commandExecutor.send(command = cmd, availability = availability)
        if (result.success) {
          if (result.shouldRefreshBikeState) {
            connectionManager.refreshBikeState()
          }
          AppSnack.info(snackbarHostState, result.successMessage ?: "${cmd.label}成功")
          commandLog.finish(activityId, successTitle(cmd), successSubtitle(cmd), ControlCommandActivityStatus.SUCCEEDED)
        } else {
          log.operation("Cyber 控车失败: ${cmd.label}", detail = "渠道=${result.transport} 原因=${result.failureMessage}", level = LogLevel.ERROR)
          AppSnack.error(snackbarHostState, failureMessage(cmd, result.failureMessage))
          commandLog.finish(activityId, "${cmd.label}失败", result.failureMessage?.trim()?.ifEmpty { null } ?: "请稍后重试", ControlCommandActivityStatus.FAILED)
        }
      } catch (e: Exception) {
        log.operation("Cyber 控车异常: ${cmd.label}", detail = e.toString(), level = LogLevel.ERROR)
        AppSnack.error(snackbarHostState, failureMessage(cmd, e.message))
        commandLog.finish(activityId, "${cmd.label}失败", e.message ?: "请稍后重试", ControlCommandActivityStatus.FAILED)
      } finally {
        busy = false
        activeCommand = null
        commandVersion++
      }
    }
  }

  fun sendPowerToggle() {
    val powered = isPowerOn
    if (powered == null) {
      scope.launch { AppSnack.error(snackbarHostState, "车辆状态未知，请刷新后重试") }
      return
    }
    val cmd = if (powered) CommandCode.POWER_OFF else CommandCode.POWER_ON
    sendCommand(cmd)
  }

  fun sendArmToggle() {
    val locked = isArmed
    if (locked == null) {
      scope.launch { AppSnack.error(snackbarHostState, "车辆状态未知，请刷新后重试") }
      return
    }
    val cmd = if (locked) CommandCode.UNLOCK else CommandCode.LOCK
    sendCommand(cmd)
  }

  val gateKind = VehicleControlHomeGate.resolve(
    signedIn = signedIn,
    hasVehicle = hasVehicle,
    loading = cloudState.loading,
    error = cloudState.error,
    showNearFieldHint = false,
  )

  val listState = rememberLazyListState()
  val collapseFraction = rememberCyberCollapseFraction(listState, maxExtent = 376)

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = CyberHomeColors.pageBg,
    snackbarHost = { AppSnackbarHost(snackbarHostState) },
  ) { padding ->
    LazyColumn(
      state = listState,
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
    ) {
      // Gate banners.
      item {
        Column {
          when (gateKind) {
            VehicleControlHomeGateKind.SignedOut -> VehicleControlGateBanner(
              title = "请先登录官方账号",
              actionLabel = "去登录",
              onAction = { onNavigate("login") },
            )
            VehicleControlHomeGateKind.Loading -> {
              VehicleControlGateBanner(
                title = "正在同步官方车辆…",
                actionLabel = "刷新中",
                busy = true,
                onAction = {},
              )
              Spacer(Modifier.height(18.dp))
              CyberHomeSkeleton()
            }
            VehicleControlHomeGateKind.Error -> VehicleControlGateBanner(
              title = cloudState.error?.trim()?.ifEmpty { null } ?: "车辆同步失败，请重试",
              actionLabel = "重试",
              onAction = { handleRefresh() },
            )
            VehicleControlHomeGateKind.NoVehicle -> VehicleControlGateBanner(
              title = "暂无车辆，请先同步官方车辆",
              actionLabel = "添加车辆",
              onAction = { onNavigate("add_vehicle") },
            )
            VehicleControlHomeGateKind.NearField, VehicleControlHomeGateKind.None -> {}
          }
        }
      }
      // Collapsing vehicle header.
      item {
        CyberVehicleHeader(
          collapseFraction = collapseFraction,
          vehicleName = cloudVehicle?.displayName ?: vehicleStore.defaultVehicle?.displayName ?: "我的车辆",
          rangeText = rangeLabel(battery).replace(" ", ""),
          carPhoto = cloudVehicle?.carPhoto ?: "",
          batteryPercent = percent,
          batteryKnown = battery.percent != null,
          online = cloudVehicle?.online ?: false,
          bluetoothConnected = connectionManager.isProtocolLoggedIn,
          isLocked = isArmed ?: true,
          powered = isPowerOn,
          bleChip = bleChipState,
          channelStatus = controlChannelStatus,
          onTitleTap = { onNavigate("official_cloud") },
          onBatteryTap = { onNavigate("battery_details/current") },
          onBleChipTap = {
            scope.launch {
              if (connectionManager.isProtocolLoggedIn) {
                AppSnack.info(snackbarHostState, "蓝牙已连接")
              } else {
                AppSnack.info(snackbarHostState, "正在连接车辆蓝牙…")
              }
            }
          },
          onMessages = { onNavigate("vehicle_message/current") },
          onChannelTap = {
            // TODO: show channel selection bottom sheet (CyberChannelStrip)
          },
        )
      }
      // Control grid + map stats + recent commands.
      item {
        Column {
          Spacer(Modifier.height(18.dp))
          CyberControlGrid(
            powered = isPowerOn,
            armed = isArmed,
            busy = busy,
            activeCommand = activeCommand?.toBleCommandCode(),
            findAvailability = findAvailability,
            powerAvailability = powerAvailability,
            armAvailability = armAvailability,
            seatAvailability = seatAvailability,
            onFind = { sendCommand(CommandCode.FIND) },
            onPowerToggle = { sendPowerToggle() },
            onArmToggle = { sendArmToggle() },
            onSettings = { onNavigate("vehicle_settings/current") },
            onSeat = { sendCommand(CommandCode.OPEN_SEAT) },
            onNfc = { onNavigate("official_replica") },
          )
          Spacer(Modifier.height(32.dp))
          CyberMapStatsRow(
            location = location,
            address = locationTitle(location),
            todayKm = todayRideLabel(cloudState),
            totalKm = totalMileageLabel(cloudVehicle),
            lastDistance = lastRideVisuals.first,
            lastDuration = lastRideVisuals.second,
            onMapTap = { onNavigate("location/current") },
            onRideStatsTap = { onNavigate("ride_stats/current") },
          )
          if (commandActivities.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            CyberRecentCommands(commands = commandActivities)
          }
          Spacer(Modifier.height(24.dp))
        }
      }
    }
  }
}

private fun currentPowerState(
  bleBikeState: BikeState?,
  cloudVehicle: OfficialVehicle?,
): Boolean? {
  if (bleBikeState != null) return bleBikeState.isPowerOn
  val acc = cloudVehicle?.acc
  return acc?.let { it == 1 }
}

private fun currentLockState(
  bleBikeState: BikeState?,
  cloudVehicle: OfficialVehicle?,
): Boolean? {
  if (bleBikeState != null) return bleBikeState.isLocked
  val defence = cloudVehicle?.defenceStatus
  return defence?.let { it == 1 }
}

private fun officialBleChipState(
  vehicle: OfficialVehicle?,
  connectionManager: ConnectionManager,
  bleState: ConnectionState,
  busy: Boolean,
): OfficialBleChipState {
  if (vehicle == null) return OfficialBleChipState.Hidden
  if (connectionManager.isProtocolLoggedIn) return OfficialBleChipState.Connected
  if (bleState == ConnectionState.CONNECTING ||
    bleState == ConnectionState.CONNECTED ||
    bleState == ConnectionState.RECONNECTING
  ) {
    return OfficialBleChipState.Connecting
  }
  return OfficialBleChipState.ClickToConnect
}

private fun rangeLabel(battery: BatterySnapshot): String {
  val remaining = battery.remainingMileage?.trim()
  if (!remaining.isNullOrEmpty()) {
    val cleaned = remaining.replace(Regex("[^\\d.]"), "")
    val parsed = cleaned.toDoubleOrNull()
    if (parsed != null) return "${formatCompactDecimal(parsed)} km"
    return if (remaining.contains("km")) remaining else "$remaining km"
  }
  val estimated = battery.estimatedRangeKm
  if (estimated != null) return "${formatCompactDecimal(estimated)} km"
  return "--"
}

private fun locationTitle(location: com.tailg.plus.data.cloud.ResolvedVehicleLocation?): String {
  val address = location?.address?.trim() ?: ""
  if (address.isNotEmpty()) return address
  val coords = location?.coordinateText ?: ""
  if (coords.isNotEmpty()) return coords
  return "暂无位置"
}

private fun todayRideLabel(cloudState: OfficialCloudState): String {
  val direct = cloudState.todayRideMileage.trim()
  if (direct.isNotEmpty()) {
    val cleaned = direct.replace(Regex("[^\\d.]"), "")
    val parsed = cleaned.toDoubleOrNull()
    if (parsed != null) return "${formatCompactDecimal(parsed)} km"
    return if (direct.lowercase().contains("km")) direct else "$direct km"
  }
  return "--"
}

private fun totalMileageLabel(vehicle: OfficialVehicle?): String {
  val m = vehicle?.mileage
  if (m != null && m > 0) return "${formatCompactDecimal(m)} km"
  return "--"
}

private fun lastRideVisuals(cloudState: OfficialCloudState): Pair<String, String> {
  var latest: com.tailg.plus.data.model.OfficialTravelRecord? = null
  for (day in cloudState.travelDays) {
    for (record in day.records) {
      if (latest == null || record.startTime.compareTo(latest.startTime) > 0) {
        latest = record
      }
    }
  }
  if (latest == null) return "--" to "--"
  val distKm = latest.mileageKm
  val mins = (latest.durationSeconds / 60.0).toInt()
  val dist = "${com.tailg.plus.util.formatDecimalDown(distKm, fractionDigits = 1)} km"
  val dur = if (mins > 0) "$mins min" else latest.durationLabel
  return dist to dur
}

private fun successTitle(command: CommandCode): String = when (command) {
  CommandCode.POWER_ON -> "通电成功"
  CommandCode.POWER_OFF -> "断电完成"
  CommandCode.LOCK -> "设防完成"
  CommandCode.UNLOCK -> "解防成功"
  CommandCode.FIND -> "寻车完成"
  CommandCode.OPEN_SEAT -> "开坐垫"
  else -> "${command.label}完成"
}

private fun successSubtitle(command: CommandCode): String = when (command) {
  CommandCode.POWER_ON -> "控制系统已就绪"
  CommandCode.POWER_OFF -> "动力输出已切断"
  CommandCode.LOCK -> "车锁与报警器已激活"
  CommandCode.UNLOCK -> "车锁已打开"
  CommandCode.FIND -> "车辆已响应"
  CommandCode.OPEN_SEAT -> "坐垫锁已释放"
  else -> command.label
}

private fun failureMessage(command: CommandCode, detail: String?): String {
  val text = detail?.trim() ?: ""
  if (text.isEmpty()) return "${command.label}失败，请稍后重试"
  if (text.contains(command.label)) return text
  return "${command.label}失败：$text"
}

@Composable
private fun CyberHomeSkeleton() {
  Column(modifier = Modifier.padding(horizontal = 20.dp)) {
    Box(
      modifier = Modifier
        .height(300.dp)
        .background(CyberHomeColors.card),
    )
    Spacer(Modifier.height(18.dp))
    Box(
      modifier = Modifier
        .height(168.dp)
        .background(CyberHomeColors.card),
    )
    Spacer(Modifier.height(18.dp))
    Box(
      modifier = Modifier
        .height(180.dp)
        .background(CyberHomeColors.mapPlaceholder),
    )
  }
}

private fun OfficialCloudState.asControlCloudState(): ControlCloudState = object : ControlCloudState {
  override val signedIn: Boolean get() = this@asControlCloudState.signedIn
  override val selectedVehicle: OfficialVehicle? get() = this@asControlCloudState.selectedVehicle
  override fun linkedLocalVehicleId(officialVehicleKey: String): String? =
    this@asControlCloudState.linkedLocalVehicleId(officialVehicleKey)
}

private fun CommandCode.toBleCommandCode(): com.tailg.plus.data.ble.CommandCode =
  when (this) {
    CommandCode.LOCK -> com.tailg.plus.data.ble.CommandCode.lock
    CommandCode.UNLOCK -> com.tailg.plus.data.ble.CommandCode.unlock
    CommandCode.OPEN_SEAT -> com.tailg.plus.data.ble.CommandCode.openSeat
    CommandCode.POWER_ON -> com.tailg.plus.data.ble.CommandCode.powerOn
    CommandCode.POWER_OFF -> com.tailg.plus.data.ble.CommandCode.powerOff
    CommandCode.FIND -> com.tailg.plus.data.ble.CommandCode.find
    CommandCode.READ_STATE -> com.tailg.plus.data.ble.CommandCode.readState
    CommandCode.READ_ANTI_THEFT -> com.tailg.plus.data.ble.CommandCode.readAntiTheft
  }

private fun CommandCode.toBleCommandCode(): com.tailg.plus.data.ble.CommandCode = when (this) {
  CommandCode.LOCK -> com.tailg.plus.data.ble.CommandCode.lock
  CommandCode.UNLOCK -> com.tailg.plus.data.ble.CommandCode.unlock
  CommandCode.OPEN_SEAT -> com.tailg.plus.data.ble.CommandCode.openSeat
  CommandCode.POWER_ON -> com.tailg.plus.data.ble.CommandCode.powerOn
  CommandCode.POWER_OFF -> com.tailg.plus.data.ble.CommandCode.powerOff
  CommandCode.FIND -> com.tailg.plus.data.ble.CommandCode.find
  CommandCode.READ_STATE -> com.tailg.plus.data.ble.CommandCode.readState
  CommandCode.READ_ANTI_THEFT -> com.tailg.plus.data.ble.CommandCode.readAntiTheft
}
