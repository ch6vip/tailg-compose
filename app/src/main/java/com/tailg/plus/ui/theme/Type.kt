package com.tailg.plus.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography mapped onto Material 3 type roles.
 *
 * Colors are taken from the ACTIVE [CyberPalette] passed by [TailgTheme], so the
 * same type roles stay legible in light AND dark mode. The colors used to be
 * baked from [LightCyberPalette]; because `MaterialTheme.typography` is supplied
 * for both light and dark schemes, any `Text(style = MaterialTheme.typography.X)`
 * without an explicit color then rendered near-black ink on the dark surface
 * (e.g. the Lucide date picker's month label in DARK / DARK_AMOLED).
 *
 * Do NOT reintroduce a hardcoded palette here: build the styles from the
 * argument so the theme switch is honored.
 */
fun tailgTypography(palette: CyberPalette): Typography = Typography(
    displaySmall = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.W700,
        color = palette.ink,
    ),
    headlineMedium = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.W700,
        color = palette.ink,
    ),
    headlineSmall = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.W700,
        color = palette.ink,
    ),
    titleLarge = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.W800,
        color = palette.ink,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.W700,
        color = palette.ink,
    ),
    titleSmall = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.W700,
        color = palette.ink,
    ),
    bodyLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.W700,
        color = palette.ink,
    ),
    bodyMedium = TextStyle(
        fontSize = 13.sp,
        color = palette.inkSecondary,
    ),
    bodySmall = TextStyle(
        fontSize = 13.sp,
        color = palette.inkMuted,
    ),
    labelLarge = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.W600,
        color = palette.ink,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        color = palette.inkSecondary,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = 1.5.sp,
        color = palette.inkMuted,
    ),
)

/**
 * Light-mode default, for `@Preview`s and tests that compose a
 * `MaterialTheme` without going through [TailgTheme].
 */
val TailgTypography: Typography = tailgTypography(LightCyberPalette)