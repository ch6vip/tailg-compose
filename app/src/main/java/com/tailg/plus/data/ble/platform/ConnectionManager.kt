/**
 * Port of `lib/ble/connection_manager.dart` (tailg-ble-app) → package
 * `com.tailg.plus.data.ble.platform` — Android `BluetoothGatt` wrapper.
 *
 * ## Threading model
 *
 * Android BLE callbacks arrive on binder threads; the manager funnels them
 * through a single [Channel] consumed by one event-loop coroutine, so all
 * protocol state mutations are serialized (mirroring the Dart single-threaded
 * event loop). Public suspend functions may be called from any dispatcher;
 * GATT operations are serialized through the Dart-style priority queue
 * ([runGattOperation]).
 *
 * ## Permission contract
 *
 * The manager never checks or requests Bluetooth permissions — the calling
 * layer (UI / service) must hold `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`
 * (+ legacy `BLUETOOTH`/`BLUETOOTH_ADMIN`) and `ACCESS_FINE_LOCATION`
 * (for scan results on Android < 12) before invoking [scanDevices],
 * [connect] or [createBond].
 *
 * ## Connection-state → official LoginStatus mapping
 *
 * - [ConnectionState.DISCONNECTED] ≈ DISCONNECTED / BLE_STATE_OFF
 * - [ConnectionState.CONNECTING] / [ConnectionState.RECONNECTING] ≈ CONNECTING
 * - [ConnectionState.CONNECTED] ≈ CONNECTED / READY (GATT up, handshake in flight)
 * - [ConnectionState.READY] ≈ **LOGIN** only when [isProtocolLoggedIn] is true,
 *   which also requires a protocol credential (`token` after standard
 *   TokenResponse or QGJ login success).
 */
package com.tailg.plus.data.ble.platform

import android.annotation.SuppressLint

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.tailg.plus.data.ble.BikeState
import com.tailg.plus.data.ble.BleTimings
import com.tailg.plus.data.ble.BleUuids
import com.tailg.plus.data.ble.CommandCode
import com.tailg.plus.data.ble.CommandResponse
import com.tailg.plus.data.ble.ModelType
import com.tailg.plus.data.ble.ParsedResponse
import com.tailg.plus.data.ble.QgjCommandIds
import com.tailg.plus.data.ble.QgjResponse
import com.tailg.plus.data.ble.RidingMode
import com.tailg.plus.data.ble.StateResponse
import com.tailg.plus.data.ble.TLINK_INDUCTION_CHECK_PLAIN
import com.tailg.plus.data.ble.TLINK_INDUCTION_CLOSE_PLAIN
import com.tailg.plus.data.ble.TLINK_INDUCTION_OPEN_PLAIN
import com.tailg.plus.data.ble.TLinkCommandResponse
import com.tailg.plus.data.ble.TLinkInductionSetResponse
import com.tailg.plus.data.ble.TLinkInductionStatusResponse
import com.tailg.plus.data.ble.TLinkLoginResponse
import com.tailg.plus.data.ble.TLinkProximityDistanceSetResponse
import com.tailg.plus.data.ble.TLinkResponse
import com.tailg.plus.data.ble.TLinkTokenResponse
import com.tailg.plus.data.ble.TokenResponse
import com.tailg.plus.data.ble.aesEcbEncrypt
import com.tailg.plus.data.ble.buildCommand
import com.tailg.plus.data.ble.buildQgjCommand
import com.tailg.plus.data.ble.buildQgjControlFrame
import com.tailg.plus.data.ble.buildQgjLoginFrame
import com.tailg.plus.data.ble.buildQgjRidingModeFrame
import com.tailg.plus.data.ble.buildTLinkCommand
import com.tailg.plus.data.ble.buildTLinkInductionDistancePlain
import com.tailg.plus.data.ble.buildTLinkLoginFrame
import com.tailg.plus.data.ble.buildTLinkTokenRequest
import com.tailg.plus.data.ble.buildTokenRequest
import com.tailg.plus.data.ble.bytesToHex
import com.tailg.plus.data.ble.bytesToSpacedHex
import com.tailg.plus.data.ble.hexToBytes
import com.tailg.plus.data.ble.parseQgjResponse
import com.tailg.plus.data.ble.parseQgjRidingMode
import com.tailg.plus.data.ble.parseQgjSeatSupport
import com.tailg.plus.data.ble.parseResponse
import com.tailg.plus.data.ble.parseTLinkResponse
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Android native port of Dart `ConnectionManager`.
 *
 * See [scanDevices] for scanning, [connect]/[disconnect] for lifecycle,
 * [isProtocolLoggedIn] for the official-LOGIN latch, and the class KDoc for
 * threading / permission contracts.
 */
@SuppressLint("MissingPermission")
class ConnectionManager(
  private val context: Context,
  private val log: LogService = LogService(),
  externalScope: CoroutineScope? = null,
) {
  companion object {
    private const val MAX_RECONNECT_ATTEMPTS = 8
    private const val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"

    /** Android `GATT_ERROR` (0x85 = 133) — flutter_blue_plus reports it as "android-code: 133". */
    private const val GATT_STATUS_ERROR = 133
  }

  private val ownsScope = externalScope == null
  private val scope = externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val lock = Any()

  // Serialized GATT operation queue (Dart priority queue), extracted into
  // [GattOperationQueue] so ConnectionManager owns connection/protocol state
  // only. Lives on [scope] so queue drains share the manager lifecycle.
  private val gattQueue = GattOperationQueue(scope)

  private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
  private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

  // Delegated components (extracted from this file to reduce complexity).
  val bleScanner = BleScanner(context, log)

  init {
    bleScanner.init(bluetoothAdapter)
  }

  /** Underlying BLE device for the current connection. */
  private var _device: BluetoothDevice? = null

  // -------------------------------------------------------------------------
  // Protocol / session state (all mutations serialized via [lock] or the
  // single-threaded event loop; @Volatile for cross-thread reads).
  // -------------------------------------------------------------------------
  @Volatile private var _protocol = ProtocolType.UNKNOWN
  @Volatile private var _lastKnownProtocol = ProtocolType.UNKNOWN
  @Volatile private var _connectionContext: OfficialBleConnectionContext? = null
  @Volatile private var _token: String? = null
  @Volatile private var _protocolLoggedIn = false
  @Volatile private var _model = ModelType.KKS
  @Volatile private var _qgjLoginPassword = 0
  @Volatile private var _qgjUserId = 0
  @Volatile private var _latestBikeState: BikeState? = null
  @Volatile private var _lastPublishedBikeState: BikeState? = null
  @Volatile private var _disposed = false

  // Characteristics discovered during setup.
  @Volatile private var _writeChar: BluetoothGattCharacteristic? = null
  @Volatile private var _notifyChar: BluetoothGattCharacteristic? = null
  @Volatile private var _feb1Char: BluetoothGattCharacteristic? = null
  @Volatile private var _feb2Char: BluetoothGattCharacteristic? = null
  @Volatile private var _feb3Char: BluetoothGattCharacteristic? = null
  @Volatile private var _fe02Char: BluetoothGattCharacteristic? = null
  @Volatile private var _fe03Char: BluetoothGattCharacteristic? = null
  @Volatile private var _gpsNotifyChar: BluetoothGattCharacteristic? = null
  @Volatile private var _fcc1Char: BluetoothGattCharacteristic? = null
  @Volatile private var _fcc2Char: BluetoothGattCharacteristic? = null
  @Volatile private var _fbb1Char: BluetoothGattCharacteristic? = null
  @Volatile private var _fbb2Char: BluetoothGattCharacteristic? = null

  // Reconnect / lifecycle flags (Dart `_userDisconnected` etc.).
  @Volatile private var _userDisconnected = false
  @Volatile private var _reconnecting = false
  @Volatile private var _reconnectCancelled = false
  @Volatile private var _disconnectHandled = false
  @Volatile private var _reconnectAttempt = 0

  // Pending command completers — AtomicReference replaces the @Volatile var +
  // === pattern to close the race window between complete() and clear().
  private val _cmdAckDeferred = AtomicDeferred<Boolean>()
  private val _standardCommandAckDeferred = AtomicDeferred<Boolean>()
  @Volatile private var _standardPendingCommandType: String? = null
  private val _standardStateDeferred = AtomicDeferred<BikeState?>()
  private val _tlinkInductionStatusDeferred = AtomicDeferred<TLinkInductionStatusResponse?>()
  private val _tlinkInductionSetDeferred = AtomicDeferred<Boolean>()
  private val _tlinkProximityDistanceDeferred = AtomicDeferred<Boolean>()
  private val _qgjResponseDeferreds = ConcurrentHashMap<Int, CompletableDeferred<QgjResponse?>>()

  // Exposed flows (Dart broadcast StreamControllers).
  private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
  private val _bikeState = MutableStateFlow<BikeState?>(null)
  private val _ridingMode = MutableStateFlow(RidingMode.standard)
  private val _response = MutableSharedFlow<ParsedResponse>(extraBufferCapacity = 16)
  private val _fbb2 = MutableSharedFlow<String>(extraBufferCapacity = 1)

  // GATT bridge plumbing.
  private val gattEvents = Channel<GattEvent>(Channel.UNLIMITED)
  @Volatile private var _gatt: BluetoothGatt? = null
  @Volatile private var _connectDeferred: CompletableDeferred<Unit>? = null
  @Volatile private var _discoveryDeferred: CompletableDeferred<Unit>? = null
  @Volatile private var _mtuDeferred: CompletableDeferred<Int>? = null
  @Volatile private var _rssiDeferred: CompletableDeferred<Int>? = null
  private val readDeferreds = ConcurrentHashMap<UUID, CompletableDeferred<ByteArray>>()
  private val writeDeferreds = ConcurrentHashMap<UUID, CompletableDeferred<Unit>>()
  private val descriptorWriteDeferreds = ConcurrentHashMap<UUID, CompletableDeferred<Unit>>()

  // Background jobs.
  private var heartbeatJob: Job? = null
  private var watchdogJob: Job? = null
  @Volatile private var reconnectJob: Job? = null
  private var eventLoopJob: Job? = null

  init {
    eventLoopJob = scope.launch {
      for (event in gattEvents) {
        try {
          handleGattEvent(event)
        } catch (e: Exception) {
          log.ble("GATT 事件处理异常", detail = e.toString(), level = LogLevel.ERROR)
        }
      }
    }
  }

  // =========================================================================
  // Public API — state
  // =========================================================================

  /** Port of Dart `stateStream`. */
  val stateFlow: StateFlow<ConnectionState> = _state.asStateFlow()

  /** Port of Dart `responseStream` (standard-stack parsed replies). */
  val responseFlow: SharedFlow<ParsedResponse> = _response.asSharedFlow()

  /** Port of Dart `bikeStateStream`. */
  val bikeStateFlow: StateFlow<BikeState?> = _bikeState.asStateFlow()

  /** Port of Dart `fbb2Stream` — hex strings of fbb2 notifications. */
  val fbb2Flow: SharedFlow<String> = _fbb2.asSharedFlow()

  /** Port of Dart `ridingModeStream`. */
  val ridingModeFlow: StateFlow<RidingMode> = _ridingMode.asStateFlow()

  /** Port of Dart `state`. */
  val state: ConnectionState get() = _state.value

  /** Port of Dart `protocol`. */
  val protocol: ProtocolType get() = _protocol

  /** Port of Dart `lastKnownProtocol`. */
  val lastKnownProtocol: ProtocolType get() = _lastKnownProtocol

  /** Port of Dart `token` — 4-byte session token hex after standard login. */
  val token: String? get() = _token

  /** Port of Dart `device` — the currently bound [BluetoothDevice]. */
  val device: BluetoothDevice? get() = _device

  /** Port of Dart `connectionContext`. */
  val connectionContext: OfficialBleConnectionContext? get() = _connectionContext

  /** Port of Dart `latestBikeState`. */
  val latestBikeState: BikeState? get() = _latestBikeState

  /** Port of Dart `qgjLoginPassword`. */
  val qgjLoginPassword: Int get() = _qgjLoginPassword

  /** Port of Dart `qgjUserId`. */
  val qgjUserId: Int get() = _qgjUserId

  /** Port of Dart `fcc1Char`. */
  val fcc1Char: BluetoothGattCharacteristic? get() = _fcc1Char

  /** Port of Dart `fcc2Char`. */
  val fcc2Char: BluetoothGattCharacteristic? get() = _fcc2Char

  /** Port of Dart `fbb1Char`. */
  val fbb1Char: BluetoothGattCharacteristic? get() = _fbb1Char

  /** Port of Dart `fbb2Char`. */
  val fbb2Char: BluetoothGattCharacteristic? get() = _fbb2Char

  /** Port of Dart `ridingMode`. */
  val ridingMode: RidingMode get() = _ridingMode.value

  /**
   * Official `LoginStatus.LOGIN` equivalent for control routing. True only when
   * the connection is [ConnectionState.READY] **and** a protocol credential
   * exists (`token` from standard TokenResponse, or the QGJ login marker).
   * GATT-only [ConnectionState.CONNECTED] is never LOGIN.
   */
  val isProtocolLoggedIn: Boolean
    get() = _protocolLoggedIn && state == ConnectionState.READY && _token != null

  /** Port of Dart `protocolLoginUnavailableReason`. */
  val protocolLoginUnavailableReason: String
    get() {
      if (isProtocolLoggedIn) return ""
      return when (state) {
        ConnectionState.DISCONNECTED -> "蓝牙未连接"
        ConnectionState.CONNECTING -> "蓝牙连接中"
        ConnectionState.RECONNECTING -> "蓝牙正在重连"
        ConnectionState.CONNECTED -> "蓝牙未完成协议登录"
        ConnectionState.READY -> "蓝牙未完成协议登录"
      }
    }

  // =========================================================================
  // Public API — configuration
  // =========================================================================

  /** Port of Dart `setModel`. */
  fun setModel(model: ModelType) {
    _model = model
  }

  /**
   * Port of Dart `setOfficialConnectionContext` — select the official vehicle
   * before starting a BLE connection. Credentials remain in memory for this
   * session only.
   */
  fun setOfficialConnectionContext(context: OfficialBleConnectionContext?) {
    _connectionContext = context
    val cipher = context?.cipherModel
    _model = cipher ?: ModelType.KKS
    if (context?.stack == OfficialBleStack.QGJ) {
      _qgjLoginPassword = context.selectedPassword ?: 0
      _qgjUserId = context.userIdValue ?: 0
    } else if (context == null) {
      _qgjLoginPassword = 0
      _qgjUserId = 0
    }
  }

  /** Port of Dart `setQgjCredentials`. */
  fun setQgjCredentials(password: Int?, userId: Int?) {
    _qgjLoginPassword = password ?: 0
    _qgjUserId = userId ?: 0
  }

  // =========================================================================
  // Public API — scanning
  // =========================================================================

  /**
   * Scan for BLE peripherals (Dart `BluetoothScanner.startScan` in the
   * auto-connect service). Emits discovered [BluetoothDevice]s; scanning stops
   * automatically after [scanTimeout] (flow completes normally) or when the
   * collector cancels. Caller must hold `BLUETOOTH_SCAN` (+
   * `ACCESS_FINE_LOCATION` on Android < 12).
   */
  fun scanDevices(
    scanTimeout: Duration = BleTimings.manualScanTimeout,
    filter: ScanFilter? = null,
  ): kotlinx.coroutines.flow.Flow<BluetoothDevice> = callbackFlow {
    val adapter = bluetoothAdapter ?: return@callbackFlow
    val scanner = adapter.bluetoothLeScanner ?: return@callbackFlow
    val settings = ScanSettings.Builder()
      .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
      .build()
    val callback = object : ScanCallback() {
      override fun onScanResult(callbackType: Int, result: ScanResult) {
        trySend(result.device)
      }

      override fun onScanFailed(errorCode: Int) {
        close(IllegalStateException("BLE scan failed: $errorCode"))
      }
    }
    try {
      scanner.startScan(filter?.let { listOf(it) }, settings, callback)
    } catch (e: SecurityException) {
      close(e)
      return@callbackFlow
    }
    val autoStop = scope.launch {
      delay(scanTimeout)
      try {
        scanner.stopScan(callback)
      } catch (_: SecurityException) {
      }
      close()
    }
    awaitClose {
      autoStop.cancel()
      try {
        scanner.stopScan(callback)
      } catch (_: SecurityException) {
      }
    }
  }

  // =========================================================================
  // Public API — GATT operation queue
  // =========================================================================

  /**
   * Port of Dart `runGattOperation` — enqueue [operation] with [priority] and
   * suspend until it completes (or times out after
   * [BleTimings.gattOperationTimeout]). Operations run strictly one at a time,
   * HIGH → NORMAL → LOW. On failure the thrown exception propagates to the
   * caller, exactly like Dart's `Completer.completeError`.
   */
  suspend fun <T> runGattOperation(
    priority: GattOperationPriority = GattOperationPriority.NORMAL,
    operation: suspend () -> T,
  ): T {
    if (_disposed) throw IllegalStateException("ConnectionManager disposed")
    return gattQueue.run(priority, operation)
  }

  // =========================================================================
  // Public API — state refresh & reads
  // =========================================================================

  /** Port of Dart `readFeb3` — low-priority QGJ status read; null when not ready. */
  suspend fun readFeb3(): ByteArray? = runGattOperation(priority = GattOperationPriority.LOW) {
    if (state != ConnectionState.READY || _feb3Char == null) {
      return@runGattOperation null
    }
    readCharacteristic(_feb3Char!!)
  }

  /**
   * Port of Dart `refreshBikeState` — KKS reads via the standard state
   * command + ACK; QGJ reads feb3 directly.
   */
  suspend fun refreshBikeState(): BikeState? {
    if (_protocol == ProtocolType.KKS) {
      val write = _writeChar
      val t = _token
      if (state != ConnectionState.READY || write == null || t == null) {
        return null
      }
      return runGattOperation(priority = GattOperationPriority.HIGH) {
        val previous = _standardStateDeferred.getAndSet(null)
        if (previous != null && !previous.isCompleted) {
          previous.complete(null)
        }
        val deferred = CompletableDeferred<BikeState?>()
        _standardStateDeferred.getAndSet(deferred)
        try {
          val frame = buildCommand(_model.aesKey, CommandCode.readState, t)
          writeCharacteristic(write, frame, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
          withTimeoutOrNull(BleTimings.commandAckTimeout) { deferred.await() }
        } finally {
          _standardStateDeferred.compareAndSet(deferred, null)
        }
      }
    }

    val data = readFeb3()
    if (data == null || data.isEmpty()) return null
    val bikeState = BikeState.fromFeb3(data)
    if (bikeState != null) {
      publishBikeState(bikeState)
    }
    return bikeState
  }

  // =========================================================================
  // Public API — connection lifecycle
  // =========================================================================

  /**
   * Port of Dart `connect` — full pipeline: cancel stale reconnect, guard
   * double invocation, retry connect, KKS bond, QGJ MTU, service discovery
   * and protocol setup (which kicks off the LOGIN handshake).
   */
  suspend fun connect(
    device: BluetoothDevice,
    context: OfficialBleConnectionContext? = null,
  ) {
    if (_disposed) {
      throw IllegalStateException("ConnectionManager disposed")
    }

    // C-1: cancel any ongoing reconnect loop and WAIT for it to unwind — the
    // old 100 ms sleep left its in-flight connectGattOnce racing this connect
    // (both installing _gatt/_connectDeferred; a failure teardown could then
    // close the FRESH connection).
    _reconnectCancelled = true
    reconnectJob?.cancelAndJoin()
    reconnectJob = null

    // H-1: guard against double invocation. Guard + state flip under [lock]:
    // the plain check-then-act let two concurrent connects both pass and
    // overwrite each other's gatt/deferred slots.
    synchronized(lock) {
      val current = state
      if (current == ConnectionState.CONNECTING ||
        current == ConnectionState.CONNECTED ||
        current == ConnectionState.READY
      ) {
        log.ble("connect() ignored: already in state $current")
        return
      }

      _userDisconnected = false
      _reconnecting = false
      _reconnectAttempt = 0
      _disconnectHandled = false
      setState(ConnectionState.CONNECTING)
    }

    clearRuntimeResources(disconnectDevice = false)

    if (context != null) setOfficialConnectionContext(context)

    // Remember the session device: onDisconnected() needs it to start the
    // auto-reconnect loop, and the public createBond/removeBond read it.
    _device = device
    _lastKnownProtocol = ProtocolType.UNKNOWN
    _reconnectCancelled = false // C-1: reset after setup

    try {
      connectDeviceWithRetry(
        device,
        timeout = BleTimings.connectTimeout,
        attempts = if (_connectionContext?.stack == OfficialBleStack.TLINK) 6 else 3,
        retryDelay = if (_connectionContext?.stack == OfficialBleStack.QGJ) 300.milliseconds
        else 500.milliseconds,
      )

      setState(ConnectionState.CONNECTED)

      ensureKksBond(device)
      requestQgjMtu(device)
      delay(BleTimings.serviceSetupDelay)
      discoverAndSetup()
    } catch (e: Exception) {
      log.ble("连接失败", detail = e.toString(), level = LogLevel.ERROR)
      clearRuntimeResources(disconnectDevice = true)
      resetCharacteristics()
      _device = null
      setState(ConnectionState.DISCONNECTED)
      throw e
    }
  }

  /** Port of Dart `disconnect` — user-initiated teardown, no reconnect. */
  suspend fun disconnect() {
    _userDisconnected = true
    _reconnecting = false
    _reconnectCancelled = true
    _reconnectAttempt = 0
    cancelHeartbeat()
    completePendingOperations(IllegalStateException("QGJ disconnected"))
    completePendingGattOperations(IllegalStateException("Disconnected by user"))
    clearRuntimeResources(disconnectDevice = false)
    try {
      closeGatt()
    } catch (e: Exception) {
      log.ble("用户断开设备失败", detail = e.toString(), level = LogLevel.DEBUG)
    } finally {
      // Always clear the local session so switch-vehicle cannot keep A while selecting B.
      resetCharacteristics()
      reset()
    }
  }

  // =========================================================================
  // Public API — commands
  // =========================================================================

  /**
   * Port of Dart `sendCommand` — six-key commands. KKS/TLink wait for the
   * standard command ACK; QGJ waits for the setStatus (0x1002) response.
   */
  suspend fun sendCommand(cmd: CommandCode): Boolean {
    if (state != ConnectionState.READY) return false

    log.operation("发送指令: ${cmd.label}", detail = "code=${cmd.code}")

    if (_protocol == ProtocolType.KKS || _protocol == ProtocolType.TLINK) {
      val write = _writeChar ?: return false
      val t = _token ?: return false
      val frame = if (_protocol == ProtocolType.TLINK) {
        buildTLinkCommand(keyHex = _model.aesKey, command = cmd, token = t)
      } else {
        buildCommand(_model.aesKey, cmd, t)
      }
      return runGattOperation(priority = GattOperationPriority.HIGH) {
        val previous = _standardCommandAckDeferred.getAndSet(null)
        if (previous != null && !previous.isCompleted) {
          previous.complete(false)
        }
        val deferred = CompletableDeferred<Boolean>()
        _standardCommandAckDeferred.getAndSet(deferred)
        _standardPendingCommandType = cmd.code.uppercase()
        try {
          writeCharacteristic(write, frame, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
          withTimeoutOrNull(BleTimings.commandAckTimeout) { deferred.await() } ?: false
        } finally {
          _standardCommandAckDeferred.compareAndSet(deferred, null)
          _standardPendingCommandType = null
        }
      }
    } else if (_protocol == ProtocolType.QGJ) {
      val feb1 = _feb1Char ?: return false
      val frame = buildQgjControlFrame(cmd) ?: return false

      val success = runGattOperation(priority = GattOperationPriority.HIGH) {
        val previous = _cmdAckDeferred.getAndSet(null)
        if (previous != null && !previous.isCompleted) {
          previous.complete(false)
        }
        val deferred = CompletableDeferred<Boolean>()
        _cmdAckDeferred.getAndSet(deferred)
        try {
          writeCharacteristic(feb1, frame, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
          withTimeoutOrNull(BleTimings.commandAckTimeout) { deferred.await() } ?: false
        } finally {
          _cmdAckDeferred.compareAndSet(deferred, null)
        }
      }

      if (success) {
        log.operation("指令确认: ${cmd.label}", level = LogLevel.INFO)
      } else {
        log.operation("指令失败: ${cmd.label}", level = LogLevel.WARNING)
      }
      return success
    }
    return false
  }

  /** Port of Dart `sendQgjCommand` — raw QGJ cmd id with payload, awaits the reply. */
  suspend fun sendQgjCommand(
    cmdId: Int,
    payload: ByteArray = ByteArray(0),
  ): QgjResponse? {
    if (state != ConnectionState.READY || _protocol != ProtocolType.QGJ) {
      return null
    }
    val feb1 = _feb1Char ?: return null

    val frame = buildQgjCommand(cmdId, payload)
    return runGattOperation(priority = GattOperationPriority.HIGH) {
      val previous = _qgjResponseDeferreds.remove(cmdId)
      if (previous != null && !previous.isCompleted) {
        previous.completeExceptionally(IllegalStateException("QGJ command superseded"))
      }
      val deferred = CompletableDeferred<QgjResponse?>()
      _qgjResponseDeferreds[cmdId] = deferred
      try {
        writeCharacteristic(feb1, frame, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        withTimeoutOrNull(BleTimings.commandAckTimeout) { deferred.await() }
      } finally {
        if (_qgjResponseDeferreds[cmdId] === deferred) {
          _qgjResponseDeferreds.remove(cmdId)
        }
      }
    }
  }

  /** Port of Dart `checkQgjSeatSupport` — key-version (0x1005) seat gate. */
  suspend fun checkQgjSeatSupport(): Boolean? {
    if (!isProtocolLoggedIn || _protocol != ProtocolType.QGJ) return null
    val response = sendQgjCommand(QgjCommandIds.keyVersionGet)
    return parseQgjSeatSupport(response)
  }

  /**
   * Port of Dart `writeStandardHex` — standard-stack raw hex write (official
   * `writeData` path) after LOGIN; TLink appends the session token.
   */
  suspend fun writeStandardHex(hexData: String): Boolean {
    if (state != ConnectionState.READY ||
      (_protocol != ProtocolType.KKS && _protocol != ProtocolType.TLINK) ||
      _writeChar == null
    ) {
      return false
    }
    val t = _token
    val frame = if (_protocol == ProtocolType.TLINK) "$hexData${t ?: return false}" else hexData
    val bytes = aesEcbEncrypt(_model.aesKey, frame)
    runGattOperation(priority = GattOperationPriority.HIGH) {
      writeCharacteristic(_writeChar!!, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
    }
    return true
  }

  // =========================================================================
  // Public API — TLink induction / bond / OTA / riding mode
  // =========================================================================

  /** Port of Dart `checkTlinkInduction` — query induction switch + distance. */
  suspend fun checkTlinkInduction(): TLinkInductionStatusResponse? {
    if (!isProtocolLoggedIn || _protocol != ProtocolType.TLINK) {
      return null
    }
    val previous = _tlinkInductionStatusDeferred.getAndSet(null)
    if (previous != null && !previous.isCompleted) {
      previous.complete(null)
    }
    val deferred = CompletableDeferred<TLinkInductionStatusResponse?>()
    _tlinkInductionStatusDeferred.getAndSet(deferred)
    val written = writeStandardHex(TLINK_INDUCTION_CHECK_PLAIN)
    if (!written) {
      _tlinkInductionStatusDeferred.compareAndSet(deferred, null)
      return null
    }
    try {
      return withTimeoutOrNull(BleTimings.commandAckTimeout) { deferred.await() }
    } finally {
      _tlinkInductionStatusDeferred.compareAndSet(deferred, null)
    }
  }

  /** Port of Dart `openTlinkInduction` — open induction mode; pairing is caller's job. */
  suspend fun openTlinkInduction(): Boolean {
    if (!isProtocolLoggedIn || _protocol != ProtocolType.TLINK) {
      return false
    }
    val previous = _tlinkInductionSetDeferred.getAndSet(null)
    if (previous != null && !previous.isCompleted) {
      previous.complete(false)
    }
    val deferred = CompletableDeferred<Boolean>()
    _tlinkInductionSetDeferred.getAndSet(deferred)
    val written = writeStandardHex(TLINK_INDUCTION_OPEN_PLAIN)
    if (!written) {
      _tlinkInductionSetDeferred.compareAndSet(deferred, null)
      return false
    }
    try {
      return withTimeoutOrNull(BleTimings.commandAckTimeout) { deferred.await() } ?: false
    } finally {
      _tlinkInductionSetDeferred.compareAndSet(deferred, null)
    }
  }

  /** Port of Dart `closeTlinkInduction` — close induction mode; bond removal is caller's job. */
  suspend fun closeTlinkInduction(): Boolean {
    if (!isProtocolLoggedIn || _protocol != ProtocolType.TLINK) {
      return false
    }
    val previous = _tlinkInductionSetDeferred.getAndSet(null)
    if (previous != null && !previous.isCompleted) {
      previous.complete(false)
    }
    val deferred = CompletableDeferred<Boolean>()
    _tlinkInductionSetDeferred.getAndSet(deferred)
    val written = writeStandardHex(TLINK_INDUCTION_CLOSE_PLAIN)
    if (!written) {
      _tlinkInductionSetDeferred.compareAndSet(deferred, null)
      return false
    }
    try {
      return withTimeoutOrNull(BleTimings.commandAckTimeout) { deferred.await() } ?: false
    } finally {
      _tlinkInductionSetDeferred.compareAndSet(deferred, null)
    }
  }

  /** Port of Dart `setTlinkInductionDistance` — proximity distance level 0–30. */
  suspend fun setTlinkInductionDistance(progress: Int): Boolean {
    if (!isProtocolLoggedIn || _protocol != ProtocolType.TLINK) {
      return false
    }
    val deferred = CompletableDeferred<Boolean>()
    _tlinkProximityDistanceDeferred.getAndSet(deferred)
    val written = writeStandardHex(buildTLinkInductionDistancePlain(progress))
    if (!written) {
      _tlinkProximityDistanceDeferred.compareAndSet(deferred, null)
      return false
    }
    try {
      return withTimeoutOrNull(BleTimings.commandAckTimeout) { deferred.await() } ?: false
    } finally {
      _tlinkProximityDistanceDeferred.compareAndSet(deferred, null)
    }
  }

  /** Port of Dart `createBond` — system BLE pairing (official `pairingDevice`). */
  suspend fun createBond(quiet: Boolean = false): Boolean {
    val device = _device ?: return false
    return try {
      if (device.bondState == BluetoothDevice.BOND_BONDED) return true
      if (!device.createBond()) {
        log.ble("系统蓝牙配对失败", detail = "createBond() returned false", level = LogLevel.WARNING)
        return false
      }
      val ok = withTimeoutOrNull(15.seconds) { awaitBondState(BluetoothDevice.BOND_BONDED) } == true
      if (!quiet) {
        log.ble(
          if (ok) "系统蓝牙配对成功" else "系统蓝牙配对未完成",
          level = if (ok) LogLevel.INFO else LogLevel.WARNING,
        )
      }
      ok
    } catch (e: Exception) {
      log.ble("系统蓝牙配对失败", detail = e.toString(), level = LogLevel.WARNING)
      false
    }
  }

  /** Port of Dart `removeBond` — remove system bond (official `removeBleBond`). */
  suspend fun removeBond(quiet: Boolean = false): Boolean {
    val device = _device ?: return false
    return try {
      if (device.bondState == BluetoothDevice.BOND_NONE) return true
      val ok = removeBondCompat(device)
      if (!quiet) {
        log.ble("系统蓝牙配对已移除", level = LogLevel.INFO)
      }
      ok
    } catch (e: Exception) {
      log.ble("移除系统配对失败", detail = e.toString(), level = LogLevel.WARNING)
      false
    }
  }

  /** Port of Dart `readRemoteRssi` — one-shot RSSI read on the connected device. */
  suspend fun readRemoteRssi(): Int? {
    val gatt = _gatt ?: return null
    if (state == ConnectionState.DISCONNECTED) return null
    return try {
      val deferred = CompletableDeferred<Int>()
      _rssiDeferred = deferred
      if (!gatt.readRemoteRssi()) {
        _rssiDeferred = null
        return null
      }
      withTimeoutOrNull(5.seconds) { deferred.await() }
    } catch (e: Exception) {
      log.ble("读取 RSSI 失败", detail = e.toString(), level = LogLevel.DEBUG)
      null
    }
  }

  /** Port of Dart `writeOtaOrder` — official OTA control characteristic (gatt7000). */
  suspend fun writeOtaOrder(message: ByteArray): Boolean {
    if (state != ConnectionState.READY) return false
    val char = findCharByUuid(BleUuids.otaOrder) ?: return false
    runGattOperation(priority = GattOperationPriority.HIGH) {
      writeCharacteristic(char, message, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
    }
    return true
  }

  /** Port of Dart `writeOtaFileChunk` — official OTA file characteristic (gatt7001). */
  suspend fun writeOtaFileChunk(message: ByteArray): Boolean {
    if (state != ConnectionState.READY) return false
    val char = findCharByUuid(BleUuids.otaFile) ?: return false
    runGattOperation(priority = GattOperationPriority.HIGH) {
      writeCharacteristic(char, message, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
    }
    return true
  }

  /** Port of Dart `writeFbb2` — raw hex write to fbb2. */
  suspend fun writeFbb2(hexData: String) {
    if (state != ConnectionState.READY || _fbb2Char == null) return
    val bytes = hexToBytes(hexData)
    runGattOperation(priority = GattOperationPriority.HIGH) {
      writeCharacteristic(_fbb2Char!!, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
    }
  }

  /**
   * Port of Dart `setRidingMode` — read fcc1, patch the podg value, write
   * back, readback-verify (official QGJ riding-mode path).
   */
  suspend fun setRidingMode(mode: RidingMode): Boolean {
    if (state != ConnectionState.READY) return false

    log.operation("切换模式: ${mode.label}", detail = "code=${mode.code}")

    return try {
      val fcc1 = _fcc1Char ?: findFcc1Char() ?: return false

      val response = runGattOperation(priority = GattOperationPriority.HIGH) {
        val current = readCharacteristic(fcc1)
        val data = buildQgjRidingModeFrame(current.map { it.toInt() }, mode)
          ?: throw IllegalArgumentException("fcc1 状态数据不完整")
        val frame = ByteArray(data.size) { data[it].toByte() }
        writeCharacteristic(fcc1, frame, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        delay(BleTimings.fccReadbackDelay)
        readCharacteristic(fcc1)
      }
      _ridingMode.value = parseQgjRidingMode(response.map { it.toInt() }) ?: mode

      addRidingMode(_ridingMode.value)
      log.operation("模式已切换: ${_ridingMode.value.label}", level = LogLevel.INFO)
      true
    } catch (e: Exception) {
      log.operation("模式切换失败", detail = e.toString(), level = LogLevel.ERROR)
      false
    }
  }

  // =========================================================================
  // Public API — teardown
  // =========================================================================

  /**
   * Port of Dart `dispose` — fail pending ops, cancel timers/jobs, disconnect
   * the device and stop the event loop. Idempotent.
   */
  suspend fun dispose() {
    if (_disposed) return
    _disposed = true

    completePendingGattOperations(IllegalStateException("ConnectionManager disposed"))

    // Cancel timers.
    cancelHeartbeat()
    disarmReadyWatchdog()

    // Complete pending operations.
    completePendingOperations(IllegalStateException("ConnectionManager disposed"))

    // Disconnect the device.
    try {
      closeGatt()
    } catch (e: Exception) {
      log.ble("释放连接时断开设备失败", detail = e.toString(), level = LogLevel.WARNING)
    }

    // Stop the event loop and, when the scope is manager-owned, the scope.
    reconnectJob?.cancel()
    reconnectJob = null
    eventLoopJob?.cancel()
    eventLoopJob = null
    if (ownsScope) {
      scope.cancel()
    }
  }

  // =========================================================================
  // GATT bridge (BluetoothGattCallback → event loop)
  // =========================================================================

  /**
   * Single [BluetoothGattCallback] for all connections. Every callback is
   * posted to [gattEvents]; the event loop in [init] dispatches them. Both
   * pre-33 and 33+ read/notify overloads are overridden so values arrive
   * regardless of SDK level (minSdk 26).
   */
  private val gattCallback = object : BluetoothGattCallback() {
    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
      gattEvents.trySend(GattEvent.ConnectionStateChanged(gatt, status, newState))
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
      gattEvents.trySend(GattEvent.ServicesDiscovered(status))
    }

    @Deprecated("Deprecated in API 33; kept for minSdk 26 devices")
    @Suppress("DEPRECATION")
    override fun onCharacteristicRead(
      gatt: BluetoothGatt?,
      characteristic: BluetoothGattCharacteristic,
      status: Int,
    ) {
      gattEvents.trySend(
        GattEvent.CharacteristicRead(characteristic, status, characteristic.value ?: ByteArray(0)),
      )
    }

    override fun onCharacteristicWrite(
      gatt: BluetoothGatt?,
      characteristic: BluetoothGattCharacteristic,
      status: Int,
    ) {
      gattEvents.trySend(GattEvent.CharacteristicWrite(characteristic, status))
    }

    override fun onDescriptorWrite(
      gatt: BluetoothGatt?,
      descriptor: BluetoothGattDescriptor,
      status: Int,
    ) {
      gattEvents.trySend(GattEvent.DescriptorWrite(descriptor, status))
    }

    @Deprecated("Deprecated in API 33; kept for minSdk 26 devices")
    @Suppress("DEPRECATION")
    override fun onCharacteristicChanged(
      gatt: BluetoothGatt?,
      characteristic: BluetoothGattCharacteristic,
    ) {
      gattEvents.trySend(
        GattEvent.CharacteristicChanged(characteristic, characteristic.value ?: ByteArray(0)),
      )
    }

    // API 33+ invokes ONLY these value-carrying overloads (note: non-null
    // parameter types, unlike the deprecated pair); without them reads and
    // notifications are dead on Android 13+.
    override fun onCharacteristicRead(
      gatt: BluetoothGatt,
      characteristic: BluetoothGattCharacteristic,
      value: ByteArray,
      status: Int,
    ) {
      gattEvents.trySend(GattEvent.CharacteristicRead(characteristic, status, value))
    }

    override fun onCharacteristicChanged(
      gatt: BluetoothGatt,
      characteristic: BluetoothGattCharacteristic,
      value: ByteArray,
    ) {
      gattEvents.trySend(GattEvent.CharacteristicChanged(characteristic, value))
    }

    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
      gattEvents.trySend(GattEvent.MtuChanged(mtu, status))
    }

    override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
      gattEvents.trySend(GattEvent.ReadRemoteRssi(rssi, status))
    }
  }

  /**
   * Event-loop dispatch (single consumer of [gattEvents]). Resumes the
   * deferreds parked by the suspend GATT primitives and routes notifications
   * to the protocol handlers.
   */
  private fun handleGattEvent(event: GattEvent) {
    when (event) {
      is GattEvent.ConnectionStateChanged -> {
        // Drop events from a superseded GATT instance: closeGatt() during
        // retry/reconnect lets the OLD gatt's late STATE_DISCONNECTED arrive
        // after a new connectGattOnce() has installed its deferred — without
        // this identity check the stale event fails the fresh connect.
        if (event.gatt == null || event.gatt !== _gatt) return
        when (event.newState) {
          BluetoothProfile.STATE_CONNECTED -> {
            _connectDeferred?.complete(Unit)
            _connectDeferred = null
          }
          BluetoothProfile.STATE_DISCONNECTED -> {
            // A disconnect while connect() is awaiting fails the connect.
            val pendingConnect = _connectDeferred
            if (pendingConnect != null) {
              pendingConnect.completeExceptionally(
                GattException(event.status, "connect failed status=${event.status}"),
              )
              _connectDeferred = null
            }
            onDisconnected()
          }
        }
      }

      is GattEvent.ServicesDiscovered -> {
        val d = _discoveryDeferred
        _discoveryDeferred = null
        if (d != null) {
          if (event.status == BluetoothGatt.GATT_SUCCESS) d.complete(Unit)
          else d.completeExceptionally(GattException(event.status, "discoverServices status=${event.status}"))
        }
      }

      is GattEvent.CharacteristicRead -> {
        val d = readDeferreds.remove(event.characteristic.uuid)
        if (d != null) {
          if (event.status == BluetoothGatt.GATT_SUCCESS) d.complete(event.value)
          else d.completeExceptionally(GattException(event.status, "read status=${event.status}"))
        }
      }

      is GattEvent.CharacteristicWrite -> {
        val d = writeDeferreds.remove(event.characteristic.uuid)
        if (d != null) {
          if (event.status == BluetoothGatt.GATT_SUCCESS) d.complete(Unit)
          else d.completeExceptionally(GattException(event.status, "write status=${event.status}"))
        }
      }

      is GattEvent.DescriptorWrite -> {
        val d = descriptorWriteDeferreds.remove(event.descriptor.uuid)
        if (d != null) {
          if (event.status == BluetoothGatt.GATT_SUCCESS) d.complete(Unit)
          else d.completeExceptionally(GattException(event.status, "descriptor write status=${event.status}"))
        }
      }

      is GattEvent.CharacteristicChanged -> {
        val uuid = event.characteristic?.uuid
        when {
          // char==null is the API-33+ characteristic-less overload: route
          // best-effort to the primary notify slot (the app only subscribes
          // a handful of characteristics and feb6/feb2 carries the protocol).
          event.characteristic == null || uuid == _notifyChar?.uuid -> {
            // _notifyChar is feb6 for KKS/TLink, feb2 for QGJ (Dart attaches
            // _onStandardNotify / _onQgjNotify to the same slot).
            if (_protocol == ProtocolType.QGJ) onQgjNotify(event.value)
            else onStandardNotify(event.value)
          }
          uuid == _gpsNotifyChar?.uuid -> onQgjGpsNotify(event.value)
          uuid == _fbb2Char?.uuid -> onFbb2Notify(event.value)
        }
      }

      is GattEvent.MtuChanged -> {
        val d = _mtuDeferred
        _mtuDeferred = null
        if (d != null) {
          if (event.status == BluetoothGatt.GATT_SUCCESS) d.complete(event.mtu)
          else d.completeExceptionally(GattException(event.status, "mtu status=${event.status}"))
        }
      }

      is GattEvent.ReadRemoteRssi -> {
        val d = _rssiDeferred
        _rssiDeferred = null
        if (d != null) {
          if (event.status == BluetoothGatt.GATT_SUCCESS) d.complete(event.rssi)
          else d.completeExceptionally(GattException(event.status, "rssi status=${event.status}"))
        }
      }
    }
  }

  // =========================================================================
  // GATT primitives (write / read / notify / mtu / rssi)
  // =========================================================================

  /**
   * Suspend write with response / without response (Dart
   * `BluetoothCharacteristic.write(..., withoutResponse: …)`). Resumes when
   * `onCharacteristicWrite` fires or throws [GattException] on failure.
   */
  private suspend fun writeCharacteristic(
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray,
    writeType: Int,
  ) {
    val gatt = _gatt ?: throw IllegalStateException("GATT is null")
    val deferred = CompletableDeferred<Unit>()
    writeDeferreds[characteristic.uuid] = deferred
    // Use the modern API on API 33+; fall back to the deprecated path on older
    // devices. The deprecated API-18 path is kept for minSdk 26 compatibility.
    val started = if (Build.VERSION.SDK_INT >= 33) {
      @Suppress("NewApi")
      gatt.writeCharacteristic(characteristic, value, writeType) == BluetoothStatusCodes.SUCCESS
    } else {
      @Suppress("DEPRECATION")
      characteristic.value = value
      @Suppress("DEPRECATION")
      characteristic.writeType = writeType
      gatt.writeCharacteristic(characteristic)
    }
    if (!started) {
      writeDeferreds.remove(characteristic.uuid)
      throw IllegalStateException("writeCharacteristic failed: ${characteristic.uuid}")
    }
    try {
      deferred.await()
    } finally {
      writeDeferreds.remove(characteristic.uuid)
    }
  }

  /** Suspend read (Dart `BluetoothCharacteristic.read()`). */
  private suspend fun readCharacteristic(characteristic: BluetoothGattCharacteristic): ByteArray {
    val gatt = _gatt ?: throw IllegalStateException("GATT is null")
    val deferred = CompletableDeferred<ByteArray>()
    readDeferreds[characteristic.uuid] = deferred
    if (!gatt.readCharacteristic(characteristic)) {
      readDeferreds.remove(characteristic.uuid)
      throw IllegalStateException("readCharacteristic failed: ${characteristic.uuid}")
    }
    try {
      return deferred.await()
    } finally {
      readDeferreds.remove(characteristic.uuid)
    }
  }

  /**
   * Subscribe a characteristic: `setCharacteristicNotification(true)` +
   * CCCD (0x2902) write with [ENABLE_INDICATION_VALUE] or
   * [ENABLE_NOTIFICATION_VALUE] (Dart `setNotifyValue`).
   */
  private suspend fun setNotify(
    characteristic: BluetoothGattCharacteristic,
    indications: Boolean,
  ) {
    val gatt = _gatt ?: throw IllegalStateException("GATT is null")
    if (!gatt.setCharacteristicNotification(characteristic, true)) {
      throw IllegalStateException("setCharacteristicNotification failed: ${characteristic.uuid}")
    }
    val cccd = characteristic.getDescriptor(UUID.fromString(CCCD_UUID))
      ?: throw IllegalStateException("CCCD descriptor missing: ${characteristic.uuid}")
    val value = if (indications) {
      BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
    } else {
      BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
    }
    writeDescriptor(cccd, value)
  }

  /** Suspend CCCD / descriptor write. */
  private suspend fun writeDescriptor(descriptor: BluetoothGattDescriptor, value: ByteArray) {
    val gatt = _gatt ?: throw IllegalStateException("GATT is null")
    val deferred = CompletableDeferred<Unit>()
    descriptorWriteDeferreds[descriptor.uuid] = deferred
    descriptor.value = value
    if (!gatt.writeDescriptor(descriptor)) {
      descriptorWriteDeferreds.remove(descriptor.uuid)
      throw IllegalStateException("writeDescriptor failed: ${descriptor.uuid}")
    }
    try {
      deferred.await()
    } finally {
      descriptorWriteDeferreds.remove(descriptor.uuid)
    }
  }

  /** Disconnect + close the current [BluetoothGatt] (idempotent). */
  private fun closeGatt() {
    val gatt = _gatt
    if (gatt == null) return
    _gatt = null
    try {
      gatt.disconnect()
    } catch (_: Exception) {
    }
    try {
      gatt.close()
    } catch (_: Exception) {
    }
  }

  /** Suspend until the system bond state reaches [target] (max 15 s at call sites). */
  private suspend fun awaitBondState(target: Int): Boolean = suspendCancellableCoroutine { cont ->
    val registerContext = context
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
        val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
        if (state == target && !cont.isCancelled) {
          // Unregister BEFORE resuming: invokeOnCancellation only runs on the
          // cancellation path, so the success path must clean up itself or
          // the receiver stays registered forever — and a later bond broadcast
          // would resume the already-completed continuation (ISE crash).
          runCatching { registerContext.unregisterReceiver(this) }
          cont.resume(true)
        }
      }
    }
    val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
    if (Build.VERSION.SDK_INT >= 33) {
      registerContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      registerContext.registerReceiver(receiver, filter)
    }
    cont.invokeOnCancellation {
      try {
        registerContext.unregisterReceiver(receiver)
      } catch (_: Exception) {
      }
    }
  }

  /**
   * Remove system bond using a safe approach.
   * - On Android 8–12: use reflection (hidden API `removeBond()`).
   * - On Android 13+: use `BluetoothDevice.createBond()` with transport=LE
   *   to trigger a re-pair; user must manually unpair from Bluetooth settings.
   * - Safe fallback if reflection fails: log and return false.
   */
  private fun removeBondCompat(device: BluetoothDevice): Boolean = try {
    if (Build.VERSION.SDK_INT >= 33) {
      // On Android 13+, `removeBond` is greylisted and may be blocked.
      // Try reflection first, fall back to prompting user via settings.
      try {
        val method = BluetoothDevice::class.java.getMethod("removeBond")
        method.invoke(device) as Boolean
      } catch (e: NoSuchMethodException) {
        log.ble("removeBond not available on API 33+, user must unpair manually", level = LogLevel.WARNING)
        false
      }
    } else {
      val method = BluetoothDevice::class.java.getMethod("removeBond")
      method.invoke(device) as Boolean
    }
  } catch (e: Exception) {
    log.ble("移除系统配对失败", detail = e.toString(), level = LogLevel.DEBUG)
    false
  }

  // =========================================================================
  // Connect pipeline
  // =========================================================================

  /**
   * Port of Dart `_connectDeviceWithRetry`. TLink stacks get 6 attempts,
   * others 3; QGJ retries every 300 ms, others 500 ms. Between attempts
   * [recoverFailedConnect] clears GATT 133 / timeout failures.
   */
  private suspend fun connectDeviceWithRetry(
    device: BluetoothDevice,
    timeout: Duration,
    attempts: Int,
    retryDelay: Duration,
  ) {
    var attempt = 1
    while (attempt <= attempts) {
      try {
        connectGattOnce(device, timeout)
        return
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (e: Exception) {
        if (attempt == attempts) throw e
        log.ble("连接失败，短暂重试 $attempt/$attempts", detail = e.toString(), level = LogLevel.DEBUG)
        recoverFailedConnect(e)
        delay(retryDelay)
        attempt++
      }
    }
    throw IllegalStateException("连接失败")
  }

  /**
   * `connectGatt(TRANSPORT_LE)` and await STATE_CONNECTED within [timeout]
   * (Dart `device.connect(timeout:)`). A disconnect while awaiting fails the
   * connect with the Android status code so [recoverFailedConnect] can spot
   * GATT 133.
   */
  private suspend fun connectGattOnce(device: BluetoothDevice, timeout: Duration) {
    closeGatt()
    val deferred = CompletableDeferred<Unit>()
    _connectDeferred = deferred
    val gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
      ?: throw IllegalStateException("connectGatt returned null")
    _gatt = gatt
    try {
      withTimeout(timeout) { deferred.await() }
    } catch (e: TimeoutCancellationException) {
      closeGatt()
      throw e
    } catch (e: kotlinx.coroutines.CancellationException) {
      closeGatt()
      throw e
    }
  }

  /** Port of Dart `_ensureKksBond` — KKS vehicles need a system bond on Android. */
  private suspend fun ensureKksBond(device: BluetoothDevice) {
    if (_connectionContext?.stack != OfficialBleStack.KKS) return
    try {
      if (device.bondState != BluetoothDevice.BOND_BONDED) {
        device.createBond()
      }
    } catch (e: Exception) {
      log.ble("KKS 配对失败", detail = e.toString(), level = LogLevel.WARNING)
      throw e
    }
  }

  /** Port of Dart `_requestQgjMtu` — QGJ requires MTU 515 (Android only). */
  private suspend fun requestQgjMtu(device: BluetoothDevice) {
    if (_connectionContext?.stack != OfficialBleStack.QGJ) return
    try {
      val gatt = _gatt ?: return
      val deferred = CompletableDeferred<Int>()
      _mtuDeferred = deferred
      if (!gatt.requestMtu(BleTimings.qgjRequestedMtu)) {
        _mtuDeferred = null
        log.ble("MTU 请求失败", detail = "requestMtu returned false", level = LogLevel.DEBUG)
        return
      }
      val mtu = withTimeoutOrNull(5.seconds) { deferred.await() }
      log.ble("MTU 已请求", detail = mtu?.toString(), level = LogLevel.DEBUG)
    } catch (e: Exception) {
      log.ble("MTU 请求失败", detail = e.toString(), level = LogLevel.DEBUG)
    }
  }

  /**
   * Port of Dart `_recoverFailedConnect` — for GATT 133 or connect timeouts,
   * tear down the stale GATT and wait out the Android recovery delay.
   */
  private suspend fun recoverFailedConnect(error: Throwable) {
    val isGatt133 = error is GattException && error.status == GATT_STATUS_ERROR
    val isTimeout = error is TimeoutCancellationException ||
      error.message?.contains("timed out", ignoreCase = true) == true
    if (!isGatt133 && !isTimeout) return
    try {
      closeGatt()
    } catch (e: Exception) {
      log.ble("连接失败恢复断开设备失败", detail = e.toString(), level = LogLevel.DEBUG)
    }
    resetCharacteristics()
    log.ble(
      "连接失败后已清理 GATT 状态",
      detail = if (isGatt133) "android-code: 133" else "timeout",
      level = LogLevel.DEBUG,
    )
    delay(
      if (isGatt133) BleTimings.androidGattErrorRecoveryDelay
      else BleTimings.failedConnectRecoveryDelay,
    )
  }

  // =========================================================================
  // Protocol setup (KKS / TLink / QGJ + subscriptions)
  // =========================================================================

  /**
   * Port of Dart `_discoverAndSetup` — identify the protocol stack from the
   * discovered GATT services and run the matching setup. Expected stack from
   * [OfficialBleConnectionContext] wins when it contradicts the services.
   */
  private suspend fun discoverAndSetup() {
    try {
      val gatt = _gatt ?: throw IllegalStateException("GATT is null")
      val deferred = CompletableDeferred<Unit>()
      _discoveryDeferred = deferred
      if (!gatt.discoverServices()) {
        _discoveryDeferred = null
        throw IllegalStateException("discoverServices() returned false")
      }
      // Single source of truth: BleTimings.discoveryTimeout (15 s, mirroring
      // flutter_blue_plus) — the value used to be hardcoded here, so tuning
      // the constant had no effect.
      withTimeout(BleTimings.discoveryTimeout) { deferred.await() }

      val services = gatt.services
      log.ble(
        "发现 ${services.size} 个服务",
        detail = services.joinToString(", ") { it.uuid.toString() },
      )

      val hasFeb0 = services.any { it.uuid.toString().contains("feb0") }
      val hasFee5 = services.any { it.uuid.toString().contains("fee5") }
      val expectedStack = _connectionContext?.stack

      when {
        (expectedStack == OfficialBleStack.QGJ && hasFeb0) || (expectedStack == null && hasFeb0) -> {
          _protocol = ProtocolType.QGJ
          _lastKnownProtocol = _protocol
          log.ble("识别协议: QGJ (feb0)", level = LogLevel.INFO)
          setupQgj(services)
        }
        hasFee5 &&
          (expectedStack == null ||
            expectedStack == OfficialBleStack.KKS ||
            expectedStack == OfficialBleStack.TLINK) -> {
          _protocol = if (expectedStack == OfficialBleStack.TLINK) ProtocolType.TLINK else ProtocolType.KKS
          _lastKnownProtocol = _protocol
          if (_protocol == ProtocolType.TLINK) setupTLink(services) else setupKks(services)
        }
        else -> {
          _protocol = ProtocolType.UNKNOWN
          log.ble("未识别协议", level = LogLevel.WARNING)
          if (expectedStack != null) {
            throw IllegalStateException("GATT services do not match ${expectedStack.name} model")
          }
        }
      }
    } catch (e: Exception) {
      log.ble("服务发现/设置失败", detail = e.toString(), level = LogLevel.ERROR)
      clearRuntimeResources(disconnectDevice = true)
      resetCharacteristics()
      setState(ConnectionState.DISCONNECTED)
      throw e
    }
  }

  /** Port of Dart `_setupKks` — feb5 write / feb6 notify, token request. */
  private suspend fun setupKks(services: List<BluetoothGattService>) {
    val service = services.firstOrNull { it.uuid.toString().contains("fee5") }
      ?: throw IllegalStateException("KKS fee5 service not found")

    for (c in service.characteristics) {
      val uuid = c.uuid.toString()
      if (uuid.contains("feb5")) _writeChar = c
      if (uuid.contains("feb6")) _notifyChar = c
    }

    if (_notifyChar == null || _writeChar == null) {
      throw IllegalStateException("KKS fee5 characteristics are incomplete")
    }
    enableNotifyOrIndicate(_notifyChar!!)
    val tokenReq = buildTokenRequest(_model.aesKey)
    runGattOperation(priority = GattOperationPriority.HIGH) {
      writeCharacteristic(_writeChar!!, tokenReq, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
    }
  }

  /** Port of Dart `_setupTLink` — fee5 + 180a + fcc0 required; then token request. */
  private suspend fun setupTLink(services: List<BluetoothGattService>) {
    val service = services.firstOrNull { it.uuid.toString().contains("fee5") }
      ?: throw IllegalStateException("TLink fee5 service not found")
    val hasDeviceInfo = services.any { it.uuid.toString().contains("180a") }
    if (!hasDeviceInfo || !services.any { it.uuid.toString().contains("fcc0") }) {
      throw IllegalStateException("TLink GATT services are incomplete")
    }

    for (c in service.characteristics) {
      val uuid = c.uuid.toString()
      if (uuid.contains("feb5")) _writeChar = c
      if (uuid.contains("feb6")) _notifyChar = c
    }
    subscribeFcc0(services)
    subscribeOptionalTLinkServices(services)

    if (_notifyChar == null || _writeChar == null) {
      throw IllegalStateException("TLink fee5 characteristics are incomplete")
    }
    enableNotifyOrIndicate(_notifyChar!!)
    val tokenReq = buildTLinkTokenRequest(_model.aesKey)
    runGattOperation(priority = GattOperationPriority.HIGH) {
      writeCharacteristic(_writeChar!!, tokenReq, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
    }
  }

  /**
   * Port of Dart `_setupQgj` — feb1/feb2/feb3; subscribe fcc0 + GPS; feb2
   * notifications (forced indications); QGJ login frame on feb1.
   */
  private suspend fun setupQgj(services: List<BluetoothGattService>) {
    val service = services.firstOrNull { it.uuid.toString().contains("feb0") }
      ?: throw IllegalStateException("QGJ feb0 service not found")

    for (c in service.characteristics) {
      val uuid = c.uuid.toString()
      if (uuid.contains("feb1")) _feb1Char = c
      if (uuid.contains("feb2")) _feb2Char = c
      if (uuid.contains("feb3")) _feb3Char = c
    }

    log.ble(
      "QGJ characteristics",
      detail = "feb1=${_feb1Char != null}, feb2=${_feb2Char != null}, feb3=${_feb3Char != null}",
    )

    // fcc0 fcc1/fbb1/fcc2/fbb2 subscription is mandatory (official app step,
    // otherwise the device times out and drops the link).
    subscribeFcc0(services)
    subscribeQgjGps(services)

    val feb2 = _feb2Char
    if (feb2 != null) {
      enableNotifyOrIndicate(feb2, forceIndications = true)
      _notifyChar = feb2
    }

    val feb1 = _feb1Char
    if (feb1 != null) {
      val ctx = _connectionContext
      if (ctx != null && !ctx.hasQgjCredentials) {
        throw IllegalStateException("QGJ login credentials are unavailable")
      }
      val loginFrame = buildQgjLoginFrame(password = _qgjLoginPassword, userId = _qgjUserId)
      runGattOperation(priority = GattOperationPriority.HIGH) {
        writeCharacteristic(feb1, loginFrame, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
      }
    }
  }

  /**
   * Port of Dart `_subscribeFcc0` — records fcc1/fcc2/fbb1/fbb2 and subscribes
   * every notifiable characteristic. fbb2 notifications are routed via the
   * event loop using [_fbb2Char].
   */
  private suspend fun subscribeFcc0(services: List<BluetoothGattService>) {
    val fcc0Service = services.filter { it.uuid.toString().contains("fcc0") }
    if (fcc0Service.isEmpty()) {
      log.ble("fcc0 服务未找到", level = LogLevel.WARNING)
      return
    }

    val service = fcc0Service.first()
    var subscribed = 0
    for (c in service.characteristics) {
      val uuid = c.uuid.toString()
      if (uuid.contains("fcc1")) _fcc1Char = c
      if (uuid.contains("fcc2")) _fcc2Char = c
      if (uuid.contains("fbb1")) _fbb1Char = c
      if (uuid.contains("fbb2")) _fbb2Char = c
      if (c.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or
          BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
      ) {
        try {
          enableNotifyOrIndicate(c)
          subscribed++
        } catch (e: Exception) {
          log.ble("订阅 $uuid 失败", detail = e.toString(), level = LogLevel.DEBUG)
        }
      }
    }
    log.ble(
      "fcc0 已订阅 $subscribed 个特征",
      detail = "fcc1=${_fcc1Char != null}, fcc2=${_fcc2Char != null}, fbb1=${_fbb1Char != null}, fbb2=${_fbb2Char != null}",
      level = LogLevel.INFO,
    )
  }

  /** Port of Dart `_subscribeQgjGps` — fe01/fe02 + fe03 GPS notification. */
  private suspend fun subscribeQgjGps(services: List<BluetoothGattService>) {
    val fe01Service = services.filter { it.uuid.toString().contains("fe01") }
    if (fe01Service.isEmpty()) return

    for (c in fe01Service.first().characteristics) {
      val uuid = c.uuid.toString()
      if (uuid.contains("fe02")) _fe02Char = c
      if (uuid.contains("fe03")) _fe03Char = c
    }

    val canWriteGps = _fe02Char?.properties?.let {
      it and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0 ||
        it and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
    } ?: false
    val canNotifyGps = _fe03Char?.properties?.let {
      it and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
    } ?: false
    if (!canWriteGps || !canNotifyGps || _fe03Char == null) {
      log.ble(
        "fe01 GPS 服务不完整",
        detail = "fe02=$canWriteGps, fe03=$canNotifyGps",
        level = LogLevel.DEBUG,
      )
      return
    }

    try {
      enableNotifyOrIndicate(_fe03Char!!)
      _gpsNotifyChar = _fe03Char
      log.ble("fe03 GPS 通知已订阅", level = LogLevel.INFO)
    } catch (e: Exception) {
      log.ble("fe03 GPS 通知订阅失败", detail = e.toString(), level = LogLevel.DEBUG)
    }
  }

  /** Port of Dart `_subscribeOptionalTLinkServices` (2000 / 7000 / fe01). */
  private suspend fun subscribeOptionalTLinkServices(services: List<BluetoothGattService>) {
    for (service in services) {
      val serviceUuid = service.uuid.toString()
      if (!serviceUuid.contains("2000") &&
        !serviceUuid.contains("7000") &&
        !serviceUuid.contains("fe01")
      ) {
        continue
      }
      for (c in service.characteristics) {
        if (c.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or
            BluetoothGattCharacteristic.PROPERTY_INDICATE) == 0
        ) {
          continue
        }
        try {
          enableNotifyOrIndicate(c)
        } catch (e: Exception) {
          log.ble(
            "订阅 TLink 可选特征失败",
            detail = c.uuid.toString(),
            level = LogLevel.DEBUG,
          )
        }
      }
    }
  }

  /**
   * Port of Dart `_enableNotifyOrIndicate` — notify, or indications when
   * forced or the characteristic only supports indications (Android).
   */
  private suspend fun enableNotifyOrIndicate(
    characteristic: BluetoothGattCharacteristic,
    forceIndications: Boolean = false,
  ) {
    runGattOperation(priority = GattOperationPriority.HIGH) {
      val useIndications = forceIndications ||
        (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 &&
          characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0)
      setNotify(characteristic, useIndications)
    }
  }

  // =========================================================================
  // Notification handling
  // =========================================================================

  /** Port of Dart `_onStandardNotify` — feb6 (KKS/TLink) notification. */
  private fun onStandardNotify(value: ByteArray) {
    if (_disposed) return
    log.ble("← 收到数据", detail = bytesToSpacedHex(value.map { it.toInt() }))
    if (_protocol == ProtocolType.TLINK) {
      val response = parseTLinkResponse(_model.aesKey, value)
      scope.launch { handleTLinkResponse(response) }
      return
    }
    val response = parseResponse(_model.aesKey, value)
    addResponse(response)
    handleStandardResponse(response)
  }

  /** Port of Dart `_handleTLinkResponse` — token → login → ready state machine. */
  private suspend fun handleTLinkResponse(response: TLinkResponse) {
    when (response) {
      is TLinkTokenResponse -> {
        val ctx = _connectionContext
        val write = _writeChar
        if (ctx == null || !ctx.hasTLinkCredentials || write == null) {
          rejectProtocolLogin("TLink 登录凭据缺失")
          return
        }
        acceptTLinkToken(response.token)
        val loginFrame = buildTLinkLoginFrame(
          keyHex = _model.aesKey,
          password = ctx.selectedPassword ?: 0,
          userId = ctx.userIdValue ?: 0,
          token = response.token,
        )
        runGattOperation(priority = GattOperationPriority.HIGH) {
          writeCharacteristic(write, loginFrame, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        }
      }

      is TLinkLoginResponse -> {
        if (!acceptTLinkLogin(response.success)) {
          rejectProtocolLogin("TLink 登录被车辆拒绝")
        }
      }

      is TLinkInductionStatusResponse -> {
        _tlinkInductionStatusDeferred.complete(response)
      }

      is TLinkInductionSetResponse -> {
        _tlinkInductionSetDeferred.complete(response.success)
      }

      is TLinkProximityDistanceSetResponse -> {
        _tlinkProximityDistanceDeferred.complete(response.success)
      }

      is TLinkCommandResponse -> {
        val commandType = when (response.commandType) {
          "20" -> CommandCode.lock.code
          "21" -> CommandCode.unlock.code
          "22" -> CommandCode.powerOn.code
          "23" -> CommandCode.powerOff.code
          "24" -> CommandCode.openSeat.code
          "25" -> CommandCode.find.code
          else -> response.commandType
        }
        handleStandardResponse(
          CommandResponse(
            response.raw,
            commandType = commandType,
            statusCode = response.statusCode,
            success = response.success,
          ),
        )
      }

      else -> Unit // TLinkUnknownResponse — ignored like the Dart fall-through.
    }
  }

  /** Port of Dart `_acceptTLinkToken`. */
  private fun acceptTLinkToken(token: String) {
    _token = token
    _protocolLoggedIn = false
  }

  /** Port of Dart `_acceptTLinkLogin`. */
  private fun acceptTLinkLogin(success: Boolean): Boolean {
    val t = _token
    if (!success || t == null) return false
    markProtocolLoggedIn(t)
    return true
  }

  /** Port of Dart `_rejectProtocolLogin` — credential failure tears the link down. */
  private suspend fun rejectProtocolLogin(reason: String) {
    log.ble(reason, level = LogLevel.ERROR)
    clearProtocolLogin()
    _userDisconnected = true
    clearRuntimeResources(disconnectDevice = true)
    resetCharacteristics()
    setState(ConnectionState.DISCONNECTED)
  }

  /** Port of Dart `_handleStandardResponse` — token / state / command ACK routing. */
  private fun handleStandardResponse(response: ParsedResponse) {
    when (response) {
      is StateResponse -> {
        if (response.bikeState != null) {
          publishBikeState(response.bikeState)
        }
        _standardStateDeferred.complete(response.bikeState)
      }

      is TokenResponse -> markProtocolLoggedIn(response.token)

      is CommandResponse -> {
        applyStandardCommandState(response)
        val expected = _standardPendingCommandType
        if (expected == null || expected != response.commandType) return
        _standardPendingCommandType = null
        _standardCommandAckDeferred.complete(response.success)
      }

      else -> Unit // VoltageResponse / UnknownResponse ignored here.
    }
  }

  /** Port of Dart `_applyStandardCommandState` — optimistic BikeState updates. */
  private fun applyStandardCommandState(response: CommandResponse) {
    if (!response.success) return
    val current = _latestBikeState
    val next: BikeState? = when (response.commandType.uppercase()) {
      "01" -> BikeState(
        isLocked = true,
        isPowerOn = false,
        isMuted = current?.isMuted ?: false,
        voltage = current?.voltage,
        temperature = current?.temperature,
        batteryPercent = current?.batteryPercent,
        signalStrength = current?.signalStrength,
        faultMotor = current?.faultMotor ?: false,
        faultController = current?.faultController ?: false,
        faultBrake = current?.faultBrake ?: false,
        faultLowVoltage = current?.faultLowVoltage ?: false,
      )
      "02" ->
        if (current == null) BikeState(isLocked = false, isPowerOn = false)
        else current.copy(isLocked = false)
      "06" ->
        if (current == null) BikeState(isLocked = false, isPowerOn = true)
        else current.copy(isLocked = false, isPowerOn = true)
      "07" ->
        if (current == null) BikeState(isLocked = false, isPowerOn = false)
        else current.copy(isPowerOn = false)
      else -> null
    }
    if (next != null && (current == null || next != current)) {
      publishBikeState(next)
    }
  }

  /** Port of Dart `_onQgjNotify` — feb2 (QGJ) notification; login / setStatus ACK. */
  private fun onQgjNotify(value: ByteArray) {
    if (_disposed) return
    log.ble("← QGJ 响应", detail = bytesToSpacedHex(value.map { it.toInt() }))
    val response = parseQgjResponse(value) ?: return

    if (response.cmdId == QgjCommandIds.login && response.success) {
      log.ble("QGJ 登录成功", level = LogLevel.INFO)
      markProtocolLoggedIn("qgj")
      startHeartbeat()
    } else if (response.cmdId == QgjCommandIds.setStatus) {
      _cmdAckDeferred.complete(response.success)
    }

    val deferred = _qgjResponseDeferreds.remove(response.cmdId)
    if (deferred != null && !deferred.isCompleted) deferred.complete(response)
  }

  /** Port of Dart `_onQgjGpsNotify` — fe03 GPS notifications (logged only). */
  private fun onQgjGpsNotify(value: ByteArray) {
    if (_disposed) return
    if (value.isEmpty()) return
    log.ble(
      "← QGJ GPS 通知",
      detail = bytesToSpacedHex(value.map { it.toInt() }),
      level = LogLevel.DEBUG,
    )
  }

  /** Port of Dart `_onFbb2Notify` — fbb2 payload as uppercase hex on [fbb2Flow]. */
  private fun onFbb2Notify(value: ByteArray) {
    if (value.isEmpty()) return
    val hex = bytesToHex(value)
    log.ble("fbb2 通知", detail = hex, level = LogLevel.DEBUG)
    if (!_disposed) {
      _fbb2.tryEmit(hex)
    }
  }

  /** Port of Dart `_findCharByUuid` — OTA 7000/7001 lookup with short-form fallback. */
  private fun findCharByUuid(uuidFragment: String): BluetoothGattCharacteristic? {
    val gatt = _gatt ?: return null
    val needle = uuidFragment.lowercase().replace("-", "")
    val short = uuidFragment.lowercase()
    for (service in gatt.services) {
      for (c in service.characteristics) {
        val id = c.uuid.toString().lowercase().replace("-", "")
        val candidate = needle.replace("0000", "")
        if (candidate.length >= 8 && (id.contains(candidate.substring(0, 8)) || id.contains(needle))) {
          return c
        }
      }
    }
    // Short-form fallback (7000 / 7001).
    for (service in gatt.services) {
      for (c in service.characteristics) {
        val id = c.uuid.toString().lowercase()
        if (id.contains(
            if (short.contains("7000")) "7000"
            else if (short.contains("7001")) "7001"
            else short,
          )
        ) {
          return c
        }
      }
    }
    return null
  }

  /** Port of Dart `_findFcc1Char` — locate fcc1 in the fcc0 service. */
  private fun findFcc1Char(): BluetoothGattCharacteristic? {
    val gatt = _gatt ?: return null
    for (service in gatt.services) {
      if (service.uuid.toString().contains("fcc0")) {
        for (c in service.characteristics) {
          if (c.uuid.toString().contains("fcc1")) return c
        }
      }
    }
    return null
  }

  // =========================================================================
  // Heartbeat & ready-handshake watchdog
  // =========================================================================

  /**
   * Port of Dart `_startHeartbeat` — QGJ polls feb3 every second; 3
   * consecutive failures log a warning, ≥5 failures trigger a reconnect via
   * [onDisconnected].
   */
  private fun startHeartbeat() {
    cancelHeartbeat()
    log.ble(
      "心跳启动 feb3=${_feb3Char != null}",
      detail = "interval=${BleTimings.qgjStatusPollInterval.inWholeSeconds}s",
      level = LogLevel.INFO,
    )
    if (_feb3Char == null) {
      log.ble("feb3 未找到，无法维持心跳", level = LogLevel.ERROR)
      return
    }
    heartbeatJob = scope.launch {
      var failCount = 0
      delay(BleTimings.heartbeatInitialDelay)
      while (isActive) {
        if (state != ConnectionState.READY || _feb3Char == null) break
        try {
          val data = readFeb3()
          failCount = 0
          if (data != null && data.isNotEmpty()) {
            val bikeState = BikeState.fromFeb3(data)
            if (bikeState != null) {
              publishBikeState(bikeState)
            }
          }
        } catch (e: kotlinx.coroutines.CancellationException) {
          throw e
        } catch (e: Exception) {
          failCount++
          if (failCount == 3) {
            log.ble("心跳连续失败 3 次", detail = e.toString(), level = LogLevel.WARNING)
          }
          if (failCount >= 5 && state == ConnectionState.READY) {
            log.ble("心跳持续失败 ($failCount 次)，触发重连", level = LogLevel.WARNING)
            cancelHeartbeat()
            // Dart routes this through scheduleMicrotask so the teardown does
            // not run inside the Timer zone; here a fresh coroutine does the same.
            scope.launch {
              try {
                onDisconnected()
              } catch (e: Exception) {
                log.ble("Disconnect handler error: $e", level = LogLevel.ERROR)
              }
            }
            break
          }
        }
        delay(BleTimings.qgjStatusPollInterval)
      }
    }
  }

  /** Port of Dart `_cancelHeartbeat`. */
  private fun cancelHeartbeat() {
    heartbeatJob?.cancel()
    heartbeatJob = null
  }

  /**
   * Port of Dart `_armReadyWatchdog` — if the LOGIN handshake has not reached
   * READY within [BleTimings.readyHandshakeTimeout] of GATT connect, tear the
   * link down and let [onDisconnected] trigger the reconnect.
   */
  private fun armReadyWatchdog() {
    disarmReadyWatchdog()
    watchdogJob = scope.launch {
      delay(BleTimings.readyHandshakeTimeout)
      if (_disposed) return@launch
      if (state != ConnectionState.CONNECTED) return@launch
      log.ble("connected→ready 握手超时，回退断连并触发重连", level = LogLevel.WARNING)
      try {
        onDisconnected()
      } catch (e: Exception) {
        log.ble("Disconnect handler error: $e", level = LogLevel.ERROR)
      }
    }
  }

  /** Port of Dart `_disarmReadyWatchdog`. */
  private fun disarmReadyWatchdog() {
    watchdogJob?.cancel()
    watchdogJob = null
  }

  // =========================================================================
  // State publication & teardown helpers
  // =========================================================================

  /** Port of Dart `_setState` — arms the watchdog on CONNECTED, disarms otherwise. */
  private fun setState(s: ConnectionState) = synchronized(lock) {
    val prev = _state.value
    if (prev == s) return
    _state.value = s
    if (s == ConnectionState.CONNECTED) {
      armReadyWatchdog()
    } else {
      disarmReadyWatchdog()
    }
  }

  /** Port of Dart `_markProtocolLoggedIn` — latch official LOGIN and enter READY. */
  private fun markProtocolLoggedIn(credential: String) {
    _token = credential
    _protocolLoggedIn = true
    // Any in-flight reconnect must not tear down a successful LOGIN.
    _reconnectCancelled = true
    _reconnecting = false
    _disconnectHandled = false
    setState(ConnectionState.READY)
  }

  /** Port of Dart `_clearProtocolLogin`. */
  private fun clearProtocolLogin() {
    _protocolLoggedIn = false
    _token = null
  }

  /**
   * Port of Dart `_publishBikeState`, with a field-level debounce aligned to
   * how the official app splits state across separate LiveDatas
   * (`getBikeState()` / `getPowerState()` / `capacityDataState` …).
   *
   * The QGJ heartbeat polls `feb3` every second and re-derives a full
   * `BikeState` each time; without a debounce, the jittery `voltage` field
   * (a float from the wire) would re-emit a new state object once a second
   * and re-trigger the whole control-page recomposition graph. We only
   * re-publish when a *user-visible* field actually crossed its threshold:
   * - lock/power/mute/faults — boolean transitions always publish;
   * - voltage — publish only when it moved by at least 0.5V from the last
   *   published value (still keeps the UI battery figure honest, absorbs
   *   wire noise);
   * - battery percent — integer, publishes on any change (rare).
   */
  private fun publishBikeState(state: BikeState?) {
    if (state == null) {
      clearBikeState()
      return
    }
    val last = _lastPublishedBikeState
    if (last != null && state.matchesDebounced(last)) return
    _latestBikeState = state
    _lastPublishedBikeState = state
    if (!_disposed) {
      _bikeState.value = state
    }
  }

  /** True when [other] differs from this only in fields below the publish threshold. */
  private fun BikeState.matchesDebounced(other: BikeState): Boolean {
    if (isLocked != other.isLocked) return false
    if (isPowerOn != other.isPowerOn) return false
    if (isMuted != other.isMuted) return false
    if (batteryPercent != other.batteryPercent) return false
    if (faultMotor != other.faultMotor) return false
    if (faultController != other.faultController) return false
    if (faultBrake != other.faultBrake) return false
    if (faultLowVoltage != other.faultLowVoltage) return false
    val v = voltage ?: return true
    val ov = other.voltage ?: return true
    // 0.5V hysteresis on the wire voltage — below this the state is
    // considered unchanged (no new StateFlow emission, no recomposition).
    return kotlin.math.abs(v - ov) < 0.5
  }

  /** Port of Dart `_clearBikeState`. */
  private fun clearBikeState() {
    if (_latestBikeState == null && _lastPublishedBikeState == null) return
    _latestBikeState = null
    _lastPublishedBikeState = null
    if (!_disposed) {
      _bikeState.value = null
    }
  }

  /** Port of Dart `_addResponse`. */
  private fun addResponse(response: ParsedResponse) {
    if (!_disposed) {
      _response.tryEmit(response)
    }
  }

  /** Port of Dart `_addRidingMode`. */
  private fun addRidingMode(mode: RidingMode) {
    if (!_disposed) {
      _ridingMode.value = mode
    }
  }

  /** Port of Dart `_resetCharacteristics` — clear protocol + characteristic slots. */
  private fun resetCharacteristics() {
    _protocol = ProtocolType.UNKNOWN
    clearProtocolLogin()
    _writeChar = null
    _notifyChar = null
    _feb1Char = null
    _feb2Char = null
    _feb3Char = null
    _fe02Char = null
    _fe03Char = null
    _gpsNotifyChar = null
    _fcc1Char = null
    _fcc2Char = null
    _fbb1Char = null
    _fbb2Char = null
    clearBikeState()
  }

  /** Port of Dart `_clearRuntimeResources` — timers, subscriptions, pending ops. */
  private suspend fun clearRuntimeResources(disconnectDevice: Boolean) {
    cancelHeartbeat()
    disarmReadyWatchdog()
    completePendingOperations(IllegalStateException("BLE runtime cleared"))
    completePendingGattOperations(IllegalStateException("BLE runtime cleared"))
    if (disconnectDevice) {
      try {
        closeGatt()
      } catch (e: Exception) {
        log.ble("断开旧连接失败", detail = e.toString(), level = LogLevel.DEBUG)
      }
    }
  }

  /** Port of Dart `_completePendingOperations` — fail every parked command completer. */
  private fun completePendingOperations(error: Throwable) {
    _cmdAckDeferred.complete(false)
    _standardCommandAckDeferred.complete(false)
    _standardPendingCommandType = null
    _standardStateDeferred.complete(null)
    _tlinkInductionStatusDeferred.complete(null)
    _tlinkInductionSetDeferred.complete(false)
    _tlinkProximityDistanceDeferred.complete(false)

    for (deferred in _qgjResponseDeferreds.values) {
      if (!deferred.isCompleted) deferred.completeExceptionally(error)
    }
    _qgjResponseDeferreds.clear()
  }

  /**
   * Port of Dart `_completePendingGattOperations` — fail queued + active GATT ops.
   *
   * Also fails the parked read/write/descriptor deferreds: after [closeGatt]
   * Android never fires onCharacteristicWrite/Read again, so without this the
   * operation lambda inside [gattQueue] stays parked until the drain loop's
   * 30 s timeout — blocking the first GATT operation of the next connect.
   */
  private fun completePendingGattOperations(error: Throwable) {
    gattQueue.completePending(error)

    failDeferredMap(readDeferreds, error)
    failDeferredMap(writeDeferreds, error)
    failDeferredMap(descriptorWriteDeferreds, error)

    // Same best-effort teardown for the direct-connect deferred slots.
    val connect = _connectDeferred
    _connectDeferred = null
    if (connect != null && !connect.isCompleted) connect.completeExceptionally(error)
    val discovery = _discoveryDeferred
    _discoveryDeferred = null
    if (discovery != null && !discovery.isCompleted) discovery.completeExceptionally(error)
    val mtu = _mtuDeferred
    _mtuDeferred = null
    if (mtu != null && !mtu.isCompleted) mtu.completeExceptionally(error)
    val rssi = _rssiDeferred
    _rssiDeferred = null
    if (rssi != null && !rssi.isCompleted) rssi.completeExceptionally(error)
  }

  /**
   * Fail every parked deferred in [map]. The two-arg remove is atomic, so a
   * deferred registered by a fresh session during this teardown is left alone.
   */
  private fun <T> failDeferredMap(
    map: ConcurrentHashMap<UUID, CompletableDeferred<T>>,
    error: Throwable,
  ) {
    for (entry in map.entries) {
      if (map.remove(entry.key, entry.value) && !entry.value.isCompleted) {
        entry.value.completeExceptionally(error)
      }
    }
  }

  /** Port of Dart `_reset` — drop device, state, characteristics. */
  private fun reset() {
    setState(ConnectionState.DISCONNECTED)
    _device = null
    resetCharacteristics()
  }

  /** Port of Dart `_markDisconnectHandled` — reentry guard, reset on successful reconnect. */
  private fun markDisconnectHandled(): Boolean = synchronized(lock) {
    if (_disconnectHandled) return false
    _disconnectHandled = true
    true
  }

  /**
   * Port of Dart `_onDisconnected`. QGJ never auto-reconnects (the official
   * app waits for the user after 熄火/休眠); KKS/TLink reconnect with
   * exponential backoff unless the disconnect happened during the initial
   * handshake (connect() still owns the session).
   */
  private fun onDisconnected() {
    if (_disposed) return
    if (!markDisconnectHandled()) return
    log.ble("设备断开连接", level = LogLevel.WARNING)
    cancelHeartbeat()
    completePendingOperations(IllegalStateException("QGJ disconnected"))
    completePendingGattOperations(IllegalStateException("BLE disconnected"))

    val wasHandshaking =
      state == ConnectionState.CONNECTING || state == ConnectionState.CONNECTED
    val protocolIsQgj =
      _protocol == ProtocolType.QGJ || _lastKnownProtocol == ProtocolType.QGJ

    resetCharacteristics()
    if (!_userDisconnected && _device != null && !wasHandshaking && !protocolIsQgj) {
      setState(ConnectionState.RECONNECTING)
      reconnectJob = scope.launch {
        try {
          attemptReconnect()
        } catch (e: Exception) {
          log.ble("Reconnect error: $e", level = LogLevel.ERROR)
        }
      }
    } else {
      setState(ConnectionState.DISCONNECTED)
      if (wasHandshaking && !_userDisconnected) {
        log.ble("握手期断连，交由 connect() 处理", level = LogLevel.INFO)
      } else if (protocolIsQgj && !_userDisconnected) {
        log.ble("QGJ 断连（对齐官方：不自动重连，等待用户重连）", level = LogLevel.INFO)
      }
    }
  }

  /**
   * Port of Dart `_attemptReconnect` — exponential backoff 3 s → 30 s + jitter
   * (≤500 ms), max [MAX_RECONNECT_ATTEMPTS]. Reuses the normal connect
   * pipeline; resets [_disconnectHandled] on success so a second disconnect
   * can re-enter [onDisconnected].
   */
  private suspend fun attemptReconnect() {
    if (_reconnecting || _device == null) return
    _reconnecting = true
    _reconnectAttempt = 0

    try {
      while (_reconnectAttempt < MAX_RECONNECT_ATTEMPTS &&
        state == ConnectionState.RECONNECTING
      ) {
        if (_reconnectCancelled) break
        _reconnectAttempt++
        val baseMs = 3000
        val maxMs = 30000
        val exponential = (baseMs * 2.0.pow(_reconnectAttempt - 1))
          .toInt()
          .coerceIn(baseMs, maxMs)
        val jitter = Random.nextInt(500)
        val delayMs = exponential + jitter
        log.ble(
          "重连 $_reconnectAttempt/$MAX_RECONNECT_ATTEMPTS，${delayMs / 1000}s 后重试",
          level = LogLevel.INFO,
        )

        delay(delayMs.toLong())

        if (state != ConnectionState.RECONNECTING) break
        if (_reconnectCancelled) break

        try {
          connectGattOnce(_device!!, BleTimings.reconnectConnectTimeout)

          if (state != ConnectionState.RECONNECTING || _reconnectCancelled) {
            try {
              closeGatt()
            } catch (e: Exception) {
              log.ble("取消重连时断开失败", detail = e.toString(), level = LogLevel.DEBUG)
            }
            break
          }

          setState(ConnectionState.CONNECTED)
          requestQgjMtu(_device!!)
          delay(BleTimings.serviceSetupDelay)
          if (state != ConnectionState.CONNECTED || _reconnectCancelled) break
          discoverAndSetup()

          _reconnecting = false
          _reconnectAttempt = 0
          // P0-1: reset the guard so a second disconnect re-enters onDisconnected
          // (original bug: the flag stayed set after a successful reconnect,
          // freezing the app in reconnecting/ready).
          _disconnectHandled = false
          log.ble("重连成功", level = LogLevel.INFO)
          return
        } catch (e: kotlinx.coroutines.CancellationException) {
          throw e
        } catch (e: Exception) {
          log.ble("重连失败", detail = e.toString(), level = LogLevel.DEBUG)
          recoverFailedConnect(e)
        }
      }
    } finally {
      // Cancellation (connect()/dispose() cancelled this job) must also clear
      // the loop flags — the cancelled coroutine skips the tail below.
      _reconnecting = false
      _reconnectAttempt = 0
    }

    // Do NOT clobber a session that became ready/connected while sleeping.
    if (state == ConnectionState.RECONNECTING) {
      setState(ConnectionState.DISCONNECTED)
      log.ble("重连次数已用尽", level = LogLevel.WARNING)
    } else {
      log.ble("重连结束（保留当前状态: ${state.name}）", level = LogLevel.INFO)
    }
  }
}
