package com.tailg.plus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Dark VOID scheme — primary green neon, deep charcoal surfaces. */
private val DarkColorScheme = darkColorScheme(
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

/** Light companion scheme. */
private val LightColorScheme = lightColorScheme(
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
 * Root theme — VOID COCKPIT tokens mapped onto Material 3.
 * Fixed to light color scheme (Dart: ThemeMode.light). Dark scheme is kept
 * for reference but never activated by default.
 */
@Composable
fun TailgTheme(
    // Dart: ThemeMode.light — fixed light theme (the app is Cyber-light only).
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = TailgTypography,
        shapes = TailgShapes,
        content = content,
    )
}
