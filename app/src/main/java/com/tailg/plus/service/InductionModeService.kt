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
import com.tailg.plus.data.model.OfficialVehicle
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

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
  private val _snapshot: InductionModeSnapshot get() = _snapshotState.value
  private val bindingLock = Any()
  private val operationMutex = Mutex()
  private var bindingGeneration = 0L
  private val activeOperations = mutableSetOf<Job>()
  private var _connJob: Job? = null
  private var _appInForeground = true

  // RSSI path runtime. [rssiLock] serializes loop start/stop so concurrent
  // setAppForeground/refresh/onConnectionChanged cannot double-launch the
  // polling job or race its teardown.
  private val rssiLock = Any()
  private var _rssiJob: Job? = null
  @Volatile private var _rssiCalibration = RssiCalibration()
  @Volatile private var _boundModelType: Int? = null
  @Volatile private var _boundCarId: String? = null
  @Volatile private var _boundIdentityMac = ""

  /** Dart `snapshotStream` → StateFlow (UI observes this). */
  val snapshotFlow: StateFlow<InductionModeSnapshot> = _snapshotState.asStateFlow()

  /** Dart `snapshot` getter. */
  val snapshot: InductionModeSnapshot get() = _snapshot

  /** Port of Dart `bindVehicle`. */
  fun bindVehicle(modelType: Int?, carId: String?, vehicleRaw: Map<String, Any?>?) {
    synchronized(bindingLock) {
      val identity = vehicleRaw?.let { OfficialVehicle.fromJson(it).bleIdentityMac }.orEmpty()
        .filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }.uppercase()
      val changed = _boundModelType != modelType || _boundCarId != carId || _boundIdentityMac != identity
      if (changed) {
        bindingGeneration++
        activeOperations.toList().forEach { it.cancel(CancellationException("感应车辆已切换")) }
      }
      _boundModelType = modelType
      _boundCarId = carId
      _boundIdentityMac = identity
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
    if (_boundIdentityMac.isNotEmpty()) {
      val connectedIdentity = cm.connectionContext?.targetMacCompact?.takeIf { it.isNotEmpty() }
        ?: runCatching { cm.device?.address?.replace(":", "")?.uppercase() }.getOrNull()
      if (connectedIdentity != _boundIdentityMac) return false
    }
    return when (stack) {
      InductionStack.QGJ -> cm.protocol == ProtocolType.QGJ
      InductionStack.TLINK -> cm.protocol == ProtocolType.TLINK
      InductionStack.RSSI ->
        cm.protocol == ProtocolType.KKS || cm.protocol == ProtocolType.TLINK
      InductionStack.NONE -> false
    }
  }

  private suspend fun onConnectionChanged() {
    refresh()
  }

  /** Port of Dart `refresh`. */
  suspend fun refresh(force: Boolean = false) = withVehicleOperation { refreshCurrent(force) }

  private suspend fun refreshCurrent(force: Boolean) {
    val stack = resolveStack(_boundModelType)
    val ready = bleReadyFor(stack)
    if (!ready) {
      updateRssiLoop(false)
      publishCurrent(
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
    publishCurrent(
      _snapshot.copyWith(stack = stack, bleReady = true, busy = true, clearError = true),
    )

    try {
      when (stack) {
        InductionStack.QGJ -> refreshQgj()
        InductionStack.TLINK -> refreshTlink()
        InductionStack.RSSI -> refreshRssi()
        InductionStack.NONE -> publishCurrent(InductionModeSnapshot.EMPTY)
      }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      _log.operation("读取感应状态失败", detail = e.toString(), level = LogLevel.DEBUG)
      publishCurrent(
        _snapshot.copyWith(busy = false, lastError = e.toString(), bleReady = true),
      )
    }
  }

  private suspend fun refreshQgj() {
    val status = vehicleIo { cm.sendQgjCommand(QgjCommandIds.proximityStatusGet) }
    val distance = vehicleIo { cm.sendQgjCommand(QgjCommandIds.proximityDistanceGet) }
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
    publishCurrent(
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
    val status = vehicleIo { cm.checkTlinkInduction() }
    if (status == null) {
      publishCurrent(
        _snapshot.copyWith(
          stack = InductionStack.TLINK,
          busy = false,
          bleReady = true,
          lastError = "读取感应状态超时，请重试",
        ),
      )
      return
    }
    publishCurrent(
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
    publishCurrent(
      InductionModeSnapshot(
        stack = InductionStack.RSSI,
        enabled = enabled,
        distance = distance,
        busy = false,
        bleReady = true,
      ),
    )
    if (enabled) updateRssiLoop(true) else updateRssiLoop(false)
  }

  /**
   * Toggle induction. When [enabled] is true, clears manual mode first so the
   * home-page 感应|手动 switch cannot race with ManualModeService prefs.
   */
  suspend fun setEnabled(enabled: Boolean, clearManualMode: Boolean = true): Boolean =
    withVehicleOperation { setEnabledCurrent(enabled, clearManualMode) }

  private suspend fun setEnabledCurrent(enabled: Boolean, clearManualMode: Boolean): Boolean {
    val stack = resolveStack(_boundModelType)
    val ready = bleReadyFor(stack)
    val canDisableDisconnectedRssi = !enabled && stack == InductionStack.RSSI
    if (stack == InductionStack.NONE || (!ready && !canDisableDisconnectedRssi)) {
      publishCurrent(_snapshot.copyWith(lastError = "请先连接车辆蓝牙并完成协议登录"))
      return false
    }

    if (enabled && clearManualMode && _manual.enabled) {
      _manual.setEnabled(false)
    }
    if (enabled && _manual.enabled) {
      publishCurrent(_snapshot.copyWith(lastError = "已开启手动模式，无法开关感应解锁"))
      return false
    }

    publishCurrent(
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
        publishCurrent(
          _snapshot.copyWith(
            busy = false,
            lastError = result.message ?: (if (enabled) "开启感应解锁失败" else "关闭感应解锁失败"),
          ),
        )
        return false
      }
      saveEnabledPref(enabled)
      if (stack == InductionStack.RSSI) updateRssiLoop(enabled)
      publishCurrent(
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
      publishCurrent(_snapshot.copyWith(busy = false, lastError = e.toString()))
      return false
    }
  }

  private suspend fun setQgjEnabled(enabled: Boolean): EnableResult {
    if (enabled) {
      val proximityResponse = vehicleIo { cm.sendQgjCommand(
        QgjCommandIds.proximityStatusSet,
        buildQgjProximityStatusPayload(true),
      ) }
      if (proximityResponse?.success != true) {
        return EnableResult(ok = false, message = "车辆未确认开启感应")
      }
      val hidResponse = vehicleIo { cm.sendQgjCommand(
        QgjCommandIds.hidStatusSet,
        buildQgjHidPayload(QgjHidModes.open),
      ) }
      if (hidResponse?.success != true) {
        vehicleIo { cm.sendQgjCommand(QgjCommandIds.proximityStatusSet, buildQgjProximityStatusPayload(false)) }
        return EnableResult(ok = false, message = "车辆未确认开启蓝牙感应配对")
      }
      val bonded = vehicleIo { cm.createBond(quiet = true) }
      return EnableResult(ok = true, bondIncomplete = !bonded)
    }
    val proximityResponse = vehicleIo { cm.sendQgjCommand(
      QgjCommandIds.proximityStatusSet,
      buildQgjProximityStatusPayload(false),
    ) }
    if (proximityResponse?.success != true) {
      return EnableResult(ok = false, message = "车辆未确认关闭感应")
    }
    val hidResponse = vehicleIo { cm.sendQgjCommand(
      QgjCommandIds.hidStatusSet,
      buildQgjHidPayload(QgjHidModes.close),
    ) }
    if (hidResponse?.success != true) {
      vehicleIo { cm.sendQgjCommand(QgjCommandIds.proximityStatusSet, buildQgjProximityStatusPayload(false)) }
      return EnableResult(ok = false, message = "车辆未确认关闭蓝牙感应配对")
    }
    val bondRemoved = vehicleIo { cm.removeBond(quiet = true) }
    return EnableResult(
      ok = true,
      warning = if (bondRemoved) null else "车辆感应已关闭，但系统蓝牙配对未能移除",
    )
  }

  private suspend fun setTlinkEnabled(enabled: Boolean): EnableResult {
    if (enabled) {
      val ok = vehicleIo { cm.openTlinkInduction() }
      if (!ok) {
        return EnableResult(ok = false, message = "车辆未确认开启感应")
      }
      val bonded = vehicleIo { cm.createBond(quiet = true) }
      if (bonded) {
        val hidOpened = vehicleIo { cm.writeStandardHex(TLINK_HID_OPEN_AFTER_BOND_PLAIN) }
        if (!hidOpened) {
          vehicleIo { cm.closeTlinkInduction() }
          vehicleIo { cm.removeBond(quiet = true) }
          return EnableResult(ok = false, message = "车辆感应已开启，但蓝牙感应配对写入失败")
        }
      }
      return EnableResult(ok = true, bondIncomplete = !bonded)
    }
    val ok = vehicleIo { cm.closeTlinkInduction() }
    if (!ok) {
      return EnableResult(ok = false, message = "车辆未确认关闭感应")
    }
    val bondRemoved = vehicleIo { cm.removeBond(quiet = true) }
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
          vehicleIo { cloud.setKksHidEnabled(true) }
        } catch (e: Exception) {
          if (e is CancellationException) throw e
          return EnableResult(ok = false, message = OfficialCloudRedactor.errorMessage(e))
        }
      }
    } else {
      // Turn the loop off only after the cloud side agreed: if the cloud call
      // fails the UI must roll back to "enabled" (the caller skips the pref
      // save + success publish on ok=false), otherwise the local loop is
      // stopped while the cloud still thinks induction is on — a restart on
      // the next screen entry would silently re-enable a cloud state the user
      // believes is off.
      if (_boundModelType == 1) {
        val cloud = _cloud
        if (cloud != null) {
          try {
            vehicleIo { cloud.setKksHidEnabled(false) }
          } catch (e: Exception) {
            if (e is CancellationException) throw e
            return EnableResult(
              ok = false,
              message = "关闭车辆云端感应失败：${OfficialCloudRedactor.errorMessage(e)}",
            )
          }
        }
      }
    }
    return EnableResult(ok = true)
  }

  /** Port of Dart `setDistance`. */
  suspend fun setDistance(level: Int): Boolean = withVehicleOperation { setDistanceCurrent(level) }

  private suspend fun setDistanceCurrent(level: Int): Boolean {
    val stack = resolveStack(_boundModelType)
    val value = level.coerceIn(0, MAX_DISTANCE_LEVEL)
    if (!bleReadyFor(stack)) {
      publishCurrent(_snapshot.copyWith(lastError = "请先连接车辆蓝牙并完成协议登录"))
      return false
    }
    publishCurrent(_snapshot.copyWith(busy = true, clearError = true))
    try {
      val ok = when (stack) {
        InductionStack.QGJ -> setQgjDistance(value)
        InductionStack.TLINK -> vehicleIo { cm.setTlinkInductionDistance(value) }
        InductionStack.RSSI -> true
        InductionStack.NONE -> false
      }
      if (!ok) {
        publishCurrent(_snapshot.copyWith(busy = false, lastError = "写入感应距离失败"))
        return false
      }
      saveDistancePref(value)
      publishCurrent(_snapshot.copyWith(distance = value, busy = false, clearError = true))
      return true
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      publishCurrent(_snapshot.copyWith(busy = false, lastError = e.toString()))
      return false
    }
  }

  private suspend fun setQgjDistance(value: Int): Boolean {
    val response = vehicleIo { cm.sendQgjCommand(
      QgjCommandIds.proximityDistanceSet,
      buildQgjProximityDistancePayload(value),
    ) }
    return response?.success == true
  }

  // ---------------------------------------------------------------------------
  // RSSI path
  // ---------------------------------------------------------------------------

  private fun startRssiLoop() {
    synchronized(rssiLock) {
      if (_rssiJob?.isActive == true) return
      val session = RssiSession()
      val label = _boundCarId
      _rssiJob = scope.launch(start = CoroutineStart.LAZY) {
        val thisJob = currentCoroutineContext().job
        try {
          // Start the foreground service FIRST: if it cannot start (Android 12+
          // bans background FGS starts — a BLE reconnect restarting this loop
          // while backgrounded used to leave the poll running unprotected, with
          // the process killable at any moment), cancel the poll instead.
          if (!startRssiForegroundService(label)) {
            return@launch
          }
          currentCoroutineContext().ensureActive()
          _log.operation("RSSI 感应轮询已启动", level = LogLevel.INFO)
          while (isActive) {
            delay(rssiPollInterval)
            rssiTick(session)
          }
        } finally {
          synchronized(rssiLock) {
            if (_rssiJob === thisJob || _rssiJob == null) {
              _rssiJob = null
              stopForegroundService()
            }
          }
        }
      }
      _rssiJob?.start()
    }
  }

  /** Start the RSSI foreground service; returns whether it is running. */
  private suspend fun startRssiForegroundService(label: String?): Boolean {
    val started = _foregroundService.start(vehicleLabel = label)
    if (!started) {
      _log.operation("RSSI 前台服务启动失败", level = LogLevel.WARNING)
    }
    return started
  }

  private fun stopRssiLoop() {
    synchronized(rssiLock) {
      val job = _rssiJob
      _rssiJob = null
      job?.cancel()
      stopForegroundService()
    }
  }

  private fun stopForegroundService() {
    runCatching { _foregroundService.stopNow() }
      .onFailure { _log.operation("RSSI 前台服务停止失败", detail = it.toString(), level = LogLevel.WARNING) }
  }

  private class RssiSession {
    val samples = ArrayDeque<Int>()
    var taskState = RssiTaskState.idle
  }

  private suspend fun rssiTick(session: RssiSession) {
    if (_manual.enabled) return
    if (!cm.isProtocolLoggedIn) return
    val rssi = cm.readRemoteRssi() ?: return
    currentCoroutineContext().ensureActive()
    session.samples.addLast(rssi)
    while (session.samples.size > rssiSampleWindow) {
      session.samples.removeFirst()
    }
    if (session.samples.size < rssiSampleWindow) return

    val distance = estimateDistanceFromRssiSamples(
      session.samples.toList(),
      rssiA = _rssiCalibration.rssiA,
      rssiFactor = _rssiCalibration.rssiFactor,
    )
    val action = classifyDistance(
      distance,
      minDistanceM = _rssiCalibration.minDistanceM,
      maxDistanceM = _rssiCalibration.maxDistanceM,
    )
    if (!shouldFireRssiAction(action, session.taskState)) {
      session.samples.removeFirst()
      return
    }

    try {
      if (action == RssiProximityAction.approachUnlock) {
        _log.operation("RSSI 感应 → 解防", detail = formatDistance(distance), level = LogLevel.INFO)
      } else if (action == RssiProximityAction.leaveLock) {
        _log.operation("RSSI 感应 → 设防", detail = formatDistance(distance), level = LogLevel.INFO)
      }
      val steps = pendingRssiSteps(action, session.taskState)
      for (step in steps) {
        // Re-check manual mode before every command: the user may have flipped
        // the 感应|手动 switch while a previous step was in flight.
        currentCoroutineContext().ensureActive()
        if (_manual.enabled || !cm.isProtocolLoggedIn) break
        val command = when (step) {
          RssiProximityStep.unlock -> CommandCode.unlock
          RssiProximityStep.powerOn -> CommandCode.powerOn
          RssiProximityStep.powerOff -> CommandCode.powerOff
          RssiProximityStep.lock -> CommandCode.lock
        }
        val ok = cm.sendCommand(command)
        currentCoroutineContext().ensureActive()
        session.taskState = confirmedRssiState(session.taskState, step, success = ok)
        if (!ok) {
          _log.operation("RSSI 感应步骤未确认", detail = command.label, level = LogLevel.WARNING)
          break
        }
      }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      _log.operation("RSSI 感应指令失败", detail = e.toString(), level = LogLevel.WARNING)
    } finally {
      session.samples.clear()
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

  private suspend fun loadEnabledPref(): Boolean {
    val operation = ensureCurrentOperation()
    return vehicleIo { _prefs.loadBoolean(operation.enabledKey, false) }
  }

  private suspend fun saveEnabledPref(value: Boolean) {
    val operation = ensureCurrentOperation()
    vehicleIo { _prefs.saveBoolean(operation.enabledKey, value) }
  }

  private suspend fun loadDistancePref(): Int {
    val operation = ensureCurrentOperation()
    return vehicleIo { _prefs.loadInt(operation.distanceKey, DEFAULT_DISTANCE_LEVEL) }
      .coerceIn(0, MAX_DISTANCE_LEVEL)
  }

  private suspend fun saveDistancePref(value: Int) {
    val operation = ensureCurrentOperation()
    vehicleIo { _prefs.saveInt(operation.distanceKey, value) }
  }

  private class VehicleOperation(
    val generation: Long,
    val enabledKey: String,
    val distanceKey: String,
  ) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<VehicleOperation>
  }

  private suspend fun <T> withVehicleOperation(block: suspend () -> T): T = coroutineScope {
    val operationJob = currentCoroutineContext().job
    val operation = synchronized(bindingLock) {
      activeOperations.add(operationJob)
      VehicleOperation(bindingGeneration, enabledKey, distanceKey)
    }
    try {
      operationMutex.withLock {
        try {
          withContext(operation) {
            ensureCurrentOperation()
            block()
          }
        } finally {
          synchronized(bindingLock) {
            if (operation.generation == bindingGeneration) publish(_snapshot.copyWith(busy = false))
          }
        }
      }
    } finally {
      synchronized(bindingLock) {
        activeOperations.remove(operationJob)
      }
    }
  }

  private suspend fun ensureCurrentOperation(): VehicleOperation {
    val context = currentCoroutineContext()
    context.ensureActive()
    val operation = checkNotNull(context[VehicleOperation])
    synchronized(bindingLock) {
      if (operation.generation != bindingGeneration) throw CancellationException("感应车辆已切换")
    }
    return operation
  }

  private suspend fun <T> vehicleIo(block: suspend () -> T): T {
    ensureCurrentOperation()
    return block().also { ensureCurrentOperation() }
  }

  private suspend fun publishCurrent(next: InductionModeSnapshot) {
    val operation = ensureCurrentOperation()
    synchronized(bindingLock) {
      if (operation.generation != bindingGeneration) throw CancellationException("感应车辆已切换")
      publish(next)
    }
  }

  private suspend fun updateRssiLoop(enabled: Boolean) {
    val operation = ensureCurrentOperation()
    synchronized(bindingLock) {
      if (operation.generation != bindingGeneration) throw CancellationException("感应车辆已切换")
      if (enabled) startRssiLoop() else stopRssiLoop()
    }
  }

  private fun publish(next: InductionModeSnapshot) {
    _snapshotState.value = next
  }

  /** Port of Dart `resetForTest`. */
  fun resetForTest() {
    synchronized(bindingLock) {
      bindingGeneration++
      activeOperations.toList().forEach { it.cancel() }
      stopRssiLoop()
      _boundModelType = null
      _boundCarId = null
      _boundIdentityMac = ""
      _rssiCalibration = RssiCalibration()
      _appInForeground = true
      publish(InductionModeSnapshot.EMPTY)
    }
  }

  /**
   * Port of Dart `dispose`. The RSSI-loop stop intent is fired best-effort:
   * with an owned scope the queued stop may be cancelled together with the
   * scope (the notification then dies with the process — `START_NOT_STICKY`).
   */
  fun dispose() {
    synchronized(bindingLock) {
      bindingGeneration++
      activeOperations.toList().forEach { it.cancel() }
    }
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
