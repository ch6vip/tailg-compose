package com.tailg.plus.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
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

/**
 * Cyber control-home palette (2026 light cockpit reconstruction).
 *
 * [CyberPalette] is the semantic token set every screen reads. It is provided
 * as [LocalCyberPalette] and is the only thing that changes when the user
 * switches theme mode / key colour / palette style — the rest of the app is
 * unchanged because it still reads the same [CyberHomeColors] accessor, which
 * now delegates to the active [LocalCyberPalette].
 */
@Immutable
data class CyberPalette(
    val pageBg: Color,
    val pageBgTop: Color,
    val card: Color,
    val cardMuted: Color,
    val control: Color,
    val controlStrong: Color,
    val line: Color,
    val lineStrong: Color,
    val ink: Color,
    val inkSecondary: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val primary: Color,
    val primarySoft: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val rideAccent: Color,
    val rideAccentSoft: Color,
    val mapPlaceholder: Color,
    val alertSurface: Color,
    val navSurface: Color,
    val navSelected: Color,
    val white75: Color,
    val white96: Color,
    val white: Color,
    val shadow: Color,
    val actionShadow: Color,
    val navShadow: Color,
)

/** The original light Cyber palette — kept byte-for-byte as the default. */
val LightCyberPalette = CyberPalette(
    pageBg = Color(0xFFF4F5F7),
    pageBgTop = Color(0xFFEAF1FC),
    card = Color(0xFFFFFFFF),
    cardMuted = Color(0xFFF8F9FB),
    control = Color(0xFFF0F1F3),
    controlStrong = Color(0xFFE1E3E7),
    line = Color(0xFFE5E7EC),
    lineStrong = Color(0xFFD7DAE1),
    ink = Color(0xFF15171C),
    inkSecondary = Color(0xFF33363D),
    inkMuted = Color(0xFF696D76),
    inkFaint = Color(0xFF9A9EA7),
    primary = Color(0xFF168CFF),
    primarySoft = Color(0xFFDCEEFF),
    success = Color(0xFF34C759),
    warning = Color(0xFFFF9F0A),
    danger = Color(0xFFFF3B30),
    rideAccent = Color(0xFFFF2D68),
    rideAccentSoft = Color(0xFFFFF4E6),
    mapPlaceholder = Color(0xFFE9EEF3),
    alertSurface = Color(0xFFE9EAED),
    navSurface = Color(0xF7FFFFFF),
    navSelected = Color(0xFFE1E2E5),
    white75 = Color(0xBFFFFFFF),
    white96 = Color(0xF5FFFFFF),
    white = Color(0xFFFFFFFF),
    shadow = Color(0x140D1420),
    actionShadow = Color(0x120D1420),
    navShadow = Color(0x24182740),
)

/** Static dark fallback (used when no dynamic scheme is wired, e.g. previews). */
val DarkCyberPalette = CyberPalette(
    pageBg = Color(0xFF0B0E13),
    pageBgTop = Color(0xFF0B1220),
    card = Color(0xFF151A22),
    cardMuted = Color(0xFF1A2029),
    control = Color(0xFF1F2630),
    controlStrong = Color(0xFF2A323E),
    line = Color(0xFF2A323E),
    lineStrong = Color(0xFF3A434F),
    ink = Color(0xFFF2F4F8),
    inkSecondary = Color(0xFFD4D8DE),
    inkMuted = Color(0xFF9AA1AC),
    inkFaint = Color(0xFF6C7480),
    primary = Color(0xFF4FA8FF),
    primarySoft = Color(0xFF16324E),
    success = Color(0xFF3EDB6A),
    warning = Color(0xFFFFB84D),
    danger = Color(0xFFFF5C6C),
    rideAccent = Color(0xFFFF5C8A),
    rideAccentSoft = Color(0xFF33202B),
    mapPlaceholder = Color(0xFF161C25),
    alertSurface = Color(0xFF1A2029),
    navSurface = Color(0xF21A2029),
    navSelected = Color(0xFF232A35),
    white75 = Color(0xBFFFFFFF),
    white96 = Color(0xF5FFFFFF),
    white = Color(0xFFFFFFFF),
    shadow = Color(0x3D000000),
    actionShadow = Color(0x33000000),
    navShadow = Color(0x66000000),
)

/** The active semantic palette — set by [com.tailg.plus.ui.theme.TailgTheme]. */
val LocalCyberPalette = staticCompositionLocalOf { LightCyberPalette }

/** Maps a Material You [ColorScheme] onto the app's semantic token set. */
fun ColorScheme.toCyberPalette(): CyberPalette = CyberPalette(
    pageBg = background,
    pageBgTop = surfaceContainerLow,
    card = surface,
    cardMuted = surfaceContainerLow,
    control = surfaceContainerHighest,
    controlStrong = surfaceContainerHigh,
    line = outlineVariant,
    lineStrong = outline,
    ink = onSurface,
    inkSecondary = onSurfaceVariant,
    inkMuted = onSurfaceVariant,
    inkFaint = outline,
    primary = primary,
    primarySoft = primaryContainer,
    success = Color(0xFF34C759),
    warning = Color(0xFFFF9F0A),
    danger = error,
    rideAccent = tertiary,
    rideAccentSoft = tertiaryContainer,
    mapPlaceholder = surfaceContainerHigh,
    alertSurface = surfaceContainerHigh,
    navSurface = surfaceContainerLow,
    navSelected = surfaceContainerHigh,
    white75 = Color(0xBFFFFFFF),
    white96 = Color(0xF5FFFFFF),
    white = Color.White,
    shadow = Color(0x14000000),
    actionShadow = Color(0x12000000),
    navShadow = Color(0x24000000),
)

/**
 * Backward-compatible accessor: every call site keeps reading `CyberHomeColors.xxx`
 * but now resolves through [LocalCyberPalette], so theme / key-colour changes
 * recompose automatically. Only valid inside a composable (as before).
 */
object CyberHomeColors {
    val pageBg: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.pageBg
    val pageBgTop: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.pageBgTop
    val card: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.card
    val cardMuted: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.cardMuted
    val control: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.control
    val controlStrong: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.controlStrong
    val line: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.line
    val lineStrong: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.lineStrong
    val ink: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.ink
    val inkSecondary: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.inkSecondary
    val inkMuted: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.inkMuted
    val inkFaint: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.inkFaint
    val primary: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.primary
    val primarySoft: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.primarySoft
    val success: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.success
    val warning: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.warning
    val danger: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.danger
    val rideAccent: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.rideAccent
    val rideAccentSoft: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.rideAccentSoft
    val mapPlaceholder: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.mapPlaceholder
    val alertSurface: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.alertSurface
    val navSurface: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.navSurface
    val navSelected: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.navSelected
    val white75: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.white75
    val white96: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.white96
    val white: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.white
    val shadow: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.shadow
    val actionShadow: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.actionShadow
    val navShadow: Color
        @Composable @ReadOnlyComposable get() = LocalCyberPalette.current.navShadow
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
