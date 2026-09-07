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
 * Resource-backed counterparts of the genuine Lucide vectors in [Lucide].
 *
 * All paths are from lucide-static 0.525.0 (ISC): 24-unit viewport, 2-unit stroke,
 * round caps and joins. Attribution is bundled in assets/licenses/lucide.txt.
 * The historical Ninebot names and drawable-based [NinebotIcon] API remain compatible.
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

  /** Legacy alias for the official Lucide bolt glyph. */
  val nutFilled: Int get() = R.drawable.ic_lucide_bolt

  /** Legacy find-vehicle alias for the official Lucide lightbulb glyph. */
  val headlight: Int get() = R.drawable.ic_lucide_lightbulb
  val arrowRight: Int get() = R.drawable.ic_lucide_arrow_right
  val arrowRightLeft: Int get() = R.drawable.ic_lucide_arrow_right_left
  val arrowUp: Int get() = R.drawable.ic_lucide_arrow_up
  val arrowUpDown: Int get() = R.drawable.ic_lucide_arrow_up_down
  val badgeCheck: Int get() = R.drawable.ic_lucide_badge_check
  val badgeInfo: Int get() = R.drawable.ic_lucide_badge_info
  val ban: Int get() = R.drawable.ic_lucide_ban
  val batteryFull: Int get() = R.drawable.ic_lucide_battery_full
  val batteryWarning: Int get() = R.drawable.ic_lucide_battery_warning
  val bolt: Int get() = R.drawable.ic_lucide_bolt
  val bookmark: Int get() = R.drawable.ic_lucide_bookmark
  val briefcase: Int get() = R.drawable.ic_lucide_briefcase
  val calendar: Int get() = R.drawable.ic_lucide_calendar
  val calendarCheck: Int get() = R.drawable.ic_lucide_calendar_check
  val chartColumn: Int get() = R.drawable.ic_lucide_chart_column
  val chevronLeft: Int get() = R.drawable.ic_lucide_chevron_left
  val circle: Int get() = R.drawable.ic_lucide_circle
  val circleAlert: Int get() = R.drawable.ic_lucide_circle_alert
  val circleCheck: Int get() = R.drawable.ic_lucide_circle_check
  val circleDot: Int get() = R.drawable.ic_lucide_circle_dot
  val circlePlus: Int get() = R.drawable.ic_lucide_circle_plus
  val circleQuestionMark: Int get() = R.drawable.ic_lucide_circle_question_mark
  val circleStop: Int get() = R.drawable.ic_lucide_circle_stop
  val circleUser: Int get() = R.drawable.ic_lucide_circle_user
  val clipboard: Int get() = R.drawable.ic_lucide_clipboard
  val clipboardList: Int get() = R.drawable.ic_lucide_clipboard_list
  val clipboardPaste: Int get() = R.drawable.ic_lucide_clipboard_paste
  val cloud: Int get() = R.drawable.ic_lucide_cloud
  val cloudOff: Int get() = R.drawable.ic_lucide_cloud_off
  val compass: Int get() = R.drawable.ic_lucide_compass
  val copy: Int get() = R.drawable.ic_lucide_copy
  val creditCard: Int get() = R.drawable.ic_lucide_credit_card
  val crosshair: Int get() = R.drawable.ic_lucide_crosshair
  val download: Int get() = R.drawable.ic_lucide_download
  val eye: Int get() = R.drawable.ic_lucide_eye
  val eyeOff: Int get() = R.drawable.ic_lucide_eye_off
  val fileText: Int get() = R.drawable.ic_lucide_file_text
  val gamepad2: Int get() = R.drawable.ic_lucide_gamepad_2
  val gauge: Int get() = R.drawable.ic_lucide_gauge
  val gitBranch: Int get() = R.drawable.ic_lucide_git_branch
  val headphones: Int get() = R.drawable.ic_lucide_headphones
  val heartPulse: Int get() = R.drawable.ic_lucide_heart_pulse
  val history: Int get() = R.drawable.ic_lucide_history
  val house: Int get() = R.drawable.ic_lucide_house
  val keyRound: Int get() = R.drawable.ic_lucide_key_round
  val languages: Int get() = R.drawable.ic_lucide_languages
  val layers: Int get() = R.drawable.ic_lucide_layers
  val layoutList: Int get() = R.drawable.ic_lucide_layout_list
  val leaf: Int get() = R.drawable.ic_lucide_leaf
  val lifeBuoy: Int get() = R.drawable.ic_lucide_life_buoy
  val lightbulb: Int get() = R.drawable.ic_lucide_lightbulb
  val link: Int get() = R.drawable.ic_lucide_link
  val list: Int get() = R.drawable.ic_lucide_list
  val locate: Int get() = R.drawable.ic_lucide_locate
  val logIn: Int get() = R.drawable.ic_lucide_log_in
  val logOut: Int get() = R.drawable.ic_lucide_log_out
  val mail: Int get() = R.drawable.ic_lucide_mail
  val map: Int get() = R.drawable.ic_lucide_map
  val megaphone: Int get() = R.drawable.ic_lucide_megaphone
  val messageCircle: Int get() = R.drawable.ic_lucide_message_circle
  val navigation: Int get() = R.drawable.ic_lucide_navigation
  val packageOpen: Int get() = R.drawable.ic_lucide_package_open
  val phone: Int get() = R.drawable.ic_lucide_phone
  val pin: Int get() = R.drawable.ic_lucide_pin
  val pointer: Int get() = R.drawable.ic_lucide_pointer
  val power: Int get() = R.drawable.ic_lucide_power
  val radio: Int get() = R.drawable.ic_lucide_radio
  val radioTower: Int get() = R.drawable.ic_lucide_radio_tower
  val receipt: Int get() = R.drawable.ic_lucide_receipt
  val ruler: Int get() = R.drawable.ic_lucide_ruler
  val save: Int get() = R.drawable.ic_lucide_save
  val scanLine: Int get() = R.drawable.ic_lucide_scan_line
  val scrollText: Int get() = R.drawable.ic_lucide_scroll_text
  val search: Int get() = R.drawable.ic_lucide_search
  val settingsGear: Int get() = R.drawable.ic_lucide_settings
  val settings2: Int get() = R.drawable.ic_lucide_settings_2
  val share2: Int get() = R.drawable.ic_lucide_share_2
  val shield: Int get() = R.drawable.ic_lucide_shield
  val shieldCheck: Int get() = R.drawable.ic_lucide_shield_check
  val smartphone: Int get() = R.drawable.ic_lucide_smartphone
  val sparkles: Int get() = R.drawable.ic_lucide_sparkles
  val squarePen: Int get() = R.drawable.ic_lucide_square_pen
  val stethoscope: Int get() = R.drawable.ic_lucide_stethoscope
  val ticket: Int get() = R.drawable.ic_lucide_ticket
  val trash2: Int get() = R.drawable.ic_lucide_trash_2
  val treePine: Int get() = R.drawable.ic_lucide_tree_pine
  val type: Int get() = R.drawable.ic_lucide_type
  val unlink: Int get() = R.drawable.ic_lucide_unlink
  val unplug: Int get() = R.drawable.ic_lucide_unplug
  val upload: Int get() = R.drawable.ic_lucide_upload
  val user: Int get() = R.drawable.ic_lucide_user
  val userPlus: Int get() = R.drawable.ic_lucide_user_plus
  val userX: Int get() = R.drawable.ic_lucide_user_x
  val users: Int get() = R.drawable.ic_lucide_users
  val wallet: Int get() = R.drawable.ic_lucide_wallet
  val warehouse: Int get() = R.drawable.ic_lucide_warehouse
  val watch: Int get() = R.drawable.ic_lucide_watch
  val wifi: Int get() = R.drawable.ic_lucide_wifi
  val wifiOff: Int get() = R.drawable.ic_lucide_wifi_off
  val wrench: Int get() = R.drawable.ic_lucide_wrench
}

/** Same size, tint and accessibility contract as [LucideIcon], using Android vector resources. */
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
