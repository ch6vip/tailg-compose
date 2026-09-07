package com.tailg.plus.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield

internal class ShortcutTestDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    private val mutex = Mutex()
    var failReads = false
    var failWrites = false
    var writes = 0
    var beforeWrite: (suspend () -> Unit)? = null

    override val data: Flow<Preferences> = flow {
        if (failReads) throw IOException("Simulated read failure")
        emitAll(state)
    }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
        yield()
        beforeWrite?.invoke()
        if (failWrites) throw IOException("Simulated write failure")
        transform(state.value).also {
            state.value = it
            writes++
        }
    }
}
