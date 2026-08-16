package com.tailg.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.tailg.plus.data.model.OfficialCloudCommand
import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.data.mqtt.OfficialMqttService
import com.tailg.plus.data.mqtt.OfficialRemoteSendPath
import com.tailg.plus.data.network.NetworkAvailabilityService
import com.tailg.plus.data.store.VehicleStore
import com.tailg.plus.domain.control.ControlChannelAvailability
import com.tailg.plus.domain.control.ControlChannelResolver
import com.tailg.plus.domain.control.ControlCloudState
import com.tailg.plus.domain.control.ControlCommandConfirmation
import com.tailg.plus.domain.control.ControlCommandExecutor
import com.tailg.plus.domain.control.ControlCommandPolicy
import com.tailg.plus.domain.control.ControlCommandResult
import com.tailg.plus.domain.control.ControlCommandRoute
import com.tailg.plus.domain.control.ControlCommandTransport
import com.tailg.plus.domain.control.ControlCommandVehicleStateSnapshot
import com.tailg.plus.domain.control.ControlTopBarChannel
import com.tailg.plus.domain.control.OfficialControlChannel
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService
import com.tailg.plus.ui.components.AppPressable
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
import com.tailg.plus.ui.navigation.Routes
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
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
  val ctx = androidx.compose.ui.platform.LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  val log = remember { LogService() }
  val cloudState by cloudService.stateFlow.collectAsState()
  val bleState by connectionManager.stateFlow.collectAsState()
  val bleBikeState by connectionManager.bikeStateFlow.collectAsState()
  val mqttLinkState by mqttService.linkState.collectAsState()

  var busy by remember { mutableStateOf(false) }
  var activeCommand by remember { mutableStateOf<CommandCode?>(null) }
  var controlChannel by remember { mutableStateOf(OfficialControlChannel.AUTOMATIC) }
  var showChannelSheet by remember { mutableStateOf(false) }
  var lastCommandAtMs by remember { mutableStateOf(0L) }
  val commandLog = remember { ControlCommandActivityLog() }
  var commandVersion by remember { mutableStateOf(0) }

  // Dart subscribes to networkAvailabilityService.changes; BLE must keep
  // working offline, so the flow fails open (NetworkAvailabilityService).
  val networkService = remember { NetworkAvailabilityService(ctx) }
  var networkReady by remember { mutableStateOf(true) }
  LaunchedEffect(Unit) {
    networkService.changes.collect { ready -> networkReady = ready }
  }
  val locationService = remember(ctx) {
    com.tailg.plus.service.LocationService(ctx, vehicleStore)
  }

  // Foreground resume → retry a failed/absent MQTT preconnect (Dart 229-237).
  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
      if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        scope.launch {
          if (!mqttService.isConnected && mqttService.lastPreconnectError != null) {
            mqttService.retryPreconnect(cloudService)
          }
        }
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

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

  val findAvailability = remember(cloudState, controlChannel, bleState, networkReady) {
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

  val powerAvailability = remember(cloudState, controlChannel, isPowerOn, bleState, networkReady) {
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

  val armAvailability = remember(cloudState, controlChannel, isArmed, bleState, networkReady) {
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

  val seatAvailability = remember(cloudState, controlChannel, bleState, networkReady) {
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

  fun vehicleStateSnapshot(): ControlCommandVehicleStateSnapshot {
    val vehicle = cloudService.currentState.selectedVehicle
    return ControlCommandVehicleStateSnapshot(
      isLocked = vehicle?.isLocked,
      isPowerOn = vehicle?.isPowerOn,
    )
  }

  suspend fun refreshStateForConfirmation(preferBle: Boolean = false) {
    try {
      if (preferBle) {
        connectionManager.refreshBikeState()
      } else {
        cloudService.refreshVehicles(silent = true, refreshReplicaDetails = false, force = true)
      }
    } catch (e: Exception) {
      log.operation("Cyber 控车后确认车辆状态失败", detail = e.toString(), level = LogLevel.WARNING)
    }
  }

  /**
   * Port of Dart `_waitForCommandConfirmation`: a cloud publish is only
   * "done" once the MQTT pending command clears or ACC/defence reaches the
   * expected post-command state (and changed from the baseline).
   */
  suspend fun waitForCommandConfirmation(
    command: CommandCode,
    transport: ControlCommandTransport,
    expectedOfficialVehicleKey: String?,
    baseline: ControlCommandVehicleStateSnapshot,
    mqttPendingAtSend: String?,
  ): Boolean {
    if (transport == ControlCommandTransport.BLE) {
      return ControlCommandConfirmation.isConfirmed(
        command = command,
        transport = transport,
        expectedOfficialVehicleKey = expectedOfficialVehicleKey,
        currentOfficialVehicleKey = cloudService.currentState.selectedVehicle?.key,
        baseline = baseline,
        current = vehicleStateSnapshot(),
        mqttAcked = false,
      )
    }
    val needsMqttResponse = ControlCommandConfirmation.needsMqttResponse(command, mqttPendingAtSend)
    if (!ControlCommandConfirmation.needsVehicleStateConfirmation(command) && !needsMqttResponse) {
      return ControlCommandConfirmation.isConfirmed(
        command = command,
        transport = transport,
        expectedOfficialVehicleKey = expectedOfficialVehicleKey,
        currentOfficialVehicleKey = cloudService.currentState.selectedVehicle?.key,
        baseline = baseline,
        current = vehicleStateSnapshot(),
        mqttAcked = false,
      )
    }
    val startedAt = System.currentTimeMillis()
    while (true) {
      if (mqttService.pendingCommandError != null) return false
      val mqttAcked = ControlCommandConfirmation.mqttPendingAcknowledged(
        pendingAtSend = mqttPendingAtSend,
        pendingNow = mqttService.pendingCommandApiName,
      )
      if (needsMqttResponse && mqttAcked) {
        return ControlCommandConfirmation.guard.allows(
          context = com.tailg.plus.domain.control.ControlCommandConfirmationContext(
            transport = transport,
            officialVehicleKey = expectedOfficialVehicleKey,
          ),
          currentOfficialVehicleKey = cloudService.currentState.selectedVehicle?.key,
        )
      }
      if (needsMqttResponse) {
        if (System.currentTimeMillis() - startedAt > CONTROL_CONFIRM_TIMEOUT_MS) return false
        delay(CONTROL_CONFIRM_POLL_DELAY_MS)
        continue
      }
      val confirmed = ControlCommandConfirmation.isConfirmed(
        command = command,
        transport = transport,
        expectedOfficialVehicleKey = expectedOfficialVehicleKey,
        currentOfficialVehicleKey = cloudService.currentState.selectedVehicle?.key,
        baseline = baseline,
        current = vehicleStateSnapshot(),
        mqttAcked = mqttAcked,
      )
      if (confirmed) return true
      if (System.currentTimeMillis() - startedAt > CONTROL_CONFIRM_TIMEOUT_MS) return false
      refreshStateForConfirmation()
      if (mqttService.pendingCommandError != null) return false
      val mqttAckedAfterRefresh = ControlCommandConfirmation.mqttPendingAcknowledged(
        pendingAtSend = mqttPendingAtSend,
        pendingNow = mqttService.pendingCommandApiName,
      )
      val confirmedAfterRefresh = ControlCommandConfirmation.isConfirmed(
        command = command,
        transport = transport,
        expectedOfficialVehicleKey = expectedOfficialVehicleKey,
        currentOfficialVehicleKey = cloudService.currentState.selectedVehicle?.key,
        baseline = baseline,
        current = vehicleStateSnapshot(),
        mqttAcked = mqttAckedAfterRefresh,
      )
      if (confirmedAfterRefresh) return true
      if (System.currentTimeMillis() - startedAt > CONTROL_CONFIRM_TIMEOUT_MS) return false
      delay(CONTROL_CONFIRM_POLL_DELAY_MS)
    }
  }

  fun sendCommand(cmd: CommandCode) {
    if (busy) {
      scope.launch { AppSnack.error(snackbarHostState, "正在执行控车指令，请稍候") }
      return
    }
    val now = System.currentTimeMillis()
    if (now - lastCommandAtMs < CONTROL_COMMAND_DEBOUNCE_MS) {
      scope.launch { AppSnack.info(snackbarHostState, "指令发送过于频繁，请稍候") }
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
    // QGJ open-seat firmware preflight (Dart checkQgjSeatSupport gate) runs
    // inside the send coroutine below (suspend check).
    lastCommandAtMs = now
    busy = true
    activeCommand = cmd
    val vehicleAtSend = cloudService.currentState.selectedVehicle
    val vehicleKeyAtSend = vehicleAtSend?.key
    val baseline = vehicleStateSnapshot()
    val activityId = commandLog.start(cmd, "${cmd.label}中…", "指令已发送，等待回执")
    commandVersion++
    scope.launch {
      try {
        delay(CONTROL_COMMAND_SEND_DELAY_MS)
        // Abort if the selected vehicle changed mid-send (Dart 798-811).
        if (cloudService.currentState.selectedVehicle?.key != vehicleKeyAtSend) {
          AppSnack.error(snackbarHostState, "车辆或控车渠道已变化，本次指令已取消")
          commandLog.finish(activityId, "${cmd.label}已取消", "目标车辆或连接已变化", ControlCommandActivityStatus.CANCELLED)
          return@launch
        }
        if (cmd == CommandCode.OPEN_SEAT && availability.willUseBle) {
          val supported = connectionManager.checkQgjSeatSupport()
          if (supported == false) {
            AppSnack.error(snackbarHostState, "当前车辆固件不支持开坐垫")
            commandLog.finish(activityId, "${cmd.label}失败", "当前车辆固件不支持开坐垫", ControlCommandActivityStatus.FAILED)
            return@launch
          }
        }
        val result = commandExecutor.send(command = cmd, availability = availability)
        if (result.success) {
          if (vehicleAtSend != null) {
            try {
              cloudService.syncCarOperatorAfterCommand(command = cmd, vehicle = vehicleAtSend)
            } catch (e: Exception) {
              log.operation("同步官方车辆操作人失败", detail = e.toString(), level = LogLevel.WARNING)
            }
          }
          if (result.shouldRefreshBikeState) {
            refreshStateForConfirmation(preferBle = true)
          }
          try {
            locationService.recordDefaultVehicleLocation()
          } catch (e: Exception) {
            log.operation("控车后记录车辆位置失败", detail = e.toString(), level = LogLevel.WARNING)
          }
          // Capture the pending command set by the MQTT publish, if any.
          val mqttPendingForConfirm =
            if (result.transport == ControlCommandTransport.OFFICIAL_CLOUD &&
              mqttService.lastSendPath == OfficialRemoteSendPath.MQTT
            ) {
              mqttService.pendingCommandApiName
                ?: OfficialCloudCommand.fromCommandCode(cmd)?.apiName
            } else {
              null
            }
          val confirmed = waitForCommandConfirmation(
            command = cmd,
            transport = result.transport,
            expectedOfficialVehicleKey = vehicleKeyAtSend,
            baseline = baseline,
            mqttPendingAtSend = mqttPendingForConfirm,
          )
          if (!confirmed) {
            refreshStateForConfirmation()
            val commandError = mqttService.pendingCommandError
            AppSnack.error(snackbarHostState, commandError ?: unconfirmedMessage(cmd))
            commandLog.finish(
              activityId,
              if (commandError == null) "${cmd.label}未确认" else "${cmd.label}失败",
              commandError ?: "请稍后重试",
              ControlCommandActivityStatus.FAILED,
            )
          } else {
            AppSnack.info(snackbarHostState, result.successMessage ?: "${cmd.label}成功")
            commandLog.finish(activityId, successTitle(cmd), successSubtitle(cmd), ControlCommandActivityStatus.SUCCEEDED)
          }
        } else {
          log.operation("Cyber 控车失败: ${cmd.label}", detail = "渠道=${result.transport} 原因=${result.failureMessage}", level = LogLevel.ERROR)
          refreshStateForConfirmation()
          AppSnack.error(snackbarHostState, failureMessage(cmd, result.failureMessage))
          commandLog.finish(activityId, "${cmd.label}失败", result.failureMessage?.trim()?.ifEmpty { null } ?: "请稍后重试", ControlCommandActivityStatus.FAILED)
        }
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
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
              onAction = { onNavigate(Routes.LOGIN) },
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
              onAction = { onNavigate(Routes.ADD_VEHICLE) },
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
          onTitleTap = { onNavigate(Routes.OFFICIAL_CLOUD) },
          onBatteryTap = { onNavigate(Routes.batteryDetails("current")) },
          onBleChipTap = {
            scope.launch {
              if (connectionManager.isProtocolLoggedIn) {
                AppSnack.info(snackbarHostState, "蓝牙已连接")
                return@launch
              }
              // Dart `_ensureNearFieldLink`: link the official vehicle's BLE
              // target when its MAC is known, else fall back to the scan page.
              val state = cloudService.currentState
              val vehicle = state.selectedVehicle
              val mac = vehicle?.normalizedDeviceMac
              if (vehicle == null || mac.isNullOrEmpty()) {
                AppSnack.info(snackbarHostState, "未获取车辆蓝牙地址，请在扫码页手动连接")
                onNavigate(Routes.SCAN)
                return@launch
              }
              AppSnack.info(snackbarHostState, "正在连接车辆蓝牙…")
              try {
                val adapter = ctx.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
                val device = adapter?.getRemoteDevice(mac)
                if (device == null) {
                  AppSnack.error(snackbarHostState, "蓝牙不可用，请检查蓝牙开关")
                  return@launch
                }
                connectionManager.connect(
                  device,
                  com.tailg.plus.data.ble.platform.OfficialBleConnectionContext.fromVehicle(
                    vehicle,
                    state.userId,
                  ),
                )
                AppSnack.success(snackbarHostState, "蓝牙已连接")
              } catch (e: SecurityException) {
                AppSnack.error(snackbarHostState, "缺少蓝牙权限，请到系统设置开启")
              } catch (e: Exception) {
                log.operation("蓝牙连接失败", detail = e.toString(), level = LogLevel.WARNING)
                AppSnack.error(snackbarHostState, "蓝牙连接失败，请靠近车辆重试")
              }
            }
          },
          onMessages = { onNavigate(Routes.vehicleMessage("current")) },
          onChannelTap = { showChannelSheet = true },
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
            onSettings = { onNavigate(Routes.vehicleSettings("current")) },
            onSeat = { sendCommand(CommandCode.OPEN_SEAT) },
            onNfc = { onNavigate(Routes.OFFICIAL_REPLICA) },
          )
          Spacer(Modifier.height(32.dp))
          CyberMapStatsRow(
            location = location,
            address = locationTitle(location),
            todayKm = todayRideLabel(cloudState),
            totalKm = totalMileageLabel(cloudVehicle),
            lastDistance = lastRideVisuals.first,
            lastDuration = lastRideVisuals.second,
            onMapTap = { onNavigate(Routes.location("current")) },
            onRideStatsTap = { onNavigate(Routes.rideStats("current")) },
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

  // Channel selection sheet (Dart CyberChannelStrip bottom sheet).
  if (showChannelSheet) {
    androidx.compose.material3.ModalBottomSheet(
      onDismissRequest = { showChannelSheet = false },
      containerColor = CyberHomeColors.card,
    ) {
      Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        androidx.compose.material3.Text(
          text = "控车渠道",
          style = androidx.compose.ui.text.TextStyle(
            fontSize = 16.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.W700,
            color = CyberHomeColors.ink,
          ),
        )
        Spacer(Modifier.height(12.dp))
        listOf(
          Triple(OfficialControlChannel.AUTOMATIC, "自动", "按官方车型与蓝牙状态自动分流"),
          Triple(OfficialControlChannel.BLE, "近场蓝牙", "仅车辆蓝牙直连时可控"),
          Triple(OfficialControlChannel.OFFICIAL_CLOUD, "云端", "仅 MQTT 远程通道"),
        ).forEach { (channel, label, subtitle) ->
          val active = controlChannel == channel
          com.tailg.plus.ui.components.AppPressable(
            onClick = {
              controlChannel = channel
              showChannelSheet = false
            },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            background = if (active) CyberHomeColors.primarySoft else CyberHomeColors.cardMuted,
            semanticsLabel = label,
          ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
              androidx.compose.material3.Text(
                text = label,
                style = androidx.compose.ui.text.TextStyle(
                  fontSize = 14.sp,
                  fontWeight = androidx.compose.ui.text.font.FontWeight.W700,
                  color = if (active) CyberHomeColors.primary else CyberHomeColors.ink,
                ),
              )
              Spacer(Modifier.height(2.dp))
              androidx.compose.material3.Text(
                text = subtitle,
                style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkMuted),
              )
            }
          }
          Spacer(Modifier.height(8.dp))
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

/** Dart `_unconfirmedMessage` — cloud publish landed but the vehicle never confirmed. */
private fun unconfirmedMessage(command: CommandCode): String = when (command) {
  CommandCode.POWER_ON -> "上电未确认，请稍后重试"
  CommandCode.POWER_OFF -> "断电未确认，请稍后重试"
  CommandCode.LOCK -> "设防未确认，请稍后重试"
  CommandCode.UNLOCK -> "解防未确认，请稍后重试"
  else -> "${command.label}未确认，请稍后重试"
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
