/**
 * Port of `lib/services/manual_mode_service.dart` (tailg-ble-app).
 *
 * Tracks the user-facing "手动模式" (manual mode) switch on the control page.
 * When enabled, the app must not perform any automatic vehicle actions:
 * proximity unlock and auto-connect both consult [enabled] before scanning, so
 * the toggle's promise ("禁用自动控车") is actually honoured. The flag is
 * persisted so it survives app restarts.
 *
 * pending: 签名待集成核对 — dependency port (not part of the two-file scope).
 * The Dart class is a singleton (`factory ManualModeService() => _instance`);
 * like `LogService`, the Kotlin line uses DI to share one instance, so this is
 * a plain class. Dart `StreamController<bool>.broadcast()` → [enabledFlow]
 * (StateFlow); Dart `_initializing` future coalescing → an init [Mutex].
 */
package com.tailg.plus.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ManualModeService(
  private val prefs: InductionPrefs,
) {
  companion object {
    /** Dart `_prefKey = 'manual_mode_enabled'`. */
    private const val PREF_KEY = "manual_mode_enabled"
  }

  private val _enabled = MutableStateFlow(false)

  /** Dart `enabledStream` (broadcast) → StateFlow. */
  val enabledFlow: StateFlow<Boolean> = _enabled.asStateFlow()

  private val initMutex = Mutex()
  private var initialized = false

  /** Dart `enabled` getter. */
  val enabled: Boolean get() = _enabled.value

  /** Dart `init()` — load the persisted value and expose it. */
  suspend fun init() = ensureInitialized(emitInitialValue = true)

  /**
   * Dart `_ensureInitialized` — concurrent callers coalesce on [initMutex]
   * (Dart's `_initializing` future). [emitInitialValue] is a no-op here: a
   * StateFlow always exposes its current value, whereas Dart only emitted on
   * the broadcast stream when asked.
   */
  private suspend fun ensureInitialized(emitInitialValue: Boolean) {
    if (initialized) return
    initMutex.withLock {
      if (initialized) return
      _enabled.value = prefs.loadBoolean(PREF_KEY, false)
      initialized = true
    }
  }

  /** Dart `resetForTest()`. */
  fun resetForTest() {
    _enabled.value = false
    initialized = false
  }

  /** Dart `setEnabled(bool)` — persists first, then publishes. */
  suspend fun setEnabled(value: Boolean) {
    ensureInitialized(emitInitialValue = false)
    if (_enabled.value == value) return
    prefs.saveBoolean(PREF_KEY, value)
    _enabled.value = value
  }

  /** Dart `dispose()` — a StateFlow cannot be closed; kept for API parity. */
  fun dispose() = Unit
}
