package com.tailg.plus.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Radius tokens (mirror of AppRadii in app_colors.dart). */
object AppRadii {
    val xs = 6.dp
    val sm = 10.dp
    val md = 14.dp
    val lg = 20.dp
    val card = 12.dp
    val tile = 8.dp
    val sheet = 18.dp
    val pill = 999.dp
}

object AppSpacing {
    val screenX = 20.dp
    val sectionGap = 20.dp
    val cardPadding = 16.dp
    val cardGap = 12.dp
    val sectionTop = 16.dp
}

object AppIconSizes {
    val sm = 16.dp
    val md = 20.dp
    val lg = 24.dp
    val xl = 48.dp
}

object AppTouchTargets {
    val min = 44.dp
}

/** Material 3 shape scheme mapped from VOID radii tokens. */
val TailgShapes = Shapes(
    extraSmall = RoundedCornerShape(AppRadii.xs),
    small = RoundedCornerShape(AppRadii.sm),
    medium = RoundedCornerShape(AppRadii.md),
    large = RoundedCornerShape(AppRadii.lg),
    extraLarge = RoundedCornerShape(AppRadii.sheet),
)
