package com.tailg.plus.data.preferences

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AppPreferencesServiceTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun newInstallUsesNinebotBeforeInitializationAndPersistsIt() = runBlocking {
        withStore(file("new-install")) { store ->
            val service = AppPreferencesService(store)
            assertEquals(2, service.uiMode.value)

            service.init()

            assertEquals(2, service.uiMode.value)
            assertEquals(2, store.data.first()[uiModeKey])
            assertEquals(AppLanguagePreference.System, service.language.value)
            assertEquals(DistanceUnitPreference.Metric, service.distanceUnit.value)
            assertTrue(service.respectSystemTextScale.value)
            assertEquals(0, service.themeMode.value)
            assertEquals(1f, service.pageScale.value, 0f)
            assertFalse(service.floatingBottomBar.value)
        }
    }

    @Test
    fun removedAndUnknownIdentifiersUpgradeToTheExistingNinebotIdentifier() = runBlocking {
        for (legacyValue in listOf(0, 1, 3, -1, Int.MIN_VALUE, Int.MAX_VALUE)) {
            val file = file("old-$legacyValue")
            seed(file, mutablePreferencesOf(uiModeKey to legacyValue))

            withStore(file) { store ->
                val service = AppPreferencesService(store)
                assertEquals(2, service.uiMode.value)
                service.init()
                assertEquals("Stored style $legacyValue must resolve to Ninebot", 2, service.uiMode.value)
                assertEquals(2, store.data.first()[uiModeKey])
            }
        }
    }

    @Test
    fun anUnsupportedStoredValueTypeIsReplacedWithoutLosingOtherEntries() = runBlocking {
        val file = file("old-type")
        seed(file, mutablePreferencesOf(
            stringPreferencesKey("app_ui_mode") to "removed-style",
            unknownKey to "keep this value",
        ))

        withStore(file) { store ->
            val service = AppPreferencesService(store)
            service.init()
            assertEquals(2, service.uiMode.value)
            assertEquals(2, store.data.first()[uiModeKey])
            assertEquals("keep this value", store.data.first()[unknownKey])
        }
    }

    @Test
    fun upgradingPreservesAllOtherPreferencesAndRemainsStableAfterReopeningTheFile() = runBlocking {
        val file = file("preserved")
        val original = configuredPreferences(uiMode = 0)
        val expected = original.asMap().toMutableMap().apply { put(uiModeKey, 2) }
        seed(file, original)

        // Close the real DataStore between reads to cover an application restart, not just caching.
        repeat(2) {
            withStore(file) { store ->
                val service = AppPreferencesService(store)
                service.init()
                assertEquals(2, service.uiMode.value)
                assertEquals(AppLanguagePreference.English, service.language.value)
                assertEquals(DistanceUnitPreference.Imperial, service.distanceUnit.value)
                assertFalse(service.respectSystemTextScale.value)
                assertEquals(2, service.themeMode.value)
                assertEquals(1.25f, service.pageScale.value, 0f)
                assertTrue(service.floatingBottomBar.value)
                assertEquals(expected, store.data.first().asMap())
                assertFalse(NinebotUiModeMigration.shouldMigrate(store.data.first()))

                val secondService = AppPreferencesService(store)
                secondService.init()
                assertEquals(2, secondService.uiMode.value)
                assertEquals(expected, store.data.first().asMap())
            }
        }
        // The migration creates a new snapshot, leaving the original preference set intact.
        assertEquals(0, original[uiModeKey])
    }

    @Test
    fun existingNinebotPreferencesDoNotRequireAnotherMigration() = runBlocking {
        val file = file("already-ninebot")
        val original = configuredPreferences(uiMode = 2)
        assertFalse(NinebotUiModeMigration.shouldMigrate(original))
        seed(file, original)

        withStore(file) { store ->
            val service = AppPreferencesService(store)
            service.init()
            assertEquals(original.asMap(), store.data.first().asMap())
            assertEquals(2, service.uiMode.value)
        }
    }

    @Test
    fun theLegacySetterCannotRestoreARemovedStyle() = runBlocking {
        withStore(file("setter")) { store ->
            val service = AppPreferencesService(store)
            for (value in listOf(0, 1, 2, 3, -1, Int.MAX_VALUE)) {
                service.setUiMode(value)
                assertEquals(2, service.uiMode.value)
                assertEquals(2, store.data.first()[uiModeKey])
            }
        }
    }

    @Test
    fun writingAnotherPreferenceBeforeInitPreservesTheRemainingMigratedValues() = runBlocking {
        val file = file("save-before-init")
        val original = configuredPreferences(uiMode = 0)
        val expected = original.asMap().toMutableMap().apply {
            put(uiModeKey, 2)
            put(floatingBarKey, false)
        }
        seed(file, original)

        withStore(file) { store ->
            val service = AppPreferencesService(store)
            service.setFloatingBottomBar(false)
            assertEquals(2, service.uiMode.value)
            assertFalse(service.floatingBottomBar.value)
            assertEquals(2, service.themeMode.value)
            assertEquals(1.25f, service.pageScale.value, 0f)
            assertEquals(expected, store.data.first().asMap())
        }
    }

    private fun configuredPreferences(uiMode: Int): Preferences = mutablePreferencesOf(
        uiModeKey to uiMode,
        stringPreferencesKey("app_language_preference") to "en",
        stringPreferencesKey("app_distance_unit_preference") to "imperial",
        booleanPreferencesKey("app_respect_text_scale") to false,
        intPreferencesKey("app_theme_mode") to 2,
        floatPreferencesKey("app_page_scale") to 1.25f,
        floatingBarKey to true,
        unknownKey to "preserve values outside this migration",
    )

    private fun file(name: String): File = File(temporaryFolder.root, "$name.preferences_pb")

    private suspend fun seed(file: File, preferences: Preferences) {
        withStore(file, upgrade = false) { store -> store.updateData { preferences } }
    }

    private suspend fun <T> withStore(
        file: File,
        upgrade: Boolean = true,
        block: suspend (DataStore<Preferences>) -> T,
    ): T {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(
            migrations = if (upgrade) listOf(NinebotUiModeMigration) else emptyList(),
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { file },
        )
        return try {
            block(store)
        } finally {
            job.cancelAndJoin()
        }
    }

    companion object {
        private val uiModeKey = intPreferencesKey("app_ui_mode")
        private val floatingBarKey = booleanPreferencesKey("app_floating_bottom_bar")
        private val unknownKey = stringPreferencesKey("future_preference")
    }
}
