package com.tailg.plus.data.store

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreReadTest {
    @Test
    fun nullableReadCompletesNormally() = runTest {
        assertNull(withDataStoreReadTimeout<String?> { null })
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun stalledReadReportsItsOwnTimeoutAsFailure() = runTest {
        val failure = runCatching { withDataStoreReadTimeout { awaitCancellation() } }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(failure !is CancellationException)
        assertEquals(DATA_STORE_READ_TIMEOUT_MS, testScheduler.currentTime)
    }

    @Test
    fun enclosingTimeoutStillCancelsTheRead() = runTest {
        val result = withTimeoutOrNull(100L) {
            withDataStoreReadTimeout { awaitCancellation() }
        }
        assertNull(result)
        assertEquals(100L, testScheduler.currentTime)
    }

    @Test
    fun preservesReadErrorsAndExplicitCancellation() = runTest {
        for (failure in listOf(IOException("read failed"), CancellationException("caller stopped"))) {
            val actual = runCatching { withDataStoreReadTimeout<Nothing> { throw failure } }.exceptionOrNull()
            assertEquals(failure.javaClass, actual?.javaClass)
            assertEquals(failure.message, actual?.message)
        }
    }
}
