package com.tailg.plus.ui.regression

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tailg.plus.data.cloud.OfficialCloudApiClientInterface
import com.tailg.plus.data.cloud.OfficialCloudApiConfig
import com.tailg.plus.data.cloud.OfficialCloudApiResponse
import com.tailg.plus.data.cloud.OfficialCloudRequestSummary
import com.tailg.plus.data.cloud.OfficialCloudRetryPolicy
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.cloud.OfficialCloudStorage
import com.tailg.plus.data.cloud.OfficialCloudState
import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.log.LogService
import com.tailg.plus.util.ClipboardText
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Real session logic with an in-memory HTTP boundary; no production client is constructed. */
internal class UiTestEnvironment : AutoCloseable {
    val api = ScriptedCloudApi()
    val storage = mockk<OfficialCloudStorage>(relaxed = true)
    val log = LogService()
    val clipboard = ClipboardText(ApplicationProvider.getApplicationContext<Context>())
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val cloud = OfficialCloudService(
        storage = storage,
        apiClient = api,
        vehicleStore = mockk(relaxed = true),
        log = log,
        scope = scope,
    )

    fun signIn(vararg vehicles: OfficialVehicle) {
        cloud.setStateForTest(OfficialCloudState.initial().copyWith(
            initialized = true,
            token = "test-session",
            userId = "test-user",
            vehicles = vehicles.toList(),
            selectedVehicleKey = vehicles.firstOrNull()?.key,
        ))
    }

    /** Neutral responses for the screen's ancillary reads; command endpoints must be scripted. */
    fun vehicleReadResponse(request: ScriptedCloudApi.Request): OfficialCloudApiResponse = when (request.path) {
        "app/centralControl/carStatus" -> cloudResponse(cloud.currentState.vehicles.map { it.toJson() })
        "app/msg/pageOfCarMsg", "app/msg/pageOfSysMsg",
        "app/mine/batteryInfo", "app/mine/bmsBatteryInfo",
        "app/appRiding/getRidingDetail", "app/car/extend/getByCarId",
        "app/device/getFenceData" -> cloudResponse()
        else -> api.unexpected(request)
    }

    override fun close() = cloud.dispose()
}

internal class ScriptedCloudApi : OfficialCloudApiClientInterface {
    data class Request(
        val path: String,
        val method: String,
        val token: String?,
        val body: Map<String, Any?>?,
    )

    override val config = OfficialCloudApiConfig()
    override val lastRequest: OfficialCloudRequestSummary? = null
    val requests = mutableListOf<Request>()
    val cancelledRequests = mutableListOf<Request>()
    val unexpectedRequests = mutableListOf<Request>()
    var respond: suspend (Request) -> OfficialCloudApiResponse = { unexpected(it) }

    fun unexpected(request: Request): Nothing {
        unexpectedRequests += request
        error("Unexpected cloud request: ${request.method} ${request.path}")
    }

    override suspend fun request(
        path: String,
        method: String,
        token: String?,
        body: Map<String, Any?>?,
        retryPolicy: OfficialCloudRetryPolicy,
    ): OfficialCloudApiResponse {
        val request = Request(path, method, token, body)
        requests += request
        return try {
            respond(request)
        } catch (e: CancellationException) {
            cancelledRequests += request
            throw e
        }
    }

    override fun dispose() = Unit
}

internal fun cloudResponse(data: Any = emptyMap<String, Any>()): OfficialCloudApiResponse =
    OfficialCloudApiResponse(200, emptyMap(), mapOf("code" to 200, "data" to data))
