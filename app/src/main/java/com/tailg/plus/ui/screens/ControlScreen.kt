package com.tailg.plus.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.tailg.plus.R
import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.data.cloud.OfficialCloudRedactor
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.cloud.OfficialCloudState
import com.tailg.plus.data.cloud.OfficialCloudVehicleLinks
import com.tailg.plus.data.cloud.OfficialCloudMessages
import com.tailg.plus.data.cloud.resolveVehicleLocation
import com.tailg.plus.data.model.BatterySnapshot
import com.tailg.plus.data.model.CommandCode
import com.tailg.plus.data.model.ControlCommandActivityStatus
import com.tailg.plus.data.model.OfficialBatteryInfo
import com.tailg.plus.data.model.OfficialCloudCommand
import com.tailg.plus.data.model.OfficialTravelDay
import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.data.model.OfficialVehicleLocation
import com.tailg.plus.data.mqtt.OfficialMqttService
import com.tailg.plus.data.mqtt.OfficialMqttStatusPayload
import com.tailg.plus.data.mqtt.OfficialRemoteSendPath
import com.tailg.plus.data.store.VehicleStore
import com.tailg.plus.domain.control.ControlChannelAvailability
import com.tailg.plus.domain.control.ControlChannelResolver
import com.tailg.plus.domain.control.ControlCloudState
import com.tailg.plus.domain.control.ControlCommandConfirmation
import com.tailg.plus.domain.control.ControlCommandPolicy
import com.tailg.plus.domain.control.ControlCommandResult
import com.tailg.plus.domain.control.ControlCommandRoute
import com.tailg.plus.domain.control.ControlCommandTransport
import com.tailg.plus.domain.control.ControlCommandVehicleStateSnapshot
import com.tailg.plus.domain.control.ControlTopBarChannel
import com.tailg.plus.domain.control.OfficialControlChannel
import com.tailg.plus.log.LogLevel
import com.tailg.plus.ui.components.AppSnackbarHost
import com.tailg.plus.ui.components.AppSnack
import com.tailg.plus.ui.components.CollapsingHeaderState
import com.tailg.plus.ui.components.CyberControlGrid
import com.tailg.plus.ui.components.CyberHeaderCollapsedHeight
import com.tailg.plus.ui.components.CyberHeaderExpandedHeight
import com.tailg.plus.ui.components.CyberMapStatsRow
import com.tailg.plus.ui.components.CyberRecentCommands
import com.tailg.plus.ui.components.CyberVehicleHeader
import com.tailg.plus.ui.components.OfficialBleChipState
import com.tailg.plus.ui.components.VehicleControlHomeGate
import com.tailg.plus.ui.components.VehicleControlHomeGateKind
import com.tailg.plus.ui.components.VehicleSwitchSheet
import com.tailg.plus.ui.navigation.Routes
import com.tailg.plus.ui.theme.CyberHomeColors
import com.tailg.plus.ui.theme.LocalDistanceUnitPreference
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val CONTROL_CONFIRM_TIMEOUT_MS = 8_000L

/**
 * Official-parity confirmation window: phase 1 only waits for MQTT status
 * pushes ([OfficialMqttService.statusPayloadEvents]) — zero HTTP requests,
 * exactly what the official ControlFragment does. The HTTP fallback below
 * only exists because the official app has no timeout story at all.
 */
private const val CONTROL_CONFIRM_PUSH_WINDOW_MS = 3_000L

/** Fallback cadence: one lightweight carStatus-only refresh per interval. */
private const val CONTROL_CONFIRM_FALLBACK_RETRY_MS = 3_000L

/** Entry: dependents land one beat after the first-paint carStatus refresh. */
private const val CONTROL_ENTRY_DEPENDENTS_DELAY_MS = 350L

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
  viewModel: ControlViewModel = hiltViewModel(),
) {
  val scope = rememberCoroutineScope()
  val ctx = androidx.compose.ui.platform.LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  val log = viewModel.log
  // Narrow cloud projection: only the fields this screen actually reads. The
  // raw `stateFlow` re-emits on every refresh field (messages, travel,
  // batteryInfoLoading…); collecting the whole state here made any unrelated
  // emission restart this entire composable. `map`+`distinctUntilChanged`
  // collapses emissions that leave the read set unchanged.
  val cloudState by cloudService.stateFlow
    .map { state ->
      CloudScreenState(
        signedIn = state.signedIn,
        selectedVehicle = state.selectedVehicle,
        selectedVehicleKey = state.selectedVehicle?.key,
        vehicles = state.vehicles,
        batteryInfo = state.batteryInfo,
        vehicleLocation = state.vehicleLocation,
        localVehicleLinks = state.localVehicleLinks,
        travelDays = state.travelDays,
        todayRideMileage = state.todayRideMileage,
        loading = state.loading,
        error = state.error,
      )
    }
    .distinctUntilChanged()
    .collectAsStateWithLifecycle(initialValue = CloudScreenState.from(cloudService.currentState))
  val bleState by connectionManager.stateFlow.collectAsStateWithLifecycle()
  val bleBikeState by connectionManager.bikeStateFlow.collectAsStateWithLifecycle()
  val mqttLinkState by mqttService.linkState.collectAsStateWithLifecycle()
  val ui by viewModel.uiState.collectAsStateWithLifecycle()
  val busy = ui.busy
  val activeCommand = ui.activeCommand
  val controlChannel = ui.controlChannel
  val showChannelSheet = ui.showChannelSheet
  val showVehicleSwitchSheet = ui.showVehicleSwitchSheet
  val lastCommandAtMs = ui.lastCommandAtMs
  val commandLog = viewModel.commandLog
  val commandVersion = ui.commandVersion
  val networkReady = ui.networkReady

  // String resources cached for coroutine-lambda use. The per-command title
  // maps are built inside sendCommand — once per command, not per recomposition.
  val strBusyHint = stringResource(R.string.control_busy_hint)
  val strBleConnected = stringResource(R.string.control_ble_connected)
  val strBleNoAddress = stringResource(R.string.control_ble_no_address)
  val strBleConnecting = stringResource(R.string.control_ble_connecting)
  val strBleUnavailable = stringResource(R.string.control_ble_unavailable)
  val strBlePermission = stringResource(R.string.control_ble_permission)

  val strSuccessFormat = stringResource(R.string.control_success_format)
  val strFailureFormat = stringResource(R.string.control_failure_format)
  val strFailureDetailFormat = stringResource(R.string.control_failure_detail_format)
  val strUnconfirmedFormat = stringResource(R.string.control_unconfirmed_format)
  val strDisabledFormat = stringResource(R.string.control_disabled_format)

  // Per-command title copy. Plain strings at composition level; the maps are
  // assembled inside sendCommand — once per command, not per recomposition.
  val strSuccessOn = stringResource(R.string.control_success_on)
  val strSuccessOff = stringResource(R.string.control_success_off)
  val strSuccessLock = stringResource(R.string.control_success_lock)
  val strSuccessUnlock = stringResource(R.string.control_success_unlock)
  val strSuccessFind = stringResource(R.string.control_success_find)
  val strSuccessSeat = stringResource(R.string.control_success_seat)
  val strSubtitleOn = stringResource(R.string.control_subtitle_on)
  val strSubtitleOff = stringResource(R.string.control_subtitle_off)
  val strSubtitleLock = stringResource(R.string.control_subtitle_lock)
  val strSubtitleUnlock = stringResource(R.string.control_subtitle_unlock)
  val strSubtitleFind = stringResource(R.string.control_subtitle_find)
  val strSubtitleSeat = stringResource(R.string.control_subtitle_seat)
  val strUnconfirmedOn = stringResource(R.string.control_unconfirmed_on)
  val strUnconfirmedOff = stringResource(R.string.control_unconfirmed_off)
  val strUnconfirmedLock = stringResource(R.string.control_unconfirmed_lock)
  val strUnconfirmedUnlock = stringResource(R.string.control_unconfirmed_unlock)

  val locationService = viewModel.locationService

  // Foreground resume → retry a failed/absent MQTT preconnect (Dart 229-237).
  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
      if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        viewModel.retryMqttPreconnectIfNeeded()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val commandExecutor = viewModel.commandExecutor

  val cloudVehicle = cloudState.selectedVehicle
  // Narrow keys: an unrelated cloudState field change (messages, loading
  // flags) must not rebuild the battery/location snapshots.
  val battery = remember(cloudState.signedIn, cloudVehicle, cloudState.batteryInfo) {
    BatterySnapshot.fromSources(
      officialVehicle = if (cloudState.signedIn) cloudVehicle else null,
      officialBatteryInfo = cloudState.batteryInfo,
    )
  }
  val location = remember(cloudState.vehicleLocation, cloudVehicle, vehicleStore.defaultVehicle) {
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

  // Narrow cloud identity: the resolvers below only read signedIn /
  // selectedVehicle / localVehicleLinks. Remembering the control-cloud view
  // on those keys keeps an unrelated cloudState emission (messages, travel,
  // loading flags) from producing fresh availability instances — children
  // then keep their inputs and skip.
  val controlCloudState = remember(cloudState.signedIn, cloudVehicle, cloudState.localVehicleLinks) {
    cloudState.asControlCloudState()
  }

  val controlAvailability = remember(controlCloudState, bleState, busy, controlChannel, networkReady) {
    ControlChannelResolver.resolve(
      cloudState = controlCloudState,
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

  // Shared busy-free base for the per-command routes below — resolving it
  // once avoids four identical ControlChannelResolver.resolve() passes per
  // recomposition (each keyed remember block used to re-run the same resolve).
  val baseAvailability = remember(controlCloudState, bleState, controlChannel, networkReady) {
    ControlChannelResolver.resolve(
      cloudState = controlCloudState,
      bleReady = connectionManager.isProtocolLoggedIn,
      bleNotReadyReason = connectionManager.protocolLoginUnavailableReason,
      defaultVehicleId = vehicleStore.defaultVehicle?.id,
      channel = controlChannel,
      busy = false,
      networkReady = networkReady,
    )
  }

  val findAvailability = remember(baseAvailability, cloudVehicle) {
    ControlCommandRoute.resolve(
      base = baseAvailability,
      command = CommandCode.FIND,
      vehicle = cloudVehicle,
    )
  }

  val powerAvailability = remember(baseAvailability, isPowerOn, cloudVehicle) {
    val cmd = if (isPowerOn == true) CommandCode.POWER_OFF else CommandCode.POWER_ON
    ControlCommandRoute.resolve(
      base = baseAvailability,
      command = cmd,
      vehicle = cloudVehicle,
    )
  }

  val armAvailability = remember(baseAvailability, isArmed, cloudVehicle) {
    val cmd = if (isArmed == true) CommandCode.UNLOCK else CommandCode.LOCK
    ControlCommandRoute.resolve(
      base = baseAvailability,
      command = cmd,
      vehicle = cloudVehicle,
    )
  }

  val seatAvailability = remember(baseAvailability, cloudVehicle) {
    ControlCommandRoute.resolve(
      base = baseAvailability,
      command = CommandCode.OPEN_SEAT,
      vehicle = cloudVehicle,
    )
  }

  val bleChipState = remember(cloudVehicle, bleState, busy) {
    officialBleChipState(cloudVehicle, connectionManager, bleState, busy)
  }

  val distanceUnit = LocalDistanceUnitPreference.current
  val lastRideVisuals = remember(cloudState.travelDays, distanceUnit) {
    lastRideVisuals(cloudState, distanceUnit)
  }
  val commandActivities = remember(commandVersion) { commandLog.entries }

  // Silent refresh on first composition. Official parity: first paint needs
  // carStatus + messages only (the entrance fade runs uncontended); the
  // battery/location/fence/today dependents land one beat later — the delayed
  // pass early-paths into the dependents-only refresh while the "vehicles"
  // recent-success TTL holds, and falls back to a full refresh if stage one
  // failed.
  LaunchedEffect(Unit) {
    if (cloudService.currentState.signedIn) {
      try {
        cloudService.refreshVehicles(
          silent = true,
          refreshReplicaDetails = false,
          refreshDependents = false,
        )
      } catch (e: Exception) {
        log.operation("Cyber 首页静默刷新失败", detail = e.toString(), level = LogLevel.WARNING)
      }
      try {
        cloudService.refreshMessages(silent = true)
      } catch (e: Exception) {
        log.operation("Cyber 首页消息静默刷新失败", detail = e.toString(), level = LogLevel.WARNING)
      }
      delay(CONTROL_ENTRY_DEPENDENTS_DELAY_MS)
      try {
        cloudService.refreshVehicles(silent = true, refreshReplicaDetails = true)
      } catch (e: Exception) {
        log.operation("Cyber 首页依赖数据刷新失败", detail = e.toString(), level = LogLevel.WARNING)
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
        // Official `updateCarControlInfo`: one carStatus request, no dependent
        // cascade — battery/BMS/location refreshes never belonged here.
        cloudService.refreshVehicles(
          silent = true,
          refreshReplicaDetails = false,
          force = true,
          refreshDependents = false,
        )
      }
    } catch (e: Exception) {
      log.operation("Cyber 控车后确认车辆状态失败", detail = e.toString(), level = LogLevel.WARNING)
    }
  }

  /**
   * Port of Dart `_waitForCommandConfirmation` with the official App's
   * push-driven model applied (official `ControlFragment.mqttPublish` +
   * `messageArrived`): phase 1 waits for MQTT status pushes — zero HTTP.
   * A publish is only "done" once the MQTT pending command clears or
   * ACC/defence reaches the expected post-command state (and changed from the
   * baseline). Phase 2 is our bounded safety net the official app lacks: a
   * lightweight carStatus-only refresh every [CONTROL_CONFIRM_FALLBACK_RETRY_MS].
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
    // Monotonic clock — wall-clock jumps (user adjusts system time) must not
    // stretch or truncate the confirmation window.
    val startedAt = SystemClock.elapsedRealtime()

    fun mqttAckedNow(): Boolean = ControlCommandConfirmation.mqttPendingAcknowledged(
      pendingAtSend = mqttPendingAtSend,
      pendingNow = mqttService.pendingCommandApiName,
    )

    // Snapshot check. For commands that only need the MQTT response, only the
    // ACK (plus the same-vehicle guard) confirms — poll-loop semantics before
    // the push-first rewrite never state-confirmed those either.
    fun confirmedNow(mqttAcked: Boolean): Boolean {
      if (needsMqttResponse) {
        if (!mqttAcked) return false
        return ControlCommandConfirmation.guard.allows(
          context = com.tailg.plus.domain.control.ControlCommandConfirmationContext(
            transport = transport,
            officialVehicleKey = expectedOfficialVehicleKey,
          ),
          currentOfficialVehicleKey = cloudService.currentState.selectedVehicle?.key,
        )
      }
      return ControlCommandConfirmation.isConfirmed(
        command = command,
        transport = transport,
        expectedOfficialVehicleKey = expectedOfficialVehicleKey,
        currentOfficialVehicleKey = cloudService.currentState.selectedVehicle?.key,
        baseline = baseline,
        current = vehicleStateSnapshot(),
        mqttAcked = mqttAcked,
      )
    }

    // Phase 1 — official behavior: wait for the MQTT status push, zero HTTP.
    // `statusPayloadEvents` fires after the push applied ACC/defence to the
    // cloud state and settled the pending-command bookkeeping, so the
    // re-check above always sees the pushed snapshot. The `!== lastPush`
    // guard consumes at most one replay-cached push (closing the
    // check-then-subscribe gap) without hot-spinning on the cached instance:
    // every live push is a fresh payload from tryParse.
    val pushDeadline = startedAt + CONTROL_CONFIRM_PUSH_WINDOW_MS
    var lastPush: OfficialMqttStatusPayload? = null
    while (true) {
      if (mqttService.pendingCommandError != null) return false
      if (confirmedNow(mqttAckedNow())) return true
      val waitMs = pushDeadline - SystemClock.elapsedRealtime()
      if (waitMs <= 0) break
      val push = withTimeoutOrNull(waitMs) {
        mqttService.statusPayloadEvents.first { it !== lastPush }
      }
      if (push == null) break
      lastPush = push
    }

    // Phase 2 — bounded fallback the official app lacks: one lightweight
    // carStatus-only refresh per retry (no battery/BMS/location cascade).
    while (true) {
      if (mqttService.pendingCommandError != null) return false
      if (SystemClock.elapsedRealtime() - startedAt > CONTROL_CONFIRM_TIMEOUT_MS) return false
      refreshStateForConfirmation()
      if (mqttService.pendingCommandError != null) return false
      if (confirmedNow(mqttAckedNow())) return true
      // Sleep only the remaining budget so busy never outlives the window.
      val remainingMs = CONTROL_CONFIRM_TIMEOUT_MS - (SystemClock.elapsedRealtime() - startedAt)
      if (remainingMs <= 0) return false
      delay(minOf(CONTROL_CONFIRM_FALLBACK_RETRY_MS, remainingMs))
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
    viewModel.markCommandIssued(now)
    viewModel.setBusy(true, cmd)
    val vehicleAtSend = cloudService.currentState.selectedVehicle
    val vehicleKeyAtSend = vehicleAtSend?.key
    val baseline = vehicleStateSnapshot()
    val activityId = commandLog.start(cmd, "${cmd.label}中…", "指令已发送，等待回执")
    viewModel.bumpCommandVersion()
    // Built once per command instead of per recomposition of this screen.
    val strSuccessTitles = mapOf(
      CommandCode.POWER_ON to strSuccessOn,
      CommandCode.POWER_OFF to strSuccessOff,
      CommandCode.LOCK to strSuccessLock,
      CommandCode.UNLOCK to strSuccessUnlock,
      CommandCode.FIND to strSuccessFind,
      CommandCode.OPEN_SEAT to strSuccessSeat,
    )
    val strSuccessSubtitles = mapOf(
      CommandCode.POWER_ON to strSubtitleOn,
      CommandCode.POWER_OFF to strSubtitleOff,
      CommandCode.LOCK to strSubtitleLock,
      CommandCode.UNLOCK to strSubtitleUnlock,
      CommandCode.FIND to strSubtitleFind,
      CommandCode.OPEN_SEAT to strSubtitleSeat,
    )
    val strUnconfirmedTitles = mapOf(
      CommandCode.POWER_ON to strUnconfirmedOn,
      CommandCode.POWER_OFF to strUnconfirmedOff,
      CommandCode.LOCK to strUnconfirmedLock,
      CommandCode.UNLOCK to strUnconfirmedUnlock,
    )
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
        viewModel.setBusy(false)
        viewModel.bumpCommandVersion()
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

  val scrollState = rememberScrollState()
  val density = LocalDensity.current
  val collapseRangePx = with(density) {
    (CyberHeaderExpandedHeight - CyberHeaderCollapsedHeight).toPx()
  }
  val collapseState = remember { CollapsingHeaderState(collapseRangePx) }
  SideEffect { collapseState.updateRange(collapseRangePx) }
  val headerScrollableState = rememberScrollableState { delta ->
    collapseState.consumeScrollableDelta(delta, scrollState::dispatchRawDelta)
  }
  val configuration = LocalConfiguration.current
  val viewportKey = configuration.orientation to configuration.screenWidthDp

  val latestSendCommand = rememberUpdatedState { cmd: CommandCode -> sendCommand(cmd) }
  val latestSendPowerToggle = rememberUpdatedState { sendPowerToggle() }
  val latestSendArmToggle = rememberUpdatedState { sendArmToggle() }
  val latestOnNavigate = rememberUpdatedState(onNavigate)
  val latestBusy = rememberUpdatedState(busy)
  val latestVehicleCount = rememberUpdatedState(cloudState.vehicles.size)
  val latestOpenVehicleHeader = rememberUpdatedState {
    when {
      latestBusy.value -> {
        scope.launch { AppSnack.error(snackbarHostState, strBusyHint) }
      }
      latestVehicleCount.value > 1 -> viewModel.setShowVehicleSwitchSheet(true)
      else -> latestOnNavigate.value(Routes.OFFICIAL_CLOUD)
    }
    Unit
  }
  val latestEnsureNearFieldLink = rememberUpdatedState { auto: Boolean ->
    scope.launch { ensureNearFieldLink(auto) }
    Unit
  }
  val onTitleTap = remember { { latestOpenVehicleHeader.value() } }
  val onBatteryTap = remember { { latestOnNavigate.value(Routes.batteryDetails("current")) } }
  val onBleChipTap = remember { { latestEnsureNearFieldLink.value(false) } }
  val onMessages = remember { { latestOnNavigate.value(Routes.vehicleMessage("current")) } }
  val onChannelTap = remember { { viewModel.setShowChannelSheet(true) } }
  val onFind = remember { { latestSendCommand.value(CommandCode.FIND) } }
  val onPowerToggle = remember {
    val action: suspend () -> Unit = { latestSendPowerToggle.value() }
    action
  }
  val onArmToggle = remember { { latestSendArmToggle.value() } }
  val onSettings = remember { { latestOnNavigate.value(Routes.vehicleSettings("current")) } }
  val onSeat = remember { { latestSendCommand.value(CommandCode.OPEN_SEAT) } }
  val onNfc = remember { { latestOnNavigate.value(Routes.OFFICIAL_REPLICA) } }
  val onMapTap = remember { { latestOnNavigate.value(Routes.location("current")) } }
  val onRideStatsTap = remember { { latestOnNavigate.value(Routes.rideStats("current")) } }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = CyberHomeColors.pageBg,
    contentWindowInsets = WindowInsets.statusBars,
    snackbarHost = { AppSnackbarHost(snackbarHostState) },
  ) { padding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .clipToBounds(),
    ) {
      key(viewportKey) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(top = CyberHeaderCollapsedHeight)
            .offset {
              IntOffset(0, (collapseState.rangePx - collapseState.offsetPx).roundToInt())
            }
            .nestedScroll(collapseState.listConnection)
            .verticalScroll(scrollState),
        ) {
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
            onFind = onFind,
            onPowerToggle = onPowerToggle,
            onArmToggle = onArmToggle,
            onSettings = onSettings,
            onSeat = onSeat,
            onNfc = onNfc,
          )
          Spacer(Modifier.height(32.dp))
          CyberMapStatsRow(
            location = location,
            address = locationTitle(location),
            todayKm = todayRideLabel(cloudState, distanceUnit),
            totalKm = totalMileageLabel(cloudVehicle, distanceUnit),
            lastDistance = lastRideVisuals.first,
            lastDuration = lastRideVisuals.second,
            onMapTap = onMapTap,
            onRideStatsTap = onRideStatsTap,
          )
          if (commandActivities.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            CyberRecentCommands(commands = commandActivities)
          }
          Spacer(Modifier.height(24.dp))
        }
      }
        CyberVehicleHeader(
          modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .scrollable(
              state = headerScrollableState,
              orientation = Orientation.Vertical,
            ),
          collapseState = collapseState,
          vehicleName = cloudVehicle?.displayName ?: vehicleStore.defaultVehicle?.displayName ?: stringResource(R.string.control_my_vehicle),
          rangeText = rangeLabel(battery, distanceUnit),
          carPhoto = cloudVehicle?.carPhoto ?: "",
          batteryPercent = percent,
          batteryKnown = battery.percent != null,
          online = cloudVehicle?.online ?: false,
          bluetoothConnected = connectionManager.isProtocolLoggedIn,
          isLocked = isArmed ?: true,
          powered = isPowerOn,
          bleChip = bleChipState,
          channelStatus = controlChannelStatus,
          onTitleTap = onTitleTap,
          onBatteryTap = onBatteryTap,
          onBleChipTap = onBleChipTap,
          onMessages = onMessages,
          onChannelTap = onChannelTap,
        )
      // Gate overlay (banner / loading skeleton) above the stable list.
      CyberControlGateOverlay(
        gateKind = gateKind,
        error = cloudState.error,
        onRetry = { handleRefresh() },
        onLogin = { onNavigate(Routes.LOGIN) },
        onAddVehicle = { onNavigate(Routes.ADD_VEHICLE) },
      )
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
          viewModel.setShowVehicleSwitchSheet(false)
          true
        } catch (e: Exception) {
          scope.launch { AppSnack.error(snackbarHostState, OfficialCloudRedactor.errorMessage(e)) }
          false
        }
      },
      onDismiss = { viewModel.setShowVehicleSwitchSheet(false) },
    )
  }


  // Channel selection sheet (Dart CyberChannelStrip bottom sheet).
  if (showChannelSheet) {
    ControlChannelSheet(
      currentChannel = controlChannel,
      channelStatus = controlChannelStatus,
      busy = busy,
      onSelect = { channel ->
        viewModel.setControlChannel(channel)
        viewModel.setShowChannelSheet(false)
        if (channel == OfficialControlChannel.BLE) {
          // Silent BLE path: try linking the official vehicle now.
          scope.launch { ensureNearFieldLink(auto = true) }
        } else if (channel == OfficialControlChannel.OFFICIAL_CLOUD && cloudState.signedIn) {
          scope.launch { mqttService.preconnectForCloud(cloudService) }
        }
      },
      onDismiss = { viewModel.setShowChannelSheet(false) },
      onOpenInduction = {
        onNavigate(Routes.inductionSettings(cloudService.currentState.selectedVehicle?.key ?: "current"))
      },
      onBusyError = {
        scope.launch { AppSnack.error(snackbarHostState, strBusyHint) }
      },
    )
  }
}

/**
 * Narrow projection of [OfficialCloudState] for the control home — only the
 * fields this screen reads. Collecting the raw state made every refresh field
 * (messages, travel, batteryInfoLoading, vehicleLocationLoading…) restart the
 * whole screen; [kotlinx.coroutines.flow.distinctUntilChanged] additionally
 * collapses emissions where none of the read fields changed.
 */
@Immutable
internal data class CloudScreenState(
  val signedIn: Boolean,
  val selectedVehicle: OfficialVehicle?,
  val selectedVehicleKey: String?,
  val vehicles: List<OfficialVehicle>,
  val batteryInfo: OfficialBatteryInfo?,
  val vehicleLocation: OfficialVehicleLocation?,
  val localVehicleLinks: Map<String, String>,
  val travelDays: List<OfficialTravelDay>,
  val todayRideMileage: String,
  val loading: Boolean,
  val error: String?,
) {
  /** Same contract as [OfficialCloudState.asControlCloudState], backed by the
   *  projection's own [localVehicleLinks] so no full-state reference is kept. */
  fun asControlCloudState(): ControlCloudState = object : ControlCloudState {
    override val signedIn: Boolean get() = this@CloudScreenState.signedIn
    override val selectedVehicle: OfficialVehicle? get() = this@CloudScreenState.selectedVehicle
    override fun linkedLocalVehicleId(officialVehicleKey: String): String? =
      OfficialCloudVehicleLinks.normalize(localVehicleLinks)[officialVehicleKey.trim()]
  }

  companion object {
    fun from(state: OfficialCloudState): CloudScreenState = CloudScreenState(
      signedIn = state.signedIn,
      selectedVehicle = state.selectedVehicle,
      selectedVehicleKey = state.selectedVehicle?.key,
      vehicles = state.vehicles,
      batteryInfo = state.batteryInfo,
      vehicleLocation = state.vehicleLocation,
      localVehicleLinks = state.localVehicleLinks,
      travelDays = state.travelDays,
      todayRideMileage = state.todayRideMileage,
      loading = state.loading,
      error = state.error,
    )
  }
}
