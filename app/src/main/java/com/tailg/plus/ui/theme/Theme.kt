package com.tailg.plus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.tailg.plus.di.rememberTailgEntryPoint

/**
 * UI style — mirrors KernelSU's `UiMode` (Material/Miuix): the static Cyber
 * brand look vs the dynamic Monet (Material You) scheme. Stored as an Int in
 * [com.tailg.plus.data.preferences.AppPreferencesService].
 */
enum class UiMode(val value: Int) {
    CYBER(0),
    MONET(1);

    companion object {
        fun fromValue(value: Int): UiMode = entries.firstOrNull { it.value == value } ?: MONET
    }
}

/**
 * Theme mode — mirrors KernelSU's `ColorMode` minus the Miuix Monet variants
 * (Tailg has no Miuix style). Stored as an Int in
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

/** Seed colour swatches for the key-colour picker (Material primary hues). */
val keyColorOptions = listOf(
    Color(0xFFF44336).toArgb(),
    Color(0xFFE91E63).toArgb(),
    Color(0xFF9C27B0).toArgb(),
    Color(0xFF673AB7).toArgb(),
    Color(0xFF3F51B5).toArgb(),
    Color(0xFF2196F3).toArgb(),
    Color(0xFF00BCD4).toArgb(),
    Color(0xFF009688).toArgb(),
    Color(0xFF4FAF50).toArgb(),
    Color(0xFFFFEB3B).toArgb(),
    Color(0xFFFFC107).toArgb(),
    Color(0xFFFF9800).toArgb(),
    Color(0xFF795548).toArgb(),
    Color(0xFF607D8F).toArgb(),
    Color(0xFFFF9CA8).toArgb(),
)

/** Port of KernelSU `ui/theme/Theme.kt` spec-version gating. */
val PaletteStyle.supportsSpec2025: Boolean
    get() = this == PaletteStyle.TonalSpot ||
            this == PaletteStyle.Neutral ||
            this == PaletteStyle.Vibrant ||
            this == PaletteStyle.Expressive

fun ColorSpec.SpecVersion.effectiveFor(style: PaletteStyle): ColorSpec.SpecVersion =
    if (this == ColorSpec.SpecVersion.SPEC_2025 && !style.supportsSpec2025) {
        ColorSpec.SpecVersion.SPEC_2021
    } else {
        this
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
 * Builds the active Material You [ColorScheme]. When [seedColor] is
 * [Color.Unspecified] the system wallpaper's dynamic colour is used (Android 12+
 * Monet), otherwise the custom key colour drives the scheme.
 */
@Composable
fun rememberTailgColorScheme(
    seedColor: Color,
    isDark: Boolean,
    isAmoled: Boolean,
    paletteStyle: PaletteStyle,
    colorSpec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2025,
): ColorScheme {
    val context = LocalContext.current
    val seed = if (seedColor == Color.Unspecified) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary
        } else {
            // Pre-Android 12 has no Monet wallpaper colour; fall back to the
            // Cyber brand blue so "follow system" still yields a coherent seed.
            if (isDark) DarkCyberPalette.primary else LightCyberPalette.primary
        }
    } else {
        seedColor
    }
    return rememberDynamicColorScheme(
        seedColor = seed,
        isDark = isDark,
        isAmoled = isAmoled,
        style = paletteStyle,
        specVersion = colorSpec.effectiveFor(paletteStyle),
    ).amoledBackground(isAmoled)
}

/**
 * Root theme. Resolves the persisted theme mode / key colour / palette style /
 * colour spec, derives a dynamic Material You [ColorScheme], maps it onto the
 * semantic [CyberPalette] and provides it via [LocalCyberPalette] so every screen
 * (which still reads [CyberHomeColors]) re-themes automatically. Mirrors
 * KernelSU's `MaterialKernelSUTheme`: expressive motion + animated colour
 * transitions + global page scale.
 */
@Composable
fun TailgTheme(
    content: @Composable () -> Unit,
) {
    val prefs = rememberTailgEntryPoint().appPreferences()
    val themeMode by prefs.themeMode.collectAsStateWithLifecycle(initialValue = ColorMode.SYSTEM.value)
    val uiModeValue by prefs.uiMode.collectAsStateWithLifecycle(initialValue = UiMode.MONET.value)
    val keyColor by prefs.keyColor.collectAsStateWithLifecycle(initialValue = 0)
    val colorStyleName by prefs.colorStyle.collectAsStateWithLifecycle(initialValue = PaletteStyle.TonalSpot.name)
    val colorSpecName by prefs.colorSpec.collectAsStateWithLifecycle(initialValue = ColorSpec.SpecVersion.SPEC_2025.name)
    val pageScale by prefs.pageScale.collectAsStateWithLifecycle(initialValue = 1.0f)
    LaunchedEffect(Unit) { prefs.init() }

    val colorMode = ColorMode.fromValue(themeMode)
    val isDark = when (colorMode) {
        ColorMode.DARK, ColorMode.DARK_AMOLED -> true
        ColorMode.LIGHT -> false
        ColorMode.SYSTEM -> isSystemInDarkTheme()
    }
    val paletteStyle = try {
        PaletteStyle.valueOf(colorStyleName)
    } catch (_: Exception) {
        PaletteStyle.TonalSpot
    }
    val colorSpec = try {
        ColorSpec.SpecVersion.valueOf(colorSpecName)
    } catch (_: Exception) {
        ColorSpec.SpecVersion.SPEC_2025
    }
    val seed = if (keyColor == 0) Color.Unspecified else Color(keyColor)
    val uiMode = UiMode.fromValue(uiModeValue)

    // Cyber = static brand scheme (light/dark still follows the theme mode);
    // Monet = KernelSU-style dynamic scheme from key colour / wallpaper.
    val scheme = if (uiMode == UiMode.CYBER) {
        (if (isDark) CyberDarkColorScheme else CyberLightColorScheme)
            .amoledBackground(colorMode.isAmoled)
    } else {
        rememberTailgColorScheme(
            seedColor = seed,
            isDark = isDark,
            isAmoled = colorMode.isAmoled,
            paletteStyle = paletteStyle,
            colorSpec = colorSpec,
        )
    }
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

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialExpressiveTheme(
            colorScheme = animatedScheme,
            motionScheme = MotionScheme.expressive(),
            typography = TailgTypography,
            shapes = TailgShapes,
            content = content,
        )
    }
}
