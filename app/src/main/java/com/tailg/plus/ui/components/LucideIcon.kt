package com.tailg.plus.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ContentPasteGo
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.tailg.plus.ui.theme.AppIconSizes
import com.tailg.plus.ui.theme.CyberHomeColors

/**
 * Port of `lib/widgets/lucide_icon.dart`.
 *
 * Flutter wraps every icon through `flutter_lucide`. Until a Lucide font/vector
 * set lands in this project, every call site goes through [LucideIcon] and the
 * [Lucide] map below, which currently resolves to **Material icons**. Each
 * entry carries the exact Lucide icon name in a comment so the later Lucide
 * swap is a mechanical `Lucide.xxx → lucide.xxx` replacement.
 */

/**
 * Thin wrapper so every icon call site routes through [Lucide] (Material icons
 * interim). Dart default `size` was 22.0 — mapped to the [AppIconSizes.md]
 * token (20.dp) to stay on the token ladder.
 *
 * [contentDescription] defaults to null (decorative) — icons inside labeled
 * `AppPressable`s are already covered by their semantics label. Pass a
 * description for standalone, meaningful icons so TalkBack announces them.
 */
@Composable
fun LucideIcon(
  icon: ImageVector,
  modifier: Modifier = Modifier,
  size: Dp = AppIconSizes.md,
  color: Color = CyberHomeColors.inkMuted,
  strokeWidth: Float = 2f,
  contentDescription: String? = null,
) {
  Icon(
    imageVector = icon,
    contentDescription = contentDescription,
    modifier = modifier.size(size),
    tint = color,
  )
}

/** Canonical icon map for the Tailg VOID shell (see file header). */
object Lucide {
  // Lucide: layout-grid
  val service: ImageVector = Icons.Filled.GridView
  // Lucide: bike
  val vehicle: ImageVector = Icons.Filled.DirectionsBike
  // Lucide: user
  val mine: ImageVector = Icons.Filled.Person
  // Lucide: settings
  val settings: ImageVector = Icons.Filled.Settings
  // Lucide: chevron-right
  val chevronRight: ImageVector = Icons.Filled.ChevronRight
  // Lucide: chevron-down
  val chevronDown: ImageVector = Icons.Filled.KeyboardArrowDown
  // Lucide: chevron-left
  val chevronLeft: ImageVector = Icons.Filled.ChevronLeft
  // Lucide: arrow-left
  val arrowLeft: ImageVector = Icons.Filled.ArrowBack
  // Lucide: map-pin
  val mapPin: ImageVector = Icons.Filled.LocationOn
  // Lucide: git-branch
  val route: ImageVector = Icons.Filled.AltRoute
  // Lucide: shield
  val fence: ImageVector = Icons.Filled.Shield
  // Lucide: battery-charging
  val battery: ImageVector = Icons.Filled.BatteryChargingFull
  // Lucide: battery-full
  val batteryFull: ImageVector = Icons.Filled.BatteryFull
  // Lucide: chart-column
  val chart: ImageVector = Icons.Filled.BarChart
  // Lucide: sliders-horizontal
  val tune: ImageVector = Icons.Filled.Tune
  // Lucide: layout-list
  val more: ImageVector = Icons.Filled.ViewList
  // Lucide: radio
  val find: ImageVector = Icons.Filled.Radio
  // Lucide: lock
  val lock: ImageVector = Icons.Filled.Lock
  // Lucide: lock-open
  val unlock: ImageVector = Icons.Filled.LockOpen
  // Lucide: power
  val power: ImageVector = Icons.Filled.PowerSettingsNew
  // Lucide: package-open
  val seat: ImageVector = Icons.Filled.Inventory2
  // Lucide: bluetooth
  val bluetooth: ImageVector = Icons.Filled.Bluetooth
  // Lucide: bluetooth-off
  val bluetoothOff: ImageVector = Icons.Filled.BluetoothDisabled
  // Lucide: wifi
  val wifi: ImageVector = Icons.Filled.Wifi
  // Lucide: cloud
  val cloud: ImageVector = Icons.Filled.Cloud
  // Lucide: git-branch
  val channel: ImageVector = Icons.Filled.AltRoute
  // Lucide: bell
  val message: ImageVector = Icons.Filled.Notifications
  // Lucide: circle-question-mark
  val help: ImageVector = Icons.Filled.Help
  // Lucide: info
  val info: ImageVector = Icons.Filled.Info
  // Lucide: badge-info
  val about: ImageVector = Icons.Filled.Info
  // Lucide: warehouse
  val garage: ImageVector = Icons.Filled.Warehouse
  // Lucide: log-in
  val login: ImageVector = Icons.Filled.Login
  // Lucide: log-out
  val logout: ImageVector = Icons.Filled.Logout
  // Lucide: phone
  val phone: ImageVector = Icons.Filled.Phone
  // Lucide: key-round
  val key: ImageVector = Icons.Filled.Key
  // Lucide: scan-line
  val scan: ImageVector = Icons.Filled.QrCodeScanner
  // Lucide: plus
  val plus: ImageVector = Icons.Filled.Add
  // Lucide: check
  val check: ImageVector = Icons.Filled.Check
  // Lucide: x
  val x: ImageVector = Icons.Filled.Close
  // Lucide: triangle-alert
  val alert: ImageVector = Icons.Filled.WarningAmber
  // Lucide: zap
  val zap: ImageVector = Icons.Filled.Bolt
  // Lucide: activity
  val activity: ImageVector = Icons.Filled.ShowChart
  // Lucide: compass
  val compass: ImageVector = Icons.Filled.Explore
  // Lucide: shield
  val shield: ImageVector = Icons.Filled.Shield
  // Lucide: heart-pulse
  val pulse: ImageVector = Icons.Filled.MonitorHeart
  // Lucide: refresh-cw
  val refresh: ImageVector = Icons.Filled.Refresh
  // Lucide: copy
  val copy: ImageVector = Icons.Filled.ContentCopy
  // Lucide: eye
  val eye: ImageVector = Icons.Filled.Visibility
  // Lucide: eye-off
  val eyeOff: ImageVector = Icons.Filled.VisibilityOff
  // Lucide: link
  val link: ImageVector = Icons.Filled.Link
  // Lucide: unplug
  val unplug: ImageVector = Icons.Filled.UsbOff
  // Lucide: sparkles
  val spark: ImageVector = Icons.Filled.AutoAwesome
  // Lucide: layers
  val layers: ImageVector = Icons.Filled.Layers
  // Lucide: wrench
  val wrench: ImageVector = Icons.Filled.Construction
  // Lucide: stethoscope
  val stethoscope: ImageVector = Icons.Filled.MedicalServices
  // Lucide: circle-user
  val userCircle: ImageVector = Icons.Filled.AccountCircle
  // Lucide: house
  val home: ImageVector = Icons.Filled.Home
  // Lucide: circle-check
  val checkCircle: ImageVector = Icons.Filled.CheckCircle
  // Lucide: circle-plus
  val plusCircle: ImageVector = Icons.Filled.AddCircle
  // Lucide: bluetooth-searching
  val bluetoothSearching: ImageVector = Icons.Filled.BluetoothSearching
  // Lucide: circle-stop
  val stop: ImageVector = Icons.Filled.StopCircle
  // Lucide: languages
  val languages: ImageVector = Icons.Filled.Language
  // Lucide: ruler
  val ruler: ImageVector = Icons.Filled.Straighten
  // Lucide: type
  val type: ImageVector = Icons.Filled.TextFields
  // Lucide: shield-check
  val shieldCheck: ImageVector = Icons.Filled.VerifiedUser
  // Lucide: file-text
  val fileText: ImageVector = Icons.Filled.Description
  // Lucide: mail
  val mail: ImageVector = Icons.Filled.Mail
  // Lucide: megaphone
  val megaphone: ImageVector = Icons.Filled.Campaign
  // Lucide: battery-warning
  val batteryWarning: ImageVector = Icons.Filled.BatteryAlert
  // Lucide: list
  val list: ImageVector = Icons.Filled.List
  // Lucide: square-pen
  val edit: ImageVector = Icons.Filled.Edit
  // Lucide: pointer
  val pointer: ImageVector = Icons.Filled.NearMe
  // Lucide: thermometer
  val thermometer: ImageVector = Icons.Filled.DeviceThermostat
  // Lucide: gauge
  val gauge: ImageVector = Icons.Filled.Speed
  // Lucide: rotate-ccw
  val rotateCcw: ImageVector = Icons.Filled.RestartAlt
  // Lucide: clipboard
  val clipboard: ImageVector = Icons.Filled.ContentPaste
  // Lucide: clipboard-paste
  val clipboardPaste: ImageVector = Icons.Filled.ContentPasteGo
  // Lucide: map
  val map: ImageVector = Icons.Filled.Map
  // Lucide: navigation
  val navigation: ImageVector = Icons.Filled.Navigation
  // Lucide: locate
  val locate: ImageVector = Icons.Filled.MyLocation
  // Lucide: wifi-off
  val wifiOff: ImageVector = Icons.Filled.WifiOff
  // Lucide: unlink
  val unlink: ImageVector = Icons.Filled.LinkOff
  // Lucide: trash-2
  val trash: ImageVector = Icons.Filled.Delete
  // Lucide: badge-check
  val badgeCheck: ImageVector = Icons.Filled.Verified
  // Lucide: circle-alert
  val alertCircle: ImageVector = Icons.Filled.ErrorOutline
  // Lucide: briefcase
  val briefcase: ImageVector = Icons.Filled.BusinessCenter
  // Lucide: radar
  val radar: ImageVector = Icons.Filled.Radar
  // Lucide: pin
  val pin: ImageVector = Icons.Filled.PushPin
  // Lucide: gamepad-2
  val gamepad: ImageVector = Icons.Filled.SportsEsports
  // Lucide: radio
  val sensors: ImageVector = Icons.Filled.Sensors
  // Lucide: crosshair
  val control: ImageVector = Icons.Filled.CenterFocusStrong
  // Lucide: nfc
  val nfc: ImageVector = Icons.Filled.Nfc
  // Lucide: history
  val history: ImageVector = Icons.Filled.History
  // Lucide: credit-card
  val creditCard: ImageVector = Icons.Filled.CreditCard
  // Lucide: watch
  val watch: ImageVector = Icons.Filled.Watch
  // Lucide: smartphone
  val smartphone: ImageVector = Icons.Filled.Smartphone
  // Lucide: user-plus
  val userPlus: ImageVector = Icons.Filled.PersonAdd
  // Lucide: share-2
  val share: ImageVector = Icons.Filled.Share
  // Lucide: save
  val save: ImageVector = Icons.Filled.Save
  // Lucide: calendar
  val calendar: ImageVector = Icons.Filled.CalendarMonth
  // Lucide: bookmark
  val bookmark: ImageVector = Icons.Filled.Bookmark
  // Lucide: clipboard-list
  val clipboardList: ImageVector = Icons.Filled.Assignment
  // Lucide: ticket
  val ticket: ImageVector = Icons.Filled.ConfirmationNumber
  // Lucide: leaf
  val leaf: ImageVector = Icons.Filled.EnergySavingsLeaf
  // Lucide: tree (park) — tree-absorption metric
  val tree: ImageVector = Icons.Filled.Park
  // Lucide: headphones
  val headphones: ImageVector = Icons.Filled.Headphones
  // Lucide: users
  val users: ImageVector = Icons.Filled.Groups
  // Lucide: user-x
  val userX: ImageVector = Icons.Filled.PersonRemove
  // Lucide: search
  val search: ImageVector = Icons.Filled.Search
  // Lucide: circle
  val circle: ImageVector = Icons.Filled.Circle
  // Lucide: circle-dot
  val circleDot: ImageVector = Icons.Filled.Adjust
  // Lucide: wallet
  val wallet: ImageVector = Icons.Filled.AccountBalanceWallet
  // Lucide: calendar-check
  val calendarCheck: ImageVector = Icons.Filled.EventAvailable
  // Lucide: receipt
  val receipt: ImageVector = Icons.Filled.Receipt
  // Lucide: ban
  val ban: ImageVector = Icons.Filled.Block
  // Lucide: upload
  val upload: ImageVector = Icons.Filled.Upload
  // Lucide: scroll-text
  val scrollText: ImageVector = Icons.Filled.Article
  // Lucide: life-buoy
  val lifeBuoy: ImageVector = Icons.Filled.Anchor
  // Lucide: message-circle
  val messageCircle: ImageVector = Icons.Filled.Notifications // closest Material glyph; Lucide swap later
  // Lucide: radio-tower
  val radioTower: ImageVector = Icons.Filled.SettingsInputAntenna
  // Lucide: chart-column
  val chartBar: ImageVector = Icons.Filled.BarChart
  // Lucide: cloud-off
  val cloudOff: ImageVector = Icons.Filled.CloudOff
  // Lucide: ban (key-off alias)
  val keyOff: ImageVector = Icons.Filled.Block
  // Lucide: headphones (support alias)
  val support: ImageVector = Icons.Filled.SupportAgent
  // Lucide: shield-check (privacy alias)
  val privacy: ImageVector = Icons.Filled.VerifiedUser
  // Lucide: arrow-up-down (swap-vert alias)
  val swapVert: ImageVector = Icons.Filled.SwapVert
  // Lucide: circle (radio-unchecked alias)
  val radioUnchecked: ImageVector = Icons.Filled.RadioButtonUnchecked
  // Lucide: circle-dot (trip-origin alias)
  val tripOrigin: ImageVector = Icons.Filled.TripOrigin
  // Lucide: compass (explore alias)
  val explore: ImageVector = Icons.Filled.Explore
  // Lucide: crosshair (location-searching alias)
  val locationSearching: ImageVector = Icons.Filled.LocationSearching
  // Lucide: user-x (group-off alias)
  val groupOff: ImageVector = Icons.Filled.PersonRemove
  // Lucide: chevron-down (arrow-down alias)
  val arrowDown: ImageVector = Icons.Filled.KeyboardArrowDown
  // Lucide: download
  val download: ImageVector = Icons.Filled.Download
}
