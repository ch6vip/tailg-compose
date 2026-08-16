/**
 * Port of `lib/services/induction_foreground_service.dart` (tailg-ble-app).
 *
 * The Dart file is a thin platform-channel wrapper that starts / stops the
 * native Android foreground service while RSSI induction is on; the BLE
 * protocol itself stays in the connection manager. This Kotlin class is that
 * native service: it supplies the visible foreground-service notification so
 * the process stays eligible for BLE work in the background.
 *
 * Manifest contract (already declared in `app/src/main/AndroidManifest.xml`):
 * - `<service android:name=".service.InductionForegroundService" ...>`
 * - `android.permission.FOREGROUND_SERVICE` +
 *   `android.permission.FOREGROUND_SERVICE_LOCATION`
 * - `android:foregroundServiceType="location"` → started with
 *   `ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION` on API 29+.
 *
 * Behavior mirrors the Flutter project's native service
 * (`android/.../InductionForegroundService.kt`): notification channel
 * `tailg_induction_unlock` (id 2031), `START_NOT_STICKY`, `onTaskRemoved` →
 * `stopSelf`, `onBind` → null. One porting addition: the Dart `vehicleLabel`
 * passed from `InductionModeService` is surfaced in the notification text.
 *
 * [InductionForegroundServiceBridge] / [AndroidInductionForegroundServiceBridge]
 * reproduce the Dart facade contract (`supportsBackgroundRssi` /
 * `start` / `stop`) consumed by `InductionModeService`; `start` returns false
 * when the platform refuses the foreground-service launch (Dart caught
 * `MissingPluginException` / `PlatformException` the same way).
 */
package com.tailg.plus.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tailg.plus.MainActivity
import com.tailg.plus.R
import kotlinx.coroutines.CancellationException

class InductionForegroundService : Service() {

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val vehicleLabel = intent?.getStringExtra(EXTRA_VEHICLE_LABEL)
    val openApp = Intent(this, MainActivity::class.java).apply {
      addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    val contentIntent = PendingIntent.getActivity(
      this,
      0,
      openApp,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val text = if (vehicleLabel.isNullOrBlank()) {
      "感应解锁正在监测车辆距离"
    } else {
      "感应解锁正在监测 $vehicleLabel 距离"
    }
    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("台铃智能")
      .setContentText(text)
      .setContentIntent(contentIntent)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setOnlyAlertOnce(true)
      .setOngoing(true)
      .build()
    // Android 14+ throws SecurityException when a location-type FGS starts
    // while ACCESS_FINE_LOCATION has been revoked; degrade to stopping the
    // service instead of crashing the app (induction cannot run without it).
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
      } else {
        startForeground(NOTIFICATION_ID, notification)
      }
    } catch (e: Exception) {
      android.util.Log.w("InductionFgs", "startForeground failed; stopping", e)
      stopSelf()
    }
    return START_NOT_STICKY
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    stopSelf()
    super.onTaskRemoved(rootIntent)
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val channel = NotificationChannel(
      CHANNEL_ID,
      "感应解锁",
      NotificationManager.IMPORTANCE_LOW,
    ).apply {
      description = "车辆蓝牙距离监测状态"
      setShowBadge(false)
    }
    getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  }

  companion object {
    /** Dart MethodChannel extra name (`de.tttq.tailg_ble_app/induction_service`). */
    const val EXTRA_VEHICLE_LABEL = "vehicleLabel"

    /** Dart native `CHANNEL_ID = "tailg_induction_unlock"`. */
    const val CHANNEL_ID = "tailg_induction_unlock"

    /** Dart native `NOTIFICATION_ID = 2031` (matches the 0x2031 proximity cmd id). */
    const val NOTIFICATION_ID = 2031

    /** Dart `InductionForegroundService.start` — launch the foreground service. */
    fun start(context: Context, vehicleLabel: String?) {
      val intent = Intent(context, InductionForegroundService::class.java).apply {
        putExtra(EXTRA_VEHICLE_LABEL, vehicleLabel ?: "")
      }
      ContextCompat.startForegroundService(context, intent)
    }

    /** Dart `InductionForegroundService.stop` — stop the foreground service. */
    fun stop(context: Context) {
      val intent = Intent(context, InductionForegroundService::class.java)
      context.stopService(intent)
    }
  }
}

/** Port of the Dart `InductionForegroundService` facade consumed by `InductionModeService`. */
interface InductionForegroundServiceBridge {
  /** Dart `supportsBackgroundRssi` — true on Android. */
  val supportsBackgroundRssi: Boolean

  /** Dart `start({vehicleLabel})` — false when the platform refuses the start. */
  suspend fun start(vehicleLabel: String?): Boolean

  /** Dart `stop()`. */
  suspend fun stop(): Boolean
}

/** Default bridge — launches the Android [InductionForegroundService]. */
class AndroidInductionForegroundServiceBridge(private val context: Context) :
  InductionForegroundServiceBridge {

  override val supportsBackgroundRssi: Boolean = true

  override suspend fun start(vehicleLabel: String?): Boolean = try {
    InductionForegroundService.start(context, vehicleLabel)
    true
  } catch (e: Exception) {
    if (e is CancellationException) throw e
    false
  }

  override suspend fun stop(): Boolean = try {
    InductionForegroundService.stop(context)
    true
  } catch (e: Exception) {
    if (e is CancellationException) throw e
    false
  }
}
