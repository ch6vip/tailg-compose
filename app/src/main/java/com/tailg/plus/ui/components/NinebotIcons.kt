package com.tailg.plus.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.tailg.plus.R
import com.tailg.plus.ui.theme.AppIconSizes
import com.tailg.plus.ui.theme.CyberHomeColors

/**
 * Real Lucide stroke vectors for the 九号 (NINEBOT) control home.
 *
 * The app-wide [Lucide] map still resolves to Material stand-ins (see
 * [LucideIcon]); these drawables are the genuine lucide-static 0.525 glyph
 * paths (24dp viewport, stroke 2, round caps/joins) converted 1:1, so the
 * 九号 skin renders with the actual Lucide line style instead of filled
 * Material approximations. Other screens can migrate here mechanically.
 */
object NinebotLucide {
  val bell: Int get() = R.drawable.ic_lucide_bell
  val bellRing: Int get() = R.drawable.ic_lucide_bell_ring
  val bluetooth: Int get() = R.drawable.ic_lucide_bluetooth
  val bluetoothOff: Int get() = R.drawable.ic_lucide_bluetooth_off
  val bluetoothSearching: Int get() = R.drawable.ic_lucide_bluetooth_searching
  val batteryCharging: Int get() = R.drawable.ic_lucide_battery_charging
  val thermometer: Int get() = R.drawable.ic_lucide_thermometer
  val route: Int get() = R.drawable.ic_lucide_route
  val timer: Int get() = R.drawable.ic_lucide_timer
  val activity: Int get() = R.drawable.ic_lucide_activity
  val zap: Int get() = R.drawable.ic_lucide_zap
  val armchair: Int get() = R.drawable.ic_lucide_armchair
  val settings: Int get() = R.drawable.ic_lucide_settings_2
  val fingerprint: Int get() = R.drawable.ic_lucide_fingerprint
  val nfc: Int get() = R.drawable.ic_lucide_nfc
  val radar: Int get() = R.drawable.ic_lucide_radar
  val mapPin: Int get() = R.drawable.ic_lucide_map_pin
  val swapHorizontal: Int get() = R.drawable.ic_lucide_arrow_right_left
  val lock: Int get() = R.drawable.ic_lucide_lock
  val lockOpen: Int get() = R.drawable.ic_lucide_lock_open
  val chevronRight: Int get() = R.drawable.ic_lucide_chevron_right
  val hexagon: Int get() = R.drawable.ic_lucide_hexagon
  val messageSquare: Int get() = R.drawable.ic_lucide_message_square
  val signal: Int get() = R.drawable.ic_lucide_signal
  val battery: Int get() = R.drawable.ic_lucide_battery
  val arrowLeft: Int get() = R.drawable.ic_lucide_arrow_left
  val arrowUpRight: Int get() = R.drawable.ic_lucide_arrow_up_right
  val refreshCw: Int get() = R.drawable.ic_lucide_refresh_cw
  val info: Int get() = R.drawable.ic_lucide_info
  val circleHelp: Int get() = R.drawable.ic_lucide_circle_question_mark
  val chevronDown: Int get() = R.drawable.ic_lucide_chevron_down
  val triangleAlert: Int get() = R.drawable.ic_lucide_triangle_alert
  val slidersHorizontal: Int get() = R.drawable.ic_lucide_sliders_horizontal
  val rotateCcw: Int get() = R.drawable.ic_lucide_rotate_ccw
  val cpu: Int get() = R.drawable.ic_lucide_cpu
  val cloudDownload: Int get() = R.drawable.ic_lucide_cloud_download
  val moveHorizontal: Int get() = R.drawable.ic_lucide_move_horizontal
  val x: Int get() = R.drawable.ic_lucide_x
  val pencil: Int get() = R.drawable.ic_lucide_pencil
  val plus: Int get() = R.drawable.ic_lucide_plus
  val check: Int get() = R.drawable.ic_lucide_check
  val layoutGrid: Int get() = R.drawable.ic_lucide_layout_grid
  val bike: Int get() = R.drawable.ic_lucide_bike
  val userRound: Int get() = R.drawable.ic_lucide_user_round
  val panelBottom: Int get() = R.drawable.ic_lucide_panel_bottom
  val scaling: Int get() = R.drawable.ic_lucide_scaling

  /** 官方「更多功能」实心六角螺母(中心圆孔,evenOdd),单色可 tint。 */
  val nutFilled: Int get() = R.drawable.ic_nb_nut_filled

  /** 官方「闪灯鸣笛」车灯字形 — lucide 无对应图标,手绘同风格描边。 */
  val headlight: Int get() = R.drawable.ic_nb_headlight
}

/**
 * The [LucideIcon] counterpart for the real Lucide vectors in [NinebotLucide].
 * Same defaulting contract (decorative null description, token size/color).
 */
@Composable
fun NinebotIcon(
  @DrawableRes icon: Int,
  modifier: Modifier = Modifier,
  size: Dp = AppIconSizes.md,
  color: Color = CyberHomeColors.inkMuted,
  contentDescription: String? = null,
) {
  Icon(
    painter = painterResource(icon),
    contentDescription = contentDescription,
    modifier = modifier.size(size),
    tint = color,
  )
}
