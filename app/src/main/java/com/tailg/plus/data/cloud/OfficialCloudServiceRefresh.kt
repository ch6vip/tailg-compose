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
        try {
            val stored = service.storage.loadSession()
            val cachedVehicles = if (stored.token.isEmpty()) {
                emptyList()
            } else {
                stored.cachedVehicles
            }
            val selectedVehicleKey = service.selectVehicleKey(cachedVehicles, stored.selectedVehicleKey)
            service.state = service.state.copyWith(
                initialized = true,
                token = stored.token,
                phone = stored.phone,
                userId = stored.userId,
                userProfile = stored.cachedUserProfile,
                vehicles = cachedVehicles,
                selectedVehicleKey = selectedVehicleKey,
                localVehicleLinks = stored.localVehicleLinks,
            )
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
        } finally {
            service.initializing = null
        }
    }

    // -- user profile --------------------------------------------------------

    suspend fun refreshUserProfile(silent: Boolean, force: Boolean) {
        val token = service.state.token
        if (token.isEmpty()) return
        val refreshKey = "userProfile"
        service.coalesceRefresh(refreshKey, silent, force) {
            refreshUserProfileNow(silent, refreshKey, token)
        }
    }

    private suspend fun refreshUserProfileNow(silent: Boolean, refreshKey: String, token: String) {
        try {
            val response = service.apiClient.request(
                "app/getUserProfile",
                method = "POST",
                token = token,
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            service.ensureSuccess(response.body, fallback = "获取官方用户资料失败")
            if (!service.isCurrentSession(token)) return
            val profile = OfficialCloudDataParser.userProfile(response.body["data"])
            service.state = service.state.copyWith(userProfile = profile)
            service.runSilentRefresh(
                { service.storage.saveUserProfile(profile) },
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
            service.markRefreshSuccess(refreshKey)
        } catch (e: Exception) {
            if (!service.isCurrentSession(token)) return
            service.handleAuthFailureIfNeeded(e)
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
        refreshReplicaDetails: Boolean,
        force: Boolean,
        preferredVehicleKey: String?,
    ) {
        val token = service.state.token
        if (token.isEmpty()) return
        val refreshKey = "vehicles"
        if (!force && silent && service.shouldUseRecentRefresh(refreshKey)) {
            service.refreshVehicleDependents(refreshReplicaDetails)
            return
        }
        service.coalesceRefresh(refreshKey, silent, force) {
            refreshVehiclesNow(silent, refreshReplicaDetails, refreshKey, token, preferredVehicleKey)
        }
    }

    private suspend fun refreshVehiclesNow(
        silent: Boolean,
        refreshReplicaDetails: Boolean,
        refreshKey: String,
        token: String,
        preferredVehicleKey: String?,
    ) {
        if (!silent) service.setLoading(true)
        try {
            val response = service.apiClient.request(
                "app/centralControl/carStatus",
                method = "POST",
                token = token,
                body = mapOf("phoneMode" to service.apiClient.config.phoneMode),
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            service.ensureSuccess(response.body, fallback = "获取官方车辆失败")
            if (!service.isCurrentSession(token)) return
            val vehicles = OfficialCloudDataParser.vehicles(response.body["data"])
            var selected = preferredVehicleKey ?: service.state.selectedVehicleKey
            if (vehicles.isEmpty()) {
                selected = null
            } else if (selected == null || vehicles.none { it.key == selected }) {
                selected = vehicles.first().key
            }
            coroutineScope {
                val saves = listOf(
                    async { service.storage.saveSelectedVehicleKey(selected) },
                    async { service.storage.saveCarControlInfo(service.vehicleByKey(vehicles, selected)) },
                )
                saves.forEach { it.await() }
            }
            service.state = service.state.copyWith(
                vehicles = vehicles,
                selectedVehicleKey = selected,
                error = null,
            )
            service.applySelectedVehicleToLocalProfile()
            service.log.operation("官方车辆列表已刷新", detail = "count=${vehicles.size}")
            service.refreshVehicleDependents(refreshReplicaDetails)
            service.markRefreshSuccess(refreshKey)
        } catch (e: Exception) {
            if (!service.isCurrentSession(token)) return
            service.handleAuthFailureIfNeeded(e)
            if (service.state.signedIn) {
                val message = OfficialCloudRedactor.errorMessage(e)
                service.state = service.state.copyWith(error = message)
            }
            throw e
        } finally {
            if (!silent && service.isCurrentSession(token)) service.setLoading(false)
        }
    }

    /** GarageV2 paged vehicle list (`POST app/userCarPage`). */
    suspend fun fetchGaragePage(
        pageIndex: Int,
        frame: String,
        shareUserPhone: String,
    ): OfficialGaragePage {
        val token = service.state.token
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
                token = token,
                body = body,
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            service.ensureSuccess(response.body, fallback = "获取车库车辆失败")
            service.ensureCurrentSession(token)
            return OfficialGaragePage.fromPayload(
                response.body["data"],
                requestedPageIndex = normalizedPageIndex,
            )
        } catch (e: Exception) {
            service.ensureCurrentSession(token)
            service.handleAuthFailureIfNeeded(e)
            throw e
        }
    }

    // -- messages ------------------------------------------------------------

    suspend fun refreshMessages(
        silent: Boolean,
        force: Boolean,
        pageSize: Int,
        pageIndex: Int,
    ) {
        val token = service.state.token
        if (token.isEmpty()) {
            throw OfficialCloudApiException(OfficialCloudMessages.SIGN_IN_REQUIRED)
        }
        val refreshKey = "messages"
        service.coalesceRefresh(refreshKey, silent, force) {
            refreshMessagesNow(silent, refreshKey, token, pageSize, pageIndex)
        }
    }

    private suspend fun refreshMessagesNow(
        silent: Boolean,
        refreshKey: String,
        token: String,
        pageSize: Int,
        pageIndex: Int,
    ) {
        if (!silent) {
            service.state = service.state.copyWith(messagesLoading = true, messagesError = null)
        }
        try {
            val userId = service.state.userId.trim()
            val vehicleBody = linkedMapOf<String, Any?>("pageSize" to pageSize, "nowPageIndex" to pageIndex)
            if (userId.isNotEmpty()) vehicleBody["uid"] = userId
            val systemBody = linkedMapOf<String, Any?>("pageSize" to pageSize, "nowPageIndex" to pageIndex)
            val responses = coroutineScope {
                val vehicleDeferred = async {
                    service.apiClient.request(
                        "app/msg/pageOfCarMsg",
                        method = "POST",
                        token = token,
                        body = vehicleBody,
                        retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
                    )
                }
                val systemDeferred = async {
                    service.apiClient.request(
                        "app/msg/pageOfSysMsg",
                        method = "POST",
                        token = token,
                        body = systemBody,
                        retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
                    )
                }
                vehicleDeferred.await() to systemDeferred.await()
            }
            if (!service.isCurrentSession(token)) return
            val vehicleResponse = responses.first
            val systemResponse = responses.second
            service.ensureSuccess(vehicleResponse.body, fallback = "获取车辆消息失败")
            service.ensureSuccess(systemResponse.body, fallback = "获取系统消息失败")
            val vehicleMessages = OfficialCloudDataParser.vehicleMessages(vehicleResponse.body["data"])
            val systemMessages = OfficialCloudDataParser.systemMessages(systemResponse.body["data"])
            service.state = service.state.copyWith(
                vehicleMessages = vehicleMessages,
                systemMessages = systemMessages,
                messagesLoading = false,
                messagesError = null,
            )
            service.log.operation(
                "官方消息已刷新",
                detail = "vehicle=${vehicleMessages.size} system=${systemMessages.size}",
            )
            service.markRefreshSuccess(refreshKey)
        } catch (e: Exception) {
            if (!service.isCurrentSession(token)) return
            service.handleAuthFailureIfNeeded(e)
            if (service.state.signedIn) {
                val message = OfficialCloudRedactor.errorMessage(e)
                service.state = service.state.copyWith(messagesLoading = false, messagesError = message)
            }
            throw e
        } finally {
            if (!silent && service.isCurrentSession(token) && service.state.messagesLoading) {
                service.state = service.state.copyWith(messagesLoading = false)
            }
        }
    }

    // -- battery / BMS -------------------------------------------------------

    suspend fun refreshBatteryInfo(silent: Boolean, force: Boolean) {
        val token = service.state.token
        if (token.isEmpty()) return
        val refreshKey = "batteryInfo"
        service.coalesceRefresh(refreshKey, silent, force) {
            refreshBatteryInfoNow(silent, refreshKey, token)
        }
    }

    private suspend fun refreshBatteryInfoNow(silent: Boolean, refreshKey: String, token: String) {
        if (!silent) {
            service.state = service.state.copyWith(batteryInfoLoading = true, batteryInfoError = null)
        }
        try {
            val response = service.apiClient.request(
                "app/mine/batteryInfo",
                method = "POST",
                token = token,
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            service.ensureSuccess(response.body, fallback = "获取官方电池信息失败")
            if (!service.isCurrentSession(token)) return
            val info = OfficialCloudDataParser.batteryInfo(response.body["data"])
            service.state = service.state.copyWith(
                batteryInfo = if (info.hasData) info else null,
                batteryInfoLoading = false,
                batteryInfoError = null,
            )
            service.log.operation(
                "官方电池信息已刷新",
                detail = if (info.hasData) "hasData=true" else "hasData=false",
            )
            service.markRefreshSuccess(refreshKey)
        } catch (e: Exception) {
            if (!service.isCurrentSession(token)) return
            service.handleAuthFailureIfNeeded(e)
            if (service.state.signedIn) {
                val message = OfficialCloudRedactor.errorMessage(e)
                service.state = service.state.copyWith(batteryInfoLoading = false, batteryInfoError = message)
            }
            if (!silent) throw e
            service.log.operation(
                "官方电池信息刷新失败",
                detail = OfficialCloudRedactor.errorMessage(e),
                level = LogLevel.WARNING,
            )
        } finally {
            if (!silent && service.isCurrentSession(token) && service.state.batteryInfoLoading) {
                service.state = service.state.copyWith(batteryInfoLoading = false)
            }
        }
    }

    suspend fun refreshBmsInfo(silent: Boolean, force: Boolean) {
        val token = service.state.token
        val vehicle = service.state.selectedVehicle
        val uid = service.state.userId.trim()
        val imei = if (vehicle != null && vehicle.imei.trim().isNotEmpty()) {
            vehicle.imei.trim()
        } else {
            vehicle?.commandImei?.trim() ?: ""
        }
        if (token.isEmpty() || uid.isEmpty() || imei.isEmpty()) return
        val refreshKey = "bmsInfo:${vehicle?.key ?: imei}"
        service.coalesceRefresh(refreshKey, silent, force) {
            refreshBmsInfoNow(silent, refreshKey, token, uid, imei)
        }
    }

    private suspend fun refreshBmsInfoNow(
        silent: Boolean,
        refreshKey: String,
        token: String,
        uid: String,
        imei: String,
    ) {
        if (!silent) {
            service.state = service.state.copyWith(bmsInfoLoading = true, bmsInfoError = null)
        }
        try {
            val response = service.apiClient.request(
                "app/mine/bmsBatteryInfo",
                method = "POST",
                token = token,
                body = mapOf("uid" to uid, "imei" to imei),
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            // Some vehicles without an intelligent battery answer code=100 —
            // treat that as "no BMS" rather than an error.
            val code = response.body["code"]?.toString()
            if (code == "100") {
                service.state = service.state.copyWith(
                    bmsInfo = null,
                    bmsInfoLoading = false,
                    bmsInfoError = null,
                )
                service.log.operation("官方 BMS 信息不可用", detail = "code=100")
                service.markRefreshSuccess(refreshKey)
                return
            }
            service.ensureSuccess(response.body, fallback = "获取官方 BMS 信息失败")
            if (!service.isCurrentSession(token)) return
            val info = OfficialCloudDataParser.bmsInfo(response.body["data"])
            service.state = service.state.copyWith(
                bmsInfo = if (info.hasData) info else null,
                bmsInfoLoading = false,
                bmsInfoError = null,
            )
            service.log.operation(
                "官方 BMS 信息已刷新",
                detail = "hasData=${info.hasData} details=${info.details.size} " +
                    "soc=${if (info.soc.isEmpty()) "none" else "present"}",
            )
            service.markRefreshSuccess(refreshKey)
        } catch (e: Exception) {
            if (!service.isCurrentSession(token)) return
            service.handleAuthFailureIfNeeded(e)
            if (service.state.signedIn) {
                val message = OfficialCloudRedactor.errorMessage(e)
                service.state = service.state.copyWith(bmsInfoLoading = false, bmsInfoError = message)
            }
            if (!silent) throw e
            service.log.operation(
                "官方 BMS 信息刷新失败",
                detail = OfficialCloudRedactor.errorMessage(e),
                level = LogLevel.WARNING,
            )
        } finally {
            if (!silent && service.isCurrentSession(token) && service.state.bmsInfoLoading) {
                service.state = service.state.copyWith(bmsInfoLoading = false)
            }
        }
    }

    /** Official battery-type catalog with a non-ext fallback. */
    suspend fun fetchBatteryTypes(): List<OfficialBatteryType> {
        val token = service.state.token
        if (token.isEmpty()) {
            throw OfficialCloudApiException(OfficialCloudMessages.SIGN_IN_REQUIRED)
        }
        try {
            val response = service.apiClient.request(
                "app/centralControl/batteryType/ext",
                method = "POST",
                token = token,
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            service.ensureSuccess(response.body, fallback = "获取电池类型失败")
            val types = OfficialCloudDataParser.batteryTypes(response.body["data"])
            if (types.isNotEmpty()) return types
            // Fallback to the non-ext endpoint when the ext one returns nothing.
            val fallback = service.apiClient.request(
                "app/centralControl/batteryType",
                method = "POST",
                token = token,
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            service.ensureSuccess(fallback.body, fallback = "获取电池类型失败")
            return OfficialCloudDataParser.batteryTypes(fallback.body["data"])
        } catch (e: Exception) {
            service.handleAuthFailureIfNeeded(e)
            throw e
        }
    }

    suspend fun fetchBatterySpecsByType(typeId: String): List<OfficialBatterySpec> {
        val token = service.state.token
        val normalized = typeId.trim()
        if (token.isEmpty()) {
            throw OfficialCloudApiException(OfficialCloudMessages.SIGN_IN_REQUIRED)
        }
        if (normalized.isEmpty() || normalized == "0") return emptyList()
        try {
            val response = service.apiClient.request(
                "app/centralControl/batterySpecByType",
                method = "POST",
                token = token,
                body = mapOf("typeId" to normalized),
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            service.ensureSuccess(response.body, fallback = "获取电池规格失败")
            return OfficialCloudDataParser.batterySpecs(response.body["data"])
        } catch (e: Exception) {
            service.handleAuthFailureIfNeeded(e)
            throw e
        }
    }

    // -- parking location / fence --------------------------------------------

    suspend fun refreshVehicleLocation(silent: Boolean, force: Boolean) {
        val token = service.state.token
        val vehicle = service.state.selectedVehicle
        if (token.isEmpty() || vehicle == null || vehicle.carId.isEmpty()) {
            return
        }
        val refreshKey = "vehicleLocation:${vehicle.key}"
        service.coalesceRefresh(refreshKey, silent, force) {
            refreshVehicleLocationNow(silent, refreshKey, vehicle, token)
        }
    }

    private suspend fun refreshVehicleLocationNow(
        silent: Boolean,
        refreshKey: String,
        vehicle: OfficialVehicle,
        token: String,
    ) {
        if (!silent) {
            service.state = service.state.copyWith(
                vehicleLocationLoading = true,
                vehicleLocationError = null,
            )
        }
        try {
            val response = service.apiClient.request(
                "app/car/extend/getByCarId",
                method = "POST",
                token = token,
                body = mapOf("carId" to vehicle.carId),
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            service.ensureSuccess(response.body, fallback = "获取官方停车位置失败")
            if (!service.isCurrentSession(token)) return
            val location = OfficialCloudDataParser.vehicleLocation(response.body["data"])
            service.state = service.state.copyWith(
                vehicleLocation = if (location.hasData) location else null,
                vehicleLocationLoading = false,
                vehicleLocationError = null,
            )
            service.log.operation(
                "官方停车位置已刷新",
                detail = if (location.hasData) "hasData=true" else "hasData=false",
            )
            service.markRefreshSuccess(refreshKey)
        } catch (e: Exception) {
            if (!service.isCurrentSession(token)) return
            service.handleAuthFailureIfNeeded(e)
            if (service.state.signedIn) {
                service.state = service.state.copyWith(
                    vehicleLocationLoading = false,
                    vehicleLocationError = OfficialCloudRedactor.errorMessage(e),
                )
            }
            if (!silent) throw e
            service.log.operation(
                "官方停车位置刷新失败",
                detail = OfficialCloudRedactor.errorMessage(e),
                level = LogLevel.WARNING,
            )
        } finally {
            if (!silent && service.isCurrentSession(token) && service.state.vehicleLocationLoading) {
                service.state = service.state.copyWith(vehicleLocationLoading = false)
            }
        }
    }

    suspend fun refreshFenceData(silent: Boolean, force: Boolean) {
        val token = service.state.token
        val vehicle = service.state.selectedVehicle
        if (token.isEmpty() || vehicle == null || vehicle.carId.isEmpty()) {
            return
        }
        val refreshKey = "fence:${vehicle.key}"
        service.coalesceRefresh(refreshKey, silent, force) {
            refreshFenceDataNow(silent, refreshKey, vehicle, token)
        }
    }

    private suspend fun refreshFenceDataNow(
        silent: Boolean,
        refreshKey: String,
        vehicle: OfficialVehicle,
        token: String,
    ) {
        if (!silent) {
            service.state = service.state.copyWith(fenceLoading = true, fenceError = null)
        }
        try {
            val response = service.apiClient.request(
                "app/device/getFenceData",
                method = "POST",
                token = token,
                body = mapOf("carId" to vehicle.carId),
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            service.ensureSuccess(response.body, fallback = "获取官方电子围栏失败")
            if (!service.isCurrentSession(token)) return
            val fence = OfficialCloudDataParser.fenceData(response.body["data"])
            service.state = service.state.copyWith(
                fenceData = if (fence.hasData) fence else null,
                fenceLoading = false,
                fenceError = null,
            )
            service.log.operation(
                "官方电子围栏已刷新",
                detail = if (fence.hasData) "hasData=true" else "hasData=false",
            )
            service.markRefreshSuccess(refreshKey)
        } catch (e: Exception) {
            if (!service.isCurrentSession(token)) return
            service.handleAuthFailureIfNeeded(e)
            if (service.state.signedIn) {
                service.state = service.state.copyWith(
                    fenceLoading = false,
                    fenceError = OfficialCloudRedactor.errorMessage(e),
                )
            }
            if (!silent) throw e
            service.log.operation(
                "官方电子围栏刷新失败",
                detail = OfficialCloudRedactor.errorMessage(e),
                level = LogLevel.WARNING,
            )
        } finally {
            if (!silent && service.isCurrentSession(token) && service.state.fenceLoading) {
                service.state = service.state.copyWith(fenceLoading = false)
            }
        }
    }

    // -- today ride mileage --------------------------------------------------

    suspend fun refreshTodayRideMileage(silent: Boolean, force: Boolean) {
        val token = service.state.token
        val vehicle = service.state.selectedVehicle
        val userId = service.state.userId.trim()
        val frame = vehicle?.frame?.trim() ?: ""
        if (token.isEmpty() || vehicle == null || frame.isEmpty() || userId.isEmpty()) {
            if (service.state.todayRideMileage.isNotEmpty()) {
                service.state = service.state.copyWith(todayRideMileage = "")
            }
            return
        }
        val refreshKey = "todayRide:${vehicle.key}"
        service.coalesceRefresh(refreshKey, silent, force) {
            refreshTodayRideMileageNow(refreshKey, vehicle, userId, token)
        }
    }

    private suspend fun refreshTodayRideMileageNow(
        refreshKey: String,
        vehicle: OfficialVehicle,
        userId: String,
        token: String,
    ) {
        try {
            val response = service.apiClient.request(
                "app/carTravel/records",
                method = "POST",
                token = token,
                body = mapOf("frame" to vehicle.frame.trim(), "uid" to userId),
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            service.ensureSuccess(response.body, fallback = "获取今日骑行失败")
            if (!service.isCurrentSession(token)) return
            val data = response.body["data"]
            val raw = data?.toString()?.trim() ?: ""
            service.state = service.state.copyWith(todayRideMileage = raw)
            service.log.operation(
                "官方今日骑行已刷新",
                detail = if (raw.isEmpty()) "empty" else "value=$raw",
            )
            service.markRefreshSuccess(refreshKey)
        } catch (e: Exception) {
            if (!service.isCurrentSession(token)) return
            service.handleAuthFailureIfNeeded(e)
            service.log.operation(
                "官方今日骑行刷新失败",
                detail = OfficialCloudRedactor.errorMessage(e),
                level = LogLevel.WARNING,
            )
        }
    }

    // -- ride statistics -----------------------------------------------------

    suspend fun refreshRideStatistics(
        period: OfficialRidePeriod,
        silent: Boolean,
        force: Boolean,
    ) {
        val token = service.state.token
        val vehicle = service.state.selectedVehicle
        if (token.isEmpty() || vehicle == null) return
        val frame = vehicle.frame.trim()
        if (frame.isEmpty()) {
            service.rideStatisticsGeneration++
            service.state = service.state.copyWith(
                rideStatistics = null,
                ridePeriod = period,
                rideStatisticsLoading = false,
                rideStatisticsError = "当前车辆缺少车架号，无法读取骑行统计",
            )
            return
        }
        val key = period.requestKey(service.clock().atZone(ZoneId.systemDefault()).toInstant())
        val refreshKey = "rideStatistics:${vehicle.key}:${period.wireName}:$key"
        if (!force && silent &&
            service.state.ridePeriod == period &&
            service.state.rideStatistics != null &&
            service.shouldUseRecentRefresh(refreshKey)
        ) {
            return
        }
        val inFlight = service.inFlightRefreshes[refreshKey]
        if (silent && inFlight != null) {
            inFlight.await()
            return
        }
        val generation = ++service.rideStatisticsGeneration
        val override = service.refreshRideStatisticsOverride
        if (override != null) {
            if (!silent) {
                service.state = service.state.copyWith(
                    rideStatistics = if (service.state.ridePeriod == period) service.state.rideStatistics else null,
                    ridePeriod = period,
                    rideStatisticsLoading = true,
                    rideStatisticsError = null,
                )
            }
            try {
                override(period)
            } finally {
                if (service.isCurrentRideStatisticsRequest(generation, token, vehicle.key, period)) {
                    service.state = service.state.copyWith(rideStatisticsLoading = false)
                }
            }
            return
        }
        service.coalesceRefresh(refreshKey, silent, force) {
            refreshRideStatisticsNow(silent, refreshKey, generation, vehicle, period, key, token)
        }
    }

    private suspend fun refreshRideStatisticsNow(
        silent: Boolean,
        refreshKey: String,
        generation: Int,
        vehicle: OfficialVehicle,
        period: OfficialRidePeriod,
        key: String,
        token: String,
    ) {
        if (!silent) {
            service.state = service.state.copyWith(
                rideStatistics = if (service.state.ridePeriod == period) service.state.rideStatistics else null,
                ridePeriod = period,
                rideStatisticsLoading = true,
                rideStatisticsError = null,
            )
        }
        try {
            val response = service.apiClient.request(
                "app/appRiding/getRidingDetail",
                method = "POST",
                token = token,
                body = mapOf(
                    "model" to period.wireName,
                    "key" to key,
                    "carFrame" to vehicle.frame.trim(),
                ),
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            service.ensureSuccess(response.body, fallback = "获取官方骑行统计失败")
            if (!service.isCurrentRideStatisticsRequest(generation, token, vehicle.key, period)) {
                return
            }
            val rawData = response.body["data"]
            if (rawData !is Map<*, *>) {
                throw OfficialCloudApiException("官方骑行统计数据格式异常")
            }
            val data = rawData.entries.associate { it.key.toString() to it.value }
            val statistics = OfficialRideStatistics.fromJson(data)
            service.state = service.state.copyWith(
                rideStatistics = statistics,
                ridePeriod = period,
                rideStatisticsLoading = false,
                rideStatisticsError = null,
            )
            service.log.operation("官方骑行统计已刷新", detail = "period=${period.wireName}")
            service.markRefreshSuccess(refreshKey)
        } catch (e: Exception) {
            if (!service.isCurrentSession(token)) return
            service.handleAuthFailureIfNeeded(e)
            if (service.isCurrentRideStatisticsRequest(generation, token, vehicle.key, period)) {
                service.state = service.state.copyWith(
                    rideStatisticsLoading = false,
                    rideStatisticsError = OfficialCloudRedactor.errorMessage(e),
                )
            }
            if (!silent) throw e
            service.log.operation(
                "官方骑行统计刷新失败",
                detail = OfficialCloudRedactor.errorMessage(e),
                level = LogLevel.WARNING,
            )
        } finally {
            if (service.isCurrentRideStatisticsRequest(generation, token, vehicle.key, period) &&
                service.state.rideStatisticsLoading
            ) {
                service.state = service.state.copyWith(rideStatisticsLoading = false)
            }
        }
    }

    // -- travel history ------------------------------------------------------

    suspend fun refreshTravelHistory(month: String?, silent: Boolean, force: Boolean) {
        val token = service.state.token
        val vehicle = service.state.selectedVehicle
        if (token.isEmpty() || vehicle == null) return
        val userId = service.state.userId.trim()
        if (userId.isEmpty()) {
            service.state = service.state.copyWith(
                travelDays = emptyList(),
                travelMonth = month ?: service.currentMonth(),
                travelError = "官方登录未返回 uid，无法读取历史轨迹",
            )
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
            service.shouldUseRecentRefresh(refreshKey)
        ) {
            return
        }
        service.coalesceRefresh(refreshKey, silent, force) {
            refreshTravelHistoryNow(silent, refreshKey, vehicle, queryMonth, userId, token)
        }
    }

    private suspend fun refreshTravelHistoryNow(
        silent: Boolean,
        refreshKey: String,
        vehicle: OfficialVehicle,
        queryMonth: String,
        userId: String,
        token: String,
    ) {
        if (!silent) {
            service.state = service.state.copyWith(
                travelLoading = true,
                travelError = null,
                travelMonth = queryMonth,
            )
        }
        try {
            val response = service.apiClient.request(
                "app/centralControl/deviceTravel",
                method = "POST",
                token = token,
                body = mapOf("queryMonth" to queryMonth, "frame" to vehicle.frame, "uid" to userId),
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            service.ensureSuccess(response.body, fallback = "获取官方历史轨迹失败")
            if (!service.isCurrentSession(token)) return
            val days = OfficialCloudDataParser.travelDays(response.body["data"])
            service.state = service.state.copyWith(
                travelDays = days,
                travelMonth = queryMonth,
                travelLoading = false,
                travelError = null,
            )
            service.log.operation("官方历史轨迹已刷新", detail = "days=${days.size}")
            service.markRefreshSuccess(refreshKey)
        } catch (e: Exception) {
            if (!service.isCurrentSession(token)) return
            service.handleAuthFailureIfNeeded(e)
            if (service.state.signedIn) {
                service.state = service.state.copyWith(
                    travelLoading = false,
                    travelError = OfficialCloudRedactor.errorMessage(e),
                )
            }
            if (!silent) throw e
            service.log.operation(
                "官方历史轨迹刷新失败",
                detail = OfficialCloudRedactor.errorMessage(e),
                level = LogLevel.WARNING,
            )
        } finally {
            if (!silent && service.isCurrentSession(token) && service.state.travelLoading) {
                service.state = service.state.copyWith(travelLoading = false)
            }
        }
    }

    suspend fun refreshTravelDetail(travelId: String) {
        val token = service.state.token
        if (token.isEmpty() || travelId.trim().isEmpty()) return
        service.state = service.state.copyWith(travelDetailLoading = true, travelDetailError = null)
        try {
            val response = service.apiClient.request(
                "app/centralControl/deviceTravelDetail",
                method = "POST",
                token = token,
                body = mapOf("deviceTravelId" to travelId),
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            service.ensureSuccess(response.body, fallback = "获取官方轨迹详情失败")
            if (!service.isCurrentSession(token)) return
            val points = OfficialCloudDataParser.travelPoints(response.body["data"])
            val details = service.state.travelDetails.toMutableMap()
            details[travelId] = points
            service.state = service.state.copyWith(
                travelDetails = details,
                travelDetailLoading = false,
                travelDetailError = null,
            )
            service.log.operation("官方轨迹详情已刷新", detail = "points=${points.size}")
        } catch (e: Exception) {
            if (!service.isCurrentSession(token)) return
            service.handleAuthFailureIfNeeded(e)
            if (service.state.signedIn) {
                service.state = service.state.copyWith(
                    travelDetailLoading = false,
                    travelDetailError = OfficialCloudRedactor.errorMessage(e),
                )
            }
            throw e
        } finally {
            if (service.isCurrentSession(token) && service.state.travelDetailLoading) {
                service.state = service.state.copyWith(travelDetailLoading = false)
            }
        }
    }

    // -- smart service status ------------------------------------------------

    suspend fun refreshSelectedSmartServiceStatus(silent: Boolean, force: Boolean) {
        val token = service.state.token
        val vehicle = service.state.selectedVehicle
        if (token.isEmpty() || vehicle == null) return
        val vehicleKey = vehicle.key
        val refreshKey = "smartService:$vehicleKey"
        if (vehicle.iccId.isEmpty()) {
            service.smartServiceStatuses.remove(vehicleKey)
            service.smartServiceStatusLoadedKeys.add(vehicleKey)
            service.markRefreshSuccess(refreshKey)
            return
        }
        service.coalesceRefresh(refreshKey, silent, force) {
            try {
                val status: OfficialSmartServiceStatus
                val override = service.refreshSmartServiceStatusOverride
                if (override != null) {
                    status = override(vehicle)
                } else {
                    val response = service.apiClient.request(
                        "app/sim/queryDetail",
                        method = "POST",
                        token = token,
                        body = mapOf("simNo" to vehicle.simNo, "iccId" to vehicle.iccId),
                        retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
                    )
                    service.ensureSuccess(response.body, fallback = "获取智能服务状态失败")
                    status = OfficialSmartServiceStatus.fromPayload(response.body["data"])
                }
                if (!service.isCurrentSession(token) || service.state.selectedVehicle?.key != vehicleKey) {
                    return@coalesceRefresh
                }
                service.smartServiceStatuses[vehicleKey] = status
                service.smartServiceStatusLoadedKeys.add(vehicleKey)
                service.markRefreshSuccess(refreshKey)
            } catch (e: Exception) {
                if (!service.isCurrentSession(token)) return@coalesceRefresh
                service.handleAuthFailureIfNeeded(e)
                throw e
            }
        }
    }

    /**
     * Resolve whether remote control is available for the selected vehicle,
     * pre-loading the smart-service status when it has not been fetched yet.
     */
    suspend fun resolveSelectedRemoteControlServiceDecision(): OfficialSmartServiceControlDecision {
        val vehicle = service.state.selectedVehicle
        if (!service.state.signedIn || vehicle == null) {
            return OfficialSmartServiceControlDecision()
        }
        // Official KKS/YJ control branches do not consult querySimDetail.
        if (vehicle.modelType == 1 || vehicle.modelType == 2) {
            return OfficialSmartServiceControlDecision()
        }
        if (vehicle.key !in service.smartServiceStatusLoadedKeys) {
            try {
                refreshSelectedSmartServiceStatus(silent = true)
            } catch (e: Exception) {
                service.log.operation(
                    "官方智能服务状态预检失败",
                    detail = OfficialCloudRedactor.errorMessage(e),
                    level = LogLevel.WARNING,
                )
            }
        }
        return service.selectedRemoteControlServiceDecision
    }
}
