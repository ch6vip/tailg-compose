package com.tailg.plus.di

import android.content.Context
import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.data.cloud.OfficialCloudApiClient
import com.tailg.plus.data.cloud.OfficialCloudApiConfig
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.cloud.OfficialCloudStorage
import com.tailg.plus.data.cloud.OfficialCloudVehicleStore
import com.tailg.plus.data.mqtt.OfficialMqttService
import com.tailg.plus.data.network.NetworkAvailabilityService
import com.tailg.plus.data.preferences.AppPreferencesService
import com.tailg.plus.data.store.ReplicaFeatureStore
import com.tailg.plus.data.store.VehicleStore
import com.tailg.plus.log.LogService
import com.tailg.plus.ui.screens.VehicleStoreCloudAdapter
import com.tailg.plus.util.ClipboardText
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI graph for tailg-compose.
 *
 * All app-wide services are [Singleton]-scoped. Screens and the navigation
 * host resolve them through [TailgEntryPoint] (or constructor injection on
 * ViewModels) — do not construct parallel instances via remember factories.
 */
@Module
@InstallIn(SingletonComponent::class)
object TailgModule {

  @Provides
  @Singleton
  fun provideLogService(): LogService = LogService()

  @Provides
  @Singleton
  fun provideOfficialCloudStorage(
    @ApplicationContext context: Context,
    log: LogService,
  ): OfficialCloudStorage = OfficialCloudStorage(
    context = context,
    log = log,
  )

  @Provides
  @Singleton
  fun provideOfficialCloudApiClient(
    log: LogService,
  ): OfficialCloudApiClient = OfficialCloudApiClient(
    config = OfficialCloudApiConfig(),
    log = log,
  )

  @Provides
  @Singleton
  fun provideVehicleStore(
    @ApplicationContext context: Context,
    log: LogService,
  ): VehicleStore = VehicleStore(
    context = context,
    logService = log,
  )

  @Provides
  @Singleton
  fun provideVehicleStoreCloudAdapter(
    vehicleStore: VehicleStore,
  ): OfficialCloudVehicleStore = VehicleStoreCloudAdapter(vehicleStore)

  @Provides
  @Singleton
  fun provideOfficialCloudService(
    storage: OfficialCloudStorage,
    apiClient: OfficialCloudApiClient,
    vehicleStore: OfficialCloudVehicleStore,
    log: LogService,
  ): OfficialCloudService = OfficialCloudService(
    storage = storage,
    apiClient = apiClient,
    vehicleStore = vehicleStore,
    log = log,
  )

  @Provides
  @Singleton
  fun provideConnectionManager(
    @ApplicationContext context: Context,
    log: LogService,
  ): ConnectionManager = ConnectionManager(
    context = context,
    log = log,
  )

  @Provides
  @Singleton
  fun provideOfficialMqttService(
    log: LogService,
  ): OfficialMqttService = OfficialMqttService(
    log = log,
  )

  @Provides
  @Singleton
  fun provideClipboardText(
    @ApplicationContext context: Context,
  ): ClipboardText = ClipboardText(context)

  @Provides
  @Singleton
  fun provideAppPreferencesService(
    @ApplicationContext context: Context,
    log: LogService,
  ): AppPreferencesService = AppPreferencesService(
    context = context,
    logService = log,
  )

  @Provides
  @Singleton
  fun provideNetworkAvailabilityService(
    @ApplicationContext context: Context,
  ): NetworkAvailabilityService = NetworkAvailabilityService(context)

  @Provides
  @Singleton
  fun provideReplicaFeatureStore(
    @ApplicationContext context: Context,
    log: LogService,
  ): ReplicaFeatureStore = ReplicaFeatureStore(
    context = context,
    logService = log,
  )
}
