package com.tailg.plus.util

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.LruCache

/**
 * Shared app-wide in-memory bitmap cache with system-memory trimming.
 *
 * Modeled after the ComicPlus_Pure pipeline: a size-aware [LruCache] whose
 * capacity scales with the device heap (`memoryClass / 8`, the same budget
 * ComicPlus_Pure uses for its page bitmap cache) and a registered
 * [ComponentCallbacks2] that trims entries by severity so the OS never has to
 * kill the process under memory pressure.
 *
 * A single instance is shared by every image decode path in the app
 * (vehicle photos, mini-map tiles, future cover/avatar loads) so cache
 * pressure is bounded globally instead of per-screen.
 */
object BitmapMemoryCache {

  /** Byte budget = memoryClass / 8, clamped to a sane window. */
  private const val MIN_CACHE_KB = 4 * 1024
  private const val MAX_CACHE_KB = 48 * 1024
  private const val DEFAULT_MEMORY_MB = 256

  private val cache = object : LruCache<String, Bitmap>(sizeKbForMemoryMb(DEFAULT_MEMORY_MB)) {
    override fun sizeOf(key: String, value: Bitmap): Int =
      (value.allocationByteCount / 1024).coerceAtLeast(1)
  }

  private val memoryCallbacks = object : ComponentCallbacks2 {
    override fun onTrimMemory(level: Int) {
      when {
        level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> cache.evictAll()
        level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
          level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ->
          cache.trimToSize(cache.maxSize() / 4)
        level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ->
          cache.trimToSize(cache.maxSize() / 2)
      }
    }

    override fun onLowMemory() = cache.evictAll()
    override fun onConfigurationChanged(newConfig: Configuration) = Unit
  }

  @Volatile
  private var registered = false

  /**
   * Register the trim callbacks once for the process. Idempotent; safe to
   * call from any Application/Service/Composable path.
   */
  fun register(context: Context) {
    if (registered) return
    synchronized(this) {
      if (registered) return
      cache.resize(initialSizeKb(context))
      context.applicationContext.registerComponentCallbacks(memoryCallbacks)
      registered = true
    }
  }

  fun get(key: String): Bitmap? = cache.get(key)

  fun put(key: String, bitmap: Bitmap) {
    cache.put(key, bitmap)
  }

  /** Number of cached entries (diagnostics / tests). */
  fun size(): Int = cache.size()

  /** Evict everything (logout, low-memory test hooks). */
  fun evictAll() = cache.evictAll()

  private fun initialSizeKb(context: Context?): Int {
    val memoryMb = context
      ?.getSystemService(ActivityManager::class.java)
      ?.memoryClass
      ?: DEFAULT_MEMORY_MB
    return sizeKbForMemoryMb(memoryMb)
  }

  private fun sizeKbForMemoryMb(memoryMb: Int): Int =
    (memoryMb * 1024 / 8).coerceIn(MIN_CACHE_KB, MAX_CACHE_KB)
}
