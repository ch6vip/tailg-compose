package com.tailg.plus.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Lumen design tokens. One calm mobility language for light and dark:
 * cool-neutral canvas, ink text, a single cobalt accent. Neon cockpit
 * greens are retired; semantic names stay so call sites do not change.
 */

object AppColors {
    val primary = Color(0xFF1F6FEB)
    val primaryDark = Color(0xFF1858C7)
    val pageBg = Color(0xFF0F1216)
    val textPrimary = Color(0xFFF3F5F7)
    val textSecondary = Color(0xFFA8B0BA)
    val textTertiary = Color(0xFF7C8694)
    val border = Color(0xFF2A313B)
    val danger = Color(0xFFE23B3B)
    val surface = Color(0xFF181C22)
    val surfaceContainerHigh = Color(0xFF242A33)
    val pageBgBot = Color(0xFF0F1216)
    val inkBtn = Color(0xFF242A33)
    val surfaceBrandRedTint = Color(0x22E23B3B)
    val surfaceBrandTealTint = Color(0x1A1F6FEB)
    val energyGreen = Color(0xFF2EC98A)
    val energyRed = Color(0xFFE23B3B)
}

/** Dark-mode token set. */
object AppColorsDark {
    val primary = Color(0xFF7EAEFF)
    val primaryDark = Color(0xFF1F6FEB)
    val pageBg = Color(0xFF0F1216)
    val textPrimary = Color(0xFFF3F5F7)
    val textSecondary = Color(0xFFA8B0BA)
    val textTertiary = Color(0xFF7C8694)
    val border = Color(0xFF2A313B)
    val success = Color(0xFF2EC98A)
    val warning = Color(0xFFE39A2B)
    val danger = Color(0xFFFF8A84)
    val surface = Color(0xFF181C22)
    val surfaceContainerLow = Color(0xFF151920)
    val surfaceContainerHigh = Color(0xFF242A33)
    val outlineVariant = Color(0xFF2A313B)
    val darkSurface = Color(0xFF0F1216)
    val energyGreen = Color(0xFF2EC98A)
    val energyAmber = Color(0xFFE39A2B)
    val energyRed = Color(0xFFFF8A84)
    val inkBtn = Color(0xFF242A33)
    val inkBtn2 = Color(0xFF2C333E)
    val accentSky = Color(0xFF7EAEFF)
    val accentViolet = Color(0xFF9B8CFF)
    val accentAmber = Color(0xFFE39A2B)
    val accentPurple = Color(0xFFA78BFA)
    val accentOrange = Color(0xFFE08A3C)
    val brandRed = Color(0xFFE23B3B)
    val pageBgTop = Color(0xFF151920)
    val pageBgBot = Color(0xFF0F1216)
}

/** Light-mode companion token set. */
object AppColorsLight {
    val primary = Color(0xFF1F6FEB)
    val primaryDark = Color(0xFF1858C7)
    val pageBg = Color(0xFFF3F5F8)
    val textPrimary = Color(0xFF12161C)
    val textSecondary = Color(0xFF5E6774)
    val textTertiary = Color(0xFF98A0AB)
    val border = Color(0x1412161C)
    val success = Color(0xFF1F9D6A)
    val warning = Color(0xFFE39A2B)
    val danger = Color(0xFFE23B3B)
    val surface = Color(0xFFFFFFFF)
    val surfaceContainerLow = Color(0xFFF7F8FA)
    val surfaceContainerHigh = Color(0xFFEEF1F5)
    val outlineVariant = Color(0x1A12161C)
    val darkSurface = Color(0xFF12161C)
    val energyGreen = Color(0xFF1F9D6A)
    val energyAmber = Color(0xFFE39A2B)
    val energyRed = Color(0xFFE23B3B)
    val inkBtn = Color(0xFF1A1F27)
    val inkBtn2 = Color(0xFF2A313B)
    val accentSky = Color(0xFF1F6FEB)
    val accentViolet = Color(0xFF6E5CFF)
    val accentAmber = Color(0xFFE39A2B)
    val accentPurple = Color(0xFF7C6AF2)
    val accentOrange = Color(0xFFE08A3C)
    val brandRed = Color(0xFFE23B3B)
    val pageBgTop = Color(0xFFEEF1F5)
    val pageBgBot = Color(0xFFF7F8FA)
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

/** Default light palette — previews and any caller outside [TailgTheme]. */
val LightCyberPalette = CyberPalette(
    pageBg = Color(0xFFF3F5F8),
    pageBgTop = Color(0xFFEEF1F5),
    card = Color(0xFFFFFFFF),
    cardMuted = Color(0xFFF7F8FA),
    control = Color(0xFFE8EDF2),
    controlStrong = Color(0xFFEEF1F5),
    line = Color(0xFFE3E8EE),
    lineStrong = Color(0xFFC5CDD6),
    ink = Color(0xFF12161C),
    inkSecondary = Color(0xFF3E4652),
    inkMuted = Color(0xFF6B7380),
    inkFaint = Color(0xFF767E89),
    primary = Color(0xFF1F6FEB),
    primarySoft = Color(0xFFE6F0FF),
    success = Color(0xFF1F9D6A),
    warning = Color(0xFFE39A2B),
    danger = Color(0xFFE23B3B),
    rideAccent = Color(0xFFE08A3C),
    rideAccentSoft = Color(0xFFF8EBD9),
    mapPlaceholder = Color(0xFFE8EDF2),
    alertSurface = Color(0xFFEEF1F5),
    navSurface = Color(0xF7FFFFFF),
    navSelected = Color(0xFFE8EDF2),
    white75 = Color(0xBFFFFFFF),
    white96 = Color(0xF5FFFFFF),
    white = Color(0xFFFFFFFF),
    shadow = Color(0x1212161C),
    actionShadow = Color(0x1012161C),
    navShadow = Color(0x1A12161C),
)

/** Static dark fallback (used when no dynamic scheme is wired, e.g. previews). */
val DarkCyberPalette = CyberPalette(
    pageBg = Color(0xFF0F1216),
    pageBgTop = Color(0xFF151920),
    card = Color(0xFF1C2128),
    cardMuted = Color(0xFF181C22),
    control = Color(0xFF2C333E),
    controlStrong = Color(0xFF242A33),
    line = Color(0xFF2A313B),
    lineStrong = Color(0xFF5C6672),
    ink = Color(0xFFF3F5F7),
    inkSecondary = Color(0xFFD5DAE1),
    inkMuted = Color(0xFFA8B0BA),
    inkFaint = Color(0xFF7C8694),
    primary = Color(0xFF7EAEFF),
    primarySoft = Color(0xFF1A335C),
    success = Color(0xFF3DDC9A),
    warning = Color(0xFFE8B15A),
    danger = Color(0xFFFF8A84),
    rideAccent = Color(0xFFF0B27A),
    rideAccentSoft = Color(0xFF3D2E1C),
    mapPlaceholder = Color(0xFF151920),
    alertSurface = Color(0xFF242A33),
    navSurface = Color(0xF2181C22),
    navSelected = Color(0xFF2A313B),
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
    inkFaint = lerp(onSurfaceVariant, background, 0.16f),
    primary = primary,
    primarySoft = primaryContainer,
    success = Color(0xFF2EBE6A),
    warning = Color(0xFFE39A2B),
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
