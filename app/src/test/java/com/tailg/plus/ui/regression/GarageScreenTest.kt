package com.tailg.plus.ui.regression

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import com.tailg.plus.R
import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.data.cloud.OfficialCloudApiResponse
import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.data.mqtt.OfficialMqttService
import com.tailg.plus.ui.screens.GarageScreen
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GarageScreenTest : ComposeRegressionTest() {
    private val current = OfficialVehicle(carId = "current-car", carNickName = "当前车辆")
    private val target = OfficialVehicle(carId = "target-car", carNickName = "待切换车辆")
    private val mqtt = mockk<OfficialMqttService>()
    private val ble = mockk<ConnectionManager>()
    private val actions = mutableListOf<String>()

    @Before
    fun setUp() {
        environment.signIn(current, target)
        coEvery { mqtt.disconnect() } coAnswers { actions += "mqtt" }
        coEvery { ble.disconnect() } coAnswers { actions += "ble" }
        environment.api.respond = { request ->
            when (request.path) {
                "app/userCarPage" -> garageResponse(target)
                "app/centralControl/changeUsingCar" -> {
                    actions += "switch:${request.body?.get("carId")}"
                    cloudResponse()
                }
                else -> environment.vehicleReadResponse(request)
            }
        }
    }

    private fun openGarage() {
        render {
            GarageScreen(
                onBack = {}, onNavigate = {}, cloudService = environment.cloud,
                mqttService = mqtt, connectionManager = ble,
            )
        }
        compose.waitForIdle()
    }

    private fun chooseTarget() {
        compose.onNode(hasText(target.displayName) and hasClickAction()).performClick()
        compose.onNodeWithText(string(R.string.garage_switch_title)).assertIsDisplayed()
    }

    @Test
    fun cancellingConfirmationLeavesVehicleAndConnectionsUntouched() {
        openGarage()
        chooseTarget()
        assertTrue(actions.isEmpty())

        button(R.string.common_cancel).performClick()

        compose.onNodeWithText(string(R.string.garage_switch_title)).assertDoesNotExist()
        assertTrue(actions.isEmpty())
        assertEquals(current.key, environment.cloud.currentState.selectedVehicleKey)
        assertEquals(listOf("app/userCarPage"), environment.api.requests.map { it.path })
    }

    @Test
    fun confirmingDisconnectsBothChannelsBeforeSwitchingAndUpdatesSelection() {
        openGarage()
        chooseTarget()
        assertTrue(actions.isEmpty())

        button(R.string.common_switch).performClick()

        compose.onNodeWithText(string(R.string.garage_in_use)).assertIsDisplayed()
        assertEquals(listOf("mqtt", "ble", "switch:${target.carId}"), actions)
        assertEquals(target.key, environment.cloud.currentState.selectedVehicleKey)
        val request = environment.api.requests.single { it.path == "app/centralControl/changeUsingCar" }
        assertEquals("POST", request.method)
        assertEquals("test-session", request.token)
        coVerify(atLeast = 1) { environment.storage.saveSelectedVehicleKey(target.key) }
    }

    @Test
    fun leavingDuringMqttDisconnectDoesNotContinueWithBleOrSwitch() {
        val disconnect = CompletableDeferred<Unit>()
        coEvery { mqtt.disconnect() } coAnswers {
            actions += "mqtt"
            disconnect.await()
        }
        openGarage()
        chooseTarget()
        button(R.string.common_switch).performClick()
        compose.waitForIdle()
        assertEquals(listOf("mqtt"), actions)

        leaveScreen()

        assertEquals(listOf("mqtt"), actions)
        assertEquals(current.key, environment.cloud.currentState.selectedVehicleKey)
        assertTrue(environment.api.requests.none { it.path == "app/centralControl/changeUsingCar" })
    }

    @Test
    fun leavingDuringBleDisconnectDoesNotSendSwitchRequest() {
        val disconnect = CompletableDeferred<Unit>()
        coEvery { ble.disconnect() } coAnswers {
            actions += "ble"
            disconnect.await()
        }
        openGarage()
        chooseTarget()
        button(R.string.common_switch).performClick()
        compose.waitForIdle()
        assertEquals(listOf("mqtt", "ble"), actions)

        leaveScreen()

        assertEquals(listOf("mqtt", "ble"), actions)
        assertEquals(current.key, environment.cloud.currentState.selectedVehicleKey)
        assertTrue(environment.api.requests.none { it.path == "app/centralControl/changeUsingCar" })
    }

    @Test
    fun leavingDuringNonCancellableTeardownDoesNotSendSwitchRequest() {
        val disconnected = CompletableDeferred<Unit>()
        coEvery { ble.disconnect() } coAnswers {
            actions += "ble"
            withContext(NonCancellable) { disconnected.await() }
        }
        try {
            openGarage()
            chooseTarget()
            button(R.string.common_switch).performClick()
            compose.waitForIdle()
            assertEquals(listOf("mqtt", "ble"), actions)

            leaveScreen()
            compose.runOnIdle { disconnected.complete(Unit) }
            compose.waitForIdle()

            assertEquals(listOf("mqtt", "ble"), actions)
            assertEquals(current.key, environment.cloud.currentState.selectedVehicleKey)
        } finally {
            disconnected.complete(Unit)
        }
    }

    @Test
    fun olderSearchResponseCannotReplaceNewerResults() {
        val older = CompletableDeferred<OfficialCloudApiResponse>()
        val newer = CompletableDeferred<OfficialCloudApiResponse>()
        val olderVehicle = OfficialVehicle(carId = "old-result", carNickName = "旧查询车辆")
        val newerVehicle = OfficialVehicle(carId = "new-result", carNickName = "新查询车辆")
        scriptSearch(older, newer)
        openGarage()
        search("old")
        search("new")

        compose.runOnIdle { newer.complete(garageResponse(newerVehicle)) }
        compose.onNodeWithText(newerVehicle.displayName).assertIsDisplayed()
        compose.runOnIdle { older.complete(garageResponse(olderVehicle)) }

        compose.onNodeWithText(newerVehicle.displayName).assertIsDisplayed()
        compose.onNodeWithText(olderVehicle.displayName).assertDoesNotExist()
        assertEquals(listOf(null, "old", "new"), environment.api.requests.map { it.body?.get("frame") })
    }

    @Test
    fun olderSearchFailureCannotReplaceNewerEmptyState() {
        val older = CompletableDeferred<OfficialCloudApiResponse>()
        val newer = CompletableDeferred<OfficialCloudApiResponse>()
        scriptSearch(older, newer)
        openGarage()
        search("old")
        search("new")
        compose.runOnIdle { newer.complete(garageResponse()) }
        compose.onNodeWithText(string(R.string.garage_not_found)).assertIsDisplayed()

        compose.runOnIdle {
            older.complete(OfficialCloudApiResponse(200, emptyMap(), mapOf("code" to 500, "msg" to "过期查询失败")))
        }

        compose.onNodeWithText(string(R.string.garage_not_found)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.garage_loading_failed)).assertDoesNotExist()
        compose.onNodeWithText("过期查询失败").assertDoesNotExist()
    }

    @Test
    fun clearingSearchIgnoresTheOutstandingFilteredResponse() {
        val older = CompletableDeferred<OfficialCloudApiResponse>()
        scriptSearch(older, CompletableDeferred())
        openGarage()
        search("old")
        compose.onNodeWithContentDescription(string(R.string.garage_clear_search)).performClick()
        compose.onNodeWithText(target.displayName).assertIsDisplayed()

        compose.runOnIdle { older.complete(garageResponse()) }

        compose.onNodeWithText(target.displayName).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.garage_not_found)).assertDoesNotExist()
        assertEquals(listOf(null, "old", null), environment.api.requests.map { it.body?.get("frame") })
    }

    @Test
    fun leavingGarageCancelsOutstandingSearch() {
        val older = CompletableDeferred<OfficialCloudApiResponse>()
        scriptSearch(older, CompletableDeferred())
        openGarage()
        search("old")

        leaveScreen()

        assertEquals(listOf("old"), environment.api.cancelledRequests.map { it.body?.get("frame") })
        assertEquals(current.key, environment.cloud.currentState.selectedVehicleKey)
        assertTrue(actions.isEmpty())
    }

    private fun search(query: String) {
        compose.onNode(hasSetTextAction()).performTextReplacement(query)
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitForIdle()
    }

    private fun scriptSearch(
        older: CompletableDeferred<OfficialCloudApiResponse>,
        newer: CompletableDeferred<OfficialCloudApiResponse>,
    ) {
        environment.api.respond = { request ->
            if (request.path != "app/userCarPage") environment.api.unexpected(request)
            when (request.body?.get("frame")) {
                "old" -> older.await()
                "new" -> newer.await()
                else -> garageResponse(target)
            }
        }
    }

    private fun garageResponse(vararg vehicles: OfficialVehicle) = cloudResponse(mapOf(
        "pageData" to vehicles.map { it.toJson() },
        "nowPageIndex" to 1, "pageSize" to 5, "total" to vehicles.size, "hasNext" to false,
    ))
}
