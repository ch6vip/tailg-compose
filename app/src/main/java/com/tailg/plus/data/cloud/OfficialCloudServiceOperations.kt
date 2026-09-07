package com.tailg.plus.data.cloud

import com.tailg.plus.data.model.AffirmBatteryInfoRequest
import com.tailg.plus.data.model.CommandCode
import com.tailg.plus.data.model.OfficialCloudCommand
import com.tailg.plus.data.model.OfficialRidePeriod
import com.tailg.plus.data.model.OfficialUserProfile
import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.data.model.OfficialVehicleSelfCheck
import com.tailg.plus.data.model.parsePersistedBool
import com.tailg.plus.data.model.parsePersistedMap
import com.tailg.plus.log.LogLevel
import com.tailg.plus.util.SensitiveValueMasker
import timber.log.Timber
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Operation logic of `OfficialCloudService` — port of the login / logout /
 * vehicle control / bind-unbind / fence-update / nickname methods of
 * `lib/services/official_cloud_service.dart`.
 *
 * All methods keep the Dart guards and error semantics: `signInRequired`
 * exceptions, token validation, `handleAuthFailureIfNeeded` on failures,
 * and the test-only override hooks on [OfficialCloudService].
 */
internal class OfficialCloudOperationLogic(
    private val service: OfficialCloudService,
    private val refresh: OfficialCloudRefreshLogic,
) {

    // -- SMS / login / logout ------------------------------------------------

    suspend fun requestSmsCode(phone: String, ticket: String, randstr: String) {
        val normalized = phone.trim()
        if (!OfficialCloudLoginValidator.isValidPhone(normalized)) {
            throw OfficialCloudApiException("请输入 11 位手机号")
        }
        service.setLoading(true)
        try {
            // 官方 3.6.0 起登录验证码改走「滑块验证 + loginCode」双接口；
            // 旧 app/getCode 已被后端按 User-Agent 拦截（返回「请升级到最新版本」）。
            // 1) 用滑块结果 ticket/randstr 换一次性 captchaPassToken。
            val verifyResponse = service.apiClient.request(
                "app/captcha/slider/verify",
                method = "POST",
                body = mapOf("ticket" to ticket, "randstr" to randstr),
            )
            service.ensureSuccess(verifyResponse.body, fallback = "滑块验证失败")
            val captchaPassToken = parsePersistedMap(verifyResponse.body["data"])
                ?.get("captchaPassToken")
                ?.toString()
                ?.takeIf { it.isNotBlank() }
                ?: throw OfficialCloudApiException("滑块验证未返回凭证，请重试")

            // 2) 用 captchaPassToken 真正发送验证码。
            val codeResponse = service.apiClient.request(
                "app/sms/loginCode?phone=${URLEncoder.encode(normalized, StandardCharsets.UTF_8.name())}" +
                    "&captchaPassToken=${URLEncoder.encode(captchaPassToken, StandardCharsets.UTF_8.name())}",
                method = "POST",
            )
            service.ensureSuccess(codeResponse.body, fallback = "验证码发送失败")
            service.log.operation("官方云验证码已发送")
        } finally {
            service.setLoading(false)
        }
    }

    suspend fun login(phone: String, smsCode: String) {
        val normalizedPhone = phone.trim()
        val normalizedSms = smsCode.trim()
        if (!OfficialCloudLoginValidator.isValidPhone(normalizedPhone)) {
            throw OfficialCloudApiException("请输入 11 位手机号")
        }
        if (!OfficialCloudLoginValidator.isValidSmsCode(normalizedSms)) {
            throw OfficialCloudApiException("请输入短信验证码")
        }
        val attempt = beginLogin()
        try {
            val response = service.apiClient.request(
                "app/login",
                method = "POST",
                body = mapOf(
                    "macCode" to service.apiClient.config.loginMacCode,
                    "phone" to normalizedPhone,
                    "smsCode" to normalizedSms,
                    "autoCompleteUserDetail" to "true",
                ),
            )
            val token = response.headers["authorization"] ?: ""
            if (token.isEmpty()) {
                service.ensureSuccess(response.body, fallback = "登录失败，未返回 token")
                throw OfficialCloudApiException("登录失败，未返回 token")
            }
            val userId = OfficialCloudAuthParser.extractUserId(response.body)
            service.withSessionWrite {
                ensureLoginAttempt(attempt)
                service.replaceSession(token, normalizedPhone, userId, loading = true)
                service.storage.saveCredentials(token = token, phone = normalizedPhone, userId = userId)
            }
            service.log.operation("官方云登录成功")
            coroutineScope {
                val vehicles = async {
                    refresh.refreshVehicles(
                        silent = false,
                        refreshReplicaDetails = true,
                        force = false,
                        preferredVehicleKey = null,
                    )
                }
                val profile = async { refresh.refreshUserProfile(silent = true, force = false) }
                vehicles.await()
                profile.await()
            }
        } finally {
            finishLogin(attempt)
        }
    }

    suspend fun loginWithToken(rawToken: String, phone: String, userId: String) {
        // Masked even though Timber is DEBUG-only: raw phone/userId must not
        // reach logcat on developer machines or recorded screen captures.
        Timber.tag("TokenLogin").d(
            "loginWithToken called, rawToken.len=${rawToken.length}, " +
                "phone=${SensitiveValueMasker.phone(phone)}, userId=${SensitiveValueMasker.compact(userId)}",
        )
        val token = normalizeAuthorizationToken(rawToken)
        Timber.tag("TokenLogin").d("normalized token.len=${token.length}")
        if (token.isEmpty()) {
            Timber.tag("TokenLogin").w("token empty after normalize, throwing")
            throw OfficialCloudApiException("请粘贴有效的官方 Token")
        }
        val attempt = beginLogin()
        try {
            hydrateOfficialSession(token = token, seedPhone = phone, seedUserId = userId, attempt = attempt)
            Timber.tag("TokenLogin").d("hydrateOfficialSession succeeded")
            service.log.operation("官方云 Token 登录成功")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag("TokenLogin").e(e, "hydrateOfficialSession failed: ${e::class.simpleName}: ${e.message}")
            throw e
        } finally {
            finishLogin(attempt)
        }
    }

    suspend fun logout(expectedSession: OfficialCloudSession? = null, expectedAttempt: Long? = null, error: String? = null) {
        withContext(NonCancellable) {
            service.withSessionWrite {
                if (expectedSession != null && !service.isCurrentSession(expectedSession)) return@withSessionWrite
                if (expectedAttempt != null && service.loginGeneration != expectedAttempt) return@withSessionWrite
                service.loginGeneration++
                // Invalidate in-memory state before the first disk suspension.
                service.replaceSession()
                service.updateState { it.copyWith(error = error) }
                try {
                    service.storage.clearCredentialsAndSelection()
                } finally {
                    for (effect in service.afterLogoutSideEffects.toList()) {
                        try {
                            effect()
                        } catch (e: Exception) {
                            service.log.operation(
                                "退出登录后通道清理失败",
                                detail = OfficialCloudRedactor.errorMessage(e),
                                level = LogLevel.WARNING,
                            )
                        }
                    }
                }
                service.log.operation("官方云已退出登录")
            }
        }
    }

    // -- profile -------------------------------------------------------------

    suspend fun updateUserNickname(nickName: String) {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        if (token.isEmpty()) {
            throw OfficialCloudApiException(OfficialCloudMessages.SIGN_IN_REQUIRED)
        }
        val trimmed = nickName.trim()
        if (trimmed.isEmpty()) {
            throw OfficialCloudApiException("昵称不能为空")
        }
        if (trimmed.length > 20) {
            throw OfficialCloudApiException("昵称请控制在 20 字以内")
        }
        val current = service.state.userProfile
        val body = linkedMapOf<String, Any?>("nickName" to trimmed)
        if (current != null) {
            if (current.obsAvatarId.isNotEmpty()) body["obsAvatarId"] = current.obsAvatarId
            if (current.gender.isNotEmpty()) body["gender"] = current.gender
            if (current.province.isNotEmpty()) body["province"] = current.province
            if (current.city.isNotEmpty()) body["city"] = current.city
            if (current.area.isNotEmpty()) body["area"] = current.area
            if (current.address.isNotEmpty()) body["address"] = current.address
            if (current.signature.isNotEmpty()) body["signature"] = current.signature
            if (current.birthday.isNotEmpty()) body["birthDay"] = current.birthday
        }
        val response = service.apiClient.request(
            "app/updateUserProfile",
            method = "POST",
            token = token,
            body = body,
        )
        service.ensureSuccess(response.body, fallback = "更新昵称失败")
        if (!service.isCurrentSession(session)) return
        val next = (current ?: OfficialUserProfile()).copyWith(nickName = trimmed)
        service.updateSessionState(session) { it.copyWith(userProfile = next) }
        service.runSilentRefresh(
            { service.persistCurrentUserProfile(session) },
            failureMessage = "官方用户资料缓存保存失败",
        )
        service.log.operation(
            "官方昵称已更新",
            detail = "nick=${SensitiveValueMasker.compact(trimmed)}",
        )
        // Re-fetch so server-normalized fields win.
        service.runSilentRefresh(
            { refresh.refreshUserProfile(silent = true, force = true) },
            failureMessage = "官方用户资料静默刷新失败",
        )
    }

    // -- vehicle selection ---------------------------------------------------

    suspend fun selectVehicle(vehicle: OfficialVehicle) {
        val override = service.selectVehicleOverride
        if (override != null) {
            override(vehicle)
            return
        }
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        val changed = service.withSessionWrite {
            service.ensureCurrentSession(session)
            if (service.state.vehicles.none { it.key == vehicle.key }) {
                throw OfficialCloudApiException("车辆列表已变化，请刷新后重试")
            }
            val changed = service.state.selectedVehicle?.key != vehicle.key
            if (changed) {
                service.lastSuccessfulRefresh.clear()
                service.rideStatisticsGeneration.incrementAndGet()
            }
            service.updateSessionState(session) { it.withVehicleSelection(selectedKey = vehicle.key) }
            service.storage.saveSelectedVehicleKey(vehicle.key)
            service.storage.saveCarControlInfo(service.state.selectedVehicle)
            changed
        }
        service.ensureCurrentSession(session)
        service.applySelectedVehicleToLocalProfile()
        if (changed) {
            service.refreshVehicleDependents(refreshReplicaDetails = true)
        }
    }

    suspend fun changeUsingVehicle(vehicle: OfficialVehicle) {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        if (token.isEmpty()) {
            throw OfficialCloudApiException(OfficialCloudMessages.SIGN_IN_REQUIRED)
        }
        val carId = vehicle.carId.trim()
        if (carId.isEmpty()) {
            throw OfficialCloudApiException("缺少车辆 carId，无法切换")
        }
        val override = service.changeUsingVehicleOverride
        if (override != null) {
            override(vehicle)
            return
        }
        try {
            val response = service.apiClient.request(
                "app/centralControl/changeUsingCar",
                method = "POST",
                token = token,
                body = mapOf("carId" to carId),
            )
            service.ensureSuccess(response.body, fallback = "切换车辆失败")
            service.ensureCurrentSession(session)
            // The status endpoint returns the newly selected vehicle with its
            // full BLE/MQTT credentials; prefer that over the lighter row.
            try {
                refresh.refreshVehicles(
                    silent = false,
                    refreshReplicaDetails = true,
                    force = true,
                    preferredVehicleKey = vehicle.key,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (!service.isCurrentSession(session)) throw e
                service.withSessionWrite {
                    service.ensureCurrentSession(session)
                    service.lastSuccessfulRefresh.clear()
                    service.rideStatisticsGeneration.incrementAndGet()
                    service.updateSessionState(session) {
                        val merged = listOf(vehicle) + it.vehicles.filter { existing -> existing.carId != carId }
                        it.withVehicleSelection(merged, vehicle.key)
                    }
                    service.storage.saveSelectedVehicleKey(vehicle.key)
                    service.storage.saveCarControlInfo(vehicle)
                }
                service.applySelectedVehicleToLocalProfile()
                service.log.operation(
                    "切车后车辆状态刷新失败，已使用车库数据",
                    detail = OfficialCloudRedactor.errorMessage(e),
                    level = LogLevel.WARNING,
                )
            }
            service.log.operation("官方当前车辆已切换", detail = carId)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            service.ensureCurrentSession(session)
            service.handleAuthFailureIfNeeded(e, session)
            throw e
        }
    }

    /** Apply MQTT-reported acc / defence state to the selected vehicle. */
    fun applyMqttVehicleStatus(acc: Int?, defenceStatus: Int?, session: OfficialCloudSession, vehicleKey: String) {
        if (service.disposed) return
        if (acc == null && defenceStatus == null) return
        // Read-modify-write INSIDE the CAS transform: reading selectedVehicle
        // outside and then replacing the whole list used to clobber a
        // concurrent refreshVehicles result (the stale list snapshot won the
        // updateState race and was then persisted to the encrypted cache).
        val appliedHolder = java.util.concurrent.atomic.AtomicReference<
            com.tailg.plus.data.model.OfficialVehicle?
        >()
        service.updateVehicleState(session, vehicleKey) { state ->
            val current = state.selectedVehicle ?: return@updateVehicleState state
            val nextAcc = acc ?: current.acc
            val nextDefence = defenceStatus ?: current.defenceStatus
            if (nextAcc == current.acc && nextDefence == current.defenceStatus) {
                return@updateVehicleState state
            }
            val updated = current.copyWith(acc = nextAcc, defenceStatus = nextDefence)
            appliedHolder.set(updated)
            state.copyWith(
                vehicles = state.vehicles.map {
                    if (it.key == updated.key) updated else it
                },
            )
        }
        val updated = appliedHolder.get() ?: return
        service.runSilentRefresh(
            { service.persistCurrentVehicle(session) },
            failureMessage = "官方车辆控制缓存保存失败",
        )
        service.log.operation(
            "官方 MQTT 状态已更新",
            detail = "acc=${updated.acc} defenceStatus=${updated.defenceStatus}",
        )
    }

    // -- battery setup -------------------------------------------------------

    suspend fun affirmBatteryInfo(request: AffirmBatteryInfoRequest) {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        if (token.isEmpty()) {
            throw OfficialCloudApiException(OfficialCloudMessages.SIGN_IN_REQUIRED)
        }
        if (request.carId.trim().isEmpty()) {
            throw OfficialCloudApiException("缺少车辆信息，无法更正电池")
        }
        try {
            val response = service.apiClient.request(
                "app/centralControl/batterySetUp",
                method = "POST",
                token = token,
                body = request.toBody(),
                retryPolicy = OfficialCloudRetryPolicy.NO_RETRY,
            )
            service.ensureSuccess(response.body, fallback = "更正电池失败")
            service.log.operation(
                "官方更正电池已提交",
                detail = "carId=${SensitiveValueMasker.compact(request.carId)}",
            )
            // Refresh vehicle + battery so bind/spec labels update.
            coroutineScope {
                val refreshes = listOf(
                    async { refresh.refreshVehicles(silent = true, refreshReplicaDetails = true, force = true, preferredVehicleKey = null) },
                    async { refresh.refreshBatteryInfo(silent = true, force = true) },
                    async { refresh.refreshBmsInfo(silent = true, force = true) },
                )
                refreshes.forEach { it.await() }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            service.handleAuthFailureIfNeeded(e, session)
            throw e
        }
    }

    // -- fence ---------------------------------------------------------------

    suspend fun updateFenceData(enabled: Boolean, radiusValue: Int, timeFrom: String, timeTo: String) {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        val vehicle = service.state.selectedVehicle
        if (token.isEmpty() || vehicle == null) return
        service.updateVehicleState(session, vehicle.key) { it.copyWith(fenceLoading = true, fenceError = null) }
        try {
            val response = service.apiClient.request(
                "app/device/updFenceData",
                method = "POST",
                token = token,
                body = mapOf(
                    "carId" to vehicle.carId,
                    "fenceSwitch" to (if (enabled) "1" else "0"),
                    "fenceRadius" to "$radiusValue",
                    "fenceTimeFr" to timeFrom,
                    "fenceTimeTo" to timeTo,
                ),
                retryPolicy = OfficialCloudRetryPolicy.NO_RETRY,
            )
            service.ensureSuccess(response.body, fallback = "围栏设置保存失败")
            if (!service.isCurrentVehicleSession(session, vehicle.key)) return
            refresh.refreshFenceData(silent = true, force = true)
            service.log.operation("官方电子围栏已更新")
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
            throw e
        } finally {
            if (service.isCurrentVehicleSession(session, vehicle.key) && service.state.fenceLoading) {
                service.updateVehicleState(session, vehicle.key) { it.copyWith(fenceLoading = false) }
            }
        }
    }

    // -- car nickname --------------------------------------------------------

    suspend fun updateCarNickName(carId: String, carNickName: String) {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        if (token.isEmpty()) {
            // Dart deliberately throws a plain Exception here (not the API
            // exception); kept for parity.
            throw Exception(OfficialCloudMessages.SIGN_IN_REQUIRED)
        }
        val normalizedCarId = carId.trim()
        val nick = carNickName.trim()
        if (normalizedCarId.isEmpty()) {
            throw Exception("车辆 ID 无效")
        }
        if (nick.isEmpty()) {
            throw Exception("车辆昵称不能为空")
        }
        try {
            val response = service.apiClient.request(
                "app/car/updateCarInfo",
                method = "POST",
                token = token,
                body = mapOf("carId" to normalizedCarId, "carNickName" to nick),
                retryPolicy = OfficialCloudRetryPolicy.NO_RETRY,
            )
            service.ensureSuccess(response.body, fallback = "车辆昵称保存失败")
            if (!service.isCurrentSession(session)) return
            service.updateSessionState(session) { current ->
                val vehicles = current.vehicles.map { vehicle ->
                    if (vehicle.carId == normalizedCarId) {
                        OfficialVehicle.fromJson(vehicle.toJson() + ("carNickName" to nick))
                    } else {
                        vehicle
                    }
                }
                current.copyWith(vehicles = vehicles, error = null)
            }
            service.persistCurrentVehicle(session)
            service.ensureCurrentSession(session)
            service.applySelectedVehicleToLocalProfile()
            service.log.operation("官方车辆昵称已更新", detail = normalizedCarId)
            try {
                refresh.refreshVehicles(silent = true, refreshReplicaDetails = true, force = true, preferredVehicleKey = null)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Keep the optimistic local nick if the status refresh fails.
                service.log.operation(
                    "官方车辆列表刷新失败（昵称已写回）",
                    detail = OfficialCloudRedactor.errorMessage(e),
                    level = LogLevel.WARNING,
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!service.isCurrentSession(session)) return
            service.handleAuthFailureIfNeeded(e, session)
            throw e
        }
    }

    // -- messages control ----------------------------------------------------

    suspend fun getMessageControl(): Map<String, Boolean> {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        if (token.isEmpty()) return emptyMap()
        try {
            val override = service.getMessageControlOverride
            if (override != null) return override()
            val response = service.apiClient.request(
                "app/msg/getMessageControl",
                method = "POST",
                token = token,
                retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
            )
            service.ensureSuccess(response.body, fallback = "获取消息偏好失败")
            service.ensureCurrentSession(session)
            val data = response.body["data"]
            if (data !is Map<*, *>) return emptyMap()
            return data.entries.associate { (key, value) ->
                key.toString() to parsePersistedBool(value)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            service.handleAuthFailureIfNeeded(e, session)
            throw e
        }
    }

    suspend fun setMessagePushConfig(config: Map<String, Boolean>) {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        if (token.isEmpty()) return
        try {
            val override = service.setMessagePushConfigOverride
            if (override != null) {
                override(config.toMap())
            } else {
                val body = config.mapValues { (_, value) -> if (value) "1" else "0" }
                val response = service.apiClient.request(
                    "app/msg/setMessagePushConfig",
                    method = "POST",
                    token = token,
                    body = body,
                    retryPolicy = OfficialCloudRetryPolicy.NO_RETRY,
                )
                service.ensureSuccess(response.body, fallback = "消息偏好保存失败")
            }
            service.log.operation("消息推送偏好已更新")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            service.handleAuthFailureIfNeeded(e, session)
            throw e
        }
    }

    suspend fun deleteMessages() {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        if (token.isEmpty()) return
        try {
            val override = service.deleteMessagesOverride
            if (override != null) {
                override()
            } else {
                val response = service.apiClient.request(
                    "app/msg/delMsg",
                    method = "POST",
                    token = token,
                    retryPolicy = OfficialCloudRetryPolicy.NO_RETRY,
                )
                service.ensureSuccess(response.body, fallback = "清空消息失败")
            }
            if (!service.isCurrentSession(session)) return
            service.updateSessionState(session) { it.copyWith(
                vehicleMessages = emptyList(),
                systemMessages = emptyList(),
                messagesError = null,
            ) }
            service.log.operation("官方消息已清空")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            service.handleAuthFailureIfNeeded(e, session)
            throw e
        }
    }

    // -- local garage links --------------------------------------------------

    suspend fun linkLocalVehicle(officialVehicleKey: String, localVehicleId: String) = service.withSessionWrite {
        service.saveLinks(
            OfficialCloudVehicleLinks.link(
                service.state.localVehicleLinks,
                officialVehicleKey = officialVehicleKey,
                localVehicleId = localVehicleId,
            ),
        )
    }

    suspend fun unlinkLocalVehicle(officialVehicleKey: String) = service.withSessionWrite {
        service.saveLinks(
            OfficialCloudVehicleLinks.unlink(service.state.localVehicleLinks, officialVehicleKey),
        )
    }

    suspend fun pruneLocalVehicleLinks(validLocalVehicleIds: Set<String>) = service.withSessionWrite {
        val links = OfficialCloudVehicleLinks.prune(service.state.localVehicleLinks, validLocalVehicleIds)
        if (links == service.state.localVehicleLinks) return@withSessionWrite
        service.saveLinks(links)
        service.log.operation("官方车辆失效关联已清理")
    }

    // -- control commands ----------------------------------------------------

    suspend fun selfCheck(): OfficialVehicleSelfCheck {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        val vehicle = service.state.selectedVehicle
        if (token.isEmpty() || vehicle == null) {
            throw OfficialCloudApiException(OfficialCloudMessages.SIGN_IN_AND_SELECT_VEHICLE_REQUIRED)
        }
        if (vehicle.commandImei.isEmpty()) {
            throw OfficialCloudApiException("当前车辆缺少官方 IMEI，无法云端自检")
        }
        try {
            service.log.operation("发送官方云端自检")
            val response = service.apiClient.request(
                "app/device/cmd/status",
                method = "POST",
                token = token,
                body = mapOf("imei" to vehicle.commandImei),
            )
            service.ensureSuccess(response.body, fallback = "云端自检失败")
            service.ensureCurrentSession(session)
            val result = OfficialVehicleSelfCheck.fromResponse(response.body)
            service.log.operation(
                "官方云端自检已返回",
                detail = "code=${result.code?.toString() ?: "none"}, data=${result.hasData}",
            )
            return result
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            service.ensureCurrentSession(session)
            service.handleAuthFailureIfNeeded(e, session)
            service.log.operation(
                "官方云端自检失败",
                detail = OfficialCloudRedactor.errorMessage(e),
                level = LogLevel.WARNING,
            )
            throw e
        }
    }

    suspend fun sendCommand(command: CommandCode): String {
        val cloudCommand = OfficialCloudCommand.fromCommandCode(command)
            ?: throw OfficialCloudApiException("官方云端不支持${command.label}")
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        val vehicle = service.state.selectedVehicle
        if (token.isEmpty() || vehicle == null) {
            throw OfficialCloudApiException(OfficialCloudMessages.SIGN_IN_AND_SELECT_VEHICLE_REQUIRED)
        }
        if (vehicle.commandImei.isEmpty()) {
            throw OfficialCloudApiException("当前车辆缺少官方 IMEI，无法云端控车")
        }
        // Test-only override: record the command and let the stub decide the
        // outcome instead of hitting the network.
        val override = service.sendCommandOverride
        if (override != null) {
            service.sentCommands.add(command)
            return override(command)
        }
        try {
            service.log.operation("发送官方云端指令: ${command.label}")
            val response = service.apiClient.request(
                "app/device/cmd/${cloudCommand.apiName}",
                method = "POST",
                token = token,
                body = mapOf("imei" to vehicle.commandImei),
            )
            service.ensureSuccess(response.body, fallback = "${command.label}失败")
            service.ensureCurrentSession(session)
            val message = response.body["msg"]?.toString()
            service.log.operation("官方云端指令已返回: ${command.label}")
            service.refreshVehiclesAfterCommand(command)
            return if (message.isNullOrEmpty()) "success" else message
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            service.ensureCurrentSession(session)
            service.handleAuthFailureIfNeeded(e, session)
            throw e
        }
    }

    suspend fun syncCarOperatorAfterCommand(command: CommandCode, vehicle: OfficialVehicle) {
        val update = OfficialCarOperatorPolicy.updateFor(command = command, vehicle = vehicle) ?: return
        setCarOperator(carId = update.carId, operatorFlag = update.operatorFlag)
    }

    suspend fun setCarOperator(carId: String, operatorFlag: String) {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        if (token.isEmpty()) {
            throw OfficialCloudApiException(OfficialCloudMessages.SIGN_IN_REQUIRED)
        }
        val id = carId.trim()
        if (id.isEmpty()) {
            throw OfficialCloudApiException("缺少车辆 carId，无法同步操作人")
        }
        if (operatorFlag != "0" && operatorFlag != "1") {
            throw OfficialCloudApiException("车辆操作人状态无效")
        }
        val override = service.setCarOperatorOverride
        if (override != null) {
            service.sentCarOperatorUpdates.add(OfficialCarOperatorUpdate(carId = id, operatorFlag = operatorFlag))
            override(id, operatorFlag)
            return
        }
        try {
            val response = service.apiClient.request(
                "app/car/setCarOperator",
                method = "POST",
                token = token,
                body = mapOf("carId" to id, "operatorFlag" to operatorFlag),
            )
            service.ensureSuccess(response.body, fallback = "同步车辆操作人失败")
            service.ensureCurrentSession(session)
            service.log.operation(
                "官方车辆操作人已同步",
                detail = "carId=${SensitiveValueMasker.compact(id)} flag=$operatorFlag",
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            service.ensureCurrentSession(session)
            service.handleAuthFailureIfNeeded(e, session)
            throw e
        }
    }

    suspend fun setKksHidEnabled(enabled: Boolean) {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        val vehicle = service.state.selectedVehicle
        if (token.isEmpty() || vehicle == null) {
            throw OfficialCloudApiException(OfficialCloudMessages.SIGN_IN_AND_SELECT_VEHICLE_REQUIRED)
        }
        if (vehicle.imei.isEmpty()) {
            throw OfficialCloudApiException("当前车辆缺少官方 IMEI，无法设置感应解锁")
        }
        val override = service.setKksHidEnabledOverride
        if (override != null) {
            service.sentKksHidStates.add(enabled)
            override(enabled)
            return
        }
        try {
            val response = service.apiClient.request(
                if (enabled) "app/web/hid/on" else "app/web/hid/off",
                method = "POST",
                token = token,
                body = mapOf("imei" to vehicle.imei, "protocolType" to "1"),
            )
            service.ensureSuccess(
                response.body,
                fallback = if (enabled) "开启感应解锁失败" else "关闭感应解锁失败",
            )
            service.ensureCurrentSession(session)
            service.log.operation(if (enabled) "KKS 感应解锁已开启" else "KKS 感应解锁已关闭")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            service.ensureCurrentSession(session)
            service.handleAuthFailureIfNeeded(e, session)
            throw e
        }
    }

    // -- bind / unbind -------------------------------------------------------

    suspend fun bindVehicleByImei(imei: String) {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        if (token.isEmpty()) {
            throw OfficialCloudApiException(OfficialCloudMessages.SIGN_IN_REQUIRED)
        }
        val cleaned = imei.trim()
        if (cleaned.isEmpty()) {
            throw OfficialCloudApiException("设备 IMEI 不能为空")
        }
        val override = service.bindVehicleByImeiOverride
        if (override != null) {
            override(cleaned)
            return
        }
        try {
            service.log.operation("官方 IMEI 绑车", detail = cleaned)
            val response = service.apiClient.request(
                "app/car/bikeBind",
                method = "POST",
                token = token,
                body = mapOf("imei" to cleaned),
            )
            service.ensureSuccess(response.body, fallback = "绑车失败")
            service.ensureCurrentSession(session)
            refresh.refreshVehicles(silent = false, refreshReplicaDetails = true, force = true, preferredVehicleKey = null)
            service.log.operation("官方 IMEI 绑车成功")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            service.ensureCurrentSession(session)
            service.handleAuthFailureIfNeeded(e, session)
            throw e
        }
    }

    suspend fun unbindVehicle(carId: String?, unbindType: Int) {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        if (token.isEmpty()) {
            throw OfficialCloudApiException(OfficialCloudMessages.SIGN_IN_REQUIRED)
        }
        val id = (carId ?: service.state.selectedVehicle?.carId ?: "").trim()
        if (id.isEmpty()) {
            throw OfficialCloudApiException("缺少车辆 carId，无法解绑")
        }
        val override = service.unbindVehicleOverride
        if (override != null) {
            override(id, unbindType)
            return
        }
        try {
            service.log.operation("官方解绑车辆", detail = "carId=$id type=$unbindType")
            val response = service.apiClient.request(
                "app/car/bikeUnbind",
                method = "POST",
                token = token,
                body = mapOf("carId" to id, "unbindType" to unbindType),
            )
            service.ensureSuccess(response.body, fallback = "解绑失败")
            service.ensureCurrentSession(session)
            refresh.refreshVehicles(silent = false, refreshReplicaDetails = true, force = true, preferredVehicleKey = null)
            service.log.operation("官方解绑成功", detail = id)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            service.ensureCurrentSession(session)
            service.handleAuthFailureIfNeeded(e, session)
            throw e
        }
    }

    /** P3-5: query official firm version for OTA (`getFirmVersion`). */
    suspend fun getFirmVersion(imei: String?): Map<String, Any?> {
        val requestState = service.state
        val session = requestState.sessionIdentity
        val token = session.token
        if (token.isEmpty()) {
            throw OfficialCloudApiException(OfficialCloudMessages.SIGN_IN_REQUIRED)
        }
        val id = (imei ?: service.state.selectedVehicle?.commandImei ?: "").trim()
        if (id.isEmpty()) {
            throw OfficialCloudApiException("缺少 IMEI，无法查询固件")
        }
        val override = service.getFirmVersionOverride
        if (override != null) return override(id)
        val response = service.apiClient.request(
            "app/firmVersionInfo/getFirmVersion",
            method = "POST",
            token = token,
            body = mapOf("imei" to id),
            retryPolicy = OfficialCloudRetryPolicy.READ_REQUEST,
        )
        service.ensureSuccess(response.body, fallback = "获取固件版本失败")
        service.ensureCurrentSession(session)
        val data = response.body["data"]
        if (data is Map<*, *>) {
            return parsePersistedMap(data) ?: emptyMap()
        }
        return parsePersistedMap(response.body) ?: emptyMap()
    }

    // -- session hydration ---------------------------------------------------

    /**
     * Dart `_hydrateOfficialSession`: stage the candidate session in memory,
     * verify it with a real server round-trip, then persist. Any failure
     * aborts the candidate session.
     */
    private suspend fun hydrateOfficialSession(token: String, seedPhone: String, seedUserId: String, attempt: Long) {
        val verifiedPhone = seedPhone.trim()
        try {
            val session = service.withSessionWrite {
                ensureLoginAttempt(attempt)
                service.replaceSession(token, verifiedPhone, seedUserId.trim(), loading = true)
                service.storage.clearCredentialsAndSelection()
                service.state.sessionIdentity
            }
            Timber.tag("TokenLogin").d("hydrate: calling refreshVehicles to verify token")
            refresh.refreshVehicles(silent = false, force = true, refreshDependents = false)
            service.ensureCurrentSession(session)
            Timber.tag("TokenLogin").d("hydrate: refreshVehicles succeeded, vehicles=${service.state.vehicles.size}")
            refresh.refreshUserProfile(silent = true, force = true)
            service.ensureCurrentSession(session)
            Timber.tag("TokenLogin").d("hydrate: refreshUserProfile succeeded")
            service.withSessionWrite {
                ensureLoginAttempt(attempt)
                service.ensureCurrentSession(session)
                val verifiedUserId = service.state.userProfile?.id?.trim()?.takeIf { it.isNotEmpty() }
                    ?: service.state.userId.trim().ifEmpty { seedUserId.trim() }
                service.storage.saveCredentials(token = token, phone = verifiedPhone, userId = verifiedUserId)
                // saveCredentials deliberately removes the previous account's cache.
                // Restore this verified session only after credentials have been saved.
                service.storage.saveSelectedVehicleKey(service.state.selectedVehicleKey)
                service.storage.saveCarControlInfo(service.state.selectedVehicle)
                service.storage.saveUserProfile(service.state.userProfile)
                service.updateSessionState(session) { it.copyWith(userId = verifiedUserId) }
            }
            service.refreshVehicleDependents(refreshReplicaDetails = true)
        } catch (e: Exception) {
            logout(expectedAttempt = attempt)
            throw e
        }
    }

    private suspend fun beginLogin(): Long = service.withSessionWrite {
        service.loginGeneration++
        service.setLoading(true)
        service.loginGeneration
    }

    private fun ensureLoginAttempt(attempt: Long) {
        if (service.disposed || service.loginGeneration != attempt) {
            throw OfficialCloudApiException("官方登录状态已变化，请重试")
        }
    }

    private suspend fun finishLogin(attempt: Long) = withContext(NonCancellable) {
        service.withSessionWrite {
            if (service.loginGeneration == attempt) service.setLoading(false)
        }
    }

    /** Delegates to [OfficialCloudAuthParser.normalizeAuthorizationToken]. */
    private fun normalizeAuthorizationToken(raw: String): String =
        OfficialCloudAuthParser.normalizeAuthorizationToken(raw)
}
