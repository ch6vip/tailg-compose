package com.tailg.plus.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.R
import kotlin.math.roundToInt

internal enum class BottomNavDestination(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    SERVICE(R.string.nav_service, R.drawable.ic_lucide_layout_grid),
    CONTROL(R.string.nav_control, R.drawable.ic_lucide_bike),
    MINE(R.string.nav_mine, R.drawable.ic_lucide_user_round),
    SETTINGS(R.string.nav_settings, R.drawable.ic_lucide_settings_2),
}

/** Appearance changes keep the same destinations and navigation callbacks. */
@Composable
internal fun TailgBottomNavigation(
    currentIndex: Int,
    floating: Boolean,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (floating) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            FloatingBottomBar(
                currentIndex = currentIndex,
                onSelected = onSelected,
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            )
        }
    } else {
        VoidOrbitalNav(
            currentIndex = currentIndex,
            modifier = modifier.testTag("classic-bottom-bar"),
            onService = { onSelected(0) },
            onVehicle = { onSelected(1) },
            onMine = { onSelected(2) },
            onSettings = { onSelected(3) },
        )
    }
}

/**
 * Translucent floating bar with a KernelSU-inspired sliding indicator,
 * a damped press response, and navigation committed only when a drag ends.
 * Compose handles tab focus, keyboard activation, and reduced-motion settings.
 */
@Composable
internal fun FloatingBottomBar(
    currentIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val destinations = BottomNavDestination.entries
    val selectedIndex = currentIndex.coerceIn(destinations.indices)
    val currentSelection by rememberUpdatedState(selectedIndex)
    val selectTab by rememberUpdatedState(onSelected)
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val haptics = LocalHapticFeedback.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val insetPx = with(LocalDensity.current) { 4.dp.toPx() }
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    var widthPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }

    val indicatorPosition by animateFloatAsState(
        targetValue = if (dragging) dragPosition else selectedIndex.toFloat(),
        animationSpec = if (dragging) snap() else spring(dampingRatio = 0.78f, stiffness = 430f),
        label = "floatingBarIndicator",
    )
    val pressProgress by animateFloatAsState(
        targetValue = if (dragging || pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "floatingBarPress",
    )
    val panelOffset by animateFloatAsState(
        targetValue = if (dragging) (dragPosition - selectedIndex).coerceIn(-2f, 2f) else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f),
        label = "floatingBarOffset",
    )
    val highlightedIndex = if (dragging) dragPosition.roundToInt() else selectedIndex

    BoxWithConstraints(
        modifier = modifier
            .testTag("floating-bottom-bar")
            .height(64.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .graphicsLayer {
                translationX = panelOffset * if (isRtl) -1.dp.toPx() else 1.dp.toPx()
                scaleX = 1f + 0.012f * pressProgress
                scaleY = 1f + 0.012f * pressProgress
            }
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (dark) 0.2f else 0.06f),
                spotColor = Color.Black.copy(alpha = if (dark) 0.25f else 0.12f),
            )
            .background(colors.surfaceContainer.copy(alpha = BottomNavigationContainerAlpha), CircleShape)
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.45f), CircleShape)
            .clip(CircleShape)
            .selectableGroup()
            .pointerInput(widthPx, insetPx, isRtl) {
                if (widthPx <= insetPx * 2) return@pointerInput
                fun updateDrag(x: Float) {
                    val logicalX = if (isRtl) widthPx - x else x
                    val tabWidth = (widthPx - insetPx * 2) / destinations.size
                    val next = ((logicalX - insetPx) / tabWidth - 0.5f)
                        .coerceIn(0f, destinations.lastIndex.toFloat())
                    if (next.roundToInt() != dragPosition.roundToInt()) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    }
                    dragPosition = next
                }
                detectHorizontalDragGestures(
                    onDragStart = { position ->
                        dragPosition = currentSelection.toFloat()
                        dragging = true
                        updateDrag(position.x)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        updateDrag(change.position.x)
                    },
                    onDragEnd = {
                        val target = dragPosition.roundToInt()
                        dragging = false
                        if (target != currentSelection) selectTab(target)
                    },
                    onDragCancel = { dragging = false },
                )
            },
    ) {
        val tabWidth = (maxWidth - 8.dp) / destinations.size
        Box(
            Modifier
                .offset { IntOffset((4.dp + tabWidth * indicatorPosition).roundToPx(), 4.dp.roundToPx()) }
                .width(tabWidth)
                .height(56.dp)
                .graphicsLayer {
                    scaleX = 1f + 0.04f * pressProgress
                    scaleY = 1f + 0.07f * pressProgress
                }
                .background(
                    colors.primary.copy(alpha = if (dark) 0.2f else 0.12f),
                    CircleShape,
                ),
        )
        Row(Modifier.fillMaxSize().padding(4.dp)) {
            destinations.forEachIndexed { index, destination ->
                val selected = selectedIndex == index
                val highlighted = highlightedIndex == index
                val contentColor by animateColorAsState(
                    if (highlighted) colors.primary else colors.onSurfaceVariant,
                    label = "floatingTabColor",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("floating-nav-tab-$index")
                        .clip(CircleShape)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            interactionSource = interactions,
                            indication = null,
                            onClick = {
                                if (index != currentSelection) {
                                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    selectTab(index)
                                }
                            },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                ) {
                    NinebotIcon(destination.iconRes, size = 23.dp, color = contentColor)
                    BasicText(
                        text = stringResource(destination.labelRes),
                        modifier = Modifier.padding(horizontal = 2.dp),
                        style = TextStyle(
                            color = contentColor,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontWeight = if (highlighted) FontWeight.W600 else FontWeight.W500,
                            fontFamily = FontFamily.Default,
                            textAlign = TextAlign.Center,
                        ),
                        autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 11.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
