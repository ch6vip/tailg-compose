package com.tailg.plus.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Radius tokens. Larger corners, same roles. */
object AppRadii {
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val card = 20.dp
    val tile = 16.dp
    val sheet = 28.dp
    val pill = 999.dp
}

object AppSpacing {
    val screenX = 20.dp
    val sectionGap = 24.dp
    val cardPadding = 18.dp
    val cardGap = 14.dp
    val sectionTop = 20.dp
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

/** Material 3 shape scheme mapped from the Lumen radii. */
val TailgShapes = Shapes(
    extraSmall = RoundedCornerShape(AppRadii.xs),
    small = RoundedCornerShape(AppRadii.sm),
    medium = RoundedCornerShape(AppRadii.md),
    large = RoundedCornerShape(AppRadii.lg),
    extraLarge = RoundedCornerShape(AppRadii.sheet),
)
