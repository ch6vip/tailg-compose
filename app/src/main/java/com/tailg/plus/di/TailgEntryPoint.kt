package com.tailg.plus.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.mqtt.OfficialMqttService
import com.tailg.plus.data.network.NetworkAvailabilityService
import com.tailg.plus.data.preferences.AppPreferencesService
import com.tailg.plus.data.store.ReplicaFeatureStore
import com.tailg.plus.data.store.VehicleStore
import com.tailg.plus.log.LogService
import com.tailg.plus.util.ClipboardText
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Legacy runtime access to the Hilt SingletonComponent graph from non-injectable
 * hosts (Compose screens and the navigation host).
 *
 * **Prefer [MainViewModel] for new code.** This entry point is kept for backward
 * compatibility with screens that still use it. New screens should receive
 * services through `@HiltViewModel` constructor injection or via the
 * [MainViewModel] passed down from [TailgNavHost].
 *
 * Resolve once per composition and pass singletons down as parameters so
 * screens stay testable with fakes while production always shares one graph.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TailgEntryPoint {
  fun cloudService(): OfficialCloudService

  fun connectionManager(): ConnectionManager

  fun mqttService(): OfficialMqttService

  fun vehicleStore(): VehicleStore

  fun logService(): LogService

  fun clipboardText(): ClipboardText

  fun appPreferences(): AppPreferencesService

  fun networkAvailability(): NetworkAvailabilityService

  fun replicaFeatureStore(): ReplicaFeatureStore
}

/**
 * Resolves TailgEntryPoint for the current Context.
 * Safe to call from any Composable; Hilt guarantees the same singleton
 * instances for the lifetime of the process.
 */
@Composable
fun rememberTailgEntryPoint(): TailgEntryPoint {
  val appContext = LocalContext.current.applicationContext
  return remember(appContext) {
    EntryPointAccessors.fromApplication(appContext, TailgEntryPoint::class.java)
  }
}
