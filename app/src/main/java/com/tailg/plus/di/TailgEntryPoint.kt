package com.tailg.plus.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.mqtt.OfficialMqttService
import com.tailg.plus.data.store.VehicleStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Runtime access to the Hilt [SingletonComponent] graph from non-injectable
 * hosts (Compose [androidx.compose.runtime.Composable] screens and the
 * navigation host).
 *
 * The navigation layer resolves this entry point once per composition and
 * passes the shared singletons down as plain function parameters, so the
 * screens keep their constructor-style signatures (testable without Hilt)
 * while all app-wide services come from one graph.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TailgEntryPoint {
  fun cloudService(): OfficialCloudService

  fun connectionManager(): ConnectionManager

  fun mqttService(): OfficialMqttService

  fun vehicleStore(): VehicleStore
}

/**
 * Resolves [TailgEntryPoint] for the current [android.content.Context].
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
