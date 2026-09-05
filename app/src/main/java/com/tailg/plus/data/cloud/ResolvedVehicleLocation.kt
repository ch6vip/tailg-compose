package com.tailg.plus.data.cloud

import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.data.model.OfficialVehicleLocation
import com.tailg.plus.data.model.VehicleLocation
import com.tailg.plus.data.model.VehicleProfile
import com.tailg.plus.data.model.formatCoordinateText
import com.tailg.plus.data.model.isZeroCoordinate
import com.tailg.plus.util.formatDateMinuteText
import java.time.ZoneId

/**
 * Port of `lib/services/vehicle_location_resolver.dart`.
 *
 * Shared near-zero filter for official/local vehicle coordinates.
 */
const val vehicleCoordinateTolerance = 0.000001

/**
 * Resolved vehicle location shared by map/home surfaces.
 *
 * Latitude/longitude may be null when [allowCloudMetadataWithoutCoordinate]
 * is enabled and the official parking payload has address/time but no usable
 * pin.
 */
data class ResolvedVehicleLocation(
    val latitude: Double?,
    val longitude: Double?,
    val accuracy: Double,
    val timeLabel: String,
    val address: String,
    val source: String,
) {
    val hasCoordinate: Boolean
        get() {
            val lat = latitude ?: return false
            val lng = longitude ?: return false
            return !isZeroCoordinate(lat, lng, tolerance = vehicleCoordinateTolerance)
        }

    val coordinateText: String
        get() {
            val lat = latitude ?: return ""
            val lng = longitude ?: return ""
            return formatCoordinateText(lat, lng)
        }
}

/**
 * Resolve display location with fixed priority:
 * official parking pin → official vehicle lat/lng → local last location.
 *
 * When [allowCloudMetadataWithoutCoordinate] is true, an official parking
 * payload that has time/address but no usable pin is still returned so home
 * cards can show "has data" placeholders.
 */
fun resolveVehicleLocation(
    cloudState: OfficialCloudState,
    localVehicle: VehicleProfile?,
    allowCloudMetadataWithoutCoordinate: Boolean = false,
): ResolvedVehicleLocation? = resolveVehicleLocation(
    vehicleLocation = cloudState.vehicleLocation,
    officialVehicle = cloudState.selectedVehicle,
    localVehicle = localVehicle,
    allowCloudMetadataWithoutCoordinate = allowCloudMetadataWithoutCoordinate,
)

/**
 * [CloudScreenState] overload used by the control home's narrowed projection —
 * identical priority chain, driven by the two fields that projection carries.
 */
internal fun resolveVehicleLocation(
    cloudState: com.tailg.plus.ui.screens.CloudScreenState,
    localVehicle: VehicleProfile?,
    allowCloudMetadataWithoutCoordinate: Boolean = false,
): ResolvedVehicleLocation? = resolveVehicleLocation(
    vehicleLocation = cloudState.vehicleLocation,
    officialVehicle = cloudState.selectedVehicle,
    localVehicle = localVehicle,
    allowCloudMetadataWithoutCoordinate = allowCloudMetadataWithoutCoordinate,
)

internal fun resolveVehicleLocation(
    vehicleLocation: OfficialVehicleLocation?,
    officialVehicle: OfficialVehicle?,
    localVehicle: VehicleProfile?,
    allowCloudMetadataWithoutCoordinate: Boolean = false,
): ResolvedVehicleLocation? {
    val cloudLocation: OfficialVehicleLocation? = vehicleLocation
    if (cloudLocation != null) {
        val cloudLat = cloudLocation.latitude
        val cloudLng = cloudLocation.longitude
        if (cloudLat != null &&
            cloudLng != null &&
            !isZeroCoordinate(cloudLat, cloudLng, tolerance = vehicleCoordinateTolerance)
        ) {
            return ResolvedVehicleLocation(
                latitude = cloudLat,
                longitude = cloudLng,
                accuracy = 0.0,
                timeLabel = cloudLocation.bleConnectTime.trim(),
                address = cloudLocation.bleConnectAddress.trim(),
                source = "官方停车位置",
            )
        }
        if (allowCloudMetadataWithoutCoordinate && cloudLocation.hasData) {
            return ResolvedVehicleLocation(
                latitude = null,
                longitude = null,
                accuracy = 0.0,
                timeLabel = cloudLocation.bleConnectTime.trim(),
                address = cloudLocation.bleConnectAddress.trim(),
                source = "官方停车位置",
            )
        }
    }

    val vehicleLat = officialVehicle?.latitude?.toDoubleOrNull()
    val vehicleLng = officialVehicle?.longitude?.toDoubleOrNull()
    if (vehicleLat != null &&
        vehicleLng != null &&
        !isZeroCoordinate(vehicleLat, vehicleLng, tolerance = vehicleCoordinateTolerance)
    ) {
        return ResolvedVehicleLocation(
            latitude = vehicleLat,
            longitude = vehicleLng,
            accuracy = 0.0,
            timeLabel = "",
            address = "",
            source = "官方车辆状态",
        )
    }

    val local: VehicleLocation? = localVehicle?.lastLocation
    if (local != null &&
        !isZeroCoordinate(local.latitude, local.longitude, tolerance = vehicleCoordinateTolerance)
    ) {
        return ResolvedVehicleLocation(
            latitude = local.latitude,
            longitude = local.longitude,
            accuracy = local.accuracy,
            timeLabel = formatDateMinuteText(local.recordedAt.atZone(ZoneId.systemDefault()).toLocalDateTime()),
            address = "",
            source = "本地记录",
        )
    }

    return null
}
