package com.tailg.plus.log

import com.tailg.plus.util.SensitiveTextRedactor
import java.time.LocalDateTime
import java.util.ArrayDeque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Dart `enum LogLevel { debug, info, warning, error }`. */
enum class LogLevel { DEBUG, INFO, WARNING, ERROR }

/** Dart `enum LogCategory { ble, operation }`. */
enum class LogCategory { BLE, OPERATION }

/**
 * Dart `class LogEntry`. [time] is the local wall clock (Dart `DateTime`);
 * messages and details are already redacted before the entry is stored.
 */
data class LogEntry(
    val time: LocalDateTime,
    val level: LogLevel,
    val category: LogCategory,
    val message: String,
    val detail: String?,
)

/**
 * Port of `lib/services/log_service.dart` (LogService).
 *
 * The Dart source is an **in-memory** ring buffer (max 2000 entries) and does
 * NOT persist to disk; this port matches that contract. File export lives in
 * `diagnostic_export_service.dart`, a separate Dart service (not in scope).
 *
 * Dart singleton → plain class; DI (Hilt) should create the single shared
 * instance. The clock is constructor-injected for deterministic tests, like
 * the Dart `resetForTest`/`_clock` hook.
 *
 * Thread safety: the Dart original relied on the single-threaded event loop,
 * but BLE/MQTT callbacks and UI reads here can come from different threads,
 * so all buffer mutations are guarded by a lock.
 *
 * Change notification: Dart broadcast `StreamController<void>` → [changes],
 * a `StateFlow` generation counter. The counter is bumped on every mutation,
 * so the UI can key its snapshot cache on its value.
 */
class LogService(
    clock: () -> LocalDateTime = { LocalDateTime.now() },
) {

    companion object {
        private const val MAX_ENTRIES = 2000
        private val LOGIN_HINT = Regex("(登录|login)", RegexOption.IGNORE_CASE)

        /**
         * Quiet-window width used by UI collectors to coalesce log bursts.
         * Lives here so every screen debounces [changes] by the same amount.
         */
        const val REFRESH_DEBOUNCE_MS = 120L
    }

    private val lock = Any()
    private val _logs = ArrayDeque<LogEntry>()
    private var _evictedCount = 0
    private var clock: () -> LocalDateTime = clock

    // Note: 代次计数器而非 SharedFlow<Unit>，以及本轮全部帧预算修复 — 见
    // .agents/notes/implemented/architecture/2026-09-21-frame-budget-cleanup.md
    /**
     * Change notification: Dart broadcast `StreamController<void>` → a
     * `StateFlow` generation counter.
     *
     * The old shape was `SharedFlow<Unit>`: a ping carried no value, so every
     * collector had to keep a parallel counter of its own just to have
     * something usable as a `remember` key, and debouncing was re-implemented
     * at each call site. The generation value *is* the key.
     */
    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes.asStateFlow()

    /** Current [changes] value — usable directly as a `remember` key. */
    val generation: Long get() = _changes.value

    /** Snapshot of all entries (newest last). */
    val all: List<LogEntry>
        get() = snapshot()

    /** Number of entries dropped from the front of the ring buffer. */
    val evictedCount: Int
        get() = _evictedCount

    /** Snapshot of entries in one [category] (newest last). */
    fun byCategory(category: LogCategory): List<LogEntry> = snapshot(category = category)

    private fun snapshot(category: LogCategory? = null): List<LogEntry> {
        synchronized(lock) {
            val result = ArrayList<LogEntry>()
            for (entry in _logs) {
                if (category != null && entry.category != category) continue
                result.add(entry)
            }
            return result
        }
    }

    /**
     * Dart `resetForTest({clock})`: clear + replace the clock. The Dart
     * version also recreates the stream controller after `dispose`; a
     * `StateFlow` cannot be closed either, so nothing needs recreating here.
     */
    fun resetForTest(clock: (() -> LocalDateTime)? = null) {
        clear()
        this.clock = clock ?: { LocalDateTime.now() }
    }

    /** Dart `ble(...)`: default level is DEBUG. */
    fun ble(
        message: String,
        detail: String? = null,
        level: LogLevel = LogLevel.DEBUG,
        time: LocalDateTime? = null,
    ) {
        add(redactedEntry(LogCategory.BLE, message, detail = detail, level = level, time = time))
    }

    /** Dart `operation(...)`: default level is INFO. */
    fun operation(
        message: String,
        detail: String? = null,
        level: LogLevel = LogLevel.INFO,
        time: LocalDateTime? = null,
    ) {
        add(redactedEntry(LogCategory.OPERATION, message, detail = detail, level = level, time = time))
    }

    private fun redactedEntry(
        category: LogCategory,
        message: String,
        detail: String?,
        level: LogLevel,
        time: LocalDateTime?,
    ): LogEntry {
        val redactedMessage = redactSensitiveText(message)
        return LogEntry(
            time = time ?: clock(),
            level = level,
            category = category,
            message = redactedMessage,
            detail = redactDetail(message, detail),
        )
    }

    /**
     * Dart `_redactDetail`: redacts sensitive login payloads before they hit
     * the ring buffer. When the message hints at login, hex payloads are
     * replaced by a length summary so troubleshooting still sees "frame sent,
     * N bytes" without leaking credentials.
     */
    private fun redactDetail(message: String, detail: String?): String? {
        if (detail == null) return null
        if (!LOGIN_HINT.containsMatchIn(message)) return redactSensitiveText(detail)
        val hexByteCount = detail.split(' ').count { it.isNotEmpty() }
        return "<redacted login frame, $hexByteCount bytes>"
    }

    private fun redactSensitiveText(value: String): String = SensitiveTextRedactor.redact(value)

    private fun add(entry: LogEntry) {
        synchronized(lock) {
            _logs.addLast(entry)
            while (_logs.size > MAX_ENTRIES) {
                _logs.removeFirst()
                _evictedCount++
            }
            _changes.value += 1
        }
    }

    /** Clear the buffer and reset [evictedCount]; notifies subscribers. */
    fun clear() {
        synchronized(lock) {
            _logs.clear()
            _evictedCount = 0
            _changes.value += 1
        }
    }

    /**
     * API parity with Dart `dispose`. Unlike Dart's stream controller there
     * is nothing to close (the `SharedFlow` lives for the app lifetime), so
     * this only clears the buffer without notifying subscribers.
     */
    fun dispose() {
        synchronized(lock) {
            _logs.clear()
            _evictedCount = 0
        }
    }
}
