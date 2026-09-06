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
import kotlinx.coroutines.NonCancellable
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
    private val clientFactory: (String, String) -> MqttAsyncClient = { broker, clientId ->
        MqttAsyncClient(broker, clientId, MemoryPersistence())
    },
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
    private val preconnectMutex = Mutex()

    private data class ConnectionIdentity(
        val token: String?,
        val vehicleKey: String,
        val userId: String,
        val broker: String,
        val imei: String,
        val modelType: Int?,
        val username: String,
        val password: String,
    ) {
        override fun toString(): String = "ConnectionIdentity(redacted)"
    }

    private fun connectionIdentity(vehicle: OfficialVehicle, userId: String, token: String?) =
        ConnectionIdentity(
            token = token,
            vehicleKey = vehicle.key,
            userId = userId.trim(),
            broker = OfficialMqttConfig.brokerUriFor(vehicle),
            imei = OfficialMqttConfig.commandImei(vehicle),
            modelType = vehicle.modelType,
            username = vehicle.mqUsername.trim(),
            password = vehicle.mqPassword.trim(),
        )

    private fun isCurrentConnection(identity: ConnectionIdentity, cloud: OfficialCloudService?): Boolean {
        if (_disposed || (_boundCloud ?: defaultCloud) !== cloud) return false
        if (cloud == null) return true
        val state = cloud.currentState
        val vehicle = state.selectedVehicle ?: return false
        return state.signedIn && connectionIdentity(vehicle, state.userId, state.token) == identity
    }

    private fun ensureCurrentConnection(identity: ConnectionIdentity, cloud: OfficialCloudService?) {
        if (!isCurrentConnection(identity, cloud)) {
            throw OfficialCloudApiException("车辆或登录状态已变化，请重新操作")
        }
    }

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
    @Volatile private var _connectedIdentity: ConnectionIdentity? = null
    @Volatile private var _connectedTransportSecurity: MqttTransportSecurity? = null
    @Volatile private var _pendingCommandApiName: String? = null
    @Volatile private var _pendingCommandError: String? = null
    @Volatile private var _acknowledgedCommandApiName: String? = null
    @Volatile private var _latestStatusPayload: OfficialMqttStatusPayload? = null
    @Volatile private var _lastSendPath: OfficialRemoteSendPath? = null
    @Volatile private var _lastPreconnectError: String? = null
    @Volatile private var _lastPreconnectRawError: String? = null
    @Volatile private var _preconnectInFlight: Boolean = false
    @Volatile private var _disposed: Boolean = false
    @Volatile private var _boundCloud: OfficialCloudService? = null
    private var _cloudJob: Job? = null
    private var _reconnectJob: Job? = null
    private val reconnectGeneration = java.util.concurrent.atomic.AtomicLong()
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
    val acknowledgedCommandApiName: String? get() = synchronized(lock) { _acknowledgedCommandApiName }
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
        // Only preconnect when the *session identity* (vehicle key / user) or
        // the connectable address actually changed. The official app connects
        // lazily (MqttUtil.connect is invoked on command send / explicit
        // trigger), and every cloud-state emission here used to re-run
        // preconnect (config assembly + IMEI derivation + lock + logging)
        // even for unrelated refreshes (loading flags, messages, battery).
        val sessionKey = connectionIdentity(vehicle, state.userId, state.token)
        if (sessionKey == _lastSessionKey) return
        preconnect(vehicle = vehicle, userId = state.userId)
        if (isCurrentConnection(sessionKey, _boundCloud ?: defaultCloud)) _lastSessionKey = sessionKey
    }

    /** Includes account, credentials, broker and topic configuration. */
    @Volatile private var _lastSessionKey: ConnectionIdentity? = null

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
        val cloud = _boundCloud ?: defaultCloud
        val identity = connectionIdentity(vehicle, userId, cloud?.currentState?.token)
        preconnectMutex.withLock { preconnectInternal(vehicle, userId, force, identity, cloud) }
    }

    private suspend fun preconnectInternal(
        vehicle: OfficialVehicle,
        userId: String,
        force: Boolean,
        identity: ConnectionIdentity,
        cloud: OfficialCloudService?,
    ) {
        if (_disposed) return
        if (!force &&
            isConnected &&
            _connectedIdentity == identity && isCurrentConnection(identity, cloud)
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
        _preconnectInFlight = true
        var lastError: Throwable? = null
        try {
            val maxAttempts = 1 + OfficialMqttConfig.PRECONNECT_MAX_RETRIES
            for (attempt in 1..maxAttempts) {
                if (!isCurrentConnection(identity, cloud)) return
                try {
                    lifecycleMutex.withLock { ensureConnectedInternal(vehicle, userId, identity, cloud) }
                    if (!isCurrentConnection(identity, cloud)) return
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
                    if (!isCurrentConnection(identity, cloud)) return
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
            _preconnectInFlight = false
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
            val generation = reconnectGeneration.get()
            _reconnectJob = scope.launch { autoReconnectLoop(lostClient, generation) }
        }
    }

    /**
     * Bounded exponential-backoff restore of the lost session. Self-terminates
     * when the session moved on (logout clears `_client`; a vehicle switch or
     * manual preconnect installs a different client), so no explicit cancel
     * bookkeeping is needed on those paths.
     */
    private suspend fun autoReconnectLoop(lostClient: MqttAsyncClient, generation: Long) {
        val cloud = _boundCloud ?: return
        val expectedToken = cloud.currentState.token
        val expectedVehicleKey = cloud.currentState.selectedVehicle?.key
        for (attempt in 1..AUTO_RECONNECT_MAX_ATTEMPTS) {
            val delayMs = (AUTO_RECONNECT_BASE_DELAY_MS shl (attempt - 1))
                .coerceAtMost(AUTO_RECONNECT_MAX_DELAY_MS)
            delay(delayMs)
            if (_disposed || reconnectGeneration.get() != generation || _boundCloud !== cloud) return
            // Superseded by a newer session or explicitly disconnected.
            // A failed attempt clears _client; keep retrying that same cloud session.
            if (_client != null && _client !== lostClient) return
            val state = cloud.currentState
            val vehicle = state.selectedVehicle
            if (!state.signedIn || vehicle == null || state.token != expectedToken || vehicle.key != expectedVehicleKey) return
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
        reconnectGeneration.incrementAndGet()
        synchronized(lock) {
            _reconnectJob?.cancel()
            _reconnectJob = null
        }
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
        _connectedIdentity = null
        _connectedTransportSecurity = null
        // Forget the bound session identity too: after sign-out + sign-in to
        // the SAME account/vehicle, onCloudState must re-run preconnect —
        // keeping the old key silently skipped it and the link stayed down
        // until the first command send.
        _lastSessionKey = null
        _pendingCommandApiName = null
        _pendingCommandError = null
        _acknowledgedCommandApiName = null
        _latestStatusPayload = null
        _lastPreconnectError = null
        _lastPreconnectRawError = null
        _subscribedTopics.value = emptyList()
        _linkState.value = OfficialMqttLinkState.DISCONNECTED
        if (old != null) {
            teardownClient(old)
        }
    }

    private suspend fun teardownClient(client: MqttAsyncClient) = withContext(NonCancellable + Dispatchers.IO) {
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
     * Ensure a live MQTT session for [vehicle], reconnecting when its account,
     * credentials or transport configuration changes. Dart `ensureConnected`.
     */
    suspend fun ensureConnected(vehicle: OfficialVehicle, userId: String) {
        val cloud = _boundCloud ?: defaultCloud
        val identity = connectionIdentity(vehicle, userId, cloud?.currentState?.token)
        lifecycleMutex.withLock { ensureConnectedInternal(vehicle, userId, identity, cloud) }
    }

    private suspend fun ensureConnectedInternal(
        vehicle: OfficialVehicle,
        userId: String,
        identity: ConnectionIdentity,
        cloud: OfficialCloudService?,
    ) {
        if (_disposed) throw OfficialCloudApiException("MQTT 服务已释放")
        ensureCurrentConnection(identity, cloud)
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
        if (isConnected && _connectedIdentity == identity) {
            _linkState.value = OfficialMqttLinkState.CONNECTED
            return
        }

        disconnectInternal()
        ensureCurrentConnection(identity, cloud)
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
            val created = clientFactory(broker, clientId)
            newClient = created
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                isAutomaticReconnect = false
                connectionTimeout = OfficialMqttConfig.CONNECT_TIMEOUT.inWholeSeconds.toInt()
                keepAliveInterval = OfficialMqttConfig.KEEP_ALIVE_SECONDS
                userName = mqUser
                this.password = mqPass.toCharArray()
                if (parsed.security == MqttTransportSecurity.TLS) {
                    // Hosts served down by the official cloud (mqHost/mqPort on
                    // the vehicle row) are official private-CA endpoints too;
                    // trusting them is what keeps C18/QGJ remote control from
                    // silently degrading to the HTTP fallback. Never trust a
                    // host that came from anywhere else.
                    val servedByCloud = vehicle.mqHost.trim().isNotEmpty() &&
                        vehicle.mqPort.trim().isNotEmpty()
                    val socketFactoryOverride = tlsSocketFactoryFor(
                        host = parsed.host,
                        trustOfficialMqHost = servedByCloud,
                    )
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
                    enqueueStatusPayload(created, raw)
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
            ensureCurrentConnection(identity, cloud)
        } catch (e: Throwable) {
            _linkState.value = OfficialMqttLinkState.DISCONNECTED
            log.operation(
                "官方 MQTT 底层连接异常",
                detail = formatConnectError(e),
                level = LogLevel.DEBUG,
            )
            newClient?.let { teardownClient(it) }
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
            ensureCurrentConnection(identity, cloud)
        } catch (e: Throwable) {
            _linkState.value = OfficialMqttLinkState.DISCONNECTED
            teardownClient(client)
            if (e is CancellationException) throw e
            log.operation(
                "官方 MQTT 订阅失败",
                detail = formatConnectError(e),
                level = LogLevel.WARNING,
            )
            throw translateConnectError(e, broker)
        }

        _client = client
        _connectedIdentity = identity
        _connectedTransportSecurity = parsed.security
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
    private data class StatusMessage(val client: MqttAsyncClient, val raw: String)
    private val statusPayloads = Channel<StatusMessage>(Channel.CONFLATED)

    @Volatile private var statusConsumerStarted = false

    /**
     * While a command is pending EVERY frame may be its ACK (a vehicle moving
     * streams ACC/defence frames right after "stop"/"start", and the CONFLATED
     * channel would collapse the ACK frame away). Route pending-window frames
     * synchronously so the single-shot confirmation is never dropped; only
     * idle-window status uses the conflated queue.
     */
    private fun enqueueStatusPayload(client: MqttAsyncClient, raw: String) {
        if (_disposed || _client !== client) return
        if (_pendingCommandApiName != null) {
            try {
                handleStatusPayload(raw)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                log.operation(
                    "官方 MQTT 状态处理异常",
                    detail = e.toString(),
                    level = LogLevel.DEBUG,
                )
            }
        } else {
            ensureStatusConsumer()
            statusPayloads.trySend(StatusMessage(client, raw))
        }
    }

    private fun ensureStatusConsumer() {
        if (statusConsumerStarted) return
        synchronized(lock) {
            if (statusConsumerStarted) return
            statusConsumerStarted = true
            scope.launch {
                for ((client, raw) in statusPayloads) {
                    if (_disposed || _client !== client) continue
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
        if (_disposed) return
        val identity = _connectedIdentity
        if (identity != null && !isCurrentConnection(identity, _boundCloud ?: defaultCloud)) return
        val payload = OfficialMqttStatusPayload.tryParse(raw) ?: return

        val cloud = _boundCloud ?: defaultCloud
        if (cloud != null) {
            val state = cloud.currentState
            if (!state.signedIn || state.selectedVehicle == null) return
            val selectedImei = state.selectedVehicle?.commandImei?.trim().orEmpty()
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
                _acknowledgedCommandApiName = pending
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
        val cloud = _boundCloud ?: defaultCloud
        val token = cloud?.currentState?.token
        val identity = connectionIdentity(vehicle, userId, token)
        val override = publishCommandOverride
        if (override != null) {
            setPending(commandApiName, null)
            override(vehicle, userId, commandApiName)
            return
        }
        ensureConnected(vehicle = vehicle, userId = userId)
        if (cloud != null &&
            (cloud.currentState.token != token || cloud.currentState.selectedVehicle?.key != vehicle.key)
        ) {
            throw OfficialCloudApiException("车辆或登录状态已变化，请重新操作")
        }
        val client = _client
        if (client == null || !client.isConnected) {
            throw OfficialCloudApiException("官方 MQTT 未连接")
        }
        val imei = OfficialMqttConfig.commandImei(vehicle)
        val topic = OfficialMqttConfig.publishTopic(vehicle, imei)
        val payload = OfficialMqttConfig.commandPayload(imei, commandApiName)

        setPending(commandApiName, null)
        withContext(Dispatchers.IO) {
            ensureCurrentConnection(identity, cloud)
            if (_client !== client || _connectedIdentity != identity) {
                throw OfficialCloudApiException("官方 MQTT 连接已变化，请重试")
            }
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
            _acknowledgedCommandApiName = null
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

        fun ensureSameSession() {
            val current = cloud.currentState
            if (current.token != state.token || current.selectedVehicle?.key != vehicle.key) {
                throw OfficialCloudApiException("车辆或登录状态已变化，请重新操作")
            }
        }

        try {
            publishCommand(
                vehicle = vehicle,
                userId = state.userId,
                commandApiName = api.apiName,
            )
            ensureSameSession()
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
                _acknowledgedCommandApiName = null
            }
            // HTTP resolves its target from current cloud state. Never retarget
            // an A command to B after a vehicle switch or a different login.
            ensureSameSession()
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
            _lastSessionKey = null
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
     * for the hardcoded official hosts **and** for hosts the official cloud
     * actually serves down in `mqHost`/`mqPort` (those are the same private-CA
     * infrastructure, so system validation fails on them too and the vehicle
     * would otherwise be stuck on the HTTP fallback forever). The Debug
     * `ALLOW_INSECURE_MQTT_TLS` hatch remains the only way for arbitrary hosts.
     */
    private fun tlsSocketFactoryFor(host: String, trustOfficialMqHost: Boolean): SSLSocketFactory? = when {
        host.lowercase() in OFFICIAL_TLS_HOSTS || (trustOfficialMqHost && host.isNotBlank()) -> {
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
