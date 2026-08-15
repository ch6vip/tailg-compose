package com.tailg.plus.data.cloud

import com.tailg.plus.data.model.VehicleProfile
import com.tailg.plus.data.model.VehicleProtocol

/**
 * Port boundary for `lib/services/vehicle_store.dart` (the local garage store),
 * which is NOT part of this cloud port.
 *
 * The Dart facade constructs `VehicleStore()` inline; here the dependency is
 * constructor-injected so the cloud module stays decoupled. Implement this
 * interface with the future VehicleStore port (or an adapter over it).
 *
 * Only the members the cloud sync logic actually uses are declared:
 * - [vehicles] — current local garage list (planner input)
 * - [init] — Dart `store.init()`
 * - [setDefault] — Dart `store.setDefault(id)`
 * - [upsert] — Dart `store.upsert(id:, name:, protocol:, makeDefault:)`
 */
interface OfficialCloudVehicleStore {
    val vehicles: List<VehicleProfile>

    suspend fun init()

    suspend fun setDefault(id: String)

    suspend fun upsert(
        id: String,
        name: String,
        protocol: VehicleProtocol,
        makeDefault: Boolean = false,
    ): VehicleProfile
}
