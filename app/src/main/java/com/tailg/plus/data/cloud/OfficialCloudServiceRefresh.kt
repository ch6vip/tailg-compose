package com.tailg.plus.data.cloud

import com.tailg.plus.data.model.OfficialBatterySpec
import com.tailg.plus.data.model.OfficialBatteryType
import com.tailg.plus.data.model.OfficialGaragePage
import com.tailg.plus.data.model.OfficialRidePeriod
import com.tailg.plus.data.model.OfficialRideStatistics
import com.tailg.plus.data.model.OfficialSmartServiceControlDecision
import com.tailg.plus.data.model.OfficialSmartServiceStatus
import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.data.model.requestKey
import com.tailg.plus.data.model.wireName
import com.tailg.plus.log.LogLevel
import com.tailg.plus.util.SensitiveValueMasker
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Refresh logic of `OfficialCloudService` — port of the `refresh*` /
 * `fetch*` / `*Now` methods of `lib/services/official_cloud_service.dart`.
 *
 * Every method operates on the facade's internal session state ([service])
 * and follows the same skeleton as the Dart original: token/session guards,
 * `coalesceRefresh` for silent TTL/in-flight dedup, loading-state mutations,
 * `ensureSuccess`, per-resource error capture into the state, and
 * `markRefreshSuccess` on success.
 */
internal class OfficialCloudRefreshLogic(
    private val service: OfficialCloudService,
) {

    // -- session bootstrap ---------------------------------------------------

    /**
     * Dart `_loadInitialSession`: restore the persisted session, seed the
     * cached vehicles / links / profile, then kick silent refreshes when a
     * session is present and the caller asked for them.
     */
    suspend fun loadInitialSession(refreshOnSignedIn: Boolean) {
        service.withSessionWrite {
            if (service.initialized) return@withSessionWrite
            val stored = service.storage.loadSession()
            val cachedVehicles = if (stored.token.isEmpty()) {
                emptyList()
            } else {
                stored.cachedVehicles
            }
            val selectedVehicleKey = service.selectVehicleKey(cachedVehicles, stored.selectedVehicleKey)
            service.updateState { it.copyWith(
                initialized = true,
                sessionGeneration = it.sessionGeneration + 1,
                token = stored.token,
                phone = stored.phone,
                userId = stored.userId,
                userProfile = stored.cachedUserProfile,
                vehicles = cachedVehicles,
                selectedVehicleKey = selectedVehicleKey,
                localVehicleLinks = stored.localVehicleLinks,
            ) }
            service.initialized = true
            if (service.state.selectedVehicle != null) {
                service.runSilentRefresh(
                    { service.applySelectedVehicleToLocalProfile() },
                    failureMessage = "官方缓存车辆同步到本地车库失败",
                )
            }
            if (refreshOnSignedIn && service.state.token.isNotEmpty()) {
                service.runSilentRefresh(
                    { refreshVehicles(silent = true, refreshReplicaDetails = true, force = false, preferredVehicleKey = null) },
                    failureMessage = "官方车辆静默刷新失败",
                )
                service.runSilentRefresh(
                    { refreshUserProfile(silent = true, force = false) },
                    failureMessage = "官方用户资料静默刷新失败",
                )
            }
        }
    }

    // -- user profile --------------------------------------------------------

    suspend fun refreshUserProfile(silent: Boolean, force: Boolean = false) {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        if (token.isEmpty()) return
        val refreshKey = "userProfile"
        service.coalesceRefresh(session, refreshKey, silent, force) {
            refreshUserProfileNow(silent, refreshKey, session)
        }
    }

    private suspend fun refreshUserProfileNow(silent: Boolean, refreshKey: String, session: OfficialCloudSession) {
        try {
            val response = service.apiClient.request(
                "app/getUserProfile",
                method = "POST",
                token = session.token,
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            service.ensureSuccess(response.body, fallback = "获取官方用户资料失败")
            if (!service.isCurrentSession(session)) return
            val profile = OfficialCloudDataParser.userProfile(response.body["data"])
            service.updateSessionState(session) { it.copyWith(userProfile = profile) }
            service.runSilentRefresh(
                { service.persistCurrentUserProfile(session) },
                failureMessage = "官方用户资料缓存保存失败",
            )
            service.log.operation(
                "官方用户资料已刷新",
                detail = if (profile == null) {
                    "empty"
                } else {
                    "nick=${SensitiveValueMasker.compact(profile.displayName)}"
                },
            )
            service.markRefreshSuccess(session, refreshKey)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!service.isCurrentSession(session)) return
            service.handleAuthFailureIfNeeded(e, session)
            if (!silent) throw e
            service.log.operation(
                "官方用户资料刷新失败",
                detail = OfficialCloudRedactor.errorMessage(e),
                level = LogLevel.WARNING,
            )
        }
    }

    // -- vehicles ------------------------------------------------------------

    suspend fun refreshVehicles(
        silent: Boolean,
        refreshReplicaDetails: Boolean = true,
        force: Boolean = false,
        preferredVehicleKey: String? = null,
        refreshDependents: Boolean = true,
    ) {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        if (token.isEmpty()) return
        val refreshKey = "vehicles"
        if (!force && silent && service.shouldUseRecentRefresh(session, refreshKey)) {
            if (refreshDependents) {
                service.refreshVehicleDependents(refreshReplicaDetails)
            }
            return
        }
        service.coalesceRefresh(session, refreshKey, silent, force) {
            refreshVehiclesNow(silent, refreshReplicaDetails, refreshDependents, refreshKey, session, preferredVehicleKey)
        }
    }

    private suspend fun refreshVehiclesNow(
        silent: Boolean,
        refreshReplicaDetails: Boolean,
        refreshDependents: Boolean,
        refreshKey: String,
        session: OfficialCloudSession,
        preferredVehicleKey: String?,
    ) {
        if (!service.isCurrentSession(session)) return
        if (!silent) service.updateSessionState(session) { it.copyWith(loading = true) }
        try {
            val response = service.apiClient.request(
                "app/centralControl/carStatus",
                method = "POST",
                token = session.token,
                body = mapOf("phoneMode" to service.apiClient.config.phoneMode),
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            service.ensureSuccess(response.body, fallback = "获取官方车辆失败")
            if (!service.isCurrentSession(session)) return
            val vehicles = OfficialCloudDataParser.vehicles(response.body["data"])
            service.withSessionWrite {
                if (!service.isCurrentSession(session)) return@withSessionWrite
                val selected = service.selectVehicleKey(vehicles, preferredVehicleKey ?: service.state.selectedVehicleKey)
                if (service.state.selectedVehicle?.key != service.vehicleByKey(vehicles, selected)?.key) {
                    service.lastSuccessfulRefresh.clear()
                    service.rideStatisticsGeneration.incrementAndGet()
                }
                service.updateSessionState(session) { it.withVehicleSelection(vehicles, selected) }
                service.storage.saveSelectedVehicleKey(selected)
                service.storage.saveCarControlInfo(service.state.selectedVehicle)
            }
            if (!service.isCurrentSession(session)) return
            service.applySelectedVehicleToLocalProfile()
            service.log.operation("官方车辆列表已刷新", detail = "count=${vehicles.size}")
            if (refreshDependents) {
                service.refreshVehicleDependents(refreshReplicaDetails)
            }
            service.markRefreshSuccess(session, refreshKey)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!service.isCurrentSession(session)) return
            service.handleAuthFailureIfNeeded(e, session)
            if (service.state.signedIn) {
                val message = OfficialCloudRedactor.errorMessage(e)
                service.updateSessionState(session) { it.copyWith(error = message) }
            }
            throw e
        } finally {
            if (!silent) service.updateSessionState(session) { it.copyWith(loading = false) }
        }
    }

    /** GarageV2 paged vehicle list (`POST app/userCarPage`). */
    suspend fun fetchGaragePage(
        pageIndex: Int,
        frame: String,
        shareUserPhone: String,
    ): OfficialGaragePage {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        if (token.isEmpty()) {
            throw OfficialCloudApiException(OfficialCloudMessages.SIGN_IN_REQUIRED)
        }
        val normalizedPageIndex = if (pageIndex < 1) 1 else pageIndex
        val normalizedFrame = frame.trim()
        val normalizedSharePhone = shareUserPhone.trim()
        val override = service.fetchGaragePageOverride
        if (override != null) {
            return override(normalizedPageIndex, normalizedFrame, normalizedSharePhone)
        }
        try {
            // The official client always requests five rows and sends only the
            // active search field: `frame` or `shareUserPhone`.
            val body = linkedMapOf<String, Any?>(
                "pageSize" to "5",
                "nowPageIndex" to "$normalizedPageIndex",
            )
            if (normalizedFrame.isNotEmpty()) body["frame"] = normalizedFrame
            if (normalizedSharePhone.isNotEmpty()) body["shareUserPhone"] = normalizedSharePhone
            val response = service.apiClient.request(
                "app/userCarPage",
                method = "POST",
                token = session.token,
                body = body,
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            service.ensureSuccess(response.body, fallback = "获取车库车辆失败")
            service.ensureCurrentSession(session)
            return OfficialGaragePage.fromPayload(
                response.body["data"],
                requestedPageIndex = normalizedPageIndex,
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            service.ensureCurrentSession(session)
            service.handleAuthFailureIfNeeded(e, session)
            throw e
        }
    }

    // -- messages ------------------------------------------------------------

    suspend fun refreshMessages(
        silent: Boolean,
        force: Boolean = false,
        pageSize: Int = 20,
        pageIndex: Int = 1,
    ) {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        if (token.isEmpty()) {
            throw OfficialCloudApiException(OfficialCloudMessages.SIGN_IN_REQUIRED)
        }
        val refreshKey = "messages"
        service.coalesceRefresh(session, refreshKey, silent, force) {
            refreshMessagesNow(silent, refreshKey, session, pageSize, pageIndex)
        }
    }

    private suspend fun refreshMessagesNow(
        silent: Boolean,
        refreshKey: String,
        session: OfficialCloudSession,
        pageSize: Int,
        pageIndex: Int,
    ) {
        val requestState = service.state
        if (!session.matches(requestState) || service.disposed) return
        if (!silent) {
            service.updateSessionState(session) { it.copyWith(messagesLoading = true, messagesError = null) }
        }
        try {
            val userId = requestState.userId.trim()
            val vehicleBody = linkedMapOf<String, Any?>("pageSize" to pageSize, "nowPageIndex" to pageIndex)
            if (userId.isNotEmpty()) vehicleBody["uid"] = userId
            val systemBody = linkedMapOf<String, Any?>("pageSize" to pageSize, "nowPageIndex" to pageIndex)
            val responses = coroutineScope {
                val vehicleDeferred = async {
                    service.apiClient.request(
                        "app/msg/pageOfCarMsg",
                        method = "POST",
                        token = session.token,
                        body = vehicleBody,
                        retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
                    )
                }
                val systemDeferred = async {
                    service.apiClient.request(
                        "app/msg/pageOfSysMsg",
                        method = "POST",
                        token = session.token,
                        body = systemBody,
                        retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
                    )
                }
                vehicleDeferred.await() to systemDeferred.await()
            }
            if (!service.isCurrentSession(session)) return
            val vehicleResponse = responses.first
            val systemResponse = responses.second
            service.ensureSuccess(vehicleResponse.body, fallback = "获取车辆消息失败")
            service.ensureSuccess(systemResponse.body, fallback = "获取系统消息失败")
            val vehicleMessages = OfficialCloudDataParser.vehicleMessages(vehicleResponse.body["data"])
            val systemMessages = OfficialCloudDataParser.systemMessages(systemResponse.body["data"])
            service.updateSessionState(session) { it.copyWith(
                vehicleMessages = vehicleMessages,
                systemMessages = systemMessages,
                messagesLoading = false,
                messagesError = null,
            ) }
            service.log.operation(
                "官方消息已刷新",
                detail = "vehicle=${vehicleMessages.size} system=${systemMessages.size}",
            )
            service.markRefreshSuccess(session, refreshKey)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!service.isCurrentSession(session)) return
            service.handleAuthFailureIfNeeded(e, session)
            if (service.state.signedIn) {
                val message = OfficialCloudRedactor.errorMessage(e)
                service.updateSessionState(session) { it.copyWith(messagesLoading = false, messagesError = message) }
            }
            throw e
        } finally {
            if (!silent && service.isCurrentSession(session) && service.state.messagesLoading) {
                service.updateSessionState(session) { it.copyWith(messagesLoading = false) }
            }
        }
    }

    // -- battery / BMS -------------------------------------------------------

    suspend fun refreshBatteryInfo(silent: Boolean, force: Boolean = false) {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        if (token.isEmpty()) return
        val vehicleKey = requestState.selectedVehicle?.key
        val refreshKey = "batteryInfo:${vehicleKey.orEmpty()}"
        service.coalesceRefresh(session, refreshKey, silent, force) {
            refreshBatteryInfoNow(silent, refreshKey, session, vehicleKey)
        }
    }

    private suspend fun refreshBatteryInfoNow(silent: Boolean, refreshKey: String, session: OfficialCloudSession, vehicleKey: String?) {
        if (!silent) {
            service.updateVehicleState(session, vehicleKey) { it.copyWith(batteryInfoLoading = true, batteryInfoError = null) }
        }
        try {
            val response = service.apiClient.request(
                "app/mine/batteryInfo",
                method = "POST",
                token = session.token,
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            service.ensureSuccess(response.body, fallback = "获取官方电池信息失败")
            if (!service.isCurrentVehicleSession(session, vehicleKey)) return
            val info = OfficialCloudDataParser.batteryInfo(response.body["data"])
            service.updateVehicleState(session, vehicleKey) { it.copyWith(
                batteryInfo = if (info.hasData) info else null,
                batteryInfoLoading = false,
                batteryInfoError = null,
            ) }
            service.log.operation(
                "官方电池信息已刷新",
                detail = if (info.hasData) "hasData=true" else "hasData=false",
            )
            service.markRefreshSuccess(session, refreshKey)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!service.isCurrentVehicleSession(session, vehicleKey)) return
            service.handleAuthFailureIfNeeded(e, session)
            if (service.state.signedIn) {
                val message = OfficialCloudRedactor.errorMessage(e)
                service.updateVehicleState(session, vehicleKey) { it.copyWith(batteryInfoLoading = false, batteryInfoError = message) }
            }
            if (!silent) throw e
            service.log.operation(
                "官方电池信息刷新失败",
                detail = OfficialCloudRedactor.errorMessage(e),
                level = LogLevel.WARNING,
            )
        } finally {
            if (!silent && service.isCurrentVehicleSession(session, vehicleKey) && service.state.batteryInfoLoading) {
                service.updateVehicleState(session, vehicleKey) { it.copyWith(batteryInfoLoading = false) }
            }
        }
    }

    suspend fun refreshBmsInfo(silent: Boolean, force: Boolean = false) {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        val vehicle = requestState.selectedVehicle
        val uid = requestState.userId.trim()
        val imei = if (vehicle != null && vehicle.imei.trim().isNotEmpty()) {
            vehicle.imei.trim()
        } else {
            vehicle?.commandImei?.trim() ?: ""
        }
        if (token.isEmpty() || uid.isEmpty() || imei.isEmpty()) return
        val refreshKey = "bmsInfo:${vehicle?.key ?: imei}"
        service.coalesceRefresh(session, refreshKey, silent, force) {
            refreshBmsInfoNow(silent, refreshKey, session, uid, imei, vehicle?.key)
        }
    }

    private suspend fun refreshBmsInfoNow(
        silent: Boolean,
        refreshKey: String,
        session: OfficialCloudSession,
        uid: String,
        imei: String,
        vehicleKey: String?,
    ) {
        if (!silent) {
            service.updateVehicleState(session, vehicleKey) { it.copyWith(bmsInfoLoading = true, bmsInfoError = null) }
        }
        try {
            val response = service.apiClient.request(
                "app/mine/bmsBatteryInfo",
                method = "POST",
                token = session.token,
                body = mapOf("uid" to uid, "imei" to imei),
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            if (!service.isCurrentVehicleSession(session, vehicleKey)) return
            // Some vehicles without an intelligent battery answer code=100 —
            // treat that as "no BMS" rather than an error.
            val code = OfficialCloudResponseCode.normalizeCode(response.body["code"])
            if (code == "100") {
                service.updateVehicleState(session, vehicleKey) { it.copyWith(
                    bmsInfo = null,
                    bmsInfoLoading = false,
                    bmsInfoError = null,
                ) }
                service.log.operation("官方 BMS 信息不可用", detail = "code=100")
                service.markRefreshSuccess(session, refreshKey)
                return
            }
            service.ensureSuccess(response.body, fallback = "获取官方 BMS 信息失败")
            if (!service.isCurrentVehicleSession(session, vehicleKey)) return
            val info = OfficialCloudDataParser.bmsInfo(response.body["data"])
            service.updateVehicleState(session, vehicleKey) { it.copyWith(
                bmsInfo = if (info.hasData) info else null,
                bmsInfoLoading = false,
                bmsInfoError = null,
            ) }
            service.log.operation(
                "官方 BMS 信息已刷新",
                detail = "hasData=${info.hasData} details=${info.details.size} " +
                    "soc=${if (info.soc.isEmpty()) "none" else "present"}",
            )
            service.markRefreshSuccess(session, refreshKey)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!service.isCurrentVehicleSession(session, vehicleKey)) return
            service.handleAuthFailureIfNeeded(e, session)
            if (service.state.signedIn) {
                val message = OfficialCloudRedactor.errorMessage(e)
                service.updateVehicleState(session, vehicleKey) { it.copyWith(bmsInfoLoading = false, bmsInfoError = message) }
            }
            if (!silent) throw e
            service.log.operation(
                "官方 BMS 信息刷新失败",
                detail = OfficialCloudRedactor.errorMessage(e),
                level = LogLevel.WARNING,
            )
        } finally {
            if (!silent && service.isCurrentVehicleSession(session, vehicleKey) && service.state.bmsInfoLoading) {
                service.updateVehicleState(session, vehicleKey) { it.copyWith(bmsInfoLoading = false) }
            }
        }
    }

    /** Official battery-type catalog with a non-ext fallback. */
    suspend fun fetchBatteryTypes(): List<OfficialBatteryType> {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        if (token.isEmpty()) {
            throw OfficialCloudApiException(OfficialCloudMessages.SIGN_IN_REQUIRED)
        }
        try {
            val response = service.apiClient.request(
                "app/centralControl/batteryType/ext",
                method = "POST",
                token = session.token,
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            service.ensureSuccess(response.body, fallback = "获取电池类型失败")
            service.ensureCurrentSession(session)
            val types = OfficialCloudDataParser.batteryTypes(response.body["data"])
            if (types.isNotEmpty()) return types
            // Fallback to the non-ext endpoint when the ext one returns nothing.
            val fallback = service.apiClient.request(
                "app/centralControl/batteryType",
                method = "POST",
                token = session.token,
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            service.ensureSuccess(fallback.body, fallback = "获取电池类型失败")
            service.ensureCurrentSession(session)
            return OfficialCloudDataParser.batteryTypes(fallback.body["data"])
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            service.handleAuthFailureIfNeeded(e, session)
            throw e
        }
    }

    suspend fun fetchBatterySpecsByType(typeId: String): List<OfficialBatterySpec> {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        val normalized = typeId.trim()
        if (token.isEmpty()) {
            throw OfficialCloudApiException(OfficialCloudMessages.SIGN_IN_REQUIRED)
        }
        if (normalized.isEmpty() || normalized == "0") return emptyList()
        try {
            val response = service.apiClient.request(
                "app/centralControl/batterySpecByType",
                method = "POST",
                token = session.token,
                body = mapOf("typeId" to normalized),
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            service.ensureSuccess(response.body, fallback = "获取电池规格失败")
            service.ensureCurrentSession(session)
            return OfficialCloudDataParser.batterySpecs(response.body["data"])
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            service.handleAuthFailureIfNeeded(e, session)
            throw e
        }
    }

    // -- parking location / fence --------------------------------------------

    suspend fun refreshVehicleLocation(silent: Boolean, force: Boolean = false) {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        val vehicle = requestState.selectedVehicle
        if (token.isEmpty() || vehicle == null || vehicle.carId.isEmpty()) {
            return
        }
        val refreshKey = "vehicleLocation:${vehicle.key}"
        service.coalesceRefresh(session, refreshKey, silent, force) {
            refreshVehicleLocationNow(silent, refreshKey, vehicle, session)
        }
    }

    private suspend fun refreshVehicleLocationNow(
        silent: Boolean,
        refreshKey: String,
        vehicle: OfficialVehicle,
        session: OfficialCloudSession,
    ) {
        if (!silent) {
            service.updateVehicleState(session, vehicle.key) { it.copyWith(
                vehicleLocationLoading = true,
                vehicleLocationError = null,
            ) }
        }
        try {
            val response = service.apiClient.request(
                "app/car/extend/getByCarId",
                method = "POST",
                token = session.token,
                body = mapOf("carId" to vehicle.carId),
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            service.ensureSuccess(response.body, fallback = "获取官方停车位置失败")
            if (!service.isCurrentVehicleSession(session, vehicle.key)) return
            val location = OfficialCloudDataParser.vehicleLocation(response.body["data"])
            service.updateVehicleState(session, vehicle.key) { it.copyWith(
                vehicleLocation = if (location.hasData) location else null,
                vehicleLocationLoading = false,
                vehicleLocationError = null,
            ) }
            service.log.operation(
                "官方停车位置已刷新",
                detail = if (location.hasData) "hasData=true" else "hasData=false",
            )
            service.markRefreshSuccess(session, refreshKey)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!service.isCurrentVehicleSession(session, vehicle.key)) return
            service.handleAuthFailureIfNeeded(e, session)
            if (service.state.signedIn) {
                service.updateVehicleState(session, vehicle.key) { it.copyWith(
                    vehicleLocationLoading = false,
                    vehicleLocationError = OfficialCloudRedactor.errorMessage(e),
                ) }
            }
            if (!silent) throw e
            service.log.operation(
                "官方停车位置刷新失败",
                detail = OfficialCloudRedactor.errorMessage(e),
                level = LogLevel.WARNING,
            )
        } finally {
            if (!silent && service.isCurrentVehicleSession(session, vehicle.key) && service.state.vehicleLocationLoading) {
                service.updateVehicleState(session, vehicle.key) { it.copyWith(vehicleLocationLoading = false) }
            }
        }
    }

    suspend fun refreshFenceData(silent: Boolean, force: Boolean = false) {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        val vehicle = requestState.selectedVehicle
        if (token.isEmpty() || vehicle == null || vehicle.carId.isEmpty()) {
            return
        }
        val refreshKey = "fence:${vehicle.key}"
        service.coalesceRefresh(session, refreshKey, silent, force) {
            refreshFenceDataNow(silent, refreshKey, vehicle, session)
        }
    }

    private suspend fun refreshFenceDataNow(
        silent: Boolean,
        refreshKey: String,
        vehicle: OfficialVehicle,
        session: OfficialCloudSession,
    ) {
        if (!silent) {
            service.updateVehicleState(session, vehicle.key) { it.copyWith(fenceLoading = true, fenceError = null) }
        }
        try {
            val response = service.apiClient.request(
                "app/device/getFenceData",
                method = "POST",
                token = session.token,
                body = mapOf("carId" to vehicle.carId),
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            service.ensureSuccess(response.body, fallback = "获取官方电子围栏失败")
            if (!service.isCurrentVehicleSession(session, vehicle.key)) return
            val fence = OfficialCloudDataParser.fenceData(response.body["data"])
            service.updateVehicleState(session, vehicle.key) { it.copyWith(
                fenceData = if (fence.hasData) fence else null,
                fenceLoading = false,
                fenceError = null,
            ) }
            service.log.operation(
                "官方电子围栏已刷新",
                detail = if (fence.hasData) "hasData=true" else "hasData=false",
            )
            service.markRefreshSuccess(session, refreshKey)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!service.isCurrentVehicleSession(session, vehicle.key)) return
            service.handleAuthFailureIfNeeded(e, session)
            if (service.state.signedIn) {
                service.updateVehicleState(session, vehicle.key) { it.copyWith(
                    fenceLoading = false,
                    fenceError = OfficialCloudRedactor.errorMessage(e),
                ) }
            }
            if (!silent) throw e
            service.log.operation(
                "官方电子围栏刷新失败",
                detail = OfficialCloudRedactor.errorMessage(e),
                level = LogLevel.WARNING,
            )
        } finally {
            if (!silent && service.isCurrentVehicleSession(session, vehicle.key) && service.state.fenceLoading) {
                service.updateVehicleState(session, vehicle.key) { it.copyWith(fenceLoading = false) }
            }
        }
    }

    // -- ride statistics -----------------------------------------------------

    suspend fun refreshRideStatistics(
        period: OfficialRidePeriod,
        silent: Boolean = false,
        force: Boolean = false,
    ) {
        val snapshot = service.state
        val session = snapshot.sessionIdentity
        val token = session.token
        val vehicle = snapshot.selectedVehicle
        if (token.isEmpty() || vehicle == null) return
        val key = period.requestKey(service.clock().atZone(ZoneId.systemDefault()).toInstant())
        val refreshKey = "rideStatistics:${vehicle.key}:${period.wireName}:$key"
        if (!force && silent &&
            service.state.ridePeriod == period &&
            service.state.rideStatistics != null &&
            service.shouldUseRecentRefresh(session, refreshKey)
        ) {
            return
        }
        // Reserve foreground selection before waiting for older work. Silent
        // callers reserve only when they actually run, and never replace a
        // foreground request that is still loading.
        var generation = if (silent) null else {
            service.beginRideStatisticsRequest(session, vehicle.key, period, silent = false) ?: return
        }
        try {
            // The TTL check above also verifies that the displayed period has
            // cached data. A timestamp alone cannot satisfy a period switch.
            service.coalesceRefresh(session, refreshKey, silent, force = true) {
                val requestGeneration = generation
                    ?: service.beginRideStatisticsRequest(session, vehicle.key, period, silent = true)
                    ?: return@coalesceRefresh
                generation = requestGeneration
                if (!service.isCurrentRideStatisticsRequest(requestGeneration, session, vehicle.key, period)) {
                    return@coalesceRefresh
                }
                if (vehicle.frame.isBlank()) {
                    service.updateRideStatisticsState(requestGeneration, session, vehicle.key, period) {
                        it.copyWith(
                            rideStatistics = null,
                            rideStatisticsError = "当前车辆缺少车架号，无法读取骑行统计",
                        )
                    }
                    return@coalesceRefresh
                }
                val override = service.refreshRideStatisticsOverride
                if (override != null) override(period) else {
                    refreshRideStatisticsNow(silent, refreshKey, requestGeneration, vehicle, period, key, session)
                }
            }
        } finally {
            generation?.let { currentGeneration ->
                service.updateRideStatisticsState(currentGeneration, session, vehicle.key, period) {
                    it.copyWith(rideStatisticsLoading = false)
                }
            }
        }
    }

    private suspend fun refreshRideStatisticsNow(
        silent: Boolean,
        refreshKey: String,
        generation: Int,
        vehicle: OfficialVehicle,
        period: OfficialRidePeriod,
        key: String,
        session: OfficialCloudSession,
    ) {
        try {
            val response = service.apiClient.request(
                "app/appRiding/getRidingDetail",
                method = "POST",
                token = session.token,
                body = mapOf(
                    "model" to period.wireName,
                    "key" to key,
                    "carFrame" to vehicle.frame.trim(),
                ),
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            service.ensureSuccess(response.body, fallback = "获取官方骑行统计失败")
            if (!service.isCurrentRideStatisticsRequest(generation, session, vehicle.key, period)) {
                return
            }
            val rawData = response.body["data"]
            if (rawData !is Map<*, *>) {
                throw OfficialCloudApiException("官方骑行统计数据格式异常")
            }
            val data = rawData.entries.associate { it.key.toString() to it.value }
            val statistics = OfficialRideStatistics.fromJson(data)
            service.updateRideStatisticsState(generation, session, vehicle.key, period) { it.copyWith(
                rideStatistics = statistics,
                ridePeriod = period,
                rideStatisticsLoading = false,
                rideStatisticsError = null,
            ) }
            service.log.operation("官方骑行统计已刷新", detail = "period=${period.wireName}")
            service.markRefreshSuccess(session, refreshKey)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!service.isCurrentVehicleSession(session, vehicle.key)) return
            service.handleAuthFailureIfNeeded(e, session)
            service.updateRideStatisticsState(generation, session, vehicle.key, period) { it.copyWith(
                rideStatisticsLoading = false,
                rideStatisticsError = OfficialCloudRedactor.errorMessage(e),
            ) }
            if (!silent) throw e
            service.log.operation(
                "官方骑行统计刷新失败",
                detail = OfficialCloudRedactor.errorMessage(e),
                level = LogLevel.WARNING,
            )
        }
    }

    // -- travel history ------------------------------------------------------

    suspend fun refreshTravelHistory(
        month: String? = null,
        silent: Boolean = false,
        force: Boolean = false,
    ) {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        val vehicle = requestState.selectedVehicle
        if (token.isEmpty() || vehicle == null) return
        val userId = requestState.userId.trim()
        if (userId.isEmpty()) {
            service.updateVehicleState(session, vehicle.key) { it.copyWith(
                travelDays = emptyList(),
                travelMonth = month ?: service.currentMonth(),
                travelError = "官方登录未返回 uid，无法读取历史轨迹",
            ) }
            return
        }
        val queryMonth = month ?: if (service.state.travelMonth.isEmpty()) {
            service.currentMonth()
        } else {
            service.state.travelMonth
        }
        val override = service.refreshTravelHistoryOverride
        if (override != null) {
            override(queryMonth)
            return
        }
        val refreshKey = "travel:${vehicle.key}:$queryMonth"
        if (!force && silent &&
            service.state.travelMonth == queryMonth &&
            service.shouldUseRecentRefresh(session, refreshKey)
        ) {
            return
        }
        val monthChanged = service.state.travelMonth != queryMonth
        service.updateVehicleState(session, vehicle.key) { current ->
            if (current.travelMonth == queryMonth) current else current.copyWith(
                travelMonth = queryMonth,
                travelDays = emptyList(),
                travelLoading = false,
                travelError = null,
            )
        }
        service.coalesceRefresh(session, refreshKey, silent, force || monthChanged) {
            refreshTravelHistoryNow(silent, refreshKey, vehicle, queryMonth, userId, session)
        }
    }

    private suspend fun refreshTravelHistoryNow(
        silent: Boolean,
        refreshKey: String,
        vehicle: OfficialVehicle,
        queryMonth: String,
        userId: String,
        session: OfficialCloudSession,
    ) {
        fun isCurrentRequest() = service.isCurrentVehicleSession(session, vehicle.key) &&
            service.state.travelMonth == queryMonth
        fun updateTravelState(transform: (OfficialCloudState) -> OfficialCloudState) {
            service.updateVehicleState(session, vehicle.key) { current ->
                if (current.travelMonth == queryMonth) transform(current) else current
            }
        }
        if (!isCurrentRequest()) return
        if (!silent) {
            updateTravelState { it.copyWith(
                travelLoading = true,
                travelError = null,
                travelMonth = queryMonth,
            ) }
        }
        try {
            val response = service.apiClient.request(
                "app/centralControl/deviceTravel",
                method = "POST",
                token = session.token,
                body = mapOf("queryMonth" to queryMonth, "frame" to vehicle.frame, "uid" to userId),
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            service.ensureSuccess(response.body, fallback = "获取官方历史轨迹失败")
            if (!isCurrentRequest()) return
            val days = OfficialCloudDataParser.travelDays(response.body["data"])
            updateTravelState { it.copyWith(
                travelDays = days,
                travelMonth = queryMonth,
                travelLoading = false,
                travelError = null,
            ) }
            service.log.operation("官方历史轨迹已刷新", detail = "days=${days.size}")
            service.markRefreshSuccess(session, refreshKey)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!isCurrentRequest()) return
            service.handleAuthFailureIfNeeded(e, session)
            if (service.state.signedIn) {
                updateTravelState { it.copyWith(
                    travelLoading = false,
                    travelError = OfficialCloudRedactor.errorMessage(e),
                ) }
            }
            if (!silent) throw e
            service.log.operation(
                "官方历史轨迹刷新失败",
                detail = OfficialCloudRedactor.errorMessage(e),
                level = LogLevel.WARNING,
            )
        } finally {
            if (!silent && isCurrentRequest() && service.state.travelLoading) {
                updateTravelState { it.copyWith(travelLoading = false) }
            }
        }
    }

    suspend fun refreshTravelDetail(travelId: String) {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        val vehicleKey = requestState.selectedVehicle?.key
        if (token.isEmpty() || travelId.trim().isEmpty()) return
        service.updateVehicleState(session, vehicleKey) { it.copyWith(travelDetailLoading = true, travelDetailError = null) }
        try {
            val response = service.apiClient.request(
                "app/centralControl/deviceTravelDetail",
                method = "POST",
                token = session.token,
                body = mapOf("deviceTravelId" to travelId),
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            service.ensureSuccess(response.body, fallback = "获取官方轨迹详情失败")
            if (!service.isCurrentVehicleSession(session, vehicleKey)) return
            val points = OfficialCloudDataParser.travelPoints(response.body["data"])
            service.updateVehicleState(session, vehicleKey) { it.copyWith(
                travelDetails = it.travelDetails + (travelId to points),
                travelDetailLoading = false,
                travelDetailError = null,
            ) }
            service.log.operation("官方轨迹详情已刷新", detail = "points=${points.size}")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!service.isCurrentVehicleSession(session, vehicleKey)) return
            service.handleAuthFailureIfNeeded(e, session)
            if (service.state.signedIn) {
                service.updateVehicleState(session, vehicleKey) { it.copyWith(
                    travelDetailLoading = false,
                    travelDetailError = OfficialCloudRedactor.errorMessage(e),
                ) }
            }
            throw e
        } finally {
            if (service.isCurrentVehicleSession(session, vehicleKey) && service.state.travelDetailLoading) {
                service.updateVehicleState(session, vehicleKey) { it.copyWith(travelDetailLoading = false) }
            }
        }
    }

    // -- smart service status ------------------------------------------------

    suspend fun refreshSelectedSmartServiceStatus(silent: Boolean = true, force: Boolean = false) {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        val vehicle = requestState.selectedVehicle
        if (token.isEmpty() || vehicle == null) return
        val vehicleKey = vehicle.key
        val refreshKey = "smartService:$vehicleKey"
        if (vehicle.iccId.isEmpty()) {
            service.smartServiceStatuses.remove(session.resourceKey(vehicleKey))
            service.smartServiceStatusLoadedKeys.add(session.resourceKey(vehicleKey))
            service.markRefreshSuccess(session, refreshKey)
            return
        }
        service.coalesceRefresh(session, refreshKey, silent, force) {
            try {
                val status: OfficialSmartServiceStatus
                val override = service.refreshSmartServiceStatusOverride
                if (override != null) {
                    status = override(vehicle)
                } else {
                    val response = service.apiClient.request(
                        "app/sim/queryDetail",
                        method = "POST",
                        token = session.token,
                        body = mapOf("simNo" to vehicle.simNo, "iccId" to vehicle.iccId),
                        retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
                    )
                    service.ensureSuccess(response.body, fallback = "获取智能服务状态失败")
                    status = OfficialSmartServiceStatus.fromPayload(response.body["data"])
                }
                if (!service.isCurrentSession(session) || service.state.selectedVehicle?.key != vehicleKey) {
                    return@coalesceRefresh
                }
                service.smartServiceStatuses[session.resourceKey(vehicleKey)] = status
                service.smartServiceStatusLoadedKeys.add(session.resourceKey(vehicleKey))
                service.markRefreshSuccess(session, refreshKey)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (!service.isCurrentSession(session)) return@coalesceRefresh
                service.handleAuthFailureIfNeeded(e, session)
                throw e
            }
        }
    }

    /**
     * Resolve whether remote control is available for the selected vehicle,
     * pre-loading the smart-service status when it has not been fetched yet.
     */
    suspend fun resolveSelectedRemoteControlServiceDecision(): OfficialSmartServiceControlDecision {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val vehicle = requestState.selectedVehicle
        if (!requestState.signedIn || vehicle == null) {
            return OfficialSmartServiceControlDecision()
        }
        // Official KKS/YJ control branches do not consult querySimDetail.
        if (vehicle.modelType == 1 || vehicle.modelType == 2) {
            return OfficialSmartServiceControlDecision()
        }
        if (session.resourceKey(vehicle.key) !in service.smartServiceStatusLoadedKeys) {
            try {
                refreshSelectedSmartServiceStatus(silent = true)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                service.log.operation(
                    "官方智能服务状态预检失败",
                    detail = OfficialCloudRedactor.errorMessage(e),
                    level = LogLevel.WARNING,
                )
            }
        }
        if (!service.isCurrentVehicleSession(session, vehicle.key)) {
            throw OfficialCloudApiException("官方车辆或登录状态已变化，请重试")
        }
        return service.selectedRemoteControlServiceDecision
    }
}
