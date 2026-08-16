package com.tailg.plus

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration
import timber.log.Timber
import java.io.File

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
        // osmdroid: identify to tile servers and keep the cache in app-private
        // storage (Dart equivalent: CachedTileProvider disk cache).
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(filesDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
    }
}
