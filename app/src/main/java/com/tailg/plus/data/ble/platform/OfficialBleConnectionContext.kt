/**
 * Port of `lib/ble/official_ble_connection_context.dart` (tailg-ble-app)
 * → package `com.tailg.plus.data.ble.platform`.
 *
 * Runtime-only BLE inputs copied from the official vehicle response.
 * Passwords and uid are deliberately not serialised — the official app keeps
 * them in its active vehicle/session object while connecting the selected car.
 *
 * Stack mapping matches the Dart `stackForModelType` exactly (and the official
 * `com.tailg.run.intelligence` vehicle-type routing):
 * - 1 → kks (standard tailg stack)
 * - {3, 10, 14, 401, 928, 1501, 1601, 1701, 2103, 2201} → tlink (8500 series)
 * - {8, 283} → qgj (0xA7 frames)
 * - anything else → unsupported
 */
package com.tailg.plus.data.ble.platform

import com.tailg.plus.data.ble.ModelType
import com.tailg.plus.data.model.OfficialVehicle

/** Port of Dart `enum OfficialBleStack`. */
enum class OfficialBleStack { KKS, TLINK, QGJ, UNSUPPORTED }

/**
 * Port of Dart `class OfficialBleConnectionContext`.
 *
 * [selectedPassword] mirrors the Dart getter: shared vehicles use the first
 * child password, otherwise the main password. [hasTLinkCredentials] /
 * [hasQgjCredentials] gate the LOGIN handshake (see `ConnectionManager`).
 */
data class OfficialBleConnectionContext(
  val stack: OfficialBleStack,
  val modelType: Int,
  val cipherModel: ModelType?,
  val identityMac: String,
  val advertisedName: String,
  val userId: String,
  val mainPassword: Int?,
  val childPasswords: List<Int>,
  val shared: Boolean,
) {

  /** Port of Dart `hasTLinkCredentials`. */
  val hasTLinkCredentials: Boolean
    get() = stack == OfficialBleStack.TLINK && userIdValue != null && selectedPassword != null

  /** Port of Dart `hasQgjCredentials`. */
  val hasQgjCredentials: Boolean
    get() = stack == OfficialBleStack.QGJ && userIdValue != null && selectedPassword != null

  /** Port of Dart `userIdValue` = `int.tryParse(userId)`. */
  val userIdValue: Int?
    get() = userId.toIntOrNull()

  /** Port of Dart `selectedPassword` (shared → first child password). */
  val selectedPassword: Int?
    get() = if (shared) childPasswords.firstOrNull() else mainPassword

  /** Port of Dart `targetMacCompact` — hex chars only, upper-case. */
  val targetMacCompact: String
    get() = identityMac.filter { it in HEX_CHARS }.uppercase()

  companion object {
    private const val HEX_CHARS = "0123456789abcdefABCDEF"

    /** Port of Dart `OfficialBleConnectionContext.fromVehicle`. */
    fun fromVehicle(vehicle: OfficialVehicle, userId: String): OfficialBleConnectionContext {
      val modelType = vehicle.modelType ?: -1
      val stack = stackForModelType(modelType)
      return OfficialBleConnectionContext(
        stack = stack,
        modelType = modelType,
        cipherModel = cipherModelForModelType(modelType),
        identityMac = vehicle.bleIdentityMac,
        advertisedName = vehicle.btname.trim(),
        userId = userId.trim(),
        mainPassword = vehicle.mainBlePassword,
        childPasswords = vehicle.childBlePasswords,
        shared = vehicle.shareCarFlag,
      )
    }

    /** Port of Dart `stackForModelType`. */
    fun stackForModelType(modelType: Int): OfficialBleStack {
      if (modelType == 1) return OfficialBleStack.KKS
      if (modelType in TLINK_MODEL_TYPES) return OfficialBleStack.TLINK
      if (modelType in QGJ_MODEL_TYPES) return OfficialBleStack.QGJ
      return OfficialBleStack.UNSUPPORTED
    }

    /**
     * Port of Dart `cipherModelForModelType` — the official TLink
     * implementation uses a fixed key per model family.
     */
    fun cipherModelForModelType(modelType: Int): ModelType? = when (modelType) {
      1 -> ModelType.KKS
      3, 401 -> ModelType.BB
      10, 14, 928 -> ModelType.JW
      1501 -> ModelType.JD
      1601 -> ModelType.AX
      1701 -> ModelType.HJ
      2103 -> ModelType.XL
      2201 -> ModelType.YY
      else -> null
    }

    private val TLINK_MODEL_TYPES = setOf(3, 10, 14, 401, 928, 1501, 1601, 1701, 2103, 2201)
    private val QGJ_MODEL_TYPES = setOf(8, 283)
  }
}
