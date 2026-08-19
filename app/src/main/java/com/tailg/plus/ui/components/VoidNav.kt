package com.tailg.plus.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.R
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
  // Dart BackdropFilter.blur(sigma 24): RenderEffect blurs the backdrop
  // behind this layer; skipped on < API 31 and under reduce-motion. The blur
  // parameters are constant, so build the effect once instead of on every
  // layer update (this bar is hosted on every top-level screen).
  val navBlur = remember {
    if (android.os.Build.VERSION.SDK_INT >= 31) {
      android.graphics.RenderEffect.createBlurEffect(
        24f, 24f, android.graphics.Shader.TileMode.CLAMP,
      ).asComposeRenderEffect()
    } else {
      null
    }
  }

  val shape = RoundedCornerShape(AppRadii.pill)

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
          shape = shape,
          clip = false,
          ambientColor = Color.Transparent,
          spotColor = CyberHomeColors.actionShadow, // AppShadows.cyberNavShadow
        ),
    ) {
      // Backdrop layer: the blur must apply to what is BEHIND the bar
      // (Dart BackdropFilter), never to the bar's own icons/labels. So the
      // renderEffect lives on a dedicated empty background layer; the content
      // Row below it has no effect and stays crisp. alpha < 1 makes Compose
      // capture (and blur) the backdrop instead of the layer's own content.
      Box(
        modifier = Modifier
          .matchParentSize()
          .clip(shape)
          .background(CyberHomeColors.navSurface)
          .graphicsLayer {
            if (!reduceMotion) {
              renderEffect = navBlur
              alpha = 0.99f
            }
          },
      )
      // Border layer drawn on its own Box so the 1dp white stroke is not
      // clipped by the rounded clip (Dart BoxDecoration border + borderRadius).
      Box(
        modifier = Modifier
          .matchParentSize()
          .border(1.dp, CyberHomeColors.white, shape),
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
          icon = Lucide.service,
          selected = currentIndex == 0,
          onTap = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress) // Dart selectionClick
            onService()
          },
          modifier = Modifier.weight(1f),
        )
        NavItem(
          label = stringResource(R.string.nav_control),
          icon = Lucide.vehicle,
          selected = currentIndex == 1,
          onTap = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress) // Dart selectionClick
            onVehicle()
          },
          modifier = Modifier.weight(1f),
        )
        NavItem(
          label = stringResource(R.string.nav_mine),
          icon = Lucide.mine,
          selected = currentIndex == 2,
          onTap = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress) // Dart selectionClick
            onMine()
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
  icon: ImageVector,
  selected: Boolean,
  onTap: () -> Unit,
  modifier: Modifier = Modifier,
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
    modifier = modifier
      .height(VoidOrbitalNav.barHeightDp.dp)
      .padding(4.dp)
      .clip(RoundedCornerShape(bgRadius))
      .background(pillColor)
      .selectable(
        selected = selected,
        role = Role.Tab,
        onClick = onTap,
      ),
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
