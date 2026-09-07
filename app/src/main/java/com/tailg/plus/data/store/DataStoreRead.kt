package com.tailg.plus.data.store

import kotlinx.coroutines.withTimeoutOrNull

/** Budget for reading the first DataStore snapshot off disk. */
internal const val DATA_STORE_READ_TIMEOUT_MS = 5_000L

/**
 * Bounds a DataStore first-snapshot read. `data.first()` suspends until the
 * preferences file has been read; on a corrupted or locked backing file that
 * wait can last forever and strand every caller upstream. The timeout is
 * reported as a regular [IllegalStateException]. Cancellation from the caller,
 * including an enclosing timeout, must still propagate as cancellation.
 */
internal suspend fun <T> withDataStoreReadTimeout(block: suspend () -> T): T {
    // The Result wrapper distinguishes a successful null value from our timeout.
    // withTimeoutOrNull only consumes the timeout belonging to this invocation.
    val result = withTimeoutOrNull(DATA_STORE_READ_TIMEOUT_MS) { Result.success(block()) }
        ?: throw IllegalStateException("DataStore read timed out after ${DATA_STORE_READ_TIMEOUT_MS}ms")
    return result.getOrThrow()
}
