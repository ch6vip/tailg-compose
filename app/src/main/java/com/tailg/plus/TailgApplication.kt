package com.tailg.plus

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Application entry point.
 *
 * The app is dark-first ("VOID COCKPIT" design system); the light scheme is a
 * rarely-used companion, mirroring the Flutter replica's AppColors contract.
 */
@HiltAndroidApp
class TailgApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
