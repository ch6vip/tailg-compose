package com.tailg.plus.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import com.tailg.plus.R
import com.tailg.plus.ui.theme.AppRadii

/**
 * Classic pill navigation for the four bottom destinations.
 * Colors come from [MaterialTheme.colorScheme] so light and dark stay in lockstep
 * with the floating bar.
 */
object VoidOrbitalNav {
  const val barHeightDp = 64
}

@Composable
fun VoidOrbitalNav(
  currentIndex: Int,
  modifier: Modifier = Modifier,
  onService: () -> Unit,
  onVehicle: () -> Unit,
  onMine: () -> Unit,
  onSettings: () -> Unit,
) {
  val haptics = LocalHapticFeedback.current
  val shape = RoundedCornerShape(AppRadii.pill)

  val colors = MaterialTheme.colorScheme
  Box(
    modifier = modifier
      .fillMaxWidth()
      .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
      .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .shadow(
          elevation = 10.dp,
          shape = shape,
          clip = false,
          ambientColor = Color.Transparent,
          spotColor = colors.scrim.copy(alpha = 0.18f),
        ),
    ) {
      Box(
        modifier = Modifier
          .matchParentSize()
          .clip(shape)
          .background(colors.surfaceContainer.copy(alpha = BottomNavigationContainerAlpha)),
      )
      Box(
        modifier = Modifier
          .matchParentSize()
          .border(1.dp, colors.outlineVariant.copy(alpha = 0.55f), shape),
      )
      // Content layer: crisp icons + labels, clipped to the pill.
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(VoidOrbitalNav.barHeightDp.dp)
          .clip(shape),
      ) {
        NavItem(
          label = stringResource(R.string.nav_service),
          icon = NinebotLucide.layoutGrid,
          selected = currentIndex == 0,
          onTap = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress) // Dart selectionClick
            onService()
          },
          modifier = Modifier.weight(1f),
        )
        NavItem(
          label = stringResource(R.string.nav_control),
          icon = NinebotLucide.bike,
          selected = currentIndex == 1,
          onTap = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress) // Dart selectionClick
            onVehicle()
          },
          modifier = Modifier.weight(1f),
        )
        NavItem(
          label = stringResource(R.string.nav_mine),
          icon = NinebotLucide.userRound,
          selected = currentIndex == 2,
          onTap = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress) // Dart selectionClick
            onMine()
          },
          modifier = Modifier.weight(1f),
        )
        NavItem(
          label = stringResource(R.string.nav_settings),
          icon = NinebotLucide.settings,
          selected = currentIndex == 3,
          onTap = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress) // Dart selectionClick
            onSettings()
          },
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun RowScope.NavItem(
  label: String,
  @DrawableRes icon: Int,
  selected: Boolean,
  onTap: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val color by animateColorAsState(
    targetValue = if (selected) colors.primary else colors.onSurfaceVariant,
    animationSpec = tween(AppMotion.standard),
    label = "navColor",
  )
  val pillColor by animateColorAsState(
    targetValue = if (selected) colors.primary.copy(alpha = 0.14f) else Color.Transparent,
    animationSpec = tween(AppMotion.standard, easing = AppMotion.pressCurve),
    label = "navPill",
  )

  Column(
    modifier = modifier
      .height(VoidOrbitalNav.barHeightDp.dp)
      .padding(4.dp)
      .clip(RoundedCornerShape(AppRadii.pill))
      .background(pillColor)
      .selectable(
        selected = selected,
        role = Role.Tab,
        onClick = onTap,
      ),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    NinebotIcon(icon = icon, size = 21.dp, color = color)
    Spacer(Modifier.height(3.dp))
    BasicText(
      text = label,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
      style = androidx.compose.ui.text.TextStyle(
        fontSize = 10.sp,
        lineHeight = 13.sp,
        fontWeight = if (selected) FontWeight.W700 else FontWeight.W500,
        fontFamily = FontFamily.Default,
        letterSpacing = 0.sp,
        color = color,
        textAlign = TextAlign.Center,
      ),
      autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 10.sp),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
