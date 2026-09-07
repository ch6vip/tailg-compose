package com.tailg.plus.data.preferences

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tailg.plus.log.LogService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val NINEBOT_UI_MODE = 2
private val KEY_UI_MODE = intPreferencesKey("app_ui_mode")

private val Context.dataStore by preferencesDataStore(
    name = "app_preferences",
    produceMigrations = { listOf(NinebotUiModeMigration) },
)

/** Upgrade only the retired appearance identifier before any preferences are published. */
internal object NinebotUiModeMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData.asMap()[KEY_UI_MODE] != NINEBOT_UI_MODE

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply { this[KEY_UI_MODE] = NINEBOT_UI_MODE }.toPreferences()

    override suspend fun cleanUp() = Unit
}

/** Port of Dart enum `AppLanguagePreference` (app_preferences_service.dart). */
enum class AppLanguagePreference(val value: String, val label: String) {
    System("system", "跟随系统"),
    SimplifiedChinese("zh-Hans", "简体中文"),
    English("en", "English");

    companion object {
        fun fromValue(value: String?): AppLanguagePreference =
            entries.firstOrNull { it.value == value } ?: System
    }
}

/** Port of Dart enum `DistanceUnitPreference` (app_preferences_service.dart). */
enum class DistanceUnitPreference(val value: String, val label: String, val hint: String) {
    Metric("metric", "公制", "km / m"),
    Imperial("imperial", "英制", "mi / ft");

    companion object {
        fun fromValue(value: String?): DistanceUnitPreference =
            entries.firstOrNull { it.value == value } ?: Metric
    }
}

/**
 * Port of Dart `AppPreferencesService` (app_preferences_service.dart).
 * SharedPreferences → DataStore Preferences; broadcast Streams → StateFlow.
 */
class AppPreferencesService internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val logService: LogService = LogService(),
) {
    constructor(context: Context, logService: LogService = LogService()) : this(context.dataStore, logService)

    private val _language = MutableStateFlow(AppLanguagePreference.System)
    val language: StateFlow<AppLanguagePreference> = _language.asStateFlow()

    private val _distanceUnit = MutableStateFlow(DistanceUnitPreference.Metric)
    val distanceUnit: StateFlow<DistanceUnitPreference> = _distanceUnit.asStateFlow()

    private val _respectTextScale = MutableStateFlow(true)
    val respectSystemTextScale: StateFlow<Boolean> = _respectTextScale.asStateFlow()

    // Theme / appearance — Int values mirror `com.tailg.plus.ui.theme.ColorMode`.
    private val _themeMode = MutableStateFlow(0)
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    // The Ninebot style keeps its existing persisted identifier, including before init().
    private val _uiMode = MutableStateFlow(NINEBOT_UI_MODE)
    val uiMode: StateFlow<Int> = _uiMode.asStateFlow()

    // Global page scale (KernelSU 界面缩放) — multiplies the root density.
    private val _pageScale = MutableStateFlow(1.0f)
    val pageScale: StateFlow<Float> = _pageScale.asStateFlow()

    private val _floatingBottomBar = MutableStateFlow(false)
    val floatingBottomBar: StateFlow<Boolean> = _floatingBottomBar.asStateFlow()

    private var initialized = false
    private val initMutex = Mutex()
    private val mutationMutex = Mutex()

    suspend fun init() = initMutex.withLock {
        if (initialized) return@withLock
        val prefs = com.tailg.plus.data.store.withDataStoreReadTimeout { dataStore.data.first() }
        _language.value = AppLanguagePreference.fromValue(prefs[KEY_LANGUAGE])
        _distanceUnit.value = DistanceUnitPreference.fromValue(prefs[KEY_DISTANCE_UNIT])
        _respectTextScale.value = prefs[KEY_RESPECT_TEXT_SCALE] ?: true
        _themeMode.value = prefs[KEY_THEME_MODE] ?: 0
        _uiMode.value = normalizeUiMode(prefs[KEY_UI_MODE])
        _pageScale.value = normalizePageScale(prefs[KEY_PAGE_SCALE] ?: 1.0f)
        _floatingBottomBar.value = prefs[KEY_FLOATING_BOTTOM_BAR] ?: false
        initialized = true
    }

    suspend fun setLanguage(preference: AppLanguagePreference) = mutationMutex.withLock {
        init()
        runCatching {
            dataStore.edit { it[KEY_LANGUAGE] = preference.value }
            _language.value = preference
        }.onFailure { if (it is CancellationException) throw it; logService.operation("setLanguage failed", detail = it.toString()) }
    }

    suspend fun setDistanceUnit(preference: DistanceUnitPreference) = mutationMutex.withLock {
        init()
        runCatching {
            dataStore.edit { it[KEY_DISTANCE_UNIT] = preference.value }
            _distanceUnit.value = preference
        }.onFailure { if (it is CancellationException) throw it; logService.operation("setDistanceUnit failed", detail = it.toString()) }
    }

    suspend fun setRespectSystemTextScale(value: Boolean) = mutationMutex.withLock {
        init()
        runCatching {
            dataStore.edit { it[KEY_RESPECT_TEXT_SCALE] = value }
            _respectTextScale.value = value
        }.onFailure { if (it is CancellationException) throw it; logService.operation("setRespectSystemTextScale failed", detail = it.toString()) }
    }

    suspend fun setThemeMode(value: Int) = mutationMutex.withLock {
        init()
        runCatching {
            dataStore.edit { it[KEY_THEME_MODE] = value }
            _themeMode.value = value
        }.onFailure { if (it is CancellationException) throw it; logService.operation("setThemeMode failed", detail = it.toString()) }
    }

    suspend fun setUiMode(value: Int) = mutationMutex.withLock {
        init()
        val supportedMode = normalizeUiMode(value)
        runCatching {
            dataStore.edit { it[KEY_UI_MODE] = supportedMode }
            _uiMode.value = supportedMode
        }.onFailure { if (it is CancellationException) throw it; logService.operation("setUiMode failed", detail = it.toString()) }
    }

    suspend fun setPageScale(value: Float) = mutationMutex.withLock {
        init()
        runCatching {
            dataStore.edit { it[KEY_PAGE_SCALE] = normalizePageScale(value) }
            _pageScale.value = normalizePageScale(value)
        }.onFailure { if (it is CancellationException) throw it; logService.operation("setPageScale failed", detail = it.toString()) }
    }

    suspend fun setFloatingBottomBar(value: Boolean) = mutationMutex.withLock {
        init()
        runCatching {
            dataStore.edit { it[KEY_FLOATING_BOTTOM_BAR] = value }
            _floatingBottomBar.value = value
        }.onFailure { if (it is CancellationException) throw it; logService.operation("setFloatingBottomBar failed", detail = it.toString()) }
    }

    private fun normalizePageScale(value: Float): Float =
        if (value.isFinite()) value.coerceIn(0.5f, 2.0f) else 1.0f

    private fun normalizeUiMode(value: Int?): Int =
        value?.takeIf { it == NINEBOT_UI_MODE } ?: NINEBOT_UI_MODE

    companion object {
        private val KEY_LANGUAGE = stringPreferencesKey("app_language_preference")
        private val KEY_DISTANCE_UNIT = stringPreferencesKey("app_distance_unit_preference")
        private val KEY_RESPECT_TEXT_SCALE = booleanPreferencesKey("app_respect_text_scale")
        private val KEY_THEME_MODE = intPreferencesKey("app_theme_mode")
        private val KEY_PAGE_SCALE = floatPreferencesKey("app_page_scale")
        private val KEY_FLOATING_BOTTOM_BAR = booleanPreferencesKey("app_floating_bottom_bar")
    }
}
