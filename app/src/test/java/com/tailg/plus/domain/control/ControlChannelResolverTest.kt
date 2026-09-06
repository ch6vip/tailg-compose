package com.tailg.plus.domain.control

import com.tailg.plus.data.model.OfficialVehicle
import org.junit.Assert.*
import org.junit.Test

class ControlChannelResolverTest {
    private fun state(linkedId: String? = null) = object : ControlCloudState {
        override val signedIn = true
        override val selectedVehicle = OfficialVehicle.fromJson(mapOf(
            "carId" to "selected", "modelType" to 3, "btmac" to "AA:BB:CC:DD:EE:FF",
        ))
        override fun linkedLocalVehicleId(officialVehicleKey: String) = linkedId
    }

    @Test
    fun connectedVehicleMustMatchSelectedVehicleEvenWithoutAnExplicitLink() {
        val wrongVehicle = ControlChannelResolver.resolve(
            state(), bleReady = true, connectedVehicleId = "11:22:33:44:55:66",
            channel = OfficialControlChannel.BLE,
        )
        assertFalse(wrongVehicle.enabled)
        assertTrue(wrongVehicle.disabledReason.contains("不一致"))
        assertTrue(ControlChannelResolver.resolve(
            state(), bleReady = true, connectedVehicleId = "aa:bb:cc:dd:ee:ff",
            channel = OfficialControlChannel.BLE,
        ).enabled)
    }

    @Test
    fun explicitLocalLinkMustMatchTheActiveDevice() {
        assertFalse(ControlChannelResolver.resolve(
            state("11:22:33:44:55:66"), bleReady = true,
            connectedVehicleId = "AA:BB:CC:DD:EE:FF", connectedIdentityMac = "AABBCCDDEEFF",
            channel = OfficialControlChannel.BLE,
        ).enabled)
    }
}
