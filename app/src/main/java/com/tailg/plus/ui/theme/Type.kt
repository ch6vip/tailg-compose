package com.tailg.plus.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography mapped onto Material 3 type roles.
 *
 * Colors come from [CyberHomeColors] (the 2026 light cockpit token set) —
 * NOT [AppColorsDark]. The app is light-only (`TailgTheme.darkTheme` defaults
 * to false); the old mapping hardcoded the dark VOID palette's near-white
 * `textPrimary` (`0xFFF4F6FA`) here, which leaked into every `Text` /
 * `TextField` that did not set an explicit color and rendered white-on-white
 * on the light `CyberHomeColors.card` (the "invisible login phone number" bug).
 */
val TailgTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.W700,
        color = LightCyberPalette.ink,
    ),
    headlineMedium = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.W700,
        color = LightCyberPalette.ink,
    ),
    headlineSmall = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.W700,
        color = LightCyberPalette.ink,
    ),
    titleLarge = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.W800,
        color = LightCyberPalette.ink,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.W700,
        color = LightCyberPalette.ink,
    ),
    titleSmall = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.W700,
        color = LightCyberPalette.ink,
    ),
    bodyLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.W700,
        color = LightCyberPalette.ink,
    ),
    bodyMedium = TextStyle(
        fontSize = 13.sp,
        color = LightCyberPalette.inkSecondary,
    ),
    bodySmall = TextStyle(
        fontSize = 13.sp,
        color = LightCyberPalette.inkMuted,
    ),
    labelLarge = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.W600,
        color = LightCyberPalette.ink,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        color = LightCyberPalette.inkSecondary,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = 1.5.sp,
        color = LightCyberPalette.inkMuted,
    ),
)
