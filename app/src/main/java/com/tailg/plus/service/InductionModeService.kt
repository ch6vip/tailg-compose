/**
 * Port of `lib/services/induction_mode_service.dart` (tailg-ble-app) →
 * package `com.tailg.plus.service`.
 *
 * Unified induction / proximity unlock facade. Mirrors the official three
 * paths (decompiled `ControlFragment` / `BleConnectService` /
 * `TLinkBleManager`):
 * - QGJ: `setProximityStatus` + `setHidStatus` + system bond
 * - TLink: `openMode` / `closeMode` / `setModeDistance` + system bond
 * - RSSI: phone `readRemoteRssi` → auto lock/unlock (KKS / legacy)
 *
 * Dart → Kotlin mapping:
 * - `Stream<InductionModeSnapshot>` (broadcast) → [snapshotFlow] (StateFlow);
 *   the `snapshot` getter is kept.
 * - `Timer.periodic` RSSI loop → a coroutine Job with `delay(rssiPollInterval)`;
 *   ticks are sequential (Dart could overlap ticks when a read outlived the
 *   200 ms poll), so overlapping `readRemoteRssi` calls cannot happen here.
 * - `WidgetsBindingObserver` lifecycle hooks are caller-driven: the app must
 *   forward resume / inactive → [setAppForeground](true) and stopped →
 *   [setAppForeground](false) (Flutter maps resumed/inactive to foreground).
 * - `SharedPreferences` with dynamic per-vehicle keys → [InductionPrefs]
 *   (DataStore-backed); key strings unchanged.
 * - `_EnableResult` is a private [EnableResult] data class.
 */
package com.tailg.plus.service

import android.content.Context
import com.tailg.plus.data.ble.CommandCode
import com.tailg.plus.data.ble.QgjCommandIds
import com.tailg.plus.data.ble.QgjHidModes
import com.tailg.plus.data.ble.TLINK_HID_OPEN_AFTER_BOND_PLAIN
import com.tailg.plus.data.ble.buildQgjHidPayload
import com.tailg.plus.data.ble.buildQgjProximityDistancePayload
import com.tailg.plus.data.ble.buildQgjProximityStatusPayload
import com.tailg.plus.data.ble.classifyDistance
import com.tailg.plus.data.ble.confirmedRssiState
import com.tailg.plus.data.ble.estimateDistanceFromRssiSamples
import com.tailg.plus.data.ble.parseQgjProximityDistance
import com.tailg.plus.data.ble.parseQgjProximityEnabled
import com.tailg.plus.data.ble.pendingRssiSteps
import com.tailg.plus.data.ble.rssiPollInterval
import com.tailg.plus.data.ble.rssiSampleWindow
import com.tailg.plus.data.ble.RssiProximityAction
import com.tailg.plus.data.ble.RssiProximityStep
import com.tailg.plus.data.ble.RssiTaskState
import com.tailg.plus.data.ble.shouldFireRssiAction
import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.data.ble.platform.ProtocolType
import com.tailg.plus.data.cloud.OfficialCloudRedactor
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.domain.control.OfficialControlRoute
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService
import java.util.ArrayDeque
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class InductionModeService(
  private val cm: ConnectionManager,
  private val context: Context,
  manual: ManualModeService? = null,
  log: LogService? = null,
  foregroundService: InductionForegroundServiceBridge? = null,
  cloud: OfficialCloudService? = null,
  prefs: InductionPrefs? = null,
  externalScope: CoroutineScope? = null,
) {
  companion object {
    /** Dart `_prefEnabledPrefix`. */
    private const val PREF_ENABLED_PREFIX = "induction_enabled_"

    /** Dart `_prefDistancePrefix`. */
    private const val PREF_DISTANCE_PREFIX = "induction_distance_"

    /** Dart `defaultDistanceLevel` = 5. */
    const val DEFAULT_DISTANCE_LEVEL = 5

    /** Dart `maxDistanceLevel` = 30. */
    const val MAX_DISTANCE_LEVEL = 30

    /** Port of Dart `InductionModeService.stackForModelType`. */
    fun stackForModelType(modelType: Int?): InductionStack {
      val type = modelType ?: -1
      if (OfficialControlRoute.qgjModelTypes.contains(type)) {
        return InductionStack.QGJ
      }
      // TLink openMode models (ControlFragment iv_mode cases).
      if (type == 3 ||
        OfficialControlRoute.c39ModelTypes.contains(type) ||
        OfficialControlRoute.gpsComboModelTypes.contains(type) ||
        OfficialControlRoute.unsupportedControlModelTypes.contains(type)
      ) {
        return InductionStack.TLINK
      }
      // KKS uses phone RSSI / cloud blueOn; local BLE still benefits from RSSI.
      if (type == 1) return InductionStack.RSSI
      // YJ remote-only — no local induction over BLE in our route table.
      return InductionStack.NONE
    }
  }

  private val ownsScope = externalScope == null
  private val scope = externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private val _prefs: InductionPrefs = prefs ?: DataStoreInductionPrefs(context)
  private val _manual: ManualModeService = manual ?: ManualModeService(_prefs)
  private val _log: LogService = log ?: LogService()
  private val _foregroundService: InductionForegroundServiceBridge =
    foregroundService ?: AndroidInductionForegroundServiceBridge(context)

  /**
   * Dart always constructed the app-wide `OfficialCloudService()` singleton.
   * Here the caller passes the shared instance; when absent the KKS cloud HID
   * sync branch reports a failure instead of enabling half-heartedly.
   */
  private val _cloud: OfficialCloudService? = cloud

  private val _snapshotState = MutableStateFlow(InductionModeSnapshot.EMPTY)
  private var _snapshot: InductionModeSnapshot = InductionModeSnapshot.EMPTY
  private var _connJob: Job? = null
  private var _appInForeground = true

  // RSSI path runtime. [rssiLock] serializes loop start/stop so concurrent
  // setAppForeground/refresh/onConnectionChanged cannot double-launch the
  // polling job or race its teardown.
  private val rssiLock = Any()
  private var _rssiJob: Job? = null
  private val _rssiSamples = ArrayDeque<Int>()
  private var _rssiTaskState = RssiTaskState.idle
  private var _rssiFiring = false
  private var _rssiCalibration = RssiCalibration()
  private var _boundModelType: Int? = null
  private var _boundCarId: String? = null

  /** Dart `snapshotStream` → StateFlow (UI observes this). */
  val snapshotFlow: StateFlow<InductionModeSnapshot> = _snapshotState.asStateFlow()

  /** Dart `snapshot` getter. */
  val snapshot: InductionModeSnapshot get() = _snapshot

  /** Port of Dart `bindVehicle`. */
  fun bindVehicle(modelType: Int?, carId: String?, vehicleRaw: Map<String, Any?>?) {
    val changed = _boundModelType != modelType || _boundCarId != carId
    _boundModelType = modelType
    _boundCarId = carId
    _rssiCalibration = RssiCalibration.fromMap(vehicleRaw)
    ensureConnectionCollector()
    if (changed) {
      stopRssiLoop()
      publish(
        InductionModeSnapshot(
          stack = stackForModelType(modelType),
          enabled = null,
          distance = null,
          busy = false,
          bleReady = bleReadyFor(stackForModelType(modelType)),
        ),
      )
      scope.launch { refresh(force = true) }
    } else {
      scope.launch { onConnectionChanged() }
    }
  }

  /**
   * Android RSSI induction continues under a visible foreground service.
   *
   * Port of Dart `setAppForeground`; the Flutter `WidgetsBindingObserver`
   * wiring is caller-driven here. Forward resume / inactive → [foreground]
   * true, stopped → [foreground] false (Flutter maps resumed/inactive to
   * foreground and everything else to background).
   */
  fun setAppForeground(foreground: Boolean) {
    if (_appInForeground == foreground) return
    _appInForeground = foreground
    if (!foreground) {
      val stack = resolveStack(_boundModelType)
      if (!_foregroundService.supportsBackgroundRssi ||
        stack != InductionStack.RSSI ||
        _snapshot.enabled != true ||
        !bleReadyFor(stack)
      ) {
        stopRssiLoop()
      }
      return
    }
    val stack = resolveStack(_boundModelType)
    if (stack == InductionStack.RSSI &&
      _snapshot.enabled == true &&
      bleReadyFor(stack)
    ) {
      startRssiLoop()
    }
  }

  /** Infer stack from live BLE protocol when modelType is unknown / none. */
  fun resolveStack(modelType: Int?): InductionStack {
    val byModel = stackForModelType(modelType)
    if (byModel != InductionStack.NONE) return byModel
    return when (cm.protocol) {
      ProtocolType.QGJ -> InductionStack.QGJ
      ProtocolType.TLINK -> InductionStack.TLINK
      ProtocolType.KKS -> InductionStack.RSSI
      ProtocolType.UNKNOWN -> InductionStack.NONE
    }
  }

  private fun ensureConnectionCollector() {
    if (_connJob != null) return
    _connJob = scope.launch {
      // StateFlow replays the current value to each new collector; the Dart
      // broadcast stream only emitted on actual changes, hence drop(1).
      cm.stateFlow.drop(1).collect { onConnectionChanged() }
    }
  }

  private fun bleReadyFor(stack: InductionStack): Boolean {
    if (!cm.isProtocolLoggedIn) return false
    return when (stack) {
      InductionStack.QGJ -> cm.protocol == ProtocolType.QGJ
      InductionStack.TLINK -> cm.protocol == ProtocolType.TLINK
      InductionStack.RSSI ->
        cm.protocol == ProtocolType.KKS || cm.protocol == ProtocolType.TLINK
      InductionStack.NONE -> false
    }
  }

  private suspend fun onConnectionChanged() {
    val stack = resolveStack(_boundModelType)
    val ready = bleReadyFor(stack)
    if (!ready) {
      stopRssiLoop()
      publish(
        _snapshot.copyWith(
          stack = stack,
          bleReady = false,
          enabled = if (stack == InductionStack.RSSI) _snapshot.enabled else null,
        ),
      )
      return
    }
    publish(_snapshot.copyWith(stack = stack, bleReady = true))
    refresh()
  }

  /** Port of Dart `refresh`. */
  suspend fun refresh(force: Boolean = false) {
    val stack = resolveStack(_boundModelType)
    val ready = bleReadyFor(stack)
    if (!ready) {
      publish(
        InductionModeSnapshot(
          stack = stack,
          enabled = if (stack == InductionStack.RSSI) loadEnabledPref() else null,
          distance = if (stack == InductionStack.RSSI) loadDistancePref() else null,
          busy = false,
          bleReady = false,
        ),
      )
      return
    }

    if (_snapshot.busy && !force) return
    publish(
      _snapshot.copyWith(stack = stack, bleReady = true, busy = true, clearError = true),
    )

    try {
      when (stack) {
        InductionStack.QGJ -> refreshQgj()
        InductionStack.TLINK -> refreshTlink()
        InductionStack.RSSI -> refreshRssi()
        InductionStack.NONE -> publish(InductionModeSnapshot.EMPTY)
      }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      _log.operation("读取感应状态失败", detail = e.toString(), level = LogLevel.DEBUG)
      publish(
        _snapshot.copyWith(busy = false, lastError = e.toString(), bleReady = true),
      )
    }
  }

  private suspend fun refreshQgj() {
    val status = cm.sendQgjCommand(QgjCommandIds.proximityStatusGet)
    val distance = cm.sendQgjCommand(QgjCommandIds.proximityDistanceGet)
    val enabled = if (status != null && status.success) {
      parseQgjProximityEnabled(status.payload.map { it.toInt() and 0xFF })
    } else {
      null
    }
    val level = if (distance != null && distance.success) {
      parseQgjProximityDistance(distance.payload.map { it.toInt() and 0xFF })
    } else {
      null
    }
    publish(
      InductionModeSnapshot(
        stack = InductionStack.QGJ,
        enabled = enabled,
        distance = level?.coerceIn(0, MAX_DISTANCE_LEVEL),
        busy = false,
        bleReady = true,
      ),
    )
  }

  private suspend fun refreshTlink() {
    val status = cm.checkTlinkInduction()
    if (status == null) {
      publish(
        _snapshot.copyWith(
          stack = InductionStack.TLINK,
          busy = false,
          bleReady = true,
          lastError = "读取感应状态超时，请重试",
        ),
      )
      return
    }
    publish(
      InductionModeSnapshot(
        stack = InductionStack.TLINK,
        enabled = status.enabled,
        distance = status.distance,
        busy = false,
        bleReady = true,
      ),
    )
  }

  private suspend fun refreshRssi() {
    val enabled = loadEnabledPref()
    val distance = loadDistancePref()
    publish(
      InductionModeSnapshot(
        stack = InductionStack.RSSI,
        enabled = enabled,
        distance = distance,
        busy = false,
        bleReady = true,
      ),
    )
    if (enabled) startRssiLoop() else stopRssiLoop()
  }

  /**
   * Toggle induction. When [enabled] is true, clears manual mode first so the
   * home-page 感应|手动 switch cannot race with ManualModeService prefs.
   */
  suspend fun setEnabled(enabled: Boolean, clearManualMode: Boolean = true): Boolean {
    val stack = resolveStack(_boundModelType)
    val ready = bleReadyFor(stack)
    val canDisableDisconnectedRssi = !enabled && stack == InductionStack.RSSI
    if (stack == InductionStack.NONE || (!ready && !canDisableDisconnectedRssi)) {
      publish(_snapshot.copyWith(lastError = "请先连接车辆蓝牙并完成协议登录"))
      return false
    }

    if (enabled && clearManualMode && _manual.enabled) {
      _manual.setEnabled(false)
    }
    if (enabled && _manual.enabled) {
      publish(_snapshot.copyWith(lastError = "已开启手动模式，无法开关感应解锁"))
      return false
    }

    publish(
      _snapshot.copyWith(
        busy = true,
        clearError = true,
        stack = stack,
        bondIncomplete = false,
      ),
    )
    try {
      val result = when (stack) {
        InductionStack.QGJ -> setQgjEnabled(enabled)
        InductionStack.TLINK -> setTlinkEnabled(enabled)
        InductionStack.RSSI -> setRssiEnabled(enabled)
        InductionStack.NONE -> EnableResult(ok = false)
      }
      if (!result.ok) {
        publish(
          _snapshot.copyWith(
            busy = false,
            lastError = result.message ?: (if (enabled) "开启感应解锁失败" else "关闭感应解锁失败"),
          ),
        )
        return false
      }
      saveEnabledPref(enabled)
      publish(
        InductionModeSnapshot(
          stack = stack,
          enabled = enabled,
          distance = _snapshot.distance,
          busy = false,
          bleReady = ready,
          bondIncomplete = result.bondIncomplete,
          lastError = result.warning ?: if (result.bondIncomplete) {
            "感应已开启，但系统蓝牙配对未完成。请在系统弹窗中允许配对，否则靠近解锁可能无效"
          } else {
            null
          },
        ),
      )
      return true
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      publish(_snapshot.copyWith(busy = false, lastError = e.toString()))
      return false
    }
  }

  private suspend fun setQgjEnabled(enabled: Boolean): EnableResult {
    if (enabled) {
      val proximityResponse = cm.sendQgjCommand(
        QgjCommandIds.proximityStatusSet,
        buildQgjProximityStatusPayload(true),
      )
      if (proximityResponse?.success != true) {
        return EnableResult(ok = false, message = "车辆未确认开启感应")
      }
      val hidResponse = cm.sendQgjCommand(
        QgjCommandIds.hidStatusSet,
        buildQgjHidPayload(QgjHidModes.open),
      )
      if (hidResponse?.success != true) {
        cm.sendQgjCommand(QgjCommandIds.proximityStatusSet, buildQgjProximityStatusPayload(false))
        return EnableResult(ok = false, message = "车辆未确认开启蓝牙感应配对")
      }
      val bonded = cm.createBond(quiet = true)
      return EnableResult(ok = true, bondIncomplete = !bonded)
    }
    val proximityResponse = cm.sendQgjCommand(
      QgjCommandIds.proximityStatusSet,
      buildQgjProximityStatusPayload(false),
    )
    if (proximityResponse?.success != true) {
      return EnableResult(ok = false, message = "车辆未确认关闭感应")
    }
    val hidResponse = cm.sendQgjCommand(
      QgjCommandIds.hidStatusSet,
      buildQgjHidPayload(QgjHidModes.close),
    )
    if (hidResponse?.success != true) {
      cm.sendQgjCommand(QgjCommandIds.proximityStatusSet, buildQgjProximityStatusPayload(false))
      return EnableResult(ok = false, message = "车辆未确认关闭蓝牙感应配对")
    }
    val bondRemoved = cm.removeBond(quiet = true)
    return EnableResult(
      ok = true,
      warning = if (bondRemoved) null else "车辆感应已关闭，但系统蓝牙配对未能移除",
    )
  }

  private suspend fun setTlinkEnabled(enabled: Boolean): EnableResult {
    if (enabled) {
      val ok = cm.openTlinkInduction()
      if (!ok) {
        return EnableResult(ok = false, message = "车辆未确认开启感应")
      }
      val bonded = cm.createBond(quiet = true)
      if (bonded) {
        val hidOpened = cm.writeStandardHex(TLINK_HID_OPEN_AFTER_BOND_PLAIN)
        if (!hidOpened) {
          cm.closeTlinkInduction()
          cm.removeBond(quiet = true)
          return EnableResult(ok = false, message = "车辆感应已开启，但蓝牙感应配对写入失败")
        }
      }
      return EnableResult(ok = true, bondIncomplete = !bonded)
    }
    val ok = cm.closeTlinkInduction()
    if (!ok) {
      return EnableResult(ok = false, message = "车辆未确认关闭感应")
    }
    val bondRemoved = cm.removeBond(quiet = true)
    return EnableResult(
      ok = true,
      warning = if (bondRemoved) null else "车辆感应已关闭，但系统蓝牙配对未能移除",
    )
  }

  private suspend fun setRssiEnabled(enabled: Boolean): EnableResult {
    if (enabled) {
      if (_boundModelType == 1) {
        val cloud = _cloud
        if (cloud == null) {
          return EnableResult(ok = false, message = "云端服务未初始化，无法开启感应解锁")
        }
        try {
          cloud.setKksHidEnabled(true)
        } catch (e: Exception) {
          if (e is CancellationException) throw e
          return EnableResult(ok = false, message = OfficialCloudRedactor.errorMessage(e))
        }
      }
      startRssiLoop()
    } else {
      stopRssiLoop()
      _rssiTaskState = RssiTaskState.idle
      if (_boundModelType == 1) {
        val cloud = _cloud
        if (cloud != null) {
          try {
            cloud.setKksHidEnabled(false)
          } catch (e: Exception) {
            if (e is CancellationException) throw e
            return EnableResult(
              ok = true,
              warning = "本机感应已停止，但车辆云端设置未关闭：${OfficialCloudRedactor.errorMessage(e)}",
            )
          }
        }
      }
    }
    return EnableResult(ok = true)
  }

  /** Port of Dart `setDistance`. */
  suspend fun setDistance(level: Int): Boolean {
    val stack = resolveStack(_boundModelType)
    val value = level.coerceIn(0, MAX_DISTANCE_LEVEL)
    if (!bleReadyFor(stack)) {
      publish(_snapshot.copyWith(lastError = "请先连接车辆蓝牙并完成协议登录"))
      return false
    }
    publish(_snapshot.copyWith(busy = true, clearError = true))
    try {
      val ok = when (stack) {
        InductionStack.QGJ -> setQgjDistance(value)
        InductionStack.TLINK -> cm.setTlinkInductionDistance(value)
        InductionStack.RSSI -> true
        InductionStack.NONE -> false
      }
      if (!ok) {
        publish(_snapshot.copyWith(busy = false, lastError = "写入感应距离失败"))
        return false
      }
      saveDistancePref(value)
      publish(_snapshot.copyWith(distance = value, busy = false, clearError = true))
      return true
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      publish(_snapshot.copyWith(busy = false, lastError = e.toString()))
      return false
    }
  }

  private suspend fun setQgjDistance(value: Int): Boolean {
    val response = cm.sendQgjCommand(
      QgjCommandIds.proximityDistanceSet,
      buildQgjProximityDistancePayload(value),
    )
    return response?.success == true
  }

  // ---------------------------------------------------------------------------
  // RSSI path
  // ---------------------------------------------------------------------------

  private fun startRssiLoop() {
    synchronized(rssiLock) {
      if (_rssiJob?.isActive == true) return
      _rssiSamples.clear()
      _rssiTaskState = RssiTaskState.idle
      _rssiJob = scope.launch {
        // Start the foreground service FIRST: if it cannot start (Android 12+
        // bans background FGS starts — a BLE reconnect restarting this loop
        // while backgrounded used to leave the poll running unprotected, with
        // the process killable at any moment), cancel the poll instead.
        if (!startRssiForegroundService()) {
          _rssiJob?.cancel()
          return@launch
        }
        // stopRssiLoop may have cancelled us while the FGS start was in
        // flight — sweep the notification we just raised before exiting.
        if (!isActive) {
          runCatching { _foregroundService.stopNow() }
            .onFailure {
              _log.operation("RSSI 前台服务停止失败", detail = it.toString(), level = LogLevel.WARNING)
            }
          return@launch
        }
        _log.operation("RSSI 感应轮询已启动", level = LogLevel.INFO)
        while (isActive) {
          delay(rssiPollInterval)
          rssiTick()
        }
      }
    }
  }

  /** Start the RSSI foreground service; returns whether it is running. */
  private suspend fun startRssiForegroundService(): Boolean {
    val started = _foregroundService.start(vehicleLabel = _boundCarId)
    if (!started) {
      _log.operation("RSSI 前台服务启动失败", level = LogLevel.WARNING)
    }
    return started
  }

  private fun stopRssiLoop() {
    val job: Job?
    synchronized(rssiLock) {
      job = _rssiJob
      _rssiJob = null
      _rssiSamples.clear()
      _rssiFiring = false
    }
    job?.cancel()
    // Stop synchronously: a cancelled/canceling scope would silently drop a
    // scope.launch'ed stop and leave the foreground notification behind.
    runCatching { _foregroundService.stopNow() }
      .onFailure { _log.operation("RSSI 前台服务停止失败", detail = it.toString(), level = LogLevel.WARNING) }
  }

  private suspend fun rssiTick() {
    if (_manual.enabled) return
    if (!cm.isProtocolLoggedIn) return
    if (_rssiFiring) return
    val rssi = cm.readRemoteRssi() ?: return
    _rssiSamples.addLast(rssi)
    while (_rssiSamples.size > rssiSampleWindow) {
      _rssiSamples.removeFirst()
    }
    if (_rssiSamples.size < rssiSampleWindow) return

    val distance = estimateDistanceFromRssiSamples(
      _rssiSamples.toList(),
      rssiA = _rssiCalibration.rssiA,
      rssiFactor = _rssiCalibration.rssiFactor,
    )
    val action = classifyDistance(
      distance,
      minDistanceM = _rssiCalibration.minDistanceM,
      maxDistanceM = _rssiCalibration.maxDistanceM,
    )
    if (!shouldFireRssiAction(action, _rssiTaskState)) {
      _rssiSamples.removeFirst()
      return
    }

    _rssiFiring = true
    try {
      if (action == RssiProximityAction.approachUnlock) {
        _log.operation("RSSI 感应 → 解防", detail = formatDistance(distance), level = LogLevel.INFO)
      } else if (action == RssiProximityAction.leaveLock) {
        _log.operation("RSSI 感应 → 设防", detail = formatDistance(distance), level = LogLevel.INFO)
      }
      val steps = pendingRssiSteps(action, _rssiTaskState)
      for (step in steps) {
        val command = when (step) {
          RssiProximityStep.unlock -> CommandCode.unlock
          RssiProximityStep.powerOn -> CommandCode.powerOn
          RssiProximityStep.powerOff -> CommandCode.powerOff
          RssiProximityStep.lock -> CommandCode.lock
        }
        val ok = cm.sendCommand(command)
        _rssiTaskState = confirmedRssiState(_rssiTaskState, step, success = ok)
        if (!ok) {
          _log.operation("RSSI 感应步骤未确认", detail = command.label, level = LogLevel.WARNING)
          break
        }
      }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      _log.operation("RSSI 感应指令失败", detail = e.toString(), level = LogLevel.WARNING)
    } finally {
      _rssiFiring = false
      _rssiSamples.clear()
    }
  }

  /** Dart `distance.toStringAsFixed(2)` — '.' decimal separator regardless of locale. */
  private fun formatDistance(distance: Double): String =
    String.format(Locale.US, "d=%.2fm", distance)

  // ---------------------------------------------------------------------------
  // Prefs
  // ---------------------------------------------------------------------------

  private val enabledKey: String
    get() = "$PREF_ENABLED_PREFIX${_boundCarId ?: _boundModelType ?: "default"}"

  private val distanceKey: String
    get() = "$PREF_DISTANCE_PREFIX${_boundCarId ?: _boundModelType ?: "default"}"

  private suspend fun loadEnabledPref(): Boolean = _prefs.loadBoolean(enabledKey, false)

  private suspend fun saveEnabledPref(value: Boolean) = _prefs.saveBoolean(enabledKey, value)

  private suspend fun loadDistancePref(): Int = _prefs.loadInt(distanceKey, DEFAULT_DISTANCE_LEVEL)

  private suspend fun saveDistancePref(value: Int) = _prefs.saveInt(distanceKey, value)

  private fun publish(next: InductionModeSnapshot) {
    _snapshot = next
    _snapshotState.value = next
  }

  /** Port of Dart `resetForTest`. */
  fun resetForTest() {
    stopRssiLoop()
    _rssiTaskState = RssiTaskState.idle
    _boundModelType = null
    _boundCarId = null
    _rssiCalibration = RssiCalibration()
    _appInForeground = true
    publish(InductionModeSnapshot.EMPTY)
  }

  /**
   * Port of Dart `dispose`. The RSSI-loop stop intent is fired best-effort:
   * with an owned scope the queued stop may be cancelled together with the
   * scope (the notification then dies with the process — `START_NOT_STICKY`).
   */
  fun dispose() {
    stopRssiLoop()
    _connJob?.cancel()
    _connJob = null
    if (ownsScope) scope.cancel()
  }

  /** Port of Dart `_EnableResult`. */
  private data class EnableResult(
    val ok: Boolean,
    val bondIncomplete: Boolean = false,
    val message: String? = null,
    val warning: String? = null,
  )
}
