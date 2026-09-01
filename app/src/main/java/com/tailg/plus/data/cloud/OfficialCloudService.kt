package com.tailg.plus.data.cloud

import androidx.annotation.VisibleForTesting
import com.tailg.plus.data.model.AffirmBatteryInfoRequest
import com.tailg.plus.data.model.CommandCode
import com.tailg.plus.data.model.OfficialBatterySpec
import com.tailg.plus.data.model.OfficialBatteryType
import com.tailg.plus.data.model.OfficialGaragePage
import com.tailg.plus.data.model.OfficialRidePeriod
import com.tailg.plus.data.model.OfficialSmartServiceControlDecision
import com.tailg.plus.data.model.OfficialSmartServiceStatus
import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.data.model.OfficialVehicleSelfCheck
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService
import com.tailg.plus.util.formatMonthText
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Public facade for the official cloud API — port of
 * `lib/services/official_cloud_service.dart` (the 2735-line Dart singleton).
 *
 * The Dart class is split by responsibility into three Kotlin files while
 * keeping the external API semantics identical:
 * - [OfficialCloudService] (this file): public API surface, session state
 *   (`StateFlow`), refresh-coalescing / session helpers, test hooks
 * - [OfficialCloudRefreshLogic]: per-resource refresh bodies
 * - [OfficialCloudOperationLogic]: login / logout / control / bind operations
 *
 * Threading: every network call runs on [Dispatchers.IO] inside the client;
 * refresh coalescing and silent refreshes run on the injected [scope]
 * (default `SupervisorJob() + Dispatchers.Default`).
 *
 * Naming notes vs Dart: `stateStream` → [stateFlow] (StateFlow), `state` →
 * [currentState]; the broadcast `StreamController` becomes a `MutableStateFlow`
 * so setting the internal state auto-notifies collectors (Dart's explicit
 * `_emit()` is folded into the internal [state] setter).
 */
class OfficialCloudService(
    storage: OfficialCloudStorage,
    apiClient: OfficialCloudApiClientInterface,
    vehicleStore: OfficialCloudVehicleStore,
    log: LogService = LogService(),
    clock: () -> LocalDateTime = { LocalDateTime.now() },
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    // -- internal state (module-visible for the refresh/operations logic) ----

    internal val storage: OfficialCloudStorage = storage
    internal var apiClient: OfficialCloudApiClientInterface = apiClient
    internal val vehicleStore: OfficialCloudVehicleStore = vehicleStore
    internal val log: LogService = log
    internal var clock: () -> LocalDateTime = clock
    internal val scope: CoroutineScope = scope

    private val _state: MutableStateFlow<OfficialCloudState> =
        MutableStateFlow(OfficialCloudState.initial())

    internal var state: OfficialCloudState
        get() = _state.value
        set(value) {
            if (!disposed) _state.value = value
        }

    internal val lastSuccessfulRefresh: MutableMap<String, LocalDateTime> = mutableMapOf()
    internal val inFlightRefreshes: MutableMap<String, Deferred<Unit>> = mutableMapOf()
    internal val smartServiceStatuses: MutableMap<String, OfficialSmartServiceStatus> = mutableMapOf()
    internal val smartServiceStatusLoadedKeys: MutableSet<String> = mutableSetOf()
    internal var rideStatisticsGeneration: Int = 0
    internal var initialized: Boolean = false
    internal var initializing: Deferred<Unit>? = null
    internal var disposed: Boolean = false

    private val refreshLogic: OfficialCloudRefreshLogic = OfficialCloudRefreshLogic(this)
    private val operationsLogic: OfficialCloudOperationLogic = OfficialCloudOperationLogic(this, refreshLogic)

    // -- public state / diagnostics ------------------------------------------

    /** Dart `stateStream` → StateFlow (UI observes this). */
    val stateFlow: StateFlow<OfficialCloudState> = _state.asStateFlow()

    /** Dart `state` getter. */
    val currentState: OfficialCloudState get() = _state.value

    val lastRequest: OfficialCloudRequestSummary? get() = apiClient.lastRequest

    val lastVehiclesRefreshAt: LocalDateTime? get() = lastSuccessfulRefresh["vehicles"]

    val lastBatteryRefreshAt: LocalDateTime? get() = lastSuccessfulRefresh["batteryInfo"]

    val lastBmsRefreshAt: LocalDateTime? get() = lastSuccessfulRefresh["bmsInfo"]

    val selectedSmartServiceStatus: OfficialSmartServiceStatus?
        get() {
            val key = state.selectedVehicle?.key ?: return null
            return smartServiceStatuses[key]
        }

    val selectedRemoteControlServiceDecision: OfficialSmartServiceControlDecision
        get() {
            val vehicle = state.selectedVehicle
            val status = selectedSmartServiceStatus
            if (vehicle == null || status == null) {
                return OfficialSmartServiceControlDecision()
            }
            return status.decisionForModelType(vehicle.modelType)
        }

    // -- lifecycle -----------------------------------------------------------

    suspend fun init() = initInternal(refreshOnSignedIn = true)

    @VisibleForTesting
    suspend fun initForTest(refreshOnSignedIn: Boolean = false) {
        initInternal(refreshOnSignedIn = refreshOnSignedIn)
    }

    internal suspend fun initInternal(refreshOnSignedIn: Boolean) {
        if (initialized) return
        val inFlight = initializing
        if (inFlight != null) {
            inFlight.await()
            return
        }
        coroutineScope {
            val job = async { refreshLogic.loadInitialSession(refreshOnSignedIn) }
            initializing = job
            try {
                job.await()
            } finally {
                if (initializing === job) initializing = null
            }
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        scope.cancel()
        apiClient.dispose()
    }

    /** Resets the service state so it can be re-used after [dispose]. */
    fun resetForTest(
        clock: (() -> LocalDateTime)? = null,
        apiConfig: OfficialCloudApiConfig? = null,
    ) {
        apiClient.dispose()
        apiClient = OfficialCloudApiClient(config = apiConfig ?: OfficialCloudApiConfig(), log = log)
        disposed = false
        initialized = false
        initializing = null
        this.clock = clock ?: { LocalDateTime.now() }
        _state.value = OfficialCloudState.initial()
        clearRefreshCache()
        rideStatisticsGeneration++
        sentCommands.clear()
        sentKksHidStates.clear()
        sentCarOperatorUpdates.clear()
        sendCommandOverride = null
        setKksHidEnabledOverride = null
        bindVehicleByImeiOverride = null
        refreshSmartServiceStatusOverride = null
        setCarOperatorOverride = null
        unbindVehicleOverride = null
        getFirmVersionOverride = null
        getMessageControlOverride = null
        setMessagePushConfigOverride = null
        deleteMessagesOverride = null
        refreshRideStatisticsOverride = null
        refreshTravelHistoryOverride = null
        selectVehicleOverride = null
        fetchGaragePageOverride = null
        changeUsingVehicleOverride = null
        afterLogoutSideEffects.clear()
        afterLogoutKeys.clear()
    }

    @VisibleForTesting
    fun setStateForTest(state: OfficialCloudState) {
        this.state = state
        initialized = state.initialized
    }

    // -- public API: refresh logic (delegated) -------------------------------

    suspend fun refreshUserProfile(silent: Boolean = false, force: Boolean = false) =
        refreshLogic.refreshUserProfile(silent = silent, force = force)

    suspend fun refreshVehicles(
        silent: Boolean = false,
        refreshReplicaDetails: Boolean = true,
        force: Boolean = false,
        preferredVehicleKey: String? = null,
        refreshDependents: Boolean = true,
    ) = refreshLogic.refreshVehicles(
        silent = silent,
        refreshReplicaDetails = refreshReplicaDetails,
        force = force,
        preferredVehicleKey = preferredVehicleKey,
        refreshDependents = refreshDependents,
    )

    suspend fun fetchGaragePage(
        pageIndex: Int = 1,
        frame: String = "",
        shareUserPhone: String = "",
    ): OfficialGaragePage = refreshLogic.fetchGaragePage(
        pageIndex = pageIndex,
        frame = frame,
        shareUserPhone = shareUserPhone,
    )

    suspend fun refreshMessages(
        silent: Boolean = false,
        force: Boolean = false,
        pageSize: Int = 20,
        pageIndex: Int = 1,
    ) = refreshLogic.refreshMessages(
        silent = silent,
        force = force,
        pageSize = pageSize,
        pageIndex = pageIndex,
    )

    suspend fun refreshBatteryInfo(silent: Boolean = false, force: Boolean = false) =
        refreshLogic.refreshBatteryInfo(silent = silent, force = force)

    suspend fun refreshBmsInfo(silent: Boolean = false, force: Boolean = false) =
        refreshLogic.refreshBmsInfo(silent = silent, force = force)

    suspend fun fetchBatteryTypes(): List<OfficialBatteryType> = refreshLogic.fetchBatteryTypes()

    suspend fun fetchBatterySpecsByType(typeId: String): List<OfficialBatterySpec> =
        refreshLogic.fetchBatterySpecsByType(typeId)

    suspend fun refreshVehicleLocation(silent: Boolean = false, force: Boolean = false) =
        refreshLogic.refreshVehicleLocation(silent = silent, force = force)

    suspend fun refreshFenceData(silent: Boolean = false, force: Boolean = false) =
        refreshLogic.refreshFenceData(silent = silent, force = force)

    suspend fun refreshTodayRideMileage(silent: Boolean = false, force: Boolean = false) =
        refreshLogic.refreshTodayRideMileage(silent = silent, force = force)

    suspend fun refreshRideStatistics(
        period: OfficialRidePeriod,
        silent: Boolean = false,
        force: Boolean = false,
    ) = refreshLogic.refreshRideStatistics(period = period, silent = silent, force = force)

    suspend fun refreshTravelHistory(
        month: String? = null,
        silent: Boolean = false,
        force: Boolean = false,
    ) = refreshLogic.refreshTravelHistory(month = month, silent = silent, force = force)

    suspend fun refreshTravelDetail(travelId: String) = refreshLogic.refreshTravelDetail(travelId)

    suspend fun refreshSelectedSmartServiceStatus(silent: Boolean = true, force: Boolean = false) =
        refreshLogic.refreshSelectedSmartServiceStatus(silent = silent, force = force)

    suspend fun resolveSelectedRemoteControlServiceDecision(): OfficialSmartServiceControlDecision =
        refreshLogic.resolveSelectedRemoteControlServiceDecision()

    // -- public API: operations (delegated) ----------------------------------

    suspend fun requestSmsCode(phone: String, ticket: String, randstr: String) =
        operationsLogic.requestSmsCode(phone, ticket, randstr)

    suspend fun login(phone: String, smsCode: String) = operationsLogic.login(phone, smsCode)

    suspend fun loginWithToken(
        rawToken: String,
        phone: String = "",
        userId: String = "",
    ) = operationsLogic.loginWithToken(rawToken, phone, userId)

    suspend fun logout() = operationsLogic.logout()

    suspend fun updateUserNickname(nickName: String) = operationsLogic.updateUserNickname(nickName)

    suspend fun selectVehicle(vehicle: OfficialVehicle) = operationsLogic.selectVehicle(vehicle)

    suspend fun changeUsingVehicle(vehicle: OfficialVehicle) = operationsLogic.changeUsingVehicle(vehicle)

    fun applyMqttVehicleStatus(acc: Int?, defenceStatus: Int?) =
        operationsLogic.applyMqttVehicleStatus(acc, defenceStatus)

    suspend fun affirmBatteryInfo(request: AffirmBatteryInfoRequest) =
        operationsLogic.affirmBatteryInfo(request)

    suspend fun updateFenceData(
        enabled: Boolean,
        radiusValue: Int,
        timeFrom: String,
        timeTo: String,
    ) = operationsLogic.updateFenceData(enabled, radiusValue, timeFrom, timeTo)

    suspend fun updateCarNickName(carId: String, carNickName: String) =
        operationsLogic.updateCarNickName(carId, carNickName)

    suspend fun getMessageControl(): Map<String, Boolean> = operationsLogic.getMessageControl()

    suspend fun setMessagePushConfig(config: Map<String, Boolean>) =
        operationsLogic.setMessagePushConfig(config)

    suspend fun deleteMessages() = operationsLogic.deleteMessages()

    suspend fun linkLocalVehicle(officialVehicleKey: String, localVehicleId: String) =
        operationsLogic.linkLocalVehicle(officialVehicleKey, localVehicleId)

    suspend fun unlinkLocalVehicle(officialVehicleKey: String) =
        operationsLogic.unlinkLocalVehicle(officialVehicleKey)

    suspend fun pruneLocalVehicleLinks(validLocalVehicleIds: Set<String>) =
        operationsLogic.pruneLocalVehicleLinks(validLocalVehicleIds)

    suspend fun selfCheck(): OfficialVehicleSelfCheck = operationsLogic.selfCheck()

    suspend fun sendCommand(command: CommandCode): String = operationsLogic.sendCommand(command)

    suspend fun syncCarOperatorAfterCommand(command: CommandCode, vehicle: OfficialVehicle) =
        operationsLogic.syncCarOperatorAfterCommand(command, vehicle)

    suspend fun setCarOperator(carId: String, operatorFlag: String) =
        operationsLogic.setCarOperator(carId, operatorFlag)

    suspend fun setKksHidEnabled(enabled: Boolean) = operationsLogic.setKksHidEnabled(enabled)

    suspend fun bindVehicleByImei(imei: String) = operationsLogic.bindVehicleByImei(imei)

    suspend fun unbindVehicle(carId: String? = null, unbindType: Int = 1) =
        operationsLogic.unbindVehicle(carId, unbindType)

    suspend fun getFirmVersion(imei: String? = null): Map<String, Any?> =
        operationsLogic.getFirmVersion(imei)

    // -- shared coordination helpers (used by refresh/operations logic) ------

    /**
     * Coalesce silent refreshes that share a [refreshKey]: reuse in-flight work
     * and skip when a successful refresh is still within TTL (unless [force]).
     * Non-silent callers always wait for a fresh run.
     */
    internal suspend fun coalesceRefresh(
        refreshKey: String,
        silent: Boolean,
        force: Boolean,
        run: suspend () -> Unit,
    ) {
        if (!force && silent && shouldUseRecentRefresh(refreshKey)) return
        val inFlight = inFlightRefreshes[refreshKey]
        if (silent && inFlight != null) {
            inFlight.await()
            return
        }
        coroutineScope {
            val refresh = async { run() }
            inFlightRefreshes[refreshKey] = refresh
            try {
                refresh.await()
            } finally {
                if (inFlightRefreshes[refreshKey] === refresh) {
                    inFlightRefreshes.remove(refreshKey)
                }
            }
        }
    }

    internal fun shouldUseRecentRefresh(key: String): Boolean {
        val refreshedAt = lastSuccessfulRefresh[key] ?: return false
        return Duration.between(refreshedAt, clock()).toMillis() < SILENT_REFRESH_TTL_MILLIS
    }

    internal fun markRefreshSuccess(key: String) {
        lastSuccessfulRefresh[key] = clock()
    }

    internal fun clearRefreshCache() {
        lastSuccessfulRefresh.clear()
        inFlightRefreshes.clear()
        smartServiceStatuses.clear()
        smartServiceStatusLoadedKeys.clear()
    }

    /** Fire-and-forget refresh that logs failures (Dart `_runSilentRefresh`). */
    internal fun runSilentRefresh(block: suspend () -> Unit, failureMessage: String) {
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                log.operation(
                    failureMessage,
                    detail = OfficialCloudRedactor.errorMessage(e),
                    level = LogLevel.WARNING,
                )
            }
        }
    }

    internal fun isCurrentSession(token: String): Boolean =
        token.isNotEmpty() && state.token == token

    internal fun ensureCurrentSession(token: String) {
        if (!isCurrentSession(token)) {
            throw OfficialCloudApiException("官方登录状态已变化，请重试")
        }
    }

    internal fun ensureSuccess(body: Map<String, Any?>, fallback: String) {
        val msg = body["msg"]?.toString()
        if (!OfficialCloudResponseCode.isSuccessBody(body)) {
            throw OfficialCloudApiException(
                OfficialCloudRedactor.text(if (msg.isNullOrEmpty()) fallback else msg),
            )
        }
    }

    internal fun setLoading(loading: Boolean) {
        state = state.copyWith(loading = loading)
    }

    internal suspend fun saveLinks(links: Map<String, String>) {
        val normalized = OfficialCloudVehicleLinks.normalize(links)
        storage.saveLinks(normalized)
        state = state.copyWith(localVehicleLinks = normalized)
    }

    internal fun selectVehicleKey(
        vehicles: List<OfficialVehicle>,
        preferredKey: String?,
    ): String? {
        if (vehicles.isEmpty()) return null
        val key = preferredKey?.trim()
        if (key != null && key.isNotEmpty() && vehicles.any { it.key == key }) {
            return key
        }
        return vehicles.first().key
    }

    internal fun vehicleByKey(
        vehicles: List<OfficialVehicle>,
        selectedKey: String?,
    ): OfficialVehicle? {
        if (vehicles.isEmpty() || selectedKey == null) return null
        return vehicles.firstOrNull { it.key == selectedKey }
    }

    internal fun currentMonth(): String = formatMonthText(clock())

    internal suspend fun handleAuthFailureIfNeeded(error: Throwable) {
        if (!OfficialCloudAuthParser.looksLikeAuthError(error)) return
        logout()
        state = state.copyWith(error = "官方登录已失效，请重新登录")
    }

    internal fun isCurrentRideStatisticsRequest(
        generation: Int,
        token: String,
        vehicleKey: String,
        period: OfficialRidePeriod,
    ): Boolean =
        generation == rideStatisticsGeneration &&
            isCurrentSession(token) &&
            state.selectedVehicle?.key == vehicleKey &&
            state.ridePeriod == period

    internal fun refreshVehicleDependents(refreshReplicaDetails: Boolean) {
        runSilentRefresh(
            { refreshLogic.refreshSelectedSmartServiceStatus(silent = true) },
            failureMessage = "官方智能服务状态静默刷新失败",
        )
        runSilentRefresh(
            { refreshLogic.refreshBatteryInfo(silent = true) },
            failureMessage = "官方电池信息静默刷新失败",
        )
        runSilentRefresh(
            { refreshLogic.refreshBmsInfo(silent = true) },
            failureMessage = "官方 BMS 信息静默刷新失败",
        )
        runSilentRefresh(
            { refreshLogic.refreshTodayRideMileage(silent = true) },
            failureMessage = "官方今日骑行静默刷新失败",
        )
        if (!refreshReplicaDetails) return
        runSilentRefresh(
            { refreshLogic.refreshVehicleLocation(silent = true) },
            failureMessage = "官方停车位置静默刷新失败",
        )
        runSilentRefresh(
            { refreshLogic.refreshFenceData(silent = true) },
            failureMessage = "官方电子围栏静默刷新失败",
        )
    }

    internal fun refreshVehiclesAfterCommand(command: CommandCode) {
        runSilentRefresh(
            {
                // Official `updateCarControlInfo`: one carStatus request, no
                // dependent cascade — the confirmation loop owns consistency.
                refreshLogic.refreshVehicles(
                    silent = true,
                    refreshReplicaDetails = false,
                    force = true,
                    preferredVehicleKey = null,
                    refreshDependents = false,
                )
            },
            failureMessage = "官方云端指令后刷新状态失败: ${command.label}",
        )
    }

    /**
     * Sync the selected official vehicle into the local garage (VehicleStore):
     * reuse the linked local vehicle when present, otherwise upsert a profile
     * derived from the official one and record the link (P1-5).
     */
    internal suspend fun applySelectedVehicleToLocalProfile() {
        val vehicle = state.selectedVehicle ?: return
        vehicleStore.init()

        val decision = OfficialCloudVehicleSyncPlanner.plan(
            selectedVehicle = vehicle,
            localVehicleLinks = state.localVehicleLinks,
            localVehicles = vehicleStore.vehicles,
        ) ?: return

        val linkedLocalVehicleId = decision.linkedLocalVehicleId
        if (linkedLocalVehicleId != null) {
            vehicleStore.setDefault(linkedLocalVehicleId)
            return
        }

        val profileData = decision.profileData ?: return
        val profile = vehicleStore.upsert(
            id = profileData.id,
            name = profileData.name,
            protocol = profileData.protocol,
            makeDefault = true,
        )
        if (!OfficialCloudVehicleLinks.isLinkedTo(
                state.localVehicleLinks,
                officialVehicleKey = vehicle.key,
                localVehicleId = profile.id,
            )
        ) {
            saveLinks(
                OfficialCloudVehicleLinks.link(
                    state.localVehicleLinks,
                    officialVehicleKey = vehicle.key,
                    localVehicleId = profile.id,
                ),
            )
        }
        log.operation("官方车辆已同步到本地车库", detail = "${vehicle.displayName} ${profile.id}")
    }

    // -- test hooks (Dart @visibleForTesting fields) -----------------------

    /**
     * Side effects run after a successful logout (MQTT teardown, BLE disconnect).
     * Prefer [registerAfterLogout] so registration is idempotent and test-friendly.
     */
    val afterLogoutSideEffects: MutableList<suspend () -> Unit> = mutableListOf()

    /** Registers a logout hook once (no-op if [key] was already registered). */
    fun registerAfterLogout(key: String, effect: suspend () -> Unit) {
        if (afterLogoutKeys.add(key)) {
            afterLogoutSideEffects += effect
        }
    }

    private val afterLogoutKeys: MutableSet<String> = mutableSetOf()

    /** Test-only override for [sendCommand] (records into [sentCommands]). */
    @VisibleForTesting
    var sendCommandOverride: (suspend (CommandCode) -> String)? = null

    @VisibleForTesting
    var setKksHidEnabledOverride: (suspend (Boolean) -> Unit)? = null

    @VisibleForTesting
    var bindVehicleByImeiOverride: (suspend (String) -> Unit)? = null

    @VisibleForTesting
    var refreshSmartServiceStatusOverride: (suspend (OfficialVehicle) -> OfficialSmartServiceStatus)? = null

    @VisibleForTesting
    var setCarOperatorOverride: (suspend (String, String) -> Unit)? = null

    @VisibleForTesting
    var unbindVehicleOverride: (suspend (String, Int) -> Unit)? = null

    @VisibleForTesting
    var getFirmVersionOverride: (suspend (String) -> Map<String, Any?>)? = null

    /** Test-only override for loading official notification preferences. */
    @VisibleForTesting
    var getMessageControlOverride: (suspend () -> Map<String, Boolean>)? = null

    /** Test-only override for saving official notification preferences. */
    @VisibleForTesting
    var setMessagePushConfigOverride: (suspend (Map<String, Boolean>) -> Unit)? = null

    /** Test-only override for the official server-side message deletion call. */
    @VisibleForTesting
    var deleteMessagesOverride: (suspend () -> Unit)? = null

    /** Test-only override for controlling ride-statistics request completion. */
    @VisibleForTesting
    var refreshRideStatisticsOverride: (suspend (OfficialRidePeriod) -> Unit)? = null

    /** Test-only override for controlling travel-history request completion. */
    @VisibleForTesting
    var refreshTravelHistoryOverride: (suspend (String) -> Unit)? = null

    /** Test-only override for controlling official vehicle selection. */
    @VisibleForTesting
    var selectVehicleOverride: (suspend (OfficialVehicle) -> Unit)? = null

    @VisibleForTesting
    var fetchGaragePageOverride: (suspend (Int, String, String) -> OfficialGaragePage)? = null

    @VisibleForTesting
    var changeUsingVehicleOverride: (suspend (OfficialVehicle) -> Unit)? = null

    /** Records every command handed to [sendCommand] (test-only). */
    @VisibleForTesting
    val sentCommands: MutableList<CommandCode> = mutableListOf()

    @VisibleForTesting
    val sentKksHidStates: MutableList<Boolean> = mutableListOf()

    @VisibleForTesting
    val sentCarOperatorUpdates: MutableList<OfficialCarOperatorUpdate> = mutableListOf()

    private companion object {
        /** Dart `_silentRefreshTtl = Duration(seconds: 45)`. */
        const val SILENT_REFRESH_TTL_MILLIS = 45_000L
    }
}
