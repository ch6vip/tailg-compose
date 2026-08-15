package com.tailg.plus.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Induction (感应解锁) foreground service.
 *
 * Stub for the skeleton build — full BLE-scan + unlock state machine lands
 * with the service-layer port (port of `lib/services/induction_foreground_service.dart`).
 */
class InductionForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null
}
