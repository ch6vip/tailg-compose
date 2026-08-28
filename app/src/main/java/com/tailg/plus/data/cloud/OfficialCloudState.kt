package com.tailg.plus.data.cloud

import androidx.compose.runtime.Immutable
import com.tailg.plus.data.model.OfficialBatteryInfo
import com.tailg.plus.data.model.OfficialBmsInfo
import com.tailg.plus.data.model.OfficialCloudMessage
import com.tailg.plus.data.model.OfficialFenceData
import com.tailg.plus.data.model.OfficialRidePeriod
import com.tailg.plus.data.model.OfficialRideStatistics
import com.tailg.plus.data.model.OfficialTravelDay
import com.tailg.plus.data.model.OfficialTravelPoint
import com.tailg.plus.data.model.OfficialUserProfile
import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.data.model.OfficialVehicleLocation

/**
 * Port of `OfficialCloudResponseCode` from `lib/services/official_cloud_state.dart`.
 */
enum class OfficialCloudResponseCode(val wireCode: String) {
    SUCCESS("200"),
    LEGACY_SUCCESS("0");

    companion object {
        /**
         * Normalize a wire `code` value for string comparison. Moshi's `Any`
         * adapter decodes every JSON number as a `Double`, so the server's
         * integer `0`/`200` arrive as `0.0`/`200.0`; stripping the `.0` keeps
         * the wire-code match working.
         */
        fun normalizeCode(code: Any?): String? =
            code?.toString()?.trim()?.removeSuffix(".0")

        fun parse(code: Any?): OfficialCloudResponseCode? {
            val normalized = normalizeCode(code)
            return entries.firstOrNull { it.wireCode == normalized }
        }

        /** Both wire codes are success; anything else is a business failure. */
        fun isSuccessBody(body: Map<String, Any?>): Boolean = parse(body["code"]) != null
    }
}

/**
 * Port of `OfficialCloudLoginValidator` (phone / SMS code validation).
 */
object OfficialCloudLoginValidator {
    private val phonePattern = Regex("""^\d{11}$""")
    private val smsCodePattern = Regex("""^\d{4,8}$""")
    private val phoneWhitespacePattern = Regex("""\s+""")

    fun compactPhone(value: String): String = phoneWhitespacePattern.replace(value, "")

    fun isValidPhone(value: String): Boolean = phonePattern.matches(value)

    fun isValidSmsCode(value: String): Boolean = smsCodePattern.matches(value)
}

/**
 * Shared user-facing copy for official-cloud auth gates (Dart
 * `OfficialCloudMessages`).
 */
object OfficialCloudMessages {
    const val SIGN_IN_REQUIRED = "请先登录官方账号"
    const val SIGN_IN_AND_SELECT_VEHICLE_REQUIRED = "请先登录官方账号并选择车辆"

    /** Contextual gate used by location sync actions. */
    fun signInRequiredBefore(action: String): String = "请先登录官方账号后再$action"
}

/**
 * Port of `OfficialCloudState` from `lib/services/official_cloud_state.dart`.
 *
 * Immutable snapshot published through the service's `StateFlow`. [copyWith]
 * keeps the Dart sentinel semantics: a parameter omitted (default sentinel)
 * preserves the current value, while an explicitly passed `null` clears it.
 */
@Immutable
data class OfficialCloudState(
    val initialized: Boolean,
    val token: String,
    val phone: String,
    val userId: String,
    val userProfile: OfficialUserProfile?,
    val loading: Boolean,
    val error: String?,
    val vehicles: List<OfficialVehicle>,
    val selectedVehicleKey: String?,
    val localVehicleLinks: Map<String, String>,
    val batteryInfo: OfficialBatteryInfo?,
    val batteryInfoLoading: Boolean,
    val batteryInfoError: String?,
    val bmsInfo: OfficialBmsInfo?,
    val bmsInfoLoading: Boolean,
    val bmsInfoError: String?,
    val vehicleLocation: OfficialVehicleLocation?,
    val vehicleLocationLoading: Boolean,
    val vehicleLocationError: String?,
    val fenceData: OfficialFenceData?,
    val fenceLoading: Boolean,
    val fenceError: String?,
    val travelDays: List<OfficialTravelDay>,
    val travelMonth: String,
    val travelLoading: Boolean,
    val travelError: String?,
    val travelDetails: Map<String, List<OfficialTravelPoint>>,
    val travelDetailLoading: Boolean,
    val travelDetailError: String?,
    val rideStatistics: OfficialRideStatistics?,
    val ridePeriod: OfficialRidePeriod,
    val rideStatisticsLoading: Boolean,
    val rideStatisticsError: String?,
    /** Official control-home "今日骑行" mileage from `app/carTravel/records`. */
    val todayRideMileage: String,
    val vehicleMessages: List<OfficialCloudMessage>,
    val systemMessages: List<OfficialCloudMessage>,
    val messagesLoading: Boolean,
    val messagesError: String?,
) {
    val signedIn: Boolean get() = token.isNotEmpty()

    val selectedVehicle: OfficialVehicle?
        get() {
            if (vehicles.isEmpty()) return null
            val key = selectedVehicleKey
            if (key == null) return vehicles.first()
            for (vehicle in vehicles) {
                if (vehicle.key == key) return vehicle
            }
            return vehicles.first()
        }

    fun linkedLocalVehicleId(officialVehicleKey: String): String? =
        OfficialCloudVehicleLinks.normalize(localVehicleLinks)[officialVehicleKey.trim()]

    fun copyWith(
        initialized: Boolean? = null,
        token: String? = null,
        phone: String? = null,
        userId: String? = null,
        userProfile: OfficialUserProfile? = SENTINEL_PROFILE,
        loading: Boolean? = null,
        error: String? = SENTINEL_STRING,
        vehicles: List<OfficialVehicle>? = null,
        selectedVehicleKey: String? = SENTINEL_STRING,
        localVehicleLinks: Map<String, String>? = null,
        batteryInfo: OfficialBatteryInfo? = SENTINEL_BATTERY,
        batteryInfoLoading: Boolean? = null,
        batteryInfoError: String? = SENTINEL_STRING,
        bmsInfo: OfficialBmsInfo? = SENTINEL_BMS,
        bmsInfoLoading: Boolean? = null,
        bmsInfoError: String? = SENTINEL_STRING,
        vehicleLocation: OfficialVehicleLocation? = SENTINEL_LOCATION,
        vehicleLocationLoading: Boolean? = null,
        vehicleLocationError: String? = SENTINEL_STRING,
        fenceData: OfficialFenceData? = SENTINEL_FENCE,
        fenceLoading: Boolean? = null,
        fenceError: String? = SENTINEL_STRING,
        travelDays: List<OfficialTravelDay>? = null,
        travelMonth: String? = null,
        travelLoading: Boolean? = null,
        travelError: String? = SENTINEL_STRING,
        travelDetails: Map<String, List<OfficialTravelPoint>>? = null,
        travelDetailLoading: Boolean? = null,
        travelDetailError: String? = SENTINEL_STRING,
        rideStatistics: OfficialRideStatistics? = SENTINEL_RIDE,
        ridePeriod: OfficialRidePeriod? = null,
        rideStatisticsLoading: Boolean? = null,
        rideStatisticsError: String? = SENTINEL_STRING,
        todayRideMileage: String? = null,
        vehicleMessages: List<OfficialCloudMessage>? = null,
        systemMessages: List<OfficialCloudMessage>? = null,
        messagesLoading: Boolean? = null,
        messagesError: String? = SENTINEL_STRING,
    ): OfficialCloudState = OfficialCloudState(
        initialized = initialized ?: this.initialized,
        token = token ?: this.token,
        phone = phone ?: this.phone,
        userId = userId ?: this.userId,
        userProfile = if (userProfile === SENTINEL_PROFILE) this.userProfile else userProfile,
        loading = loading ?: this.loading,
        error = if (error === SENTINEL_STRING) this.error else error,
        vehicles = vehicles ?: this.vehicles,
        selectedVehicleKey = if (selectedVehicleKey === SENTINEL_STRING) this.selectedVehicleKey else selectedVehicleKey,
        localVehicleLinks = localVehicleLinks ?: this.localVehicleLinks,
        batteryInfo = if (batteryInfo === SENTINEL_BATTERY) this.batteryInfo else batteryInfo,
        batteryInfoLoading = batteryInfoLoading ?: this.batteryInfoLoading,
        batteryInfoError = if (batteryInfoError === SENTINEL_STRING) this.batteryInfoError else batteryInfoError,
        bmsInfo = if (bmsInfo === SENTINEL_BMS) this.bmsInfo else bmsInfo,
        bmsInfoLoading = bmsInfoLoading ?: this.bmsInfoLoading,
        bmsInfoError = if (bmsInfoError === SENTINEL_STRING) this.bmsInfoError else bmsInfoError,
        vehicleLocation = if (vehicleLocation === SENTINEL_LOCATION) this.vehicleLocation else vehicleLocation,
        vehicleLocationLoading = vehicleLocationLoading ?: this.vehicleLocationLoading,
        vehicleLocationError = if (vehicleLocationError === SENTINEL_STRING) this.vehicleLocationError else vehicleLocationError,
        fenceData = if (fenceData === SENTINEL_FENCE) this.fenceData else fenceData,
        fenceLoading = fenceLoading ?: this.fenceLoading,
        fenceError = if (fenceError === SENTINEL_STRING) this.fenceError else fenceError,
        travelDays = travelDays ?: this.travelDays,
        travelMonth = travelMonth ?: this.travelMonth,
        travelLoading = travelLoading ?: this.travelLoading,
        travelError = if (travelError === SENTINEL_STRING) this.travelError else travelError,
        travelDetails = travelDetails ?: this.travelDetails,
        travelDetailLoading = travelDetailLoading ?: this.travelDetailLoading,
        travelDetailError = if (travelDetailError === SENTINEL_STRING) this.travelDetailError else travelDetailError,
        rideStatistics = if (rideStatistics === SENTINEL_RIDE) this.rideStatistics else rideStatistics,
        ridePeriod = ridePeriod ?: this.ridePeriod,
        rideStatisticsLoading = rideStatisticsLoading ?: this.rideStatisticsLoading,
        rideStatisticsError = if (rideStatisticsError === SENTINEL_STRING) this.rideStatisticsError else rideStatisticsError,
        todayRideMileage = todayRideMileage ?: this.todayRideMileage,
        vehicleMessages = vehicleMessages ?: this.vehicleMessages,
        systemMessages = systemMessages ?: this.systemMessages,
        messagesLoading = messagesLoading ?: this.messagesLoading,
        messagesError = if (messagesError === SENTINEL_STRING) this.messagesError else messagesError,
    )

    companion object {
        fun initial(): OfficialCloudState = OfficialCloudState(
            initialized = false,
            token = "",
            phone = "",
            userId = "",
            userProfile = null,
            loading = false,
            error = null,
            vehicles = emptyList(),
            selectedVehicleKey = null,
            localVehicleLinks = emptyMap(),
            batteryInfo = null,
            batteryInfoLoading = false,
            batteryInfoError = null,
            bmsInfo = null,
            bmsInfoLoading = false,
            bmsInfoError = null,
            vehicleLocation = null,
            vehicleLocationLoading = false,
            vehicleLocationError = null,
            fenceData = null,
            fenceLoading = false,
            fenceError = null,
            travelDays = emptyList(),
            travelMonth = "",
            travelLoading = false,
            travelError = null,
            travelDetails = emptyMap(),
            travelDetailLoading = false,
            travelDetailError = null,
            rideStatistics = null,
            ridePeriod = OfficialRidePeriod.DAY,
            rideStatisticsLoading = false,
            rideStatisticsError = null,
            todayRideMileage = "",
            vehicleMessages = emptyList(),
            systemMessages = emptyList(),
            messagesLoading = false,
            messagesError = null,
        )

        /**
         * Sentinel constants used as default values for nullable [copyWith]
         * parameters so that "parameter omitted" (preserve current value) can
         * be distinguished from "explicitly passed null" (clear the field).
         *
         * Each sentinel is a unique instance compared via `===` (reference
         * equality). They must NOT be created via `Any() as T` — that throws
         * `ClassCastException` at runtime because the JVM `checkcast` for a
         * concrete nullable type (e.g. `String?`) rejects a bare `Any` object.
         * Instead we instantiate the real type; reference equality still
         * distinguishes the sentinel from any caller-supplied value because
         * every `copyWith` call site produces fresh instances.
         *
         * `SENTINEL_STRING` uses `buildString { }` (a non-interned `String`)
         * so that `===` differs from the interned `""` literal.
         */
        private val SENTINEL_STRING: String = buildString { }
        private val SENTINEL_PROFILE: OfficialUserProfile = OfficialUserProfile()
        private val SENTINEL_BATTERY: OfficialBatteryInfo = OfficialBatteryInfo()
        private val SENTINEL_BMS: OfficialBmsInfo = OfficialBmsInfo()
        private val SENTINEL_LOCATION: OfficialVehicleLocation = OfficialVehicleLocation()
        private val SENTINEL_FENCE: OfficialFenceData = OfficialFenceData()
        private val SENTINEL_RIDE: OfficialRideStatistics = OfficialRideStatistics()
    }
}
