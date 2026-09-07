package com.tailg.plus.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.R

/** VECTOR: warm technical paper, carbon ink, and one high-visibility signal colour. */
val VectorLightColorScheme = lightColorScheme(
    primary = Color(0xFF506400),
    onPrimary = Color(0xFFFAFDF0),
    primaryContainer = Color(0xFFE6F1C3),
    onPrimaryContainer = Color(0xFF27300C),
    secondary = Color(0xFF30362B),
    onSecondary = Color(0xFFF8FAF1),
    secondaryContainer = Color(0xFFD7FF3F),
    onSecondaryContainer = Color(0xFF182000),
    tertiary = Color(0xFF35617A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD7E8F0),
    onTertiaryContainer = Color(0xFF103242),
    background = Color(0xFFF2F3EC),
    onBackground = Color(0xFF181B16),
    surface = Color(0xFFFCFDF6),
    onSurface = Color(0xFF181B16),
    surfaceVariant = Color(0xFFE7EAE0),
    onSurfaceVariant = Color(0xFF616655),
    surfaceDim = Color(0xFFDDE1D4),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEDEFE5),
    surfaceContainer = Color(0xFFE6E9DE),
    surfaceContainerHigh = Color(0xFFDDE2D3),
    surfaceContainerHighest = Color(0xFFD2D9C6),
    outline = Color(0xFF7A8070),
    outlineVariant = Color(0xFFD5DBCB),
    error = Color(0xFFB63832),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF590C09),
    inverseSurface = Color(0xFF20251D),
    inverseOnSurface = Color(0xFFF2F5E9),
    inversePrimary = Color(0xFFD7FF3F),
    scrim = Color.Black,
)

val VectorDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD7FF3F),
    onPrimary = Color(0xFF1F2B00),
    primaryContainer = Color(0xFF36431A),
    onPrimaryContainer = Color(0xFFE0F5A8),
    secondary = Color(0xFFD5DCCB),
    onSecondary = Color(0xFF222A1D),
    secondaryContainer = Color(0xFFD7FF3F),
    onSecondaryContainer = Color(0xFF182000),
    tertiary = Color(0xFF8AB6C8),
    onTertiary = Color(0xFF10313E),
    tertiaryContainer = Color(0xFF244552),
    onTertiaryContainer = Color(0xFFD1ECF5),
    background = Color(0xFF0A0D0B),
    onBackground = Color(0xFFF3F4EA),
    surface = Color(0xFF161A15),
    onSurface = Color(0xFFF3F4EA),
    surfaceVariant = Color(0xFF242B21),
    onSurfaceVariant = Color(0xFFB1B8A8),
    surfaceDim = Color(0xFF0C100C),
    surfaceBright = Color(0xFF30382B),
    surfaceContainerLowest = Color(0xFF070A08),
    surfaceContainerLow = Color(0xFF11160F),
    surfaceContainer = Color(0xFF1B2117),
    surfaceContainerHigh = Color(0xFF252C20),
    surfaceContainerHighest = Color(0xFF30392A),
    outline = Color(0xFF858F79),
    outlineVariant = Color(0xFF30392D),
    error = Color(0xFFFFA096),
    onError = Color(0xFF5E1510),
    errorContainer = Color(0xFF7B2420),
    onErrorContainer = Color(0xFFFFDAD4),
    inverseSurface = Color(0xFFE8EDDD),
    inverseOnSurface = Color(0xFF20271B),
    inversePrimary = Color(0xFF506400),
    scrim = Color.Black,
)

val VectorFontFamily = FontFamily(Font(R.font.space_grotesk))

private fun vectorType(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f) =
    TextStyle(
        fontFamily = VectorFontFamily,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = weight,
        letterSpacing = tracking.sp,
    )

/** Colour stays unspecified so content colours remain legible in every theme. */
val VectorTypography = Typography(
    displayLarge = vectorType(76, 80, FontWeight.Bold, -3f),
    displayMedium = vectorType(54, 58, FontWeight.Bold, -2f),
    displaySmall = vectorType(38, 42, FontWeight.Bold, -1.3f),
    headlineLarge = vectorType(32, 38, FontWeight.Bold, -0.8f),
    headlineMedium = vectorType(26, 32, FontWeight.SemiBold, -0.6f),
    headlineSmall = vectorType(22, 28, FontWeight.SemiBold, -0.4f),
    titleLarge = vectorType(20, 26, FontWeight.SemiBold, -0.3f),
    titleMedium = vectorType(16, 22, FontWeight.SemiBold),
    titleSmall = vectorType(14, 20, FontWeight.SemiBold),
    bodyLarge = vectorType(16, 24),
    bodyMedium = vectorType(14, 21),
    bodySmall = vectorType(12, 18),
    labelLarge = vectorType(13, 18, FontWeight.SemiBold, 0.3f),
    labelMedium = vectorType(11, 16, FontWeight.SemiBold, 0.7f),
    labelSmall = vectorType(10, 14, FontWeight.Medium, 1f),
)

val VectorShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
