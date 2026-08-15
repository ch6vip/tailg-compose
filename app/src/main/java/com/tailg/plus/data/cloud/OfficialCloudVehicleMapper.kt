package com.tailg.plus.data.cloud

import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.data.model.VehicleProtocol

/**
 * Port of `OfficialCloudVehicleProfileData` / `OfficialCloudVehicleMapper` from
 * `lib/services/official_cloud_vehicle_mapper.dart`.
 *
 * Derives the local garage profile (id = normalized device MAC) from an
 * official vehicle, choosing the QGJ protocol for QGJ-family BLE names.
 */
data class OfficialCloudVehicleProfileData(
    val id: String,
    val name: String,
    val protocol: VehicleProtocol,
)

object OfficialCloudVehicleMapper {

    fun profileFromOfficialVehicle(vehicle: OfficialVehicle): OfficialCloudVehicleProfileData? {
        val id = vehicle.normalizedDeviceMac
        if (id.isEmpty()) return null
        return OfficialCloudVehicleProfileData(
            id = id,
            name = vehicle.displayName,
            protocol = protocolForOfficialVehicle(vehicle),
        )
    }

    private fun protocolForOfficialVehicle(vehicle: OfficialVehicle): VehicleProtocol {
        val name = vehicle.btname.uppercase()
        if (name.startsWith("Q_BASH") ||
            name.startsWith("QGJ") ||
            name.startsWith("Q_")
        ) {
            return VehicleProtocol.QGJ
        }
        return VehicleProtocol.AUTO
    }
}
