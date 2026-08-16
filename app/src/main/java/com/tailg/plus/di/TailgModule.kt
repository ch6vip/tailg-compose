package com.tailg.plus.di

import android.content.Context
import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.data.cloud.OfficialCloudApiClient
import com.tailg.plus.data.cloud.OfficialCloudApiConfig
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.cloud.OfficialCloudStorage
import com.tailg.plus.data.cloud.OfficialCloudVehicleStore
import com.tailg.plus.data.mqtt.OfficialMqttService
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
 * Provides the app-wide singletons previously constructed by
 * `rememberOfficialCloudService()`. Screens still call that factory for now;
 * this module exists so future migrations can inject these bindings directly.
 *
 * All providers are [Singleton]-scoped inside [SingletonComponent] so the
 * instances survive configuration changes and are shared across the app.
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
}
