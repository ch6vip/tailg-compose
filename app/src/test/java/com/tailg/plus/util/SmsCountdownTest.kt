package com.tailg.plus.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SmsCountdownTest {

    @Test
    fun countsDownEachSecond() = runTest {
        val countdown = SmsCountdown(durationSeconds = 3, scope = this)
        countdown.start()
        assertEquals(3, countdown.remaining.value)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, countdown.remaining.value)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, countdown.remaining.value)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0, countdown.remaining.value)
        countdown.dispose()
    }

    @Test
    fun restartResetsFromFullDuration() = runTest {
        val countdown = SmsCountdown(durationSeconds = 60, scope = this)
        countdown.start()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(58, countdown.remaining.value)
        countdown.start()
        assertEquals(60, countdown.remaining.value)
        countdown.dispose()
    }

    @Test
    fun unmountedStopsTickingWithoutResetting() = runTest {
        var mounted = true
        val countdown = SmsCountdown(durationSeconds = 60, scope = this)
        countdown.start(isMounted = { mounted })
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(59, countdown.remaining.value)
        mounted = false
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(59, countdown.remaining.value)
        countdown.dispose()
    }
}
