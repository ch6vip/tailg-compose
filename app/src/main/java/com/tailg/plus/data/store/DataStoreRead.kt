package com.tailg.plus.data.store

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Budget for reading the first DataStore snapshot off disk. */
internal const val DATA_STORE_READ_TIMEOUT_MS = 5_000L

/**
 * Bounds a DataStore first-snapshot read. `data.first()` suspends until the
 * preferences file has been read; on a corrupted or locked backing file that
 * wait can last forever and strand every caller upstream. The timeout is
 * rethrown as a regular [IllegalStateException] — NOT as the original
 * [TimeoutCancellationException], which would silently cancel the caller's
 * whole coroutine scope instead of surfacing as a normal read failure.
 */
internal suspend fun <T> withDataStoreReadTimeout(block: suspend () -> T): T = try {
    withTimeout(DATA_STORE_READ_TIMEOUT_MS) { block() }
} catch (e: TimeoutCancellationException) {
    throw IllegalStateException(
        "DataStore read timed out after ${DATA_STORE_READ_TIMEOUT_MS}ms",
        e,
    )
}
