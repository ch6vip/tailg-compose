package com.tailg.plus.ui.regression

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.tailg.plus.R
import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.data.model.ControlCommandActivityStatus
import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.data.mqtt.OfficialMqttService
import com.tailg.plus.data.network.NetworkAvailabilityService
import com.tailg.plus.data.store.VehicleStore
import com.tailg.plus.service.LocationService
import com.tailg.plus.ui.screens.ControlScreen
import com.tailg.plus.ui.screens.ControlViewModel
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.Shadows.shadowOf

class ControlScreenTest : ComposeRegressionTest() {
    private val vehicle = OfficialVehicle(
        carId = "vehicle-a", imei = "860000000000001", carNickName = "测试控车车辆",
        modelType = 8, isGps = 1, acc = 0, defenceStatus = 0, online = true,
    )
    private val publications = mutableListOf<Pair<String, String>>()
    private lateinit var mqtt: OfficialMqttService
    private lateinit var ble: ConnectionManager
    private lateinit var viewModel: ControlViewModel
    private var previousLiveConnect = true

    @Before
    fun setUp() {
        previousLiveConnect = OfficialMqttService.liveConnectEnabled
        OfficialMqttService.liveConnectEnabled = false
        environment.signIn(vehicle)
        environment.api.respond = environment::vehicleReadResponse
        val context = ApplicationProvider.getApplicationContext<Context>()
        ble = ConnectionManager(context, environment.log, externalScope = environment.scope)
        mqtt = OfficialMqttService(
            log = environment.log,
            defaultCloud = environment.cloud,
            scope = environment.scope,
            clientFactory = { _, _ -> error("UI tests must never create a live MQTT client") },
        )
        mqtt.publishCommandOverride = { target, _, command -> publications += target.key to command }
        val store = VehicleStore(context, environment.log)
        val network = mockk<NetworkAvailabilityService> {
            every { changes } returns MutableStateFlow(true)
        }
        viewModel = retain(ControlViewModel(
            environment.cloud, ble, mqtt, store, environment.log, network,
            mockk<LocationService>(relaxed = true),
        ))
        render {
            ControlScreen(
                cloudService = environment.cloud, connectionManager = ble, mqttService = mqtt,
                vehicleStore = store, onBack = {}, onNavigate = {}, viewModel = viewModel,
            )
        }
        // Complete entry animations/refreshes and move beyond the initial command debounce.
        advanceTime(1_500)
    }

    @After
    fun closeTransports() {
        try {
            leaveScreen()
            compose.runOnIdle {
                viewModels.clear()
                runBlocking {
                    if (::mqtt.isInitialized) mqtt.dispose()
                    if (::ble.isInitialized) ble.dispose()
                }
            }
        } finally {
            OfficialMqttService.liveConnectEnabled = previousLiveConnect
        }
    }

    @Test
    fun publishStaysPendingUntilTheSelectedVehicleAcknowledges() {
        sendFind()

        assertEquals(listOf(vehicle.key to "search"), publications)
        assertPending()
        compose.onNodeWithText(string(R.string.control_success_find)).assertDoesNotExist()

        compose.runOnIdle {
            mqtt.handleStatusPayload("""{"imei":"860000000000002","ACC":"0"}""")
        }
        compose.waitForIdle()
        assertPending()

        compose.runOnIdle {
            mqtt.handleStatusPayload("""{"imei":"860000000000001","ACC":"0"}""")
        }
        compose.waitForIdle()

        assertFalse(viewModel.uiState.value.busy)
        assertEquals(ControlCommandActivityStatus.SUCCEEDED, viewModel.commandLog.entries.single().status)
        compose.onNodeWithText(string(R.string.control_success_find)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun lockNeedsTheExpectedStatusAndUpdatesTheControlButton() {
        compose.onNodeWithContentDescription(string(R.string.control_grid_arm)).performScrollTo().performClick()
        advanceTime(600)
        assertEquals(listOf(vehicle.key to "lock"), publications)
        assertPending()

        compose.runOnIdle {
            mqtt.handleStatusPayload("""{"imei":"860000000000001","defenceStatus":"0"}""")
        }
        compose.waitForIdle()
        assertPending()

        compose.runOnIdle {
            mqtt.handleStatusPayload("""{"imei":"860000000000001","defenceStatus":"1"}""")
        }
        compose.waitForIdle()

        assertEquals(true, environment.cloud.currentState.selectedVehicle?.isLocked)
        assertFalse(viewModel.uiState.value.busy)
        assertEquals(ControlCommandActivityStatus.SUCCEEDED, viewModel.commandLog.entries.single().status)
        compose.onNodeWithContentDescription(string(R.string.control_grid_disarm)).assertIsDisplayed()
    }

    @Test
    fun disconnectClearsPendingButCannotConfirmTheCommand() {
        sendFind()
        assertPending()

        compose.runOnIdle { runBlocking { mqtt.disconnect() } }
        compose.waitForIdle()

        assertNull(mqtt.pendingCommandApiName)
        assertNull(mqtt.acknowledgedCommandApiName)
        assertPending()
        advanceTime(9_000)

        assertFalse(viewModel.uiState.value.busy)
        val activity = viewModel.commandLog.entries.single()
        assertEquals(ControlCommandActivityStatus.FAILED, activity.status)
        compose.onNodeWithText(activity.title).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.control_success_find)).assertDoesNotExist()
        assertEquals(listOf(vehicle.key to "search"), publications)
    }

    @Test
    fun leavingCompositionKeepsConfirmationAliveAndReturningShowsItsResult() {
        sendFind()
        leaveScreen()
        assertPending()

        compose.runOnIdle {
            mqtt.handleStatusPayload("""{"imei":"860000000000001","ACC":"0"}""")
        }
        compose.waitForIdle()

        assertFalse(viewModel.uiState.value.busy)
        assertEquals(ControlCommandActivityStatus.SUCCEEDED, viewModel.commandLog.entries.single().status)
        showScreen()
        compose.onNodeWithText(string(R.string.control_success_find)).performScrollTo().assertIsDisplayed()
        assertEquals(1, publications.size)
    }

    @Test
    fun clearingViewModelCancelsConfirmationAndReleasesBusy() {
        sendFind()
        leaveScreen()
        assertPending()

        compose.runOnIdle { viewModels.clear() }
        compose.waitForIdle()

        assertFalse(viewModel.uiState.value.busy)
        assertNull(viewModel.uiState.value.activeCommand)
        assertEquals(ControlCommandActivityStatus.CANCELLED, viewModel.commandLog.entries.single().status)
        assertEquals(1, publications.size)
    }

    @Test
    fun changingVehicleBeforeDelayedSendCannotRetargetTheCommand() {
        compose.onNodeWithContentDescription(string(R.string.control_card_find)).performScrollTo().performClick()
        compose.runOnIdle {
            environment.signIn(vehicle.copy(carId = "vehicle-b", imei = "860000000000002"))
        }
        advanceTime(600)

        assertTrue(publications.isEmpty())
        assertEquals("vehicle-b", environment.cloud.currentState.selectedVehicleKey)
        assertFalse(viewModel.uiState.value.busy)
        assertEquals(ControlCommandActivityStatus.CANCELLED, viewModel.commandLog.entries.single().status)
    }

    @Test
    fun changingAccountBeforeDelayedSendCancelsTheCommand() {
        compose.onNodeWithContentDescription(string(R.string.control_card_find)).performScrollTo().performClick()
        compose.runOnIdle {
            environment.cloud.setStateForTest(environment.cloud.currentState.copyWith(token = "new-session"))
        }
        advanceTime(600)

        assertTrue(publications.isEmpty())
        assertEquals("new-session", environment.cloud.currentState.token)
        assertFalse(viewModel.uiState.value.busy)
        assertEquals(ControlCommandActivityStatus.CANCELLED, viewModel.commandLog.entries.single().status)
    }

    private fun sendFind() {
        compose.onNodeWithContentDescription(string(R.string.control_card_find)).performScrollTo().performClick()
        advanceTime(600)
        assertEquals(listOf(vehicle.key to "search"), publications)
    }

    private fun assertPending() {
        assertTrue(viewModel.uiState.value.busy)
        assertEquals(ControlCommandActivityStatus.PENDING, viewModel.commandLog.entries.single().status)
    }

    /** Compose effects and Android Main/ViewModel delays have separate clocks under Robolectric. */
    private fun advanceTime(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
        compose.waitForIdle()
    }
}
