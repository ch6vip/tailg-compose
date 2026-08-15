package com.tailg.plus.data.cloud

import com.tailg.plus.data.model.CommandCode
import com.tailg.plus.data.model.OfficialVehicle

/**
 * Port of `OfficialCarOperatorUpdate` / `OfficialCarOperatorPolicy` from
 * `lib/services/official_car_operator_policy.dart`.
 *
 * Extra port (not an `official_cloud_*` file) required by the cloud facade's
 * `syncCarOperatorAfterCommand`: mirrors the `ControlFragment.start()`
 * setCarOperator branches — KKS/YJ (1/2) always track power on/off; shared
 * vehicles on the GPS/cloud model family only track power-on.
 */
data class OfficialCarOperatorUpdate(
    val carId: String,
    val operatorFlag: String,
)

object OfficialCarOperatorPolicy {

    private val alwaysTrackedModelTypes = setOf(1, 2)
    private val sharedPowerOnModelTypes = setOf(3, 8, 10, 14, 283, 401, 928, 2103, 2201)

    fun updateFor(command: CommandCode, vehicle: OfficialVehicle): OfficialCarOperatorUpdate? {
        val carId = vehicle.carId.trim()
        if (carId.isEmpty()) return null

        val modelType = vehicle.modelType
        if (alwaysTrackedModelTypes.contains(modelType)) {
            return when (command) {
                CommandCode.POWER_ON -> OfficialCarOperatorUpdate(carId = carId, operatorFlag = "1")
                CommandCode.POWER_OFF -> OfficialCarOperatorUpdate(carId = carId, operatorFlag = "0")
                else -> null
            }
        }

        if (command == CommandCode.POWER_ON &&
            vehicle.shareCarFlag &&
            sharedPowerOnModelTypes.contains(modelType)
        ) {
            return OfficialCarOperatorUpdate(carId = carId, operatorFlag = "1")
        }
        return null
    }
}
