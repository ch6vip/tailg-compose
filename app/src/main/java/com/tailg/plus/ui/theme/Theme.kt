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
import androidx.compose.runtime.remember
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
 * The supported UI style is 九号出行, with light/dark chosen in 主题设置.
 * Stored as an Int in [com.tailg.plus.data.preferences.AppPreferencesService];
 * retired or unknown style values fall back to the supported style.
 */
enum class UiMode(val value: Int) {
    NINEBOT(2);

    companion object {
        fun fromValue(value: Int): UiMode = entries.firstOrNull { it.value == value } ?: NINEBOT
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

/** Static fallback scheme (dark) — cobalt on charcoal, not neon green. */
val CyberDarkColorScheme = darkColorScheme(
    primary = AppColorsDark.primary,
    onPrimary = Color(0xFF082044),
    primaryContainer = Color(0xFF1A335C),
    onPrimaryContainer = Color(0xFFD7E6FF),
    secondary = AppColorsDark.accentSky,
    onSecondary = Color(0xFF082044),
    secondaryContainer = Color(0xFF1A335C),
    onSecondaryContainer = Color(0xFFD7E6FF),
    tertiary = AppColorsDark.accentOrange,
    onTertiary = Color(0xFF2A1C08),
    tertiaryContainer = Color(0xFF3D2E1C),
    onTertiaryContainer = Color(0xFFFFE8CC),
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
    surfaceContainerHighest = Color(0xFF2C333E),
    outline = Color(0xFF5C6672),
    outlineVariant = AppColorsDark.outlineVariant,
    error = AppColorsDark.danger,
    onError = Color(0xFF3B090C),
    errorContainer = Color(0xFF4A1518),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = AppColorsDark.textPrimary,
    inverseOnSurface = Color(0xFF1C2128),
    inversePrimary = Color(0xFF1F6FEB),
    scrim = Color.Black,
)

/** Static fallback scheme (light). */
val CyberLightColorScheme = lightColorScheme(
    primary = AppColorsLight.primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6F0FF),
    onPrimaryContainer = Color(0xFF082044),
    secondary = AppColorsLight.accentSky,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6F0FF),
    onSecondaryContainer = Color(0xFF082044),
    tertiary = AppColorsLight.accentOrange,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF8EBD9),
    onTertiaryContainer = Color(0xFF4A2C0C),
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
    surfaceContainerHighest = Color(0xFFE3E8EE),
    outline = Color(0xFF8E97A3),
    outlineVariant = Color(0xFFE3E8EE),
    error = AppColorsLight.danger,
    onError = Color.White,
    errorContainer = Color(0xFFFDECEC),
    onErrorContainer = Color(0xFF3B090C),
    inverseSurface = Color(0xFF1C2128),
    inverseOnSurface = Color(0xFFF3F5F7),
    inversePrimary = Color(0xFF7EAEFF),
    scrim = Color.Black,
)

/**
 * Active light scheme. Cool mist canvas, white cards, ink text, cobalt
 * accent, warm amber only for mileage / ride highlights.
 */
val NinebotLightColorScheme = lightColorScheme(
    primary = Color(0xFF1F6FEB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6F0FF),
    onPrimaryContainer = Color(0xFF082044),
    secondary = Color(0xFF12161C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8EDF2),
    onSecondaryContainer = Color(0xFF12161C),
    tertiary = Color(0xFFE08A3C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF8EBD9),
    onTertiaryContainer = Color(0xFF4A2C0C),
    background = Color(0xFFF3F5F8),
    onBackground = Color(0xFF12161C),
    surface = Color.White,
    onSurface = Color(0xFF12161C),
    surfaceVariant = Color(0xFFEEF1F5),
    onSurfaceVariant = Color(0xFF5E6774),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEEF1F5),
    surfaceContainerHighest = Color(0xFFE8EDF2),
    outline = Color(0xFF8E97A3),
    outlineVariant = Color(0xFFE3E8EE),
    error = Color(0xFFE23B3B),
    onError = Color.White,
    errorContainer = Color(0xFFFDECEC),
    onErrorContainer = Color(0xFF3B090C),
    inverseSurface = Color(0xFF1C2128),
    inverseOnSurface = Color(0xFFF3F5F7),
    inversePrimary = Color(0xFF7EAEFF),
    scrim = Color.Black,
)

/** Active dark scheme — charcoal page, lifted cards, the same cobalt. */
val NinebotDarkColorScheme = darkColorScheme(
    primary = Color(0xFF7EAEFF),
    onPrimary = Color(0xFF082044),
    primaryContainer = Color(0xFF1A335C),
    onPrimaryContainer = Color(0xFFD7E6FF),
    secondary = Color(0xFFE8ECF1),
    onSecondary = Color(0xFF12161C),
    secondaryContainer = Color(0xFF242A33),
    onSecondaryContainer = Color(0xFFE8ECF1),
    tertiary = Color(0xFFF0B27A),
    onTertiary = Color(0xFF2A1C08),
    tertiaryContainer = Color(0xFF3D2E1C),
    onTertiaryContainer = Color(0xFFFFE8CC),
    background = Color(0xFF0F1216),
    onBackground = Color(0xFFF3F5F7),
    surface = Color(0xFF181C22),
    onSurface = Color(0xFFF3F5F7),
    surfaceVariant = Color(0xFF242A33),
    onSurfaceVariant = Color(0xFFA8B0BA),
    surfaceContainerLowest = Color(0xFF0F1216),
    surfaceContainerLow = Color(0xFF151920),
    surfaceContainer = Color(0xFF1C2128),
    surfaceContainerHigh = Color(0xFF242A33),
    surfaceContainerHighest = Color(0xFF2C333E),
    outline = Color(0xFF5C6672),
    outlineVariant = Color(0xFF2A313B),
    error = Color(0xFFFF8A84),
    onError = Color(0xFF3B090C),
    errorContainer = Color(0xFF4A1518),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFF3F5F7),
    inverseOnSurface = Color(0xFF1C2128),
    inversePrimary = Color(0xFF1F6FEB),
    scrim = Color.Black,
)

private fun uiModeColorScheme(uiMode: UiMode, isDark: Boolean): ColorScheme =
    when (uiMode) {
        UiMode.NINEBOT -> if (isDark) NinebotDarkColorScheme else NinebotLightColorScheme
    }

/**
 * The active UI skin — set by [TailgTheme] alongside [LocalCyberPalette].
 * Screens that render a skin-specific layout (e.g. the 九号 control home)
 * branch on this instead of re-reading the preference store.
 */
val LocalUiMode = staticCompositionLocalOf { UiMode.NINEBOT }

/**
 * Root theme. Resolves the persisted UI style and theme mode
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
    val uiModeValue by prefs.uiMode.collectAsStateWithLifecycle(initialValue = UiMode.NINEBOT.value)
    val pageScale by prefs.pageScale.collectAsStateWithLifecycle(initialValue = 1.0f)
    LaunchedEffect(prefs) {
        try {
            prefs.init()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            timber.log.Timber.tag("TailgTheme").w(e, "Appearance preferences could not be loaded")
        }
    }

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
    // Typography colors must follow the resolved scheme, not a fixed light
    // palette, or MaterialTheme.typography roles render dark ink on dark
    // surfaces (see Type.kt). Keyed on the target scheme so the type styles are
    // rebuilt only on a theme switch, not on every animated color frame.
    val typography = remember(scheme) { tailgTypography(scheme.toCyberPalette()) }

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
        LocalCyberPalette provides palette,
        LocalUiMode provides uiMode,
        LocalDensity provides scaledDensity,
    ) {
        MaterialExpressiveTheme(
            colorScheme = animatedScheme,
            motionScheme = MotionScheme.expressive(),
            typography = typography,
            shapes = TailgShapes,
            content = content,
        )
    }
}
