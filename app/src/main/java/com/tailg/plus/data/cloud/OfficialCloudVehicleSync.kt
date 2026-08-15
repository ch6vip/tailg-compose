package com.tailg.plus.data.cloud

import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.data.model.VehicleProfile

/**
 * Port of `OfficialCloudVehicleSyncDecision` / `OfficialCloudVehicleSyncPlanner`
 * from `lib/services/official_cloud_vehicle_sync.dart`.
 *
 * Decides, for the selected official vehicle, whether to reuse the already
 * linked local garage vehicle or to upsert a fresh local profile derived from
 * the official one.
 */
data class OfficialCloudVehicleSyncDecision private constructor(
    val linkedLocalVehicleId: String?,
    val profileData: OfficialCloudVehicleProfileData?,
) {
    companion object {
        fun useLinkedLocalVehicle(linkedLocalVehicleId: String): OfficialCloudVehicleSyncDecision =
            OfficialCloudVehicleSyncDecision(linkedLocalVehicleId = linkedLocalVehicleId, profileData = null)

        fun upsertLocalProfile(profileData: OfficialCloudVehicleProfileData): OfficialCloudVehicleSyncDecision =
            OfficialCloudVehicleSyncDecision(linkedLocalVehicleId = null, profileData = profileData)
    }
}

object OfficialCloudVehicleSyncPlanner {

    fun plan(
        selectedVehicle: OfficialVehicle,
        localVehicleLinks: Map<String, String>,
        localVehicles: List<VehicleProfile>,
    ): OfficialCloudVehicleSyncDecision? {
        val linkedId = if (selectedVehicle.key.isEmpty()) {
            null
        } else {
            localVehicleLinks[selectedVehicle.key]
        }
        if (linkedId != null && linkedId.isNotEmpty()) {
            val hasLinkedVehicle = localVehicles.any { local -> local.id == linkedId }
            if (hasLinkedVehicle) {
                return OfficialCloudVehicleSyncDecision.useLinkedLocalVehicle(linkedId)
            }
        }

        val profileData = OfficialCloudVehicleMapper.profileFromOfficialVehicle(selectedVehicle)
            ?: return null
        return OfficialCloudVehicleSyncDecision.upsertLocalProfile(profileData)
    }
}
