package com.tailg.plus.data.preferences

import android.content.Context
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

private val Context.dataStore by preferencesDataStore(name = "app_preferences")

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
class AppPreferencesService(
    private val context: Context,
    private val logService: LogService = LogService(),
) {

    private val _language = MutableStateFlow(AppLanguagePreference.System)
    val language: StateFlow<AppLanguagePreference> = _language.asStateFlow()

    private val _distanceUnit = MutableStateFlow(DistanceUnitPreference.Metric)
    val distanceUnit: StateFlow<DistanceUnitPreference> = _distanceUnit.asStateFlow()

    private val _respectTextScale = MutableStateFlow(true)
    val respectSystemTextScale: StateFlow<Boolean> = _respectTextScale.asStateFlow()

    // Theme / appearance — Int values mirror `com.tailg.plus.ui.theme.ColorMode`.
    private val _themeMode = MutableStateFlow(0)
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    // Key colour ARGB int; 0 = follow system (wallpaper) dynamic colour.
    private val _keyColor = MutableStateFlow(0)
    val keyColor: StateFlow<Int> = _keyColor.asStateFlow()

    // PaletteStyle name string (com.materialkolor.PaletteStyle).
    private val _colorStyle = MutableStateFlow("TonalSpot")
    val colorStyle: StateFlow<String> = _colorStyle.asStateFlow()

    // ColorSpec.SpecVersion name string (com.materialkolor.dynamiccolor.ColorSpec).
    private val _colorSpec = MutableStateFlow("SPEC_2025")
    val colorSpec: StateFlow<String> = _colorSpec.asStateFlow()

    // Global page scale (KernelSU 界面缩放) — multiplies the root density.
    private val _pageScale = MutableStateFlow(1.0f)
    val pageScale: StateFlow<Float> = _pageScale.asStateFlow()

    private var initialized = false

    suspend fun init() {
        if (initialized) return
        val prefs = context.dataStore.data.first()
        _language.value = AppLanguagePreference.fromValue(prefs[KEY_LANGUAGE])
        _distanceUnit.value = DistanceUnitPreference.fromValue(prefs[KEY_DISTANCE_UNIT])
        _respectTextScale.value = prefs[KEY_RESPECT_TEXT_SCALE] ?: true
        _themeMode.value = prefs[KEY_THEME_MODE] ?: 0
        _keyColor.value = prefs[KEY_KEY_COLOR] ?: 0
        _colorStyle.value = prefs[KEY_COLOR_STYLE] ?: "TonalSpot"
        _colorSpec.value = prefs[KEY_COLOR_SPEC] ?: "SPEC_2025"
        _pageScale.value = prefs[KEY_PAGE_SCALE] ?: 1.0f
        initialized = true
    }

    suspend fun setLanguage(preference: AppLanguagePreference) {
        if (!initialized) init()
        runCatching {
            context.dataStore.edit { it[KEY_LANGUAGE] = preference.value }
            _language.value = preference
        }.onFailure { logService.operation("setLanguage failed", detail = it.toString()) }
    }

    suspend fun setDistanceUnit(preference: DistanceUnitPreference) {
        if (!initialized) init()
        runCatching {
            context.dataStore.edit { it[KEY_DISTANCE_UNIT] = preference.value }
            _distanceUnit.value = preference
        }.onFailure { logService.operation("setDistanceUnit failed", detail = it.toString()) }
    }

    suspend fun setRespectSystemTextScale(value: Boolean) {
        if (!initialized) init()
        runCatching {
            context.dataStore.edit { it[KEY_RESPECT_TEXT_SCALE] = value }
            _respectTextScale.value = value
        }.onFailure { logService.operation("setRespectSystemTextScale failed", detail = it.toString()) }
    }

    suspend fun setThemeMode(value: Int) {
        if (!initialized) init()
        runCatching {
            context.dataStore.edit { it[KEY_THEME_MODE] = value }
            _themeMode.value = value
        }.onFailure { logService.operation("setThemeMode failed", detail = it.toString()) }
    }

    suspend fun setKeyColor(value: Int) {
        if (!initialized) init()
        runCatching {
            context.dataStore.edit { it[KEY_KEY_COLOR] = value }
            _keyColor.value = value
        }.onFailure { logService.operation("setKeyColor failed", detail = it.toString()) }
    }

    suspend fun setColorStyle(value: String) {
        if (!initialized) init()
        runCatching {
            context.dataStore.edit { it[KEY_COLOR_STYLE] = value }
            _colorStyle.value = value
        }.onFailure { logService.operation("setColorStyle failed", detail = it.toString()) }
    }

    suspend fun setColorSpec(value: String) {
        if (!initialized) init()
        runCatching {
            context.dataStore.edit { it[KEY_COLOR_SPEC] = value }
            _colorSpec.value = value
        }.onFailure { logService.operation("setColorSpec failed", detail = it.toString()) }
    }

    suspend fun setPageScale(value: Float) {
        if (!initialized) init()
        runCatching {
            context.dataStore.edit { it[KEY_PAGE_SCALE] = value }
            _pageScale.value = value
        }.onFailure { logService.operation("setPageScale failed", detail = it.toString()) }
    }

    companion object {
        private val KEY_LANGUAGE = stringPreferencesKey("app_language_preference")
        private val KEY_DISTANCE_UNIT = stringPreferencesKey("app_distance_unit_preference")
        private val KEY_RESPECT_TEXT_SCALE = booleanPreferencesKey("app_respect_text_scale")
        private val KEY_THEME_MODE = intPreferencesKey("app_theme_mode")
        private val KEY_KEY_COLOR = intPreferencesKey("app_key_color")
        private val KEY_COLOR_STYLE = stringPreferencesKey("app_color_style")
        private val KEY_COLOR_SPEC = stringPreferencesKey("app_color_spec")
        private val KEY_PAGE_SCALE = floatPreferencesKey("app_page_scale")
    }
}
