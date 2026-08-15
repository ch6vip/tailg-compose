/**
 * Port of `lib/services/auto_connect_service.dart` (tailg-ble-app).
 *
 * Auto-connect: binds the official selected vehicle (or a saved device) as the
 * near-field BLE target and, when enabled and not in 手动模式, automatically
 * connects on app start / vehicle link. Mirrors the official ControlFragment
 * flow: direct classic-MAC connect first (TLink), scan-based matching as the
 * fallback (KKS/TLink by name or MAC, QGJ by identity — see deviation note).
 *
 * Porting decisions:
 * - Dart singleton → plain class with constructor-injected deps (DI shares one
 *   instance). `StreamController<bool>.broadcast()` → [enabledFlow] StateFlow.
 * - Dart `SharedPreferences` strings → [InductionPrefs] string keys, identical
 *   key names (`auto_connect_enabled` / `auto_connect_device_id` /
 *   `auto_connect_device_name`).
 * - `VehicleStore` (vehicle_store.dart port) → `data.store.VehicleStore`.
 * - `AppPermissionService().requestBleScanPermissions(request:)` needs an
 *   Activity; injected as [activityProvider] + [permissionService] seam with
 *   the Dart [permissionRequestOverride] test hook preserved.
 * - **Deviation**: flutter_blue_plus scan results carry advertisement data
 *   (manufacturer payloads / service UUIDs / adv name); the Kotlin scan seam
 *   (`ConnectionManager.scanDevices`) emits `BluetoothDevice` only, so QGJ
 *   identity matching falls back to the radio address
 *   (`identityWithRadioFallback` with no parsed payload). A detailed-scan seam
 *   is a P0 item for real-device verification of Harmony QGJ.
 * - `BluetoothAdapterState` is ported as a small enum; the Dart state-stream
 *   wait is reduced to a short poll (no broadcast stream on Android).
 */
package com.tailg.plus.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import androidx.activity.ComponentActivity
import com.tailg.plus.data.ble.BleTimings
import com.tailg.plus.data.ble.QgjScanIdentity
import com.tailg.plus.data.ble.identityWithRadioFallback
import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.data.ble.platform.ConnectionState
import com.tailg.plus.data.ble.platform.OfficialBleConnectionContext
import com.tailg.plus.data.ble.platform.OfficialBleStack
import com.tailg.plus.data.model.VehicleProfile
import com.tailg.plus.data.model.VehicleProtocol
import com.tailg.plus.data.store.VehicleStore
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService
import com.tailg.plus.permission.AppPermissionService
import com.tailg.plus.permission.PermissionCheckResult
import java.time.Instant
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds

/** Port of Dart `enum BluetoothAdapterState` (the subset Android exposes). */
enum class BluetoothAdapterState {
  UNKNOWN,
  ON,
  OFF,
  TURNING_ON,
  TURNING_OFF,
}

/** Port of Dart `AutoConnectRunGate` — coalesce concurrent run attempts. */
class AutoConnectRunGate {
  private var runningJob: kotlinx.coroutines.Job? = null

  val isRunning: Boolean get() = runningJob?.isActive == true

  suspend fun run(operation: suspend () -> Unit) {
    val existing = runningJob
    if (existing != null && existing.isActive) {
      existing.join()
      return
    }
    coroutineScope {
      val job = launch { operation() }
      runningJob = job
      job.join()
      if (runningJob === job) {
        runningJob = null
      }
    }
  }
}

/** Port of Dart `AutoConnectTargetGuard`. */
class AutoConnectTargetGuard {
  fun allowsConnectedTarget(
    autoConnectEnabled: Boolean,
    manualModeEnabled: Boolean,
    defaultVehicleId: String?,
    deviceId: String,
    manager: ConnectionManager,
    device: BluetoothDevice,
    currentManager: ConnectionManager?,
    snapshotGuard: BleConnectionSnapshotGuard,
  ): Boolean {
    return autoConnectEnabled &&
      !manualModeEnabled &&
      AutoConnectService.sameDeviceId(defaultVehicleId ?: "", deviceId) &&
      snapshotGuard.allowsReadyTarget(
        startManager = manager,
        currentManager = currentManager,
        startDevice = device,
        currentDevice = manager.device,
        currentDeviceId = manager.device?.address,
        expectedDeviceId = deviceId,
        currentState = manager.state,
      )
  }
}

/**
 * Dart `AutoConnectService`. Test seams are constructor params:
 * [permissionRequestOverride] mirrors the Dart `@visibleForTesting` hook;
 * [adapterProvider] / [activityProvider] let host wiring supply the real
 * Android objects.
 */
class AutoConnectService(
  private val connectionManager: ConnectionManager,
  private val manualModeService: ManualModeService,
  private val vehicleStore: VehicleStore,
  private val prefs: InductionPrefs,
  private val log: LogService = LogService(),
  private val permissionService: AppPermissionService? = null,
  private val activityProvider: () -> ComponentActivity? = { null },
  private val adapterProvider: () -> BluetoothAdapter? = { null },
  private val permissionRequestOverride: (suspend (request: Boolean) -> PermissionCheckResult)? = null,
  private val clock: () -> Instant = { Instant.now() },
) {
  companion object {
    private const val PREF_ENABLED = "auto_connect_enabled"
    private const val PREF_DEVICE_ID = "auto_connect_device_id"
    private const val PREF_DEVICE_NAME = "auto_connect_device_name"

    /** Dart `sameDeviceId` — compare MAC-like ids ignoring separators/case. */
    fun sameDeviceId(a: String, b: String): Boolean {
      val left = a.replace(Regex("[^0-9a-fA-F]"), "").uppercase()
      val right = b.replace(Regex("[^0-9a-fA-F]"), "").uppercase()
      return left.isNotEmpty() && left == right
    }

    /** Dart `formatBleMacAddress` — `AABBCCDDEEFF` → `AA:BB:CC:DD:EE:FF`. */
    fun formatBleMacAddress(raw: String): String {
      val compact = raw.replace(Regex("[^0-9a-fA-F]"), "").uppercase()
      if (compact.length != 12) return ""
      return compact.chunked(2).joinToString(":")
    }

    /** Dart `matchesQgjIdentity`. */
    fun matchesQgjIdentity(
      targetMac: String,
      observedMac: String?,
      bootMode: Int,
      harmony: Boolean,
    ): Boolean {
      return !harmony &&
        bootMode == 0 &&
        observedMac != null &&
        sameDeviceId(observedMac, targetMac)
    }
  }

  private val runGate = AutoConnectRunGate()
  private val connectionSnapshotGuard = BleConnectionSnapshotGuard()
  private val targetGuard = AutoConnectTargetGuard()

  private val _enabled = MutableStateFlow(false)

  /** Dart `enabledStream` → StateFlow. */
  val enabledFlow: StateFlow<Boolean> = _enabled.asStateFlow()

  /** Dart `enabled` getter. */
  val enabled: Boolean get() = _enabled.value

  private var initialized = false
  private val initMutex = Mutex()
  private var _lastDeviceId: String? = null
  private var _lastDeviceName: String? = null
  private var _officialContext: OfficialBleConnectionContext? = null
  private var _scanContext: OfficialBleConnectionContext? = null

  /** Dart `lastDeviceName`. */
  val lastDeviceName: String? get() = _lastDeviceName

  /** Dart `init(manager)` — manager is constructor-injected; loads prefs. */
  suspend fun init() {
    if (initialized) {
      refreshTarget()
      return
    }
    initMutex.withLock {
      if (initialized) return
      vehicleStore.init()
      _enabled.value = prefs.loadBoolean(PREF_ENABLED, false)
      val defaultVehicle = vehicleStore.defaultVehicle
      if (defaultVehicle == null) {
        val legacyId = prefs.loadString(PREF_DEVICE_ID, "")
        if (legacyId.isNotEmpty()) {
          vehicleStore.upsert(
            id = legacyId,
            name = prefs.loadString(PREF_DEVICE_NAME, "未命名车辆"),
            protocol = VehicleProtocol.AUTO,
            makeDefault = true,
          )
        }
      }
      refreshTarget()
      initialized = true
    }
  }

  /** Dart `resetForTest`. */
  fun resetForTest() {
    _enabled.value = false
    _lastDeviceId = null
    _lastDeviceName = null
    _officialContext = null
    _scanContext = null
    initialized = false
  }

  /** Dart `setEnabled`. */
  suspend fun setEnabled(value: Boolean) {
    prefs.saveBoolean(PREF_ENABLED, value)
    _enabled.value = value
  }

  /** Dart `dispose()` — StateFlow cannot be closed; kept for API parity. */
  fun dispose() = Unit

  /**
   * Dart `linkOfficialTarget` — bind the official selected car as the
   * near-field auto-connect target (official ControlFragment path).
   */
  suspend fun linkOfficialTarget(
    deviceId: String,
    displayName: String,
    context: OfficialBleConnectionContext? = null,
    enable: Boolean = true,
    connectNow: Boolean = true,
    ignoreManualMode: Boolean = false,
  ) {
    val id = deviceId.trim()
    if (id.isEmpty()) return

    disconnectIfDifferentTarget(id, context)
    _officialContext = context
    connectionManager.setOfficialConnectionContext(context)

    vehicleStore.init()
    vehicleStore.upsert(
      id = id,
      name = if (displayName.trim().isEmpty()) "我的车辆" else displayName.trim(),
      protocol = VehicleProtocol.AUTO,
      makeDefault = true,
    )
    _lastDeviceId = id
    _lastDeviceName = displayName
    prefs.saveString(PREF_DEVICE_ID, id)
    if (displayName.trim().isNotEmpty()) {
      prefs.saveString(PREF_DEVICE_NAME, displayName.trim())
    }
    if (enable && !enabled) {
      setEnabled(true)
    } else {
      refreshTarget()
    }
    if (connectNow) {
      tryAutoConnect(ignoreManualMode = ignoreManualMode)
    }
  }

  /** Dart `_disconnectIfDifferentTarget`. */
  private suspend fun disconnectIfDifferentTarget(
    targetDeviceId: String,
    context: OfficialBleConnectionContext?,
  ) {
    if (connectionManager.state == ConnectionState.DISCONNECTED) return

    val currentTarget = connectionManager.connectionContext?.targetMacCompact ?: ""
    if (currentTarget.isNotEmpty() && sameDeviceId(currentTarget, targetDeviceId)) return

    val currentId = connectionManager.device?.address ?: ""
    if (currentId.isNotEmpty() && sameDeviceId(currentId, targetDeviceId)) return

    log.operation(
      "换车: 断开旧 BLE",
      detail = "from=${if (currentId.isEmpty()) connectionManager.state.name else currentId} to=$targetDeviceId",
      level = LogLevel.INFO,
    )
    try {
      connectionManager.disconnect()
    } catch (e: Exception) {
      log.operation("换车: 断开旧 BLE 失败", detail = e.toString(), level = LogLevel.WARNING)
    }
  }

  /** True when manager is already working on [targetDeviceId]. */
  fun isLinkedTo(targetDeviceId: String): Boolean {
    if (connectionManager.state == ConnectionState.DISCONNECTED) return false
    val officialTarget = connectionManager.connectionContext?.targetMacCompact ?: ""
    if (officialTarget.isNotEmpty()) {
      return sameDeviceId(officialTarget, targetDeviceId)
    }
    val currentId = connectionManager.device?.address ?: ""
    if (currentId.isEmpty()) return false
    return sameDeviceId(currentId, targetDeviceId)
  }

  /** Dart `saveDevice`. */
  suspend fun saveDevice(
    device: BluetoothDevice,
    lastConnectedAt: Instant? = null,
    protocol: VehicleProtocol = VehicleProtocol.AUTO,
  ): VehicleProfile {
    val connectedAt = lastConnectedAt ?: clock()
    val deviceId = device.address
    val deviceName = device.name ?: ""
    _lastDeviceId = deviceId
    _lastDeviceName = deviceName
    val profile = vehicleStore.upsert(
      id = deviceId,
      name = deviceName,
      protocol = protocol,
      makeDefault = true,
      lastConnectedAt = connectedAt,
      savedAt = connectedAt,
    )
    prefs.saveString(PREF_DEVICE_ID, deviceId)
    if (deviceName.isNotEmpty()) {
      prefs.saveString(PREF_DEVICE_NAME, deviceName)
    }
    return profile
  }

  /** Dart `tryAutoConnect` — coalesced by [AutoConnectRunGate]. */
  suspend fun tryAutoConnect(ignoreManualMode: Boolean = false) {
    runGate.run { tryAutoConnectOnce(ignoreManualMode = ignoreManualMode) }
  }

  private suspend fun tryAutoConnectOnce(ignoreManualMode: Boolean) {
    vehicleStore.init()
    refreshTarget()
    manualModeService.init()
    if (!ignoreManualMode && manualModeService.enabled) {
      log.operation("自动连接: 已开启手动模式，跳过", level = LogLevel.INFO)
      return
    }
    val targetDeviceId = lastDeviceId
    val targetDeviceName = lastDeviceName
    val targetContext = _officialContext
    _scanContext = targetContext
    if (!enabled || targetDeviceId == null) return
    if (connectionManager.state != ConnectionState.DISCONNECTED) return

    // Auto-scan must not skip the runtime permission prompt (Dart parity).
    val permission = ensureBleScanPermissions(request = true)
    if (!permission.granted) {
      log.operation(
        "自动连接: 缺少蓝牙/定位权限",
        detail = permission.message ?: "denied",
        level = LogLevel.WARNING,
      )
      return
    }

    val adapterState = readAdapterState()
    if (adapterState != BluetoothAdapterState.ON) {
      log.operation(
        "自动连接: 蓝牙未开启",
        detail = adapterState.name,
        level = LogLevel.WARNING,
      )
      return
    }

    if (targetContext != null) {
      logMissingCredentials(targetContext)
    }

    // Official TLink path: direct classic-MAC connect first (no scan needed).
    if (tryDirectMacConnect(
        targetDeviceId = targetDeviceId,
        targetDeviceName = targetDeviceName,
        context = targetContext,
      )
    ) {
      return
    }
    if (connectionManager.state != ConnectionState.DISCONNECTED) return

    log.operation("自动连接: 扫描 $targetDeviceName ($targetDeviceId)")

    var targetFound = false
    var sawHarmonyQgj = false
    val loggedAddresses = mutableSetOf<String>()
    try {
      connectionManager.scanDevices(scanTimeout = BleTimings.autoConnectScanTimeout)
        .takeWhile { !targetFound }
        .collect { device ->
          val foundId = device.address
          val matchesSystemId = sameDeviceId(foundId, targetDeviceId)
          val match = matchesScanResult(
            device = device,
            targetDeviceId = targetDeviceId,
            context = targetContext,
            matchesSystemId = matchesSystemId,
            logSeen = loggedAddresses.add(foundId),
          )
          if (targetContext?.stack == OfficialBleStack.QGJ && device.name == "Harmony") {
            // Dart derives harmony from service UUIDs in the advertisement;
            // the Kotlin scan seam has no advertisement data, so this stays a
            // best-effort name heuristic (deviation, see file header).
            sawHarmonyQgj = true
          }
          if (!match) return@collect
          if (!enabled || (!ignoreManualMode && manualModeService.enabled)) return@collect
          targetFound = true
          try {
            doConnect(device)
          } catch (e: Exception) {
            log.operation("自动连接: 连接异常", detail = e.toString(), level = LogLevel.ERROR)
          }
        }
    } finally {
      _scanContext = null
    }
    if (!targetFound) {
      log.operation(
        if (sawHarmonyQgj) "自动连接: Harmony QGJ 缺少 systemId" else "自动连接: 超时未找到设备",
        level = LogLevel.WARNING,
      )
    }
  }

  private suspend fun ensureBleScanPermissions(request: Boolean): PermissionCheckResult {
    val override = permissionRequestOverride
    if (override != null) return override(request)
    val activity = activityProvider()
    val service = permissionService
    if (activity == null || service == null) {
      return PermissionCheckResult.denied("activity or permission service unavailable")
    }
    return service.requestBleScanPermissions(activity, request)
  }

  /** Dart `_readAdapterState` — short poll instead of a state broadcast stream. */
  private suspend fun readAdapterState(): BluetoothAdapterState {
    repeat(5) {
      val adapter = adapterProvider()
      if (adapter != null) {
        return when (adapter.state) {
          BluetoothAdapter.STATE_ON -> BluetoothAdapterState.ON
          BluetoothAdapter.STATE_TURNING_ON -> BluetoothAdapterState.TURNING_ON
          BluetoothAdapter.STATE_TURNING_OFF -> BluetoothAdapterState.TURNING_OFF
          BluetoothAdapter.STATE_OFF -> BluetoothAdapterState.OFF
          else -> BluetoothAdapterState.UNKNOWN
        }
      }
      delay(100.milliseconds)
    }
    return BluetoothAdapterState.UNKNOWN
  }

  /** Dart `_logMissingCredentials`. */
  private fun logMissingCredentials(context: OfficialBleConnectionContext) {
    if (context.stack == OfficialBleStack.TLINK && !context.hasTLinkCredentials) {
      log.operation(
        "自动连接: TLink 登录凭据不完整",
        detail = "uid=${if (context.userId.isEmpty()) "empty" else "ok"} " +
          "password=${if (context.selectedPassword == null) "missing" else "ok"} " +
          "shared=${context.shared}",
        level = LogLevel.WARNING,
      )
    }
    if (context.stack == OfficialBleStack.QGJ && !context.hasQgjCredentials) {
      log.operation(
        "自动连接: QGJ 登录凭据不完整",
        detail = "uid=${if (context.userId.isEmpty()) "empty" else "ok"} " +
          "password=${if (context.selectedPassword == null) "missing" else "ok"}",
        level = LogLevel.WARNING,
      )
    }
  }

  /**
   * Dart `_tryDirectMacConnect` — Android classic-MAC direct connect for
   * non-QGJ stacks; returns true when an attempt was consumed.
   */
  private suspend fun tryDirectMacConnect(
    targetDeviceId: String,
    targetDeviceName: String?,
    context: OfficialBleConnectionContext?,
  ): Boolean {
    val stack = context?.stack
    // QGJ identity lives in manufacturer data, not the radio address.
    if (stack == OfficialBleStack.QGJ) return false

    val colonMac = formatBleMacAddress(targetDeviceId)
    if (colonMac.isEmpty()) return false

    log.operation(
      "自动连接: 直连 MAC",
      detail = "$targetDeviceName ($colonMac) stack=${stack?.name ?: "unknown"}",
    )
    try {
      val adapter = adapterProvider() ?: return false
      val device = adapter.getRemoteDevice(colonMac)
      doConnect(device, context = context)
      return connectionManager.state != ConnectionState.DISCONNECTED
    } catch (e: Exception) {
      log.operation("自动连接: 直连失败，改扫描", detail = e.toString(), level = LogLevel.INFO)
      return false
    }
  }

  /** Dart `_refreshTarget`. */
  private fun refreshTarget() {
    val defaultVehicle = vehicleStore.defaultVehicle
    if (defaultVehicle != null) {
      _lastDeviceId = defaultVehicle.id
      _lastDeviceName = defaultVehicle.displayName
    }
  }

  /** Dart `_doConnect`. */
  private suspend fun doConnect(
    device: BluetoothDevice,
    context: OfficialBleConnectionContext? = null,
  ) {
    val connectionContext = context ?: _scanContext
    val deviceId = device.address
    try {
      val vehicle = vehicleStore.defaultVehicle
      connectionManager.setOfficialConnectionContext(connectionContext)
      connectionManager.connect(device, connectionContext)
      if (isConnectedAutoTarget(manager = connectionManager, device = device, deviceId = deviceId)) {
        log.operation("自动连接: 成功", detail = vehicle?.displayName ?: deviceId)
      }
    } catch (e: Exception) {
      log.operation("自动连接: 失败", detail = e.toString(), level = LogLevel.WARNING)
    }
  }

  /** Dart `_matchesScanResult`. */
  private fun matchesScanResult(
    device: BluetoothDevice,
    targetDeviceId: String,
    context: OfficialBleConnectionContext?,
    matchesSystemId: Boolean,
    logSeen: Boolean = false,
  ): Boolean {
    if (context == null) return matchesSystemId
    return when (context.stack) {
      // Official KKS BleConnectService matches getBtname(); also accept MAC.
      OfficialBleStack.KKS -> matchesSystemId || advertisedNameMatches(device, context.advertisedName)
      // Official TLink connects by mac; name is a useful scan fallback.
      OfficialBleStack.TLINK -> matchesSystemId || advertisedNameMatches(device, context.advertisedName)
      OfficialBleStack.QGJ -> matchesQgjAdvertisement(
        targetMac = targetDeviceId,
        device = device,
        logSeen = logSeen,
      )
      OfficialBleStack.UNSUPPORTED -> false
    }
  }

  /** Dart `_advertisedNameMatches`. */
  private fun advertisedNameMatches(device: BluetoothDevice, expected: String): Boolean {
    val name = expected.trim()
    if (name.isEmpty()) return false
    val adv = device.name?.trim() ?: ""
    return adv.isNotEmpty() && adv == name
  }

  /**
   * Dart `_matchesQgjAdvertisement` — without advertisement data the identity
   * is the radio-address fallback (documented deviation in the file header).
   */
  private fun matchesQgjAdvertisement(
    targetMac: String,
    device: BluetoothDevice,
    logSeen: Boolean,
  ): Boolean {
    val identity = identityWithRadioFallback(
      parsed = QgjScanIdentity(identityMac = null, bootMode = 0, harmony = false),
      radioAddress = device.address,
    )
    val matched = matchesQgjIdentity(
      targetMac = targetMac,
      observedMac = identity.identityMac,
      bootMode = identity.bootMode,
      harmony = identity.harmony,
    )
    if (!matched && logSeen) {
      log.operation(
        "自动连接: 扫描到设备",
        detail = "addr=${device.address} name=${device.name ?: ""} " +
          "identity=${identity.identityMac ?: "null"} " +
          "radioFallback=${identity.fromRadioAddress} target=$targetMac matched=$matched",
        level = LogLevel.DEBUG,
      )
    }
    return matched
  }

  /** Dart `_isConnectedAutoTarget`. */
  private fun isConnectedAutoTarget(
    manager: ConnectionManager,
    device: BluetoothDevice,
    deviceId: String,
  ): Boolean {
    val context = _officialContext
    if (context != null) {
      return enabled &&
        !manualModeService.enabled &&
        manager.isProtocolLoggedIn &&
        manager.state == ConnectionState.READY &&
        manager === connectionManager &&
        device === manager.device &&
        sameDeviceId(
          manager.connectionContext?.targetMacCompact ?: "",
          context.targetMacCompact,
        )
    }
    return targetGuard.allowsConnectedTarget(
      autoConnectEnabled = enabled,
      manualModeEnabled = manualModeService.enabled,
      defaultVehicleId = vehicleStore.defaultVehicle?.id,
      deviceId = deviceId,
      manager = manager,
      device = device,
      currentManager = connectionManager,
      snapshotGuard = connectionSnapshotGuard,
    )
  }
}
