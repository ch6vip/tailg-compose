package com.tailg.plus.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography mapped from the VOID text tokens (AppTextStyles in the Flutter
 * replica) onto Material 3 type roles. UI port phase may refine per-screen.
 */
val TailgTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.W700,
        color = AppColorsDark.textPrimary,
    ),
    headlineMedium = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.W700,
        color = AppColorsDark.textPrimary,
    ),
    headlineSmall = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.W700,
        color = AppColorsDark.textPrimary,
    ),
    titleLarge = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.W800,
        color = AppColorsDark.textPrimary,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.W700,
        color = AppColorsDark.textPrimary,
    ),
    titleSmall = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.W700,
        color = AppColorsDark.textPrimary,
    ),
    bodyLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.W700,
        color = AppColorsDark.textPrimary,
    ),
    bodyMedium = TextStyle(
        fontSize = 13.sp,
        color = AppColorsDark.textSecondary,
    ),
    bodySmall = TextStyle(
        fontSize = 13.sp,
        color = AppColorsDark.textTertiary,
    ),
    labelLarge = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.W600,
        color = AppColorsDark.textPrimary,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        color = AppColorsDark.textSecondary,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = 1.5.sp,
        color = AppColorsDark.textTertiary,
    ),
)
