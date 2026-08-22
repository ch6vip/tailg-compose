package com.tailg.plus.data.mqtt

import com.tailg.plus.data.cloud.OfficialCloudApiException
import com.tailg.plus.data.cloud.OfficialCloudMessages
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.cloud.OfficialCloudState
import com.tailg.plus.data.cloud.OfficialRemoteErrorMessages
import com.tailg.plus.data.model.CommandCode
import com.tailg.plus.data.model.OfficialCloudCommand
import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService
import java.net.SocketException
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.MqttSecurityException
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

/**
 * Port of `lib/services/official_mqtt_service.dart` — official MQTT remote
 * control (`ControlFragment.mqttPublish` path).
 *
 * Connects with the same credentials/topics as the decompiled app
 * ([OfficialMqttConfig]), publishes `MqttCmdBean` JSON payloads at QoS 0, and
 * applies status replies to the cloud vehicle state via
 * [OfficialCloudService.applyMqttVehicleStatus].
 *
 * Dart singleton → plain class; DI (Hilt) should create the single shared
 * instance (same pattern as [LogService]).
 *
 * Threading: Paho v3 (`MqttAsyncClient`) is driven from `Dispatchers.IO`;
 * blocking calls (connect/subscribe/publish/disconnect) run inside
 * `withContext(Dispatchers.IO)`; Paho callback-thread events
 * ([MqttCallback.messageArrived]) hop to the service [scope]. Connection /
 * link state is exposed via [linkState]; subscribed topics via
 * [subscribedTopics].
 */
class OfficialMqttService(
    private val log: LogService = LogService(),
    /**
     * Fallback cloud session used when [attachToCloud] was never called
     * (Dart `_boundCloud ?? OfficialCloudService()`).
     */
    private val defaultCloud: OfficialCloudService? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    companion object {
        /**
         * When false, [ensureConnected] fails immediately without opening
         * sockets. Unit tests set this so MQTT tests never touch the network.
         */
        @Volatile
        var liveConnectEnabled: Boolean = true

        /**
         * Per-topic SUBACK wait. Deliberately below the connect timeout so the
         * lifecycle mutex cannot be held for connect + N x connect-timeout on
         * a dead broker (logout / next command used to queue behind that).
         */
        private const val SUBSCRIBE_TIMEOUT_MS = 5_000L

        /**
         * Auto-reconnect after an unexpected [MqttCallback.connectionLost].
         * Paho's own `isAutomaticReconnect` stays off (it bypasses our
         * credential/topic pipeline), so the loss callback schedules this
         * bounded exponential-backoff loop instead: 2s → 4s → 8s → 16s → 32s,
         * then gives up until the next trigger (command send, control screen
         * enter, or network restored via [monitorNetwork]).
         */
        private const val AUTO_RECONNECT_MAX_ATTEMPTS = 5
        private const val AUTO_RECONNECT_BASE_DELAY_MS = 2_000L
        private const val AUTO_RECONNECT_MAX_DELAY_MS = 60_000L

        /**
         * Compact raw error for logs/diagnostics (type + message, no stack).
         * Dart `formatConnectError` (dart:io types mapped to JVM equivalents:
         * SocketException, SSLException for Handshake/Tls, TimeoutException).
         */
        fun formatConnectError(error: Throwable): String = when (error) {
            is SocketTimeoutException ->
                "SocketTimeoutException: ${error.message?.trim().takeIf { !it.isNullOrEmpty() } ?: "timed out"}"
            is SocketException ->
                "SocketException: ${error.message?.trim().takeIf { !it.isNullOrEmpty() } ?: "unknown"}"
            is SSLException -> "SSLException: ${error.message?.trim().orEmpty()}"
            is TimeoutException ->
                "TimeoutException: ${error.message?.trim().takeIf { !it.isNullOrEmpty() } ?: "timed out"}"
            is MqttSecurityException ->
                "MqttSecurityException(reasonCode=${error.reasonCode}): ${error.message?.trim().orEmpty()}"
            is MqttException ->
                "MqttException(reasonCode=${error.reasonCode}): ${error.message?.trim().orEmpty()}"
            is OfficialCloudApiException -> {
                val code = error.statusCode
                if (code == null) "OfficialCloudApiException: ${error.message}"
                else "OfficialCloudApiException($code): ${error.message}"
            }
            else -> {
                val type = error::class.simpleName ?: error::class.java.name
                val msg = error.message?.trim().orEmpty()
                if (msg.isEmpty()) type else "$type: $msg"
            }
        }
    }

    /** Test hook: replace live MQTT publish (Dart `publishCommandOverride`). */
    @Volatile
    var publishCommandOverride: (suspend (OfficialVehicle, String, String) -> Unit)? = null

    // --- state ------------------------------------------------------------

    private val lock = Any()
    /** Serializes connect/disconnect lifecycle (non-reentrant Mutex). */
    private val lifecycleMutex = Mutex()

    private val _linkState = MutableStateFlow(OfficialMqttLinkState.DISCONNECTED)
    val linkState: StateFlow<OfficialMqttLinkState> = _linkState.asStateFlow()

    private val _subscribedTopics = MutableStateFlow<List<String>>(emptyList())
    val subscribedTopics: StateFlow<List<String>> = _subscribedTopics.asStateFlow()

    /**
     * Status pushes that passed IMEI filtering, emitted after the pending
     * command ACK/error bookkeeping and the ACC/defence cloud-state
     * application have settled. Command confirmation waits on this — the
     * official ControlFragment semantic: push-driven, zero HTTP polling.
     *
     * `replay = 1` closes the check-then-subscribe gap for waiters (a push
     * arriving between a state check and `first()` is still delivered).
     */
    private val _statusPayloadEvents = MutableSharedFlow<OfficialMqttStatusPayload>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val statusPayloadEvents: SharedFlow<OfficialMqttStatusPayload> =
        _statusPayloadEvents.asSharedFlow()

    @Volatile private var _client: MqttAsyncClient? = null
    @Volatile private var _connectedClientId: String? = null
    @Volatile private var _connectedBroker: String? = null
    @Volatile private var _connectedTransportSecurity: MqttTransportSecurity? = null
    @Volatile private var _connectedImei: String? = null
    @Volatile private var _pendingCommandApiName: String? = null
    @Volatile private var _pendingCommandError: String? = null
    @Volatile private var _latestStatusPayload: OfficialMqttStatusPayload? = null
    @Volatile private var _lastSendPath: OfficialRemoteSendPath? = null
    @Volatile private var _lastPreconnectError: String? = null
    @Volatile private var _lastPreconnectRawError: String? = null
    @Volatile private var _preconnectInFlight: Boolean = false
    @Volatile private var _disposed: Boolean = false
    @Volatile private var _boundCloud: OfficialCloudService? = null
    private var _cloudJob: Job? = null
    private var _reconnectJob: Job? = null
    private var _networkJob: Job? = null

    val isConnected: Boolean get() = _client?.isConnected == true

    val linkStateLabel: String
        get() = when (_linkState.value) {
            OfficialMqttLinkState.CONNECTED -> "MQTT 已连接"
            OfficialMqttLinkState.CONNECTING -> "MQTT 连接中"
            OfficialMqttLinkState.DISCONNECTED ->
                if (_lastPreconnectError == null) "MQTT 未连接" else "MQTT 预连接失败"
        }

    val pendingCommandApiName: String? get() = synchronized(lock) { _pendingCommandApiName }
    val pendingCommandError: String? get() = synchronized(lock) { _pendingCommandError }
    val latestStatusPayload: OfficialMqttStatusPayload? get() = _latestStatusPayload
    val lastSendPath: OfficialRemoteSendPath? get() = _lastSendPath
    val lastPreconnectError: String? get() = _lastPreconnectError
    val lastPreconnectRawError: String? get() = _lastPreconnectRawError
    val preconnectInFlight: Boolean get() = _preconnectInFlight
    val connectedTransportSecurity: MqttTransportSecurity? get() = _connectedTransportSecurity

    // --- cloud binding ----------------------------------------------------

    /**
     * Bind to cloud state and pre-connect whenever a vehicle is selected
     * (Dart `attachToCloud`). Rebinding is a no-op for the same instance.
     * The cloud [OfficialCloudService.stateFlow] is a StateFlow, so the first
     * collected value is always the current state — no separate "kick" needed
     * (Dart used a broadcast stream and kicked once explicitly).
     */
    fun attachToCloud(cloud: OfficialCloudService) {
        synchronized(lock) {
            if (_boundCloud === cloud && _cloudJob != null) return
            _boundCloud = cloud
            _cloudJob?.cancel()
            _cloudJob = scope.launch {
                cloud.stateFlow.collect { onCloudState(it) }
            }
        }
    }

    private suspend fun onCloudState(state: OfficialCloudState) {
        if (_disposed) return
        val vehicle = state.selectedVehicle
        if (!state.signedIn || vehicle == null) {
            disconnect()
            return
        }
        preconnect(vehicle = vehicle, userId = state.userId)
    }

    // --- preconnect / retry -----------------------------------------------

    /**
     * Best-effort pre-connect used on vehicle select / home enter.
     *
     * Failures are recorded in [lastPreconnectError] / [lastPreconnectRawError]
     * and must not block a later [ensureConnected] on first command send
     * (P0-B4). Uses a short exponential backoff retry (base × attempt) so
     * transient TLS/port blips do not leave MQTT permanently down.
     */
    suspend fun preconnect(
        vehicle: OfficialVehicle,
        userId: String,
        force: Boolean = false,
    ) {
        if (_disposed) return
        if (_preconnectInFlight) return
        if (!force &&
            isConnected &&
            _connectedImei == OfficialMqttConfig.commandImei(vehicle)
        ) {
            _lastPreconnectError = null
            _lastPreconnectRawError = null
            return
        }
        // Unit tests disable live sockets; skip entirely (no retry timers).
        if (!liveConnectEnabled) {
            _lastPreconnectError = OfficialRemoteErrorMessages.BROKER_UNREACHABLE
            _lastPreconnectRawError = "OfficialCloudApiException: live connect disabled (test)"
            _linkState.value = OfficialMqttLinkState.DISCONNECTED
            return
        }
        synchronized(lock) {
            if (_preconnectInFlight) return
            _preconnectInFlight = true
        }
        var lastError: Throwable? = null
        try {
            val maxAttempts = 1 + OfficialMqttConfig.PRECONNECT_MAX_RETRIES
            for (attempt in 1..maxAttempts) {
                if (_disposed) return
                // Stop retrying once the session moved on (sign-out, vehicle
                // switch) — otherwise the loop reopens a session for the OLD
                // vehicle right after disconnect().
                val bound = _boundCloud
                if (bound != null) {
                    val s = bound.currentState
                    if (!s.signedIn || s.selectedVehicle?.key != vehicle.key) return
                }
                try {
                    ensureConnected(vehicle = vehicle, userId = userId)
                    _lastPreconnectError = null
                    _lastPreconnectRawError = null
                    if (attempt > 1) {
                        log.operation(
                            "官方 MQTT 预连接重试成功",
                            detail = "attempt=$attempt/$maxAttempts",
                        )
                    }
                    return
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    lastError = e
                    val raw = formatConnectError(e)
                    _lastPreconnectRawError = raw
                    _linkState.value = OfficialMqttLinkState.DISCONNECTED
                    val isLast = attempt >= maxAttempts
                    log.operation(
                        if (isLast) "官方 MQTT 预连接失败" else "官方 MQTT 预连接失败，准备重试",
                        detail = "attempt=$attempt/$maxAttempts " +
                            "broker=${OfficialMqttConfig.brokerUriFor(vehicle)} " +
                            "raw=$raw " +
                            "user=${OfficialRemoteErrorMessages.describe(e)}",
                        level = LogLevel.WARNING,
                    )
                    if (isLast) break
                    delay(OfficialMqttConfig.PRECONNECT_RETRY_BASE_DELAY * attempt)
                    if (_disposed) return
                }
            }
            if (lastError != null) {
                _lastPreconnectError = OfficialRemoteErrorMessages.describe(lastError)
            }
        } finally {
            synchronized(lock) { _preconnectInFlight = false }
        }
    }

    /** Explicit retry after a failed preconnect (network restored, user retry). */
    suspend fun retryPreconnect(cloud: OfficialCloudService) {
        attachToCloud(cloud)
        val state = cloud.currentState
        val vehicle = state.selectedVehicle
        if (!state.signedIn || vehicle == null) {
            _lastPreconnectError = OfficialCloudMessages.SIGN_IN_AND_SELECT_VEHICLE_REQUIRED
            return
        }
        preconnect(vehicle = vehicle, userId = state.userId, force = true)
    }

    /** Pre-connect for the current cloud session when one is selected. */
    suspend fun preconnectForCloud(cloud: OfficialCloudService) {
        attachToCloud(cloud)
        val state = cloud.currentState
        val vehicle = state.selectedVehicle
        if (!state.signedIn || vehicle == null) return
        preconnect(vehicle = vehicle, userId = state.userId)
    }

    // --- auto reconnect / network trigger -----------------------------------

    /**
     * Observe link-layer changes and retry the session when connectivity is
     * restored while a vehicle session should be live (WiFi↔cellular handover,
     * airplane-mode off, …). Attach once at app scope; no-op until [attachToCloud].
     */
    fun monitorNetwork(network: com.tailg.plus.data.network.NetworkAvailabilityService) {
        synchronized(lock) {
            _networkJob?.cancel()
            _networkJob = scope.launch {
                var previous: Boolean? = null
                network.changes.collect { available ->
                    val was = previous
                    previous = available
                    if (_disposed || !available) return@collect
                    // Rising edge only — the initial emission must not double-
                    // trigger what onCloudState already preconnects.
                    if (was != false || isConnected) return@collect
                    val cloud = _boundCloud ?: return@collect
                    val state = cloud.currentState
                    val vehicle = state.selectedVehicle
                    if (!state.signedIn || vehicle == null) return@collect
                    log.operation("网络已恢复，重试官方 MQTT 连接", level = LogLevel.INFO)
                    preconnect(vehicle = vehicle, userId = state.userId, force = true)
                }
            }
        }
    }

    /** Single-flight scheduler for the post-loss backoff loop. */
    private fun scheduleAutoReconnect(lostClient: MqttAsyncClient) {
        if (_disposed) return
        synchronized(lock) {
            if (_reconnectJob?.isActive == true) return
            _reconnectJob = scope.launch { autoReconnectLoop(lostClient) }
        }
    }

    /**
     * Bounded exponential-backoff restore of the lost session. Self-terminates
     * when the session moved on (logout clears `_client`; a vehicle switch or
     * manual preconnect installs a different client), so no explicit cancel
     * bookkeeping is needed on those paths.
     */
    private suspend fun autoReconnectLoop(lostClient: MqttAsyncClient) {
        val cloud = _boundCloud ?: return
        for (attempt in 1..AUTO_RECONNECT_MAX_ATTEMPTS) {
            val delayMs = (AUTO_RECONNECT_BASE_DELAY_MS shl (attempt - 1))
                .coerceAtMost(AUTO_RECONNECT_MAX_DELAY_MS)
            delay(delayMs)
            if (_disposed) return
            // Superseded by a newer session or explicitly disconnected.
            if (_client !== lostClient) return
            val state = cloud.currentState
            val vehicle = state.selectedVehicle
            if (!state.signedIn || vehicle == null) return
            try {
                log.operation(
                    "官方 MQTT 自动重连",
                    detail = "attempt=$attempt/$AUTO_RECONNECT_MAX_ATTEMPTS",
                )
                preconnect(vehicle = vehicle, userId = state.userId, force = true)
                if (isConnected) {
                    log.operation("官方 MQTT 自动重连成功", detail = "attempt=$attempt")
                    return
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                log.operation(
                    "官方 MQTT 自动重连失败",
                    detail = formatConnectError(e),
                    level = LogLevel.DEBUG,
                )
            }
        }
        log.operation(
            "官方 MQTT 自动重连放弃",
            detail = "attempts=$AUTO_RECONNECT_MAX_ATTEMPTS，等待下次触发",
            level = LogLevel.WARNING,
        )
    }

    // --- connect / disconnect ---------------------------------------------

    suspend fun disconnect() {
        lifecycleMutex.withLock { disconnectInternal() }
    }

    /**
     * Dart `disconnect`: cancel retries, drop the client best-effort, clear
     * session identity + pending command state, emit DISCONNECTED.
     * Does NOT cancel the cloud binding (a later vehicle select reconnects).
     */
    private suspend fun disconnectInternal() {
        val old = _client
        _client = null
        _connectedClientId = null
        _connectedBroker = null
        _connectedTransportSecurity = null
        _connectedImei = null
        _pendingCommandApiName = null
        _pendingCommandError = null
        _latestStatusPayload = null
        _lastPreconnectError = null
        _lastPreconnectRawError = null
        _subscribedTopics.value = emptyList()
        if (old != null) {
            teardownClient(old)
        }
        _linkState.value = OfficialMqttLinkState.DISCONNECTED
    }

    private suspend fun teardownClient(client: MqttAsyncClient) = withContext(Dispatchers.IO) {
        try {
            if (client.isConnected) {
                client.disconnectForcibly(5_000L, 1_000L)
            }
        } catch (_: Throwable) {
            // Best-effort teardown; ignore disconnect errors on an already-dead client.
        }
        try {
            client.close()
        } catch (_: Throwable) {
            // Best-effort resource release.
        }
    }

    /**
     * Ensure a live MQTT session for [vehicle] (reconnect when broker/imei
     * change). Dart `ensureConnected`.
     */
    suspend fun ensureConnected(vehicle: OfficialVehicle, userId: String) {
        lifecycleMutex.withLock { ensureConnectedInternal(vehicle, userId) }
    }

    private suspend fun ensureConnectedInternal(vehicle: OfficialVehicle, userId: String) {
        if (_disposed) throw OfficialCloudApiException("MQTT 服务已释放")
        val imei = OfficialMqttConfig.commandImei(vehicle)
        if (imei.isEmpty()) throw OfficialCloudApiException("当前车辆缺少 IMEI，无法 MQTT 控车")
        val broker = OfficialMqttConfig.brokerUriFor(vehicle)
        val parsed = try {
            OfficialMqttConfig.parseBrokerUri(broker)
        } catch (e: IllegalArgumentException) {
            _linkState.value = OfficialMqttLinkState.DISCONNECTED
            throw OfficialCloudApiException(
                "官方 MQTT 地址无效: ${e.message ?: "unknown"}",
            )
        }
        if (isConnected &&
            _connectedBroker == broker &&
            _connectedImei == imei &&
            (_connectedClientId?.contains(imei) ?: false)
        ) {
            _linkState.value = OfficialMqttLinkState.CONNECTED
            return
        }

        disconnectInternal()
        _linkState.value = OfficialMqttLinkState.CONNECTING

        if (!liveConnectEnabled) {
            _linkState.value = OfficialMqttLinkState.DISCONNECTED
            throw OfficialCloudApiException("官方 MQTT 连接失败: live connect disabled (test)")
        }

        val clientId = OfficialMqttConfig.clientIdFor(vehicle, userId)
        val (mqUser, mqPass) = OfficialMqttConfig.credentialsFor(vehicle)

        log.operation(
            "官方 MQTT 连接中",
            detail = "broker=$broker transport=${parsed.diagnosticLabel} clientId=$clientId",
        )
        if (parsed.security == MqttTransportSecurity.PLAINTEXT) {
            log.operation(
                "官方 MQTT 使用明文 TCP",
                detail = "broker=$broker，账号和控制指令未加密传输",
                level = LogLevel.WARNING,
            )
        }

        var newClient: MqttAsyncClient? = null
        try {
            // Local non-null val so lambdas (withContext) don't fight smart casts.
            val created = MqttAsyncClient(broker, clientId, MemoryPersistence())
            newClient = created
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                isAutomaticReconnect = false
                connectionTimeout = OfficialMqttConfig.CONNECT_TIMEOUT.inWholeSeconds.toInt()
                keepAliveInterval = OfficialMqttConfig.KEEP_ALIVE_SECONDS
                userName = mqUser
                this.password = mqPass.toCharArray()
                if (parsed.security == MqttTransportSecurity.TLS) {
                    val socketFactoryOverride = tlsSocketFactoryFor(parsed.host)
                    if (socketFactoryOverride != null) {
                        socketFactory = socketFactoryOverride
                    }
                }
            }
            created.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    // Ignore events from superseded clients (a vehicle switch or
                    // a reconnect already replaced this session) — they would
                    // clobber the live link state.
                    if (_disposed || _client !== created) return
                    log.operation(
                        "官方 MQTT 连接丢失",
                        detail = formatConnectError(cause ?: Throwable("unknown")),
                        level = LogLevel.WARNING,
                    )
                    _linkState.value = OfficialMqttLinkState.DISCONNECTED
                    // Without a reconnect trigger here the remote-control
                    // channel stayed down until the user re-entered a screen
                    // that preconnects — schedule a bounded backoff retry.
                    scheduleAutoReconnect(created)
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val payload = message?.payload ?: return
                    val raw = String(payload, Charsets.UTF_8)
                    enqueueStatusPayload(raw)
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    // Official Java forwards to IMqttHandler.deliveryComplete; the
                    // Dart port does not use it. QoS 0 publish has no durable ack.
                }
            })

            val connected = withContext(Dispatchers.IO) {
                val token = created.connect(options)
                token.waitForCompletion(OfficialMqttConfig.CONNECT_TIMEOUT.inWholeMilliseconds)
                created.isConnected
            }
            if (!connected) {
                teardownClient(created)
                _linkState.value = OfficialMqttLinkState.DISCONNECTED
                throw OfficialCloudApiException("官方 MQTT 连接失败: state=unknown broker=$broker")
            }
        } catch (e: Throwable) {
            _linkState.value = OfficialMqttLinkState.DISCONNECTED
            log.operation(
                "官方 MQTT 底层连接异常",
                detail = formatConnectError(e),
                level = LogLevel.DEBUG,
            )
            try {
                newClient?.close()
            } catch (_: Throwable) {
                // Best-effort resource release.
            }
            throw translateConnectError(e, broker)
        }
        // The catch block always throws, so the connect succeeded here.
        val client = checkNotNull(newClient) { "MQTT client lost after connect" }

        // Subscribe failures must not leak the connected client or leave the
        // link state parked at CONNECTING (a SUBACK miss used to strand the
        // socket AND the state machine).
        val topics = OfficialMqttConfig.subscribeTopics(vehicle, imei)
        try {
            withContext(Dispatchers.IO) {
                for (topic in topics) {
                    val token = client.subscribe(topic, OfficialMqttConfig.QOS)
                    token.waitForCompletion(SUBSCRIBE_TIMEOUT_MS)
                }
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            _linkState.value = OfficialMqttLinkState.DISCONNECTED
            log.operation(
                "官方 MQTT 订阅失败",
                detail = formatConnectError(e),
                level = LogLevel.WARNING,
            )
            teardownClient(client)
            throw translateConnectError(e, broker)
        }

        _client = client
        _connectedClientId = clientId
        _connectedBroker = broker
        _connectedTransportSecurity = parsed.security
        _connectedImei = imei
        _subscribedTopics.value = topics
        _linkState.value = OfficialMqttLinkState.CONNECTED
        log.operation("官方 MQTT 已连接", detail = clientId)
    }

    /**
     * Keep machine-readable failure info in the exception so
     * [formatConnectError] and diagnostics can distinguish refused vs timeout
     * vs auth (Dart kept `MqttConnectionState.name` for the same purpose).
     * Raw IO errors (SocketException / SSLException / …) pass through unchanged.
     */
    private fun translateConnectError(e: Throwable, broker: String): Throwable = when (e) {
        is OfficialCloudApiException -> e
        is MqttSecurityException -> OfficialCloudApiException(
            "官方 MQTT 连接失败: 认证失败 reasonCode=${e.reasonCode} broker=$broker",
        )
        is MqttException -> OfficialCloudApiException(
            "官方 MQTT 连接失败: reasonCode=${e.reasonCode} broker=$broker",
        )
        else -> e
    }

    // --- inbound status ----------------------------------------------------

    /**
     * Conflated queue decoupling Paho's callback thread from status handling.
     * The old per-message `scope.launch` spawned an unbounded coroutine per
     * frame of a broker burst while only the newest acc/defence state matters —
     * a CONFLATED channel keeps exactly one pending payload and one consumer.
     */
    private val statusPayloads = Channel<String>(Channel.CONFLATED)

    @Volatile private var statusConsumerStarted = false

    private fun enqueueStatusPayload(raw: String) {
        ensureStatusConsumer()
        statusPayloads.trySend(raw)
    }

    private fun ensureStatusConsumer() {
        if (statusConsumerStarted) return
        synchronized(lock) {
            if (statusConsumerStarted) return
            statusConsumerStarted = true
            scope.launch {
                for (raw in statusPayloads) {
                    try {
                        handleStatusPayload(raw)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        // A malformed payload must not kill the single consumer.
                        log.operation(
                            "官方 MQTT 状态处理异常",
                            detail = e.toString(),
                            level = LogLevel.DEBUG,
                        )
                    }
                }
            }
        }
    }

    /**
     * Parse status JSON and push ACC/defence into cloud vehicle state
     * (Dart `handleStatusPayload`). Exposed for unit tests; used by the live
     * updates listener. Thread-safe: Paho callback thread and test callers.
     */
    fun handleStatusPayload(raw: String) {
        val payload = OfficialMqttStatusPayload.tryParse(raw) ?: return

        val cloud = _boundCloud ?: defaultCloud
        if (cloud != null) {
            val selectedImei = cloud.currentState.selectedVehicle?.commandImei?.trim().orEmpty()
            val payloadImei = payload.imei?.trim().orEmpty()
            if (payloadImei.isNotEmpty() && selectedImei.isNotEmpty() && payloadImei != selectedImei) {
                log.operation(
                    "忽略非当前车辆 MQTT 状态",
                    detail = "payload=$payloadImei selected=$selectedImei",
                    level = LogLevel.WARNING,
                )
                return
            }
        }
        _latestStatusPayload = payload

        var failMessage: String? = null
        var failDetail: String? = null
        var ackMessage: String? = null
        synchronized(lock) {
            val pending = _pendingCommandApiName
            val controlError = payload.controlErrorMessage(pending)
            if (pending != null && controlError != null) {
                _pendingCommandError = controlError
                failMessage = "官方 MQTT 指令失败: $pending"
                failDetail = controlError
            } else if (pending != null && payload.confirmsCommand(pending)) {
                _pendingCommandApiName = null
                _pendingCommandError = null
                ackMessage = "官方 MQTT 指令已确认: $pending"
            }
        }
        failMessage?.let { log.operation(it, detail = failDetail, level = LogLevel.WARNING) }
        ackMessage?.let { log.operation(it) }

        // Official also applies ACC/defence fields opportunistically on any status.
        if (payload.hasVehicleState) {
            cloud?.applyMqttVehicleStatus(acc = payload.accInt, defenceStatus = payload.defenceStatusInt)
        }
        // Wake push-driven confirmation waiters after the state/pending
        // bookkeeping above settled so their re-check sees the new snapshot.
        _statusPayloadEvents.tryEmit(payload)
    }

    // --- outbound commands -------------------------------------------------

    /** Publish one official control command over MQTT (Dart `publishCommand`). */
    suspend fun publishCommand(
        vehicle: OfficialVehicle,
        userId: String,
        commandApiName: String,
    ) {
        val override = publishCommandOverride
        if (override != null) {
            override(vehicle, userId, commandApiName)
            setPending(commandApiName, null)
            return
        }
        ensureConnected(vehicle = vehicle, userId = userId)
        val client = _client
        if (client == null || !client.isConnected) {
            throw OfficialCloudApiException("官方 MQTT 未连接")
        }
        val imei = OfficialMqttConfig.commandImei(vehicle)
        val topic = OfficialMqttConfig.publishTopic(vehicle, imei)
        val payload = OfficialMqttConfig.commandPayload(imei, commandApiName)

        setPending(commandApiName, null)
        withContext(Dispatchers.IO) {
            val message = MqttMessage(payload.toByteArray(Charsets.UTF_8)).apply {
                qos = OfficialMqttConfig.QOS
                isRetained = false
            }
            // QoS 0 fire-and-forget, matching ControlFragment.mqttPublish.
            client.publish(topic, message)
        }
        log.operation("官方 MQTT 已发令: $commandApiName", detail = "topic=$topic payload=$payload")
    }

    private fun setPending(apiName: String, error: String?) {
        synchronized(lock) {
            _pendingCommandApiName = apiName
            _pendingCommandError = error
        }
    }

    /**
     * Prefer MQTT (official remote path); fall back to HTTP cmd API
     * (Dart `sendCommandPreferMqtt`).
     *
     * The return value is a transport-level acceptance message. It does NOT
     * mean the vehicle has executed the command — callers must confirm via
     * the MQTT status ACK (pending command cleared by [handleStatusPayload])
     * or an ACC/defence change.
     */
    suspend fun sendCommandPreferMqtt(
        command: CommandCode,
        cloud: OfficialCloudService,
    ): String {
        attachToCloud(cloud)
        val api = OfficialCloudCommand.fromCommandCode(command)
            ?: throw OfficialCloudApiException("官方云端不支持${command.label}")
        val state = cloud.currentState
        val vehicle = state.selectedVehicle
        if (vehicle == null || !state.signedIn) {
            throw OfficialCloudApiException(
                OfficialCloudMessages.SIGN_IN_AND_SELECT_VEHICLE_REQUIRED,
            )
        }

        try {
            publishCommand(
                vehicle = vehicle,
                userId = state.userId,
                commandApiName = api.apiName,
            )
            _lastSendPath = OfficialRemoteSendPath.MQTT
            log.operation("官方远程通道: MQTT", detail = "command=${api.apiName}")
            // No post-send HTTP refresh here: the command is confirmed by the
            // MQTT status push (see [statusPayloadEvents]); the caller's
            // confirmation loop owns any lightweight fallback refresh.
            // Explicit channel tag so UI/logs can distinguish MQTT vs HTTP fallback.
            return "mqtt:success"
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            synchronized(lock) {
                _pendingCommandApiName = null
                _pendingCommandError = null
            }
            _lastSendPath = OfficialRemoteSendPath.HTTP
            _lastPreconnectRawError = formatConnectError(e)
            _lastPreconnectError = OfficialRemoteErrorMessages.describe(e)
            log.operation(
                "官方 MQTT 发令失败，回退 HTTP",
                detail = "raw=${formatConnectError(e)} " +
                    "user=${OfficialRemoteErrorMessages.describe(e)}",
                level = LogLevel.WARNING,
            )
            val httpMsg = cloud.sendCommand(command)
            val trimmed = httpMsg.trim()
            log.operation("官方远程通道: HTTP", detail = "command=${api.apiName} msg=$trimmed")
            if (trimmed.isEmpty() || trimmed == "success" || trimmed.lowercase() == "ok") {
                return "http:success"
            }
            return "http:$trimmed"
        }
    }

    // --- lifecycle ---------------------------------------------------------

    /**
     * Test hook (Dart `resetForTest`): stop in-flight preconnect/cloud
     * listeners, tear down the client best-effort, and reset to production
     * defaults. Does not cancel the injected [scope] (tests own it).
     */
    suspend fun resetForTest() {
        _disposed = true
        synchronized(lock) {
            _cloudJob?.cancel()
            _cloudJob = null
            _boundCloud = null
            _preconnectInFlight = false
        }
        publishCommandOverride = null
        _pendingCommandApiName = null
        _pendingCommandError = null
        _latestStatusPayload = null
        _lastSendPath = null
        _lastPreconnectError = null
        _lastPreconnectRawError = null
        lifecycleMutex.withLock { disconnectInternal() }
        // Default back to production sockets (Dart resets to liveConnectEnabled = true).
        liveConnectEnabled = true
        _disposed = false
        _linkState.value = OfficialMqttLinkState.DISCONNECTED
    }

    /** Release the service: stop cloud binding, tear down, cancel the scope. */
    suspend fun dispose() {
        _disposed = true
        synchronized(lock) {
            _cloudJob?.cancel()
            _cloudJob = null
            _reconnectJob?.cancel()
            _reconnectJob = null
            _networkJob?.cancel()
            _networkJob = null
            _boundCloud = null
            _preconnectInFlight = false
        }
        statusPayloads.close()
        lifecycleMutex.withLock { disconnectInternal() }
        scope.cancel()
    }

    // --- ssl ---------------------------------------------------------------

    /**
     * Official TLS broker hosts whose certificates are private-CA self-signed
     * (observed: CN=c18_ex_base_pro.tailgdd.com served on www.tailgdd.com:6668
     * with an untrusted chain). System validation always fails against them,
     * which is why the official MqttUtil installs a trust-all path. We align
     * with official behavior but scope it to these hosts only — any other
     * endpoint keeps strict default validation.
     */
    private val OFFICIAL_TLS_HOSTS = setOf("www.tailgdd.com")

    /**
     * [SSLSocketFactory] override for a TLS broker host: official alignment
     * for [OFFICIAL_TLS_HOSTS], the Debug `ALLOW_INSECURE_MQTT_TLS` escape
     * hatch for arbitrary hosts, null (= strict system validation) otherwise.
     */
    private fun tlsSocketFactoryFor(host: String): SSLSocketFactory? = when {
        host.lowercase() in OFFICIAL_TLS_HOSTS -> {
            log.operation(
                "官方 MQTT TLS 信任策略",
                detail = "host=$host 按官方 MqttUtil 行为跳过系统证书校验" +
                    "(官方端点为私有 CA 自签证书)",
                level = LogLevel.WARNING,
            )
            trustAllSslContext.socketFactory
        }
        com.tailg.plus.BuildConfig.DEBUG && com.tailg.plus.BuildConfig.ALLOW_INSECURE_MQTT_TLS ->
            trustAllSslContext.socketFactory
        else -> null
    }

    /**
     * Trust-all SSL context backing [tlsSocketFactoryFor]. Debug opt-in only
     * (`-PallowInsecureMqttTls=true`) for non-official hosts.
     */
    private val trustAllSslContext: SSLContext by lazy {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
        context
    }
}
