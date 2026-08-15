package com.tailg.plus.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Port of `lib/services/sms_countdown.dart` — the shared SMS resend countdown
 * used by login and official-cloud auth UIs.
 *
 * Dart `ValueNotifier<int>` → `StateFlow<Int>` (no `dispose` needed for the
 * flow itself); Dart `Timer.periodic(1s)` → a coroutine ticking every second
 * on the injected [scope], whose lifecycle the caller owns. [dispose] only
 * cancels the tick job.
 *
 * Timing matches Dart: the first tick happens 1 second after [start], and
 * `remaining` stays at 1 for the final second before dropping to 0.
 */
class SmsCountdown(
    val durationSeconds: Int = 60,
    private val scope: CoroutineScope,
) {
    private val _remaining = MutableStateFlow(0)
    val remaining: StateFlow<Int> = _remaining.asStateFlow()

    private var tickJob: Job? = null

    val isActive: Boolean get() = _remaining.value > 0

    /**
     * Starts (or restarts) the countdown from [durationSeconds]. The optional
     * [isMounted] guard mirrors the Dart `isMounted` callback: when it returns
     * false at a tick, ticking stops without resetting the value.
     */
    fun start(isMounted: () -> Boolean = { true }) {
        tickJob?.cancel()
        _remaining.value = durationSeconds
        tickJob = scope.launch {
            while (true) {
                delay(1_000)
                if (!isMounted()) break
                val current = _remaining.value
                if (current <= 1) {
                    _remaining.value = 0
                    break
                } else {
                    _remaining.value = current - 1
                }
            }
        }
    }

    fun dispose() {
        tickJob?.cancel()
        tickJob = null
    }
}
