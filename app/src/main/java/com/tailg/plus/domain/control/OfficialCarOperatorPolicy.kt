package com.tailg.plus.domain.control

import com.tailg.plus.data.model.CommandCode
import com.tailg.plus.data.model.OfficialVehicle

/**
 * Port of `lib/services/official_car_operator_policy.dart`.
 *
 * Mirrors the `ControlFragment.start()` setCarOperator branches:
 * - KKS (1) / YJ (2) always track the operator flag on power on/off.
 * - Other model families only set "1" on powerOn when the vehicle is a
 *   shared car (`shareCarFlag`), matching `ControlFragment`'s gated
 *   `mViewModel.setCarOperator(carId, "1")` calls.
 */
data class OfficialCarOperatorUpdate(
  val carId: String,
  val operatorFlag: String,
)

object OfficialCarOperatorPolicy {
  private val alwaysTrackedModelTypes: Set<Int> = setOf(1, 2)
  private val sharedPowerOnModelTypes: Set<Int> =
    setOf(3, 8, 10, 14, 283, 401, 928, 2103, 2201)

  fun updateFor(
    command: CommandCode,
    vehicle: OfficialVehicle,
  ): OfficialCarOperatorUpdate? {
    val carId = vehicle.carId.trim()
    if (carId.isEmpty()) return null

    val modelType = vehicle.modelType
    if (alwaysTrackedModelTypes.contains(modelType)) {
      return when (command) {
        CommandCode.POWER_ON -> OfficialCarOperatorUpdate(
          carId = carId,
          operatorFlag = "1",
        )
        CommandCode.POWER_OFF -> OfficialCarOperatorUpdate(
          carId = carId,
          operatorFlag = "0",
        )
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
