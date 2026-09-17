package com.tailg.plus.ui.screens

import com.tailg.plus.data.cloud.OfficialCloudVehicleStore
import com.tailg.plus.data.model.VehicleProfile
import com.tailg.plus.data.model.VehicleProtocol
import com.tailg.plus.data.store.VehicleStore
import com.tailg.plus.di.rememberTailgEntryPoint

/**
 * Adapter wrapping [VehicleStore] so it satisfies the
 * [OfficialCloudVehicleStore] contract the cloud service depends on.
 */
internal class VehicleStoreCloudAdapter(private val store: VehicleStore) : OfficialCloudVehicleStore {
  override val vehicles: List<VehicleProfile> get() = store.vehicles

  override suspend fun init() = store.init()

  override suspend fun setDefault(id: String) = store.setDefault(id)

  override suspend fun upsert(
    id: String,
    name: String,
    protocol: VehicleProtocol,
    makeDefault: Boolean,
  ): VehicleProfile = store.upsert(
    id = id,
    name = name,
    protocol = protocol,
    makeDefault = makeDefault,
  )
}

