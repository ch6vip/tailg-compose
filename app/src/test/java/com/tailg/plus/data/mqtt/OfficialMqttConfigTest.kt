package com.tailg.plus.data.mqtt

import com.tailg.plus.data.model.OfficialVehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OfficialMqttConfigTest {

  @Test
  fun kksAndYunjiaUseTheOfficialPlaintextBroker() {
    assertEquals(
      OfficialMqttConfig.KKS_YJ_HOST_URI,
      OfficialMqttConfig.brokerUriFor(OfficialVehicle(modelType = 1)),
    )
    assertEquals(
      OfficialMqttConfig.KKS_YJ_HOST_URI,
      OfficialMqttConfig.brokerUriFor(OfficialVehicle(modelType = 2)),
    )
    val parsed = OfficialMqttConfig.parseBrokerUri(OfficialMqttConfig.KKS_YJ_HOST_URI)
    assertEquals(MqttTransportSecurity.PLAINTEXT, parsed.security)
    assertFalse(parsed.secure)
    assertEquals("www.tailgdd.com", parsed.host)
    assertEquals(1883, parsed.port)
  }

  @Test
  fun nonKksVehicleUsesItsTlsEndpointWhenPresent() {
    val broker = OfficialMqttConfig.brokerUriFor(
      OfficialVehicle(modelType = 8, mqHost = "mqtt.example.test", mqPort = "7443"),
    )
    assertEquals("ssl://mqtt.example.test:7443", broker)
    val parsed = OfficialMqttConfig.parseBrokerUri(broker)
    assertEquals(MqttTransportSecurity.TLS, parsed.security)
    assertTrue(parsed.secure)
    assertEquals(7443, parsed.port)
  }

  @Test
  fun parserUsesSchemeDefaultsOnlyWhenPortIsMissing() {
    assertEquals(1883, OfficialMqttConfig.parseBrokerUri("tcp://broker.test").port)
    assertEquals(8883, OfficialMqttConfig.parseBrokerUri("ssl://broker.test").port)
    assertEquals(1883, OfficialMqttConfig.parseBrokerUri("ws://broker.test").port)
    assertEquals(8883, OfficialMqttConfig.parseBrokerUri("wss://broker.test").port)
  }

  @Test
  fun parserRejectsUnsupportedOrMalformedEndpoints() {
    assertIllegalArgument { OfficialMqttConfig.parseBrokerUri("") }
    assertIllegalArgument { OfficialMqttConfig.parseBrokerUri("http://broker.test:80") }
    assertIllegalArgument { OfficialMqttConfig.parseBrokerUri("ssl://:8883") }
    assertIllegalArgument { OfficialMqttConfig.parseBrokerUri("ssl://broker.test:not-a-port") }
    assertIllegalArgument { OfficialMqttConfig.parseBrokerUri("tcp://broker.test:0") }
    assertIllegalArgument { OfficialMqttConfig.parseBrokerUri("tcp://broker.test:65536") }
    assertIllegalArgument { OfficialMqttConfig.parseBrokerUri("tcp://broker.test/path") }
  }

  private fun assertIllegalArgument(block: () -> Unit) {
    try {
      block()
      fail("Expected IllegalArgumentException")
    } catch (_: IllegalArgumentException) {
    }
  }
}
