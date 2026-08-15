package com.tailg.plus.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tailg.plus.data.cloud.OfficialCloudApiClient
import com.tailg.plus.data.cloud.OfficialCloudApiConfig
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.cloud.OfficialCloudStorage
import com.tailg.plus.data.cloud.OfficialCloudVehicleStore
import com.tailg.plus.data.model.VehicleProfile
import com.tailg.plus.data.model.VehicleProtocol
import com.tailg.plus.data.store.VehicleStore
import com.tailg.plus.log.LogService

/**
 * Adapter wrapping [VehicleStore] so it satisfies the
 * [OfficialCloudVehicleStore] contract the cloud service depends on.
 *
 * The Dart facade constructed `VehicleStore()` inline; until Hilt is wired,
 * this adapter bridges the two without changing either side.
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

/**
 * Constructs (and remembers) the app-wide [OfficialCloudService] from the
 * current [android.content.Context]. Until Hilt is set up, screens call this
 * so they compile and run without a DI graph.
 *
 * The instance is remembered per-Activity scope (same lifecycle the Dart
 * singleton had), so cloud state survives recomposition.
 */
@Composable
fun rememberOfficialCloudService(): OfficialCloudService {
  val context = LocalContext.current
  return remember(context) {
    val log = LogService()
    val storage = OfficialCloudStorage(context = context, log = log)
    val apiClient = OfficialCloudApiClient(
      config = OfficialCloudApiConfig(),
      log = log,
    )
    val vehicleStore = VehicleStoreCloudAdapter(VehicleStore(context = context, logService = log))
    OfficialCloudService(
      storage = storage,
      apiClient = apiClient,
      vehicleStore = vehicleStore,
      log = log,
    )
  }
}
