package com.tailg.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R
import com.tailg.plus.data.cloud.ResolvedVehicleLocation
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
import com.tailg.plus.ui.components.AppSkeleton
import com.tailg.plus.ui.components.CyberCard
import com.tailg.plus.ui.theme.AppRadii
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
import com.tailg.plus.ui.components.VehicleSwitchSheet
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
  var showVehicleSwitchSheet by remember { mutableStateOf(false) }
  var lastCommandAtMs by remember { mutableStateOf(0L) }
  val commandLog = remember { ControlCommandActivityLog() }
  var commandVersion by remember { mutableStateOf(0) }

  // String resources cached for coroutine-lambda use.
  val strBusyHint = stringResource(R.string.control_busy_hint)
  val strTooFrequent = stringResource(R.string.control_too_frequent)
  val strUnavailableHint = stringResource(R.string.control_unavailable_hint)
  val strVehicleChanged = stringResource(R.string.control_vehicle_changed)
  val strChannelChanged = stringResource(R.string.control_channel_changed)
  val strVehicleOrChannelChanged = stringResource(R.string.control_vehicle_or_channel_changed)
  val strSeatUnsupported = stringResource(R.string.control_seat_unsupported)
  val strVehicleUnknown = stringResource(R.string.control_vehicle_unknown)
  val strBleConnected = stringResource(R.string.control_ble_connected)
  val strBleNoAddress = stringResource(R.string.control_ble_no_address)
  val strBleConnecting = stringResource(R.string.control_ble_connecting)
  val strBleUnavailable = stringResource(R.string.control_ble_unavailable)
  val strBlePermission = stringResource(R.string.control_ble_permission)
  val strBleConnectError = stringResource(R.string.control_ble_connect_error)
  val strBleRetry = stringResource(R.string.control_ble_retry)
  val strRetry = stringResource(R.string.control_retry)

  val strSuccessFormat = stringResource(R.string.control_success_format)
  val strFailureFormat = stringResource(R.string.control_failure_format)
  val strFailureDetailFormat = stringResource(R.string.control_failure_detail_format)
  val strUnconfirmedFormat = stringResource(R.string.control_unconfirmed_format)
  val strDisabledFormat = stringResource(R.string.control_disabled_format)
  val strSuccessTitles = mapOf(
    CommandCode.POWER_ON to stringResource(R.string.control_success_on),
    CommandCode.POWER_OFF to stringResource(R.string.control_success_off),
    CommandCode.LOCK to stringResource(R.string.control_success_lock),
    CommandCode.UNLOCK to stringResource(R.string.control_success_unlock),
    CommandCode.FIND to stringResource(R.string.control_success_find),
    CommandCode.OPEN_SEAT to stringResource(R.string.control_success_seat),
  )
  val strSuccessSubtitles = mapOf(
    CommandCode.POWER_ON to stringResource(R.string.control_subtitle_on),
    CommandCode.POWER_OFF to stringResource(R.string.control_subtitle_off),
    CommandCode.LOCK to stringResource(R.string.control_subtitle_lock),
    CommandCode.UNLOCK to stringResource(R.string.control_subtitle_unlock),
    CommandCode.FIND to stringResource(R.string.control_subtitle_find),
    CommandCode.OPEN_SEAT to stringResource(R.string.control_subtitle_seat),
  )
  val strUnconfirmedTitles = mapOf(
    CommandCode.POWER_ON to stringResource(R.string.control_unconfirmed_on),
    CommandCode.POWER_OFF to stringResource(R.string.control_unconfirmed_off),
    CommandCode.LOCK to stringResource(R.string.control_unconfirmed_lock),
    CommandCode.UNLOCK to stringResource(R.string.control_unconfirmed_unlock),
  )

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

  /** Dart `_ensureNearFieldLink`: link the official vehicle's BLE
   *  target when its MAC is known, else fall back to the scan page.
   *  [auto] suppresses the snack when the vehicle has no MAC (silent
   *  path for channel-switch auto-link).
   */
  suspend fun ensureNearFieldLink(auto: Boolean = false) {
    if (connectionManager.isProtocolLoggedIn) {
      AppSnack.info(snackbarHostState, strBleConnected)
      return
    }
    val state = cloudService.currentState
    val vehicle = state.selectedVehicle
    val mac = vehicle?.normalizedDeviceMac
    if (vehicle == null || mac.isNullOrEmpty()) {
      if (!auto) {
        AppSnack.info(snackbarHostState, strBleNoAddress)
        onNavigate(Routes.SCAN)
      }
      return
    }
    AppSnack.info(snackbarHostState, strBleConnecting)
    try {
      val adapter = ctx.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
      val device = adapter?.getRemoteDevice(mac)
      if (device == null) {
        AppSnack.error(snackbarHostState, strBleUnavailable)
        return
      }
      connectionManager.connect(
        device,
        com.tailg.plus.data.ble.platform.OfficialBleConnectionContext.fromVehicle(
          vehicle,
          state.userId,
        ),
      )
      AppSnack.success(snackbarHostState, strBleConnected)
    } catch (e: SecurityException) {
      AppSnack.error(snackbarHostState, strBlePermission)
    } catch (e: Exception) {
      log.operation("蓝牙连接失败", detail = e.toString(), level = LogLevel.WARNING)
      AppSnack.error(snackbarHostState, "蓝牙连接失败,请靠近车辆重试")
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
      scope.launch { AppSnack.error(snackbarHostState, policy.disabledReason ?: strDisabledFormat.format(cmd.label)) }
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
        // Dart 749-777: 云服务决策门控 (非 BLE 路径检查 SIM 状态/服务到期)
        if (!availability.willUseBle) {
          val vehicleKeyBeforeGate = cloudService.currentState.selectedVehicle?.key
          val serviceDecision = cloudService.resolveSelectedRemoteControlServiceDecision()
          if (cloudService.currentState.selectedVehicle?.key != vehicleKeyBeforeGate) {
            AppSnack.error(snackbarHostState, "车辆已变化,请重新操作")
            commandLog.finish(activityId, "${cmd.label}已取消", "目标车辆已变化", ControlCommandActivityStatus.CANCELLED)
            return@launch
          }
          val serviceMessage = serviceDecision.message
          if (serviceMessage != null) {
            if (serviceDecision.blocksControl) {
              AppSnack.error(snackbarHostState, serviceMessage)
              commandLog.finish(activityId, "${cmd.label}失败", serviceMessage, ControlCommandActivityStatus.FAILED)
              return@launch
            }
            AppSnack.info(snackbarHostState, serviceMessage)
          }
          // 重新计算 availability,渠道可能因 SIM 状态变化而切换
          val availabilityAfterGate = ControlCommandRoute.resolve(
            base = controlAvailability,
            command = cmd,
            vehicle = cloudVehicle,
          )
          if (availabilityAfterGate.willUseBle) {
            AppSnack.info(snackbarHostState, "控车渠道已切换,请重新操作")
            commandLog.finish(activityId, "${cmd.label}已取消", "控车渠道已切换,请重新操作", ControlCommandActivityStatus.CANCELLED)
            return@launch
          }
          if (!availabilityAfterGate.enabled) {
            val reason = availabilityAfterGate.disabledReason.ifEmpty { "当前不可控车,请检查蓝牙或网络" }
            AppSnack.error(snackbarHostState, reason)
            commandLog.finish(activityId, "${cmd.label}失败", reason, ControlCommandActivityStatus.FAILED)
            return@launch
          }
        }
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
            AppSnack.error(snackbarHostState, commandError ?: unconfirmedMessage(cmd, strUnconfirmedTitles, strUnconfirmedFormat))
            commandLog.finish(
              activityId,
              if (commandError == null) "${cmd.label}未确认" else "${cmd.label}失败",
              commandError ?: "请稍后重试",
              ControlCommandActivityStatus.FAILED,
            )
          } else {
            AppSnack.info(snackbarHostState, result.successMessage ?: "${cmd.label}成功")
            commandLog.finish(activityId, successTitle(cmd, strSuccessTitles, strSuccessFormat), successSubtitle(cmd, strSuccessSubtitles), ControlCommandActivityStatus.SUCCEEDED)
          }
        } else {
          log.operation("Cyber 控车失败: ${cmd.label}", detail = "渠道=${result.transport} 原因=${result.failureMessage}", level = LogLevel.ERROR)
          refreshStateForConfirmation()
          AppSnack.error(snackbarHostState, failureMessage(cmd, result.failureMessage, strFailureFormat, strFailureDetailFormat))
          commandLog.finish(activityId, "${cmd.label}失败", result.failureMessage?.trim()?.ifEmpty { null } ?: "请稍后重试", ControlCommandActivityStatus.FAILED)
        }
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (e: Exception) {
        log.operation("Cyber 控车异常: ${cmd.label}", detail = e.toString(), level = LogLevel.ERROR)
        AppSnack.error(snackbarHostState, failureMessage(cmd, e.message, strFailureFormat, strFailureDetailFormat))
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
    contentWindowInsets = WindowInsets.statusBars,
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
              title = stringResource(R.string.control_need_login),
              actionLabel = stringResource(R.string.control_login_action),
              onAction = { onNavigate(Routes.LOGIN) },
            )
            VehicleControlHomeGateKind.Loading -> {
              VehicleControlGateBanner(
                title = "正在同步官方车辆…",
                actionLabel = stringResource(R.string.control_syncing_action),
                busy = true,
                onAction = {},
              )
              Spacer(Modifier.height(18.dp))
              CyberHomeSkeleton()
            }
            VehicleControlHomeGateKind.Error -> VehicleControlGateBanner(
              title = cloudState.error?.trim()?.ifEmpty { null } ?: "车辆同步失败，请重试",
              actionLabel = stringResource(R.string.control_retry_action),
              onAction = { handleRefresh() },
            )
            VehicleControlHomeGateKind.NoVehicle -> VehicleControlGateBanner(
              title = "暂无车辆，请先同步官方车辆",
              actionLabel = stringResource(R.string.control_add_vehicle_action),
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
          vehicleName = cloudVehicle?.displayName ?: vehicleStore.defaultVehicle?.displayName ?: stringResource(R.string.control_my_vehicle),
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
          onTitleTap = {
            // Dart `_openVehicleHeader`: switch sheet when multiple vehicles,
            // else the official cloud page.
            if (busy) {
              scope.launch { AppSnack.error(snackbarHostState, strBusyHint) }
            } else if (cloudState.vehicles.size > 1) {
              showVehicleSwitchSheet = true
            } else {
              onNavigate(Routes.OFFICIAL_CLOUD)
            }
          },
          onBatteryTap = { onNavigate(Routes.batteryDetails("current")) },
          onBleChipTap = {
            scope.launch { ensureNearFieldLink() }
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

  // Vehicle switch sheet (Dart `showVehicleSwitchSheet`).
  if (showVehicleSwitchSheet) {
    VehicleSwitchSheet(
      vehicles = cloudState.vehicles,
      selectedKey = cloudState.selectedVehicle?.key,
      onSelect = { target: OfficialVehicle ->
        try {
          cloudService.changeUsingVehicle(target)
          showVehicleSwitchSheet = false
          true
        } catch (e: Exception) {
          scope.launch { AppSnack.error(snackbarHostState, OfficialCloudRedactor.errorMessage(e)) }
          false
        }
      },
      onDismiss = { showVehicleSwitchSheet = false },
    )
  }

  // Channel selection sheet (Dart CyberChannelStrip bottom sheet).
  if (showChannelSheet) {
    androidx.compose.material3.ModalBottomSheet(
      onDismissRequest = { showChannelSheet = false },
      containerColor = CyberHomeColors.card,
    ) {
      Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        // Dart CyberChannelStrip header: title + status + stringResource(R.string.control_induction) link.
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          androidx.compose.material3.Text(
            text = stringResource(R.string.control_channel),
            style = androidx.compose.ui.text.TextStyle(
              fontSize = 16.sp,
              fontWeight = androidx.compose.ui.text.font.FontWeight.W700,
              color = CyberHomeColors.ink,
            ),
          )
          Spacer(Modifier.weight(1f))
          androidx.compose.material3.Text(
            text = controlChannelStatus.label,
            style = androidx.compose.ui.text.TextStyle(
              fontSize = 12.sp,
              color = CyberHomeColors.inkMuted,
            ),
          )
          Spacer(Modifier.width(10.dp))
          androidx.compose.material3.TextButton(
            onClick = {
              showChannelSheet = false
              onNavigate(Routes.inductionSettings(cloudService.currentState.selectedVehicle?.key ?: "current"))
            },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
          ) {
            androidx.compose.material3.Text(
              text = stringResource(R.string.control_induction),
              style = androidx.compose.ui.text.TextStyle(
                fontSize = 12.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.W600,
                color = CyberHomeColors.primary,
              ),
            )
          }
        }
        Spacer(Modifier.height(12.dp))
        channelSheetOptions().forEach { (channel, label, subtitle) ->
          val active = controlChannel == channel
          com.tailg.plus.ui.components.AppPressable(
            onClick = {
              // Dart `_selectControlChannel`: keep busy guard, then
              // trigger channel-specific side effects (BLE auto-link,
              // official-cloud MQTT preconnect).
              if (busy) {
                scope.launch { AppSnack.error(snackbarHostState, strBusyHint) }
                return@AppPressable
              }
              if (controlChannel == channel) {
                showChannelSheet = false
                return@AppPressable
              }
              controlChannel = channel
              showChannelSheet = false
              if (channel == OfficialControlChannel.BLE) {
                // Silent BLE path: try linking the official vehicle now.
                scope.launch { ensureNearFieldLink(auto = true) }
              } else if (channel == OfficialControlChannel.OFFICIAL_CLOUD && cloudState.signedIn) {
                scope.launch { mqttService.preconnectForCloud(cloudService) }
              }
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

private val NON_DIGIT_PATTERN = Regex("[^\\d.]")

/** Channel bottom-sheet options (constant; avoids rebuilding per recomposition). */
@Composable
private fun channelSheetOptions() = listOf(
  Triple(OfficialControlChannel.AUTOMATIC, stringResource(R.string.control_channel_auto), stringResource(R.string.control_channel_auto_desc)),
  Triple(OfficialControlChannel.BLE, stringResource(R.string.control_channel_ble), stringResource(R.string.control_channel_ble_desc)),
  Triple(OfficialControlChannel.OFFICIAL_CLOUD, stringResource(R.string.control_channel_cloud), stringResource(R.string.control_channel_cloud_desc)),
)

private fun rangeLabel(battery: BatterySnapshot): String {
  val remaining = battery.remainingMileage?.trim()
  if (!remaining.isNullOrEmpty()) {
    val cleaned = remaining.replace(NON_DIGIT_PATTERN, "")
    val parsed = cleaned.toDoubleOrNull()
    if (parsed != null) return "${formatCompactDecimal(parsed)} km"
    return if (remaining.contains("km")) remaining else "$remaining km"
  }
  val estimated = battery.estimatedRangeKm
  if (estimated != null) return "${formatCompactDecimal(estimated)} km"
  return "--"
}

@Composable
private fun locationTitle(location: ResolvedVehicleLocation?): String {
  val address = location?.address?.trim() ?: ""
  if (address.isNotEmpty()) return address
  val coords = location?.coordinateText ?: ""
  if (coords.isNotEmpty()) return coords
  return stringResource(R.string.control_no_location)
}

private fun todayRideLabel(cloudState: OfficialCloudState): String {
  val direct = cloudState.todayRideMileage.trim()
  if (direct.isNotEmpty()) {
    val cleaned = direct.replace(NON_DIGIT_PATTERN, "")
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


private fun successTitle(command: CommandCode, titles: Map<CommandCode, String>, format: String): String =
  titles[command] ?: format.format(command.label)









private fun successSubtitle(command: CommandCode, subtitles: Map<CommandCode, String>): String =
  subtitles[command] ?: command.label









private fun failureMessage(command: CommandCode, detail: String?, format: String, detailFormat: String): String {
  val text = detail?.trim() ?: ""
  if (text.isEmpty()) return format.format(command.label)
  if (text.contains(command.label)) return text
  return detailFormat.format(command.label, text)
}


/** Dart `_unconfirmedMessage` — cloud publish landed but the vehicle never confirmed. */
private fun unconfirmedMessage(command: CommandCode, titles: Map<CommandCode, String>, format: String): String =
  titles[command] ?: format.format(command.label)






@Composable
private fun CyberHomeSkeleton() {
  // Dart `_CyberHomeSkeleton`: hero card + control grid (3 circles) + map placeholder.
  val base = CyberHomeColors.control
  val highlight = CyberHomeColors.cardMuted
  Column(modifier = Modifier.padding(horizontal = 20.dp)) {
    // Hero skeleton.
    CyberCard(modifier = Modifier.height(300.dp)) {
      Column(horizontalAlignment = Alignment.Start) {
        AppSkeleton(
          width = 160.dp,
          height = 22.dp,
          baseColor = base,
          highlightColor = highlight,
        )
        Spacer(Modifier.height(22.dp))
        AppSkeleton(
          width = 110.dp,
          height = 44.dp,
          baseColor = base,
          highlightColor = highlight,
        )
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
          AppSkeleton(
            width = 200.dp,
            height = 90.dp,
            borderRadius = RoundedCornerShape(AppRadii.tile),
            baseColor = base,
            highlightColor = highlight,
          )
        }
        Spacer(Modifier.height(16.dp))
        AppSkeleton(
          width = 240.dp,
          height = 12.dp,
          baseColor = base,
          highlightColor = highlight,
        )
      }
    }
    Spacer(Modifier.height(18.dp))
    // Control grid skeleton.
    CyberCard(modifier = Modifier.height(168.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
      ) {
        repeat(3) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppSkeleton(
              width = 56.dp,
              height = 56.dp,
              borderRadius = CircleShape,
              baseColor = base,
              highlightColor = highlight,
            )
            Spacer(Modifier.height(12.dp))
            AppSkeleton(
              width = 56.dp,
              height = 12.dp,
              baseColor = base,
              highlightColor = highlight,
            )
          }
        }
      }
    }
    Spacer(Modifier.height(18.dp))
    // Map skeleton.
    Box(
      modifier = Modifier
        .height(180.dp)
        .clip(RoundedCornerShape(AppRadii.sheet))
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
