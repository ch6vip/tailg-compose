package com.tailg.plus.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateBottomPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.CyberHomeColors

/**
 * Port of `lib/widgets/void_nav.dart` — three-entry floating nav
 * (服务 / 控车 / 我的) styled after the light Cyber cockpit.
 *
 * Product scope is preserved: only 服务 / 控车 / 我的 exist (no empty shells).
 *
 * Token mapping:
 * - `CyberHomeColors.navSurface/navSelected/white/ink/inkSecondary` → the
 *   same-named [CyberHomeColors] tokens; `AppRadii.pill` → [AppRadii.pill].
 * - `AppShadows.cyberNavShadow` → [Modifier.shadow] with
 *   [CyberHomeColors.actionShadow] spot (no dedicated nav-shadow token).
 * - Dart `BackdropFilter.blur(24)` → [Modifier.blur] with
 *   [MotionPolicy.reduceMotion] gating (blur is expensive under reduce-motion).
 *
 * Icons: `Lucide.service` → `Icons.Filled.GridView`; `Lucide.vehicle` →
 * `Icons.Filled.DirectionsBike`; `Lucide.mine` → `Icons.Filled.Person`.
 */
object VoidOrbitalNav {
  const val barHeightDp = 64
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VoidOrbitalNav(
  currentIndex: Int,
  modifier: Modifier = Modifier,
  onService: () -> Unit,
  onVehicle: () -> Unit,
  onMine: () -> Unit,
) {
  val reduceMotion = MotionPolicy.reduceMotion()
  val haptics = LocalHapticFeedback.current

  val surface = Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(VoidOrbitalNav.barHeightDp.dp)
      .clip(RoundedCornerShape(AppRadii.pill))
      .background(CyberHomeColors.navSurface)
      .border(1.dp, CyberHomeColors.white, RoundedCornerShape(AppRadii.pill))
      // Dart BackdropFilter.blur(sigma 24): RenderEffect blurs the backdrop
      // behind this layer; skipped on < API 31 and under reduce-motion.
      .graphicsLayer {
        if (!reduceMotion && android.os.Build.VERSION.SDK_INT >= 31) {
          renderEffect = android.graphics.RenderEffect.createBlurEffect(
            24f, 24f, android.graphics.Shader.TileMode.Decal,
          )
        } else {
          renderEffect = null
        }
      },
  ) {
    NavItem(
      label = "服务",
      icon = Lucide.service,
      selected = currentIndex == 0,
      onTap = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress) // Dart selectionClick
        onService()
      },
      modifier = Modifier.weight(1f),
    )
    NavItem(
      label = "控车",
      icon = Lucide.vehicle,
      selected = currentIndex == 1,
      onTap = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress) // Dart selectionClick
        onVehicle()
      },
      modifier = Modifier.weight(1f),
    )
    NavItem(
      label = "我的",
      icon = Lucide.mine,
      selected = currentIndex == 2,
      onTap = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress) // Dart selectionClick
        onMine()
      },
      modifier = Modifier.weight(1f),
    )
  }

  // Dart: EdgeInsets.fromLTRB(24, 0, 24, 8 + bottomInset * 0.45).
  val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(start = 24.dp, end = 24.dp, bottom = 8.dp + bottomInset * 0.45f),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .shadow(
          elevation = 12.dp,
          shape = RoundedCornerShape(AppRadii.pill),
          clip = false,
          ambientColor = Color.Transparent,
          spotColor = CyberHomeColors.actionShadow, // AppShadows.cyberNavShadow
        ),
    ) { surface }
  }
}

@Composable
private fun RowScope.NavItem(
  label: String,
  icon: ImageVector,
  selected: Boolean,
  onTap: () -> Unit,
) {
  val color by animateColorAsState(
    targetValue = if (selected) CyberHomeColors.ink else CyberHomeColors.inkSecondary,
    animationSpec = tween(AppMotion.standard),
    label = "navColor",
  )
  val pillColor by animateColorAsState(
    targetValue = if (selected) CyberHomeColors.navSelected else Color.Transparent,
    animationSpec = tween(AppMotion.standard, easing = AppMotion.pressCurve),
    label = "navPill",
  )
  val bgRadius by animateDpAsState(
    targetValue = AppRadii.pill,
    animationSpec = tween(AppMotion.standard, easing = AppMotion.pressCurve),
    label = "navPillRadius",
  )

  Column(
    modifier = Modifier
      .height(VoidOrbitalNav.barHeightDp.dp)
      .padding(4.dp)
      .clip(RoundedCornerShape(bgRadius))
      .background(pillColor)
      .clickable(onClick = onTap),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    LucideIcon(icon = icon, size = 21.dp, color = color, strokeWidth = 1.9f)
    Spacer(Modifier.height(3.dp))
    Text(
      text = label,
      style = androidx.compose.ui.text.TextStyle(
        fontSize = 10.sp,
        lineHeight = 10.sp,
        fontWeight = if (selected) FontWeight.W700 else FontWeight.W500,
        letterSpacing = 0.sp,
        color = color,
      ),
    )
  }
}
