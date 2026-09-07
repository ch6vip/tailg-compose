package com.tailg.plus.data.store

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tailg.plus.data.model.NinebotShortcut
import com.tailg.plus.data.model.NinebotShortcutLayout
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NinebotShortcutStoreTest {
    @Test
    fun layoutsAndEmptySlotsSurviveStoreRecreationForEachVehicle() = runTest {
        val data = ShortcutTestDataStore()
        val store = NinebotShortcutStore(data)
        assertEquals(NinebotShortcutLayout.Default, store.observe("vehicle-a").first())
        val first = NinebotShortcutLayout.Default.assign(0, NinebotShortcut.SEAT).assign(1, null)
        val second = NinebotShortcutLayout.decode("_,_,_")
        store.save("vehicle-a", first)
        store.save("vehicle-b", second)

        val restored = NinebotShortcutStore(data)
        assertEquals(listOf(NinebotShortcut.SEAT, null, NinebotShortcut.INDUCTION), restored.observe("vehicle-a").first().slots)
        assertEquals(listOf(null, null, null), restored.observe("vehicle-b").first().slots)
        assertEquals(NinebotShortcutLayout.Default, restored.observe("vehicle-c").first())
    }

    @Test
    fun unsupportedOrDuplicateStoredFunctionsBecomeEmptyWithoutMovingOtherSlots() = runTest {
        val data = ShortcutTestDataStore(mutablePreferencesOf(
            stringPreferencesKey("layout_vehicle-a") to "seat,future-function,seat",
            stringPreferencesKey("layout_vehicle-b") to "invalid-format",
        ))
        val store = NinebotShortcutStore(data)
        assertEquals(listOf(NinebotShortcut.SEAT, null, null), store.observe("vehicle-a").first().slots)
        assertEquals(NinebotShortcutLayout.Default, store.observe("vehicle-b").first())
    }

    @Test
    fun failedWriteKeepsTheLastSavedLayout() = runTest {
        val data = ShortcutTestDataStore()
        val store = NinebotShortcutStore(data)
        val saved = NinebotShortcutLayout.Default.assign(2, null)
        store.save("vehicle-a", saved)
        data.failWrites = true
        val failure = runCatching { store.save("vehicle-a", NinebotShortcutLayout.Default) }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertEquals(saved, NinebotShortcutStore(data).observe("vehicle-a").first())
        assertEquals(1, data.writes)
    }

    @Test
    fun concurrentWritesKeepBothVehiclesPreferences() = runTest {
        val data = ShortcutTestDataStore()
        val store = NinebotShortcutStore(data)
        val first = NinebotShortcutLayout.Default.assign(0, NinebotShortcut.BATTERY)
        val second = NinebotShortcutLayout.Default.assign(1, NinebotShortcut.SEAT)
        listOf(
            async { store.save("vehicle-a", first) },
            async { store.save("vehicle-b", second) },
        ).awaitAll()
        assertEquals(first, store.observe("vehicle-a").first())
        assertEquals(second, store.observe("vehicle-b").first())
    }

    @Test
    fun preferencesRequireAStableVehicleIdentity() {
        val store = NinebotShortcutStore(ShortcutTestDataStore())
        assertThrows(IllegalArgumentException::class.java) { store.observe(" ") }
    }
}
