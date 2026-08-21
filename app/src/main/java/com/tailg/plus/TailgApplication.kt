package com.tailg.plus

import android.app.Application
import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.mqtt.OfficialMqttService
import com.tailg.plus.data.network.NetworkAvailabilityService
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject
import org.osmdroid.config.Configuration
import timber.log.Timber

/**
 * Application entry point.
 *
 * Binds process-wide side effects (MQTT/BLE teardown on logout) once the Hilt
 * graph is ready, so navigation recomposition cannot double-register hooks.
 */
@HiltAndroidApp
class TailgApplication : Application() {

  @Inject lateinit var cloudService: OfficialCloudService
  @Inject lateinit var mqttService: OfficialMqttService
  @Inject lateinit var connectionManager: ConnectionManager
  @Inject lateinit var networkAvailability: NetworkAvailabilityService

  override fun onCreate() {
    super.onCreate()
    if (BuildConfig.DEBUG) {
      Timber.plant(Timber.DebugTree())
    }
    // osmdroid: identify to tile servers and keep the cache in app-private
    // storage (Dart equivalent: CachedTileProvider disk cache).
    Configuration.getInstance().apply {
      userAgentValue = packageName
      osmdroidBasePath = File(filesDir, "osmdroid")
      osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
    }

    // Logout channel teardown — registered once for the process lifetime.
    cloudService.registerAfterLogout("mqtt_disconnect") {
      mqttService.disconnect()
    }
    cloudService.registerAfterLogout("ble_disconnect") {
      connectionManager.disconnect()
    }
    // Network-restored trigger for the MQTT session (WiFi↔cellular handover,
    // airplane-mode off). Pairs with the connectionLost backoff loop.
    mqttService.monitorNetwork(networkAvailability)
  }
}
