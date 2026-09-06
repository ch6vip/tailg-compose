package com.tailg.plus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tailg.plus.di.rememberTailgEntryPoint

/**
 * UI style — the selectable skins in 设置 → 界面风格. Both are static brand
 * schemes (light/dark follows the theme mode chosen in 主题设置):
 *  - [CYBER]: VOID COCKPIT — Tailg brand green neon.
 *  - [NINEBOT]: 九号出行 — ink navy + electric blue on mist gray / near black.
 * Stored as an Int in [com.tailg.plus.data.preferences.AppPreferencesService];
 * value 2 chosen so stale MONET(1) entries fall back to the CYBER default.
 */
enum class UiMode(val value: Int) {
    CYBER(0),
    NINEBOT(2);

    companion object {
        fun fromValue(value: Int): UiMode = entries.firstOrNull { it.value == value } ?: CYBER
    }
}

/**
 * Theme mode — mirrors KernelSU's `ColorMode`. Stored as an Int in
 * [com.tailg.plus.data.preferences.AppPreferencesService].
 */
enum class ColorMode(val value: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2),
    DARK_AMOLED(3);

    companion object {
        fun fromValue(value: Int): ColorMode = entries.firstOrNull { it.value == value } ?: SYSTEM
    }

    val isSystem: Boolean get() = this == SYSTEM
    val isDark: Boolean get() = this == DARK || this == DARK_AMOLED
    val isAmoled: Boolean get() = this == DARK_AMOLED
}

/** Static Cyber brand scheme (dark) — primary green neon, deep charcoal surfaces. */
val CyberDarkColorScheme = darkColorScheme(
    primary = AppColorsDark.primary,
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF0F3B31),
    onPrimaryContainer = Color(0xFFB3FFE0),
    secondary = AppColorsDark.accentSky,
    onSecondary = Color(0xFF003355),
    secondaryContainer = Color(0xFF123044),
    onSecondaryContainer = Color(0xFFC9E8FF),
    tertiary = AppColorsDark.accentViolet,
    onTertiary = Color(0xFF241A5C),
    tertiaryContainer = Color(0xFF33276B),
    onTertiaryContainer = Color(0xFFE4DEFF),
    background = AppColorsDark.pageBg,
    onBackground = AppColorsDark.textPrimary,
    surface = AppColorsDark.surface,
    onSurface = AppColorsDark.textPrimary,
    surfaceVariant = AppColorsDark.surfaceContainerHigh,
    onSurfaceVariant = AppColorsDark.textSecondary,
    surfaceContainerLowest = AppColorsDark.pageBg,
    surfaceContainerLow = AppColorsDark.surfaceContainerLow,
    surfaceContainer = AppColorsDark.surface,
    surfaceContainerHigh = AppColorsDark.surfaceContainerHigh,
    surfaceContainerHighest = Color(0xFF232C3E),
    outline = AppColorsDark.outlineVariant,
    outlineVariant = AppColorsDark.outlineVariant,
    error = AppColorsDark.danger,
    onError = Color(0xFF2A0008),
    errorContainer = Color(0xFF3B0D18),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = AppColorsDark.textPrimary,
    inverseOnSurface = Color(0xFF2A2F3A),
    inversePrimary = Color(0xFF006B4F),
    scrim = Color.Black,
)

/** Static Cyber brand scheme (light companion). */
val CyberLightColorScheme = lightColorScheme(
    primary = AppColorsLight.primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB3F2E0),
    onPrimaryContainer = Color(0xFF00382B),
    secondary = AppColorsLight.accentSky,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6EBFF),
    onSecondaryContainer = Color(0xFF0A3B5C),
    tertiary = AppColorsLight.accentViolet,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE4DEFF),
    onTertiaryContainer = Color(0xFF241A5C),
    background = AppColorsLight.pageBg,
    onBackground = AppColorsLight.textPrimary,
    surface = AppColorsLight.surface,
    onSurface = AppColorsLight.textPrimary,
    surfaceVariant = AppColorsLight.surfaceContainerHigh,
    onSurfaceVariant = AppColorsLight.textSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = AppColorsLight.surfaceContainerLow,
    surfaceContainer = AppColorsLight.surface,
    surfaceContainerHigh = AppColorsLight.surfaceContainerHigh,
    surfaceContainerHighest = Color(0xFFDDE3EA),
    outline = Color(0xFF8A93A5),
    outlineVariant = AppColorsLight.outlineVariant,
    error = AppColorsLight.danger,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF2A0008),
    inverseSurface = Color(0xFF2A2F3A),
    inverseOnSurface = Color(0xFFF4F6FA),
    inversePrimary = Color(0xFF00A57C),
    scrim = Color.Black,
)

/**
 * 九号出行 scheme (light) — extracted from the reference shots: mist
 * lavender-gray page, pure white large-radius cards, ink-navy line icons and
 * an electric-blue accent (battery bar / controls), warm orange mileage tint.
 */
val NinebotLightColorScheme = lightColorScheme(
    primary = Color(0xFF3D7BFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FF),
    onPrimaryContainer = Color(0xFF0B2E6E),
    secondary = Color(0xFF1B2438),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7EAF3),
    onSecondaryContainer = Color(0xFF1B2438),
    tertiary = Color(0xFFE8A15C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF8E4C6),
    onTertiaryContainer = Color(0xFF4A2E10),
    background = Color(0xFFDDE0EB),
    onBackground = Color(0xFF1B2438),
    surface = Color.White,
    onSurface = Color(0xFF1B2438),
    surfaceVariant = Color(0xFFE9EBF3),
    onSurfaceVariant = Color(0xFF8A90A2),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3F4F9),
    surfaceContainer = Color(0xFFEDEFF6),
    surfaceContainerHigh = Color(0xFFE4E7F0),
    surfaceContainerHighest = Color(0xFFD8DCE8),
    outline = Color(0xFF9AA1B4),
    outlineVariant = Color(0xFFE0E3EE),
    error = Color(0xFFE5484D),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E4),
    onErrorContainer = Color(0xFF5C0A0E),
    inverseSurface = Color(0xFF2A3040),
    inverseOnSurface = Color(0xFFF1F2F8),
    inversePrimary = Color(0xFF7FA8FF),
    scrim = Color.Black,
)

/** 九号出行 scheme (dark) — near-black page, dark gray cards, same blue. */
val NinebotDarkColorScheme = darkColorScheme(
    primary = Color(0xFF6B9AFF),
    onPrimary = Color(0xFF0A2A5E),
    primaryContainer = Color(0xFF1E3A75),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFE3E7F2),
    onSecondary = Color(0xFF171E2E),
    secondaryContainer = Color(0xFF232A3C),
    onSecondaryContainer = Color(0xFFDCE2F2),
    tertiary = Color(0xFFF0B57A),
    onTertiary = Color(0xFF3D2508),
    tertiaryContainer = Color(0xFF4E3512),
    onTertiaryContainer = Color(0xFFFFE8CC),
    background = Color(0xFF0B0D12),
    onBackground = Color(0xFFF1F3F8),
    surface = Color(0xFF14161C),
    onSurface = Color(0xFFF1F3F8),
    surfaceVariant = Color(0xFF1E2129),
    onSurfaceVariant = Color(0xFF9AA0B0),
    surfaceContainerLowest = Color(0xFF0B0D12),
    surfaceContainerLow = Color(0xFF14161C),
    surfaceContainer = Color(0xFF1A1D24),
    surfaceContainerHigh = Color(0xFF22252E),
    surfaceContainerHighest = Color(0xFF2B2F3A),
    outline = Color(0xFF4A5060),
    outlineVariant = Color(0xFF232733),
    error = Color(0xFFFF7A7F),
    onError = Color(0xFF4C0A0E),
    errorContainer = Color(0xFF5C1A1E),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFF1F3F8),
    inverseOnSurface = Color(0xFF1A1D24),
    inversePrimary = Color(0xFF2E62D9),
    scrim = Color.Black,
)

private fun uiModeColorScheme(uiMode: UiMode, isDark: Boolean): ColorScheme =
    when (uiMode) {
        UiMode.CYBER -> if (isDark) CyberDarkColorScheme else CyberLightColorScheme
        UiMode.NINEBOT -> if (isDark) NinebotDarkColorScheme else NinebotLightColorScheme
    }

/**
 * The active UI skin — set by [TailgTheme] alongside [LocalCyberPalette].
 * Screens that render a skin-specific layout (e.g. the 九号 control home)
 * branch on this instead of re-reading the preference store.
 */
val LocalUiMode = staticCompositionLocalOf { UiMode.CYBER }

/**
 * Root theme. Resolves the persisted UI style (Cyber / 九号) and theme mode
 * (system / light / dark), maps the resulting scheme onto the semantic
 * [CyberPalette] and provides it via [LocalCyberPalette]. Mirrors KernelSU's
 * expressive motion: animated colour transitions + global page scale.
 */
@Composable
fun TailgTheme(
    content: @Composable () -> Unit,
) {
    val prefs = rememberTailgEntryPoint().appPreferences()
    val themeMode by prefs.themeMode.collectAsStateWithLifecycle(initialValue = ColorMode.SYSTEM.value)
    val uiModeValue by prefs.uiMode.collectAsStateWithLifecycle(initialValue = UiMode.CYBER.value)
    val pageScale by prefs.pageScale.collectAsStateWithLifecycle(initialValue = 1.0f)
    LaunchedEffect(Unit) { prefs.init() }

    val colorMode = ColorMode.fromValue(themeMode)
    val isDark = when (colorMode) {
        ColorMode.DARK, ColorMode.DARK_AMOLED -> true
        ColorMode.LIGHT -> false
        ColorMode.SYSTEM -> isSystemInDarkTheme()
    }

    val uiMode = UiMode.fromValue(uiModeValue)
    val scheme = uiModeColorScheme(uiMode, isDark)
        .amoledBackground(colorMode.isAmoled)
    val animatedScheme = scheme.animateAsState()
    val palette = animatedScheme.toCyberPalette()

    // Keep the system bars legible as the theme flips between light and dark:
    // dark page → light status/nav icons, and a nav bar tinted to the page bg.
    val view = LocalView.current
    SideEffect {
        if (view.isInEditMode) return@SideEffect
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
        window.navigationBarColor = palette.pageBg.toArgb()
    }

    // KernelSU's 界面缩放: scale the whole content by overriding the root
    // density (font scale is preserved).
    val systemDensity = LocalDensity.current
    val scaledDensity = Density(systemDensity.density * pageScale, systemDensity.fontScale)

    CompositionLocalProvider(
        LocalUiMode provides uiMode,
        LocalDensity provides scaledDensity,
    ) {
        MaterialExpressiveTheme(
            colorScheme = animatedScheme,
            motionScheme = MotionScheme.expressive(),
            typography = TailgTypography,
            shapes = TailgShapes,
            content = content,
        )
    }
}
