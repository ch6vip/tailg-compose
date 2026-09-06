package com.tailg.plus.data.mqtt

import com.tailg.plus.data.cloud.OfficialCloudApiException
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.cloud.OfficialCloudState
import com.tailg.plus.data.model.OfficialVehicle
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Exercises the connection lifecycle using injected Paho mocks; no sockets are opened. */
class OfficialMqttConnectionTest {
  @Before
  fun enableMockedConnections() {
    OfficialMqttService.liveConnectEnabled = true
  }

  private class Fixture(scope: CoroutineScope) {
    private val vehicle = OfficialVehicle(
      carId = "car", imei = "860000000000001", modelType = 8,
      mqUsername = "mqtt-user", mqPassword = "mqtt-password",
    )
    val states = MutableStateFlow(OfficialCloudState.initial().copyWith(
      token = "session", userId = "user", vehicles = listOf(vehicle), selectedVehicleKey = vehicle.key,
    ))
    val clients = mutableListOf<MqttAsyncClient>()
    val created = Channel<Unit>(Channel.UNLIMITED)
    var duringConnect: () -> Unit = {}
    val cloud = mockk<OfficialCloudService> {
      every { currentState } answers { states.value }
      every { stateFlow } returns states
    }
    val mqtt = OfficialMqttService(defaultCloud = cloud, scope = scope, clientFactory = { _, _ ->
      val token = mockk<IMqttToken>(relaxed = true)
      every { token.waitForCompletion(any<Long>()) } answers { duringConnect() }
      mockk<MqttAsyncClient>(relaxed = true) {
        every { isConnected } returns true
        every { connect(any<MqttConnectOptions>()) } returns token
        every { subscribe(any<String>(), any<Int>()) } returns mockk(relaxed = true)
      }.also {
        clients.add(it)
        created.trySend(Unit)
      }
    })

    suspend fun connect() {
      mqtt.ensureConnected(checkNotNull(states.value.selectedVehicle), states.value.userId)
    }
  }

  @Test
  fun unrelatedCloudUpdatesReuseTheExistingConnection() = runTest {
    val fixture = Fixture(backgroundScope)
    try {
      fixture.connect()
      fixture.states.value = fixture.states.value.copyWith(batteryInfoLoading = true)
      fixture.connect()

      assertEquals(1, fixture.clients.size)
      assertTrue(fixture.mqtt.isConnected)
    } finally {
      fixture.mqtt.dispose()
    }
  }

  @Test
  fun accountTokenBrokerAndCredentialsChangesReplaceTheConnection() = runTest {
    val fixture = Fixture(backgroundScope)
    try {
      fixture.connect()
      val changes: List<(OfficialCloudState) -> OfficialCloudState> = listOf(
        { it.copyWith(userId = "another-user") },
        { it.copyWith(token = "another-session") },
        { it.copyWith(vehicles = listOf(checkNotNull(it.selectedVehicle).copy(mqPassword = "rotated-password"))) },
        { it.copyWith(vehicles = listOf(checkNotNull(it.selectedVehicle).copy(mqUsername = "rotated-user"))) },
        { it.copyWith(vehicles = listOf(checkNotNull(it.selectedVehicle).copy(mqHost = "mqtt.example.test", mqPort = "8883"))) },
      )
      for ((index, change) in changes.withIndex()) {
        fixture.states.value = change(fixture.states.value)
        fixture.connect()

        assertEquals(index + 2, fixture.clients.size)
        verify(exactly = 1) { fixture.clients[index].close() }
      }
    } finally {
      fixture.mqtt.dispose()
    }
  }

  @Test
  fun preconnectDoesNotReuseTheSameImeiWithChangedCredentials() = runTest {
    val fixture = Fixture(backgroundScope)
    try {
      fixture.connect()
      val updated = checkNotNull(fixture.states.value.selectedVehicle).copy(mqPassword = "rotated")
      fixture.states.value = fixture.states.value.copyWith(vehicles = listOf(updated))

      fixture.mqtt.preconnect(updated, fixture.states.value.userId)

      assertEquals(2, fixture.clients.size)
      verify(exactly = 1) { fixture.clients.first().close() }
    } finally {
      fixture.mqtt.dispose()
    }
  }

  @Test
  fun sessionChangedDuringConnectionClosesTheUninstalledClient() = runTest {
    val fixture = Fixture(backgroundScope)
    fixture.duringConnect = {
      fixture.states.value = fixture.states.value.copyWith(token = "new-session")
    }
    try {
      val failure = runCatching { fixture.connect() }.exceptionOrNull()

      assertTrue(failure is OfficialCloudApiException)
      assertFalse(fixture.mqtt.isConnected)
      assertEquals(OfficialMqttLinkState.DISCONNECTED, fixture.mqtt.linkState.value)
      verify(exactly = 1) { fixture.clients.single().close() }
      verify(exactly = 0) { fixture.clients.single().subscribe(any<String>(), any<Int>()) }
    } finally {
      fixture.mqtt.dispose()
    }
  }

  @Test
  fun cloudBindingReconnectsWhenCredentialsChangeForTheSameVehicle() = runTest {
    val fixture = Fixture(backgroundScope)
    try {
      fixture.mqtt.attachToCloud(fixture.cloud)
      fixture.created.receive()
      fixture.mqtt.linkState.first { it == OfficialMqttLinkState.CONNECTED }
      val updated = checkNotNull(fixture.states.value.selectedVehicle).copy(mqPassword = "rotated")
      fixture.states.value = fixture.states.value.copyWith(vehicles = listOf(updated))
      fixture.created.receive()
      fixture.mqtt.linkState.first { it == OfficialMqttLinkState.CONNECTED }

      assertEquals(2, fixture.clients.size)
      verify(exactly = 1) { fixture.clients.first().close() }
    } finally {
      fixture.mqtt.dispose()
    }
  }
}
