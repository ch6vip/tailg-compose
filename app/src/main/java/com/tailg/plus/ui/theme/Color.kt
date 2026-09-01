package com.tailg.plus.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * VOID COCKPIT design tokens — ported 1:1 from the Flutter replica's
 * `lib/theme/app_colors.dart`. The app is dark-first; light companions exist
 * for completeness (rarely used).
 */

object AppColors {
    // Dark-first statics (mirror of AppColors dark set)
    val primary = Color(0xFF00FFB2)
    val primaryDark = Color(0xFF00C896)
    val pageBg = Color(0xFF05070B)
    val textPrimary = Color(0xFFF4F6FA)
    val textSecondary = Color(0xFF8B93A7)
    val textTertiary = Color(0xFF5A6278)
    val border = Color(0xFF1C2433)
    val danger = Color(0xFFFF4D6A)
    val surface = Color(0xFF151B26)
    val surfaceContainerHigh = Color(0xFF1C2433)
    val pageBgBot = Color(0xFF05070B)
    val inkBtn = Color(0xFF1C2433)
    val surfaceBrandRedTint = Color(0x22FF4D6A)
    val surfaceBrandTealTint = Color(0x1A00FFB2)
    val energyGreen = Color(0xFF00FFB2)
    val energyRed = Color(0xFFFF4D5E)
}

/** Dark-mode token set (VOID, primary). */
object AppColorsDark {
    val primary = Color(0xFF00FFB2)
    val primaryDark = Color(0xFF00C896)
    val pageBg = Color(0xFF05070B)
    val textPrimary = Color(0xFFF4F6FA)
    val textSecondary = Color(0xFF8B93A7)
    val textTertiary = Color(0xFF5A6278)
    val border = Color(0xFF1C2433)
    val success = Color(0xFF00FFB2)
    val warning = Color(0xFFFFB84D)
    val danger = Color(0xFFFF4D6A)
    val surface = Color(0xFF151B26)
    val surfaceContainerLow = Color(0xFF11161F)
    val surfaceContainerHigh = Color(0xFF1C2433)
    val outlineVariant = Color(0xFF2A3142)
    val darkSurface = Color(0xFF05070B)
    val energyGreen = Color(0xFF00FFB2)
    val energyAmber = Color(0xFFFFB84D)
    val energyRed = Color(0xFFFF4D6A)
    val inkBtn = Color(0xFF1C2433)
    val inkBtn2 = Color(0xFF2A3142)
    val accentSky = Color(0xFF5CB8FF)
    val accentViolet = Color(0xFF9B8EFF)
    val accentAmber = Color(0xFFFFB84D)
    val accentPurple = Color(0xFFA78BFA)
    val accentOrange = Color(0xFFFF9A3C)
    val brandRed = Color(0xFFFF4D5E)
    val pageBgTop = Color(0xFF0A0E14)
    val pageBgBot = Color(0xFF05070B)
}

/** Light-mode companion token set. */
object AppColorsLight {
    val primary = Color(0xFF00A57C)
    val primaryDark = Color(0xFF008F6A)
    val pageBg = Color(0xFFF3F5F8)
    val textPrimary = Color(0xFF0B1220)
    val textSecondary = Color(0xFF5C667A)
    val textTertiary = Color(0xFF8A93A5)
    val border = Color(0x140B1220)
    val success = Color(0xFF00A57C)
    val warning = Color(0xFFF5A623)
    val danger = Color(0xFFFF4D5E)
    val surface = Color(0xFFFFFFFF)
    val surfaceContainerLow = Color(0xFFF6F7FA)
    val surfaceContainerHigh = Color(0xFFE8ECF2)
    val outlineVariant = Color(0x1A0B1220)
    val darkSurface = Color(0xFF0B1220)
    val energyGreen = Color(0xFF00C896)
    val energyAmber = Color(0xFFF5A623)
    val energyRed = Color(0xFFFF4D5E)
    val inkBtn = Color(0xFF1B2230)
    val inkBtn2 = Color(0xFF2A3342)
    val accentSky = Color(0xFF2E9BFF)
    val accentViolet = Color(0xFF7C6CFF)
    val accentAmber = Color(0xFFF5A623)
    val accentPurple = Color(0xFF7B61FF)
    val accentOrange = Color(0xFFFF8A00)
    val brandRed = Color(0xFFF11C2C)
    val pageBgTop = Color(0xFFE8ECF2)
    val pageBgBot = Color(0xFFF6F7FA)
}

/** Cyber control-home palette (2026 light cockpit reconstruction). */
object CyberHomeColors {
    val pageBg = Color(0xFFF4F5F7)
    val pageBgTop = Color(0xFFEAF1FC)
    val card = Color(0xFFFFFFFF)
    val cardMuted = Color(0xFFF8F9FB)
    val control = Color(0xFFF0F1F3)
    val controlStrong = Color(0xFFE1E3E7)
    val line = Color(0xFFE5E7EC)
    val lineStrong = Color(0xFFD7DAE1)
    val ink = Color(0xFF15171C)
    val inkSecondary = Color(0xFF33363D)
    val inkMuted = Color(0xFF696D76)
    val inkFaint = Color(0xFF9A9EA7)
    val primary = Color(0xFF168CFF)
    val primarySoft = Color(0xFFDCEEFF)
    val success = Color(0xFF34C759)
    val warning = Color(0xFFFF9F0A)
    val danger = Color(0xFFFF3B30)
    val rideAccent = Color(0xFFFF2D68)
    val rideAccentSoft = Color(0xFFFFF4E6)
    val mapPlaceholder = Color(0xFFE9EEF3)
    val alertSurface = Color(0xFFE9EAED)
    val navSurface = Color(0xF7FFFFFF)
    val navSelected = Color(0xFFE1E2E5)
    val white75 = Color(0xBFFFFFFF)
    val white96 = Color(0xF5FFFFFF)
    val white = Color(0xFFFFFFFF)
    val shadow = Color(0x140D1420)
    val actionShadow = Color(0x120D1420)
    val navShadow = Color(0x24182740)
}

/** Bike-body painter grayscale tokens (replica fidelity). */
object ReplicaBikeColors {
    val frame = Color(0xFF2A2D35)
    val rim = Color(0xFF252525)
    val battery = Color(0xFF121418)
    val shadow = Color(0xFFDDE3EC)
    val surface = Color(0xFFF0F3F8)
    val handle = Color(0xFFD9DEE8)
    val parking = Color(0xFFDDE7D8)
}
