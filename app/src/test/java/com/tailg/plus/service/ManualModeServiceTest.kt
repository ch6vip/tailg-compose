package com.tailg.plus.service

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ManualModeServiceTest {
    @Test
    fun turningOffDuringPendingEnablePersistsTheLastChoice() = runTest {
        val firstWrite = CompletableDeferred<Unit>()
        val writes = mutableListOf<Boolean>()
        val prefs = mockk<InductionPrefs>()
        coEvery { prefs.loadBoolean(any(), any()) } returns false
        coEvery { prefs.saveBoolean(any(), any()) } coAnswers {
            val value = secondArg<Boolean>()
            if (value) firstWrite.await()
            writes += value
        }
        val service = ManualModeService(prefs)
        val enable = async { service.setEnabled(true) }
        testScheduler.runCurrent()
        val disable = async { service.setEnabled(false) }
        testScheduler.runCurrent()
        firstWrite.complete(Unit)
        enable.await()
        disable.await()

        assertFalse(service.enabled)
        assertEquals(listOf(true, false), writes)
    }
}
