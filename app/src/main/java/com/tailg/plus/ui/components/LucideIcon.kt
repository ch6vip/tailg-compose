package com.tailg.plus.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tailg.plus.ui.theme.AppIconSizes
import com.tailg.plus.ui.theme.CyberHomeColors

/**
 * Renders a genuine Lucide stroke icon, keeping the app's size, tint and accessibility contract.
 *
 * A null [contentDescription] marks a decorative icon; labeled controls provide their own
 * semantics. Standalone meaningful icons should pass a localized description.
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
  val strokeIcon = remember(icon, strokeWidth) {
    if (strokeWidth == 2f || !icon.name.startsWith("Lucide.")) icon
    else icon.withLucideStrokeWidth(strokeWidth)
  }
  Icon(
    imageVector = strokeIcon,
    contentDescription = contentDescription,
    modifier = modifier.size(size),
    tint = color,
  )
}

/**
 * App-wide Lucide vectors, converted from lucide-static 0.525.0 (ISC).
 * Attribution is bundled in assets/licenses/lucide.txt. Existing local drawable paths are
 * shared verbatim with [NinebotLucide]; all glyphs use a 24-unit viewport and round strokes.
 *
 * The original semantic aliases remain source-compatible. Lazy construction avoids parsing
 * unused icons during startup, and aliases share a single immutable [ImageVector].
 */
object Lucide {
  val service: ImageVector by lazy {
    lucideVector("layout-grid",
      "M4 3H9A1 1 0 0 1 10 4V9A1 1 0 0 1 9 10H4A1 1 0 0 1 3 9V4A1 1 0 0 1 4 3Z M15 3H20A1 1 0 0 1 21 4V9A1 1 0 0 1 20 10H15A1 1 0 0 1 14 9V4A1 1 0 0 1 15 3Z M15 14H20A1 1 0 0 1 21 15V20A1 1 0 0 1 20 21H15A1 1 0 0 1 14 20V15A1 1 0 0 1 15 14Z M4 14H9A1 1 0 0 1 10 15V20A1 1 0 0 1 9 21H4A1 1 0 0 1 3 20V15A1 1 0 0 1 4 14Z",
    )
  }
  val vehicle: ImageVector by lazy {
    lucideVector("bike",
      "M22 17.5A3.5 3.5 0 1 1 15 17.5A3.5 3.5 0 1 1 22 17.5Z M9 17.5A3.5 3.5 0 1 1 2 17.5A3.5 3.5 0 1 1 9 17.5Z M16 5A1 1 0 1 1 14 5A1 1 0 1 1 16 5Z M12 17.5V14l-3-3 4-3 2 3h2",
    )
  }
  val mine: ImageVector by lazy {
    lucideVector("user",
      "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2",
      "M16 7a4 4 0 1 0 -8 0a4 4 0 1 0 8 0Z",
    )
  }
  val settings: ImageVector by lazy {
    lucideVector("settings",
      "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
      "M15 12a3 3 0 1 0 -6 0a3 3 0 1 0 6 0Z",
    )
  }
  val chevronRight: ImageVector by lazy {
    lucideVector("chevron-right",
      "m9 18 6-6-6-6",
    )
  }
  val chevronDown: ImageVector by lazy {
    lucideVector("chevron-down",
      "m6 9 6 6 6-6",
    )
  }
  val chevronLeft: ImageVector by lazy {
    lucideVector("chevron-left",
      "m15 18-6-6 6-6",
    )
  }
  val arrowLeft: ImageVector by lazy {
    lucideVector("arrow-left",
      "m12 19-7-7 7-7",
      "M19 12H5",
    )
  }
  val mapPin: ImageVector by lazy {
    lucideVector("map-pin",
      "M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0",
      "M9,10 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0",
    )
  }
  val route: ImageVector by lazy {
    lucideVector("git-branch",
      "M6 3L6 15",
      "M21 6a3 3 0 1 0 -6 0a3 3 0 1 0 6 0Z",
      "M9 18a3 3 0 1 0 -6 0a3 3 0 1 0 6 0Z",
      "M18 9a9 9 0 0 1-9 9",
    )
  }
  val fence: ImageVector by lazy {
    lucideVector("shield",
      "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
    )
  }
  val battery: ImageVector by lazy {
    lucideVector("battery-charging",
      "m11 7-3 5h4l-3 5",
      "M14.856 6H16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2.935",
      "M22 14v-4",
      "M5.14 18H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h2.936",
    )
  }
  val batteryFull: ImageVector by lazy {
    lucideVector("battery-full",
      "M10 10v4",
      "M14 10v4",
      "M22 14v-4",
      "M6 10v4",
      "M4 6H16A2 2 0 0 1 18 8V16A2 2 0 0 1 16 18H4A2 2 0 0 1 2 16V8A2 2 0 0 1 4 6Z",
    )
  }
  val chart: ImageVector by lazy {
    lucideVector("chart-column",
      "M3 3v16a2 2 0 0 0 2 2h16",
      "M18 17V9",
      "M13 17V5",
      "M8 17v-3",
    )
  }
  val tune: ImageVector by lazy {
    lucideVector("sliders-horizontal",
      "M21 4L14 4",
      "M10 4L3 4",
      "M21 12L12 12",
      "M8 12L3 12",
      "M21 20L16 20",
      "M12 20L3 20",
      "M14 2L14 6",
      "M8 10L8 14",
      "M16 18L16 22",
    )
  }
  val more: ImageVector by lazy {
    lucideVector("layout-list",
      "M4 3H9A1 1 0 0 1 10 4V9A1 1 0 0 1 9 10H4A1 1 0 0 1 3 9V4A1 1 0 0 1 4 3Z",
      "M4 14H9A1 1 0 0 1 10 15V20A1 1 0 0 1 9 21H4A1 1 0 0 1 3 20V15A1 1 0 0 1 4 14Z",
      "M14 4h7",
      "M14 9h7",
      "M14 15h7",
      "M14 20h7",
    )
  }
  val find: ImageVector by lazy {
    lucideVector("radio",
      "M16.247 7.761a6 6 0 0 1 0 8.478",
      "M19.075 4.933a10 10 0 0 1 0 14.134",
      "M4.925 19.067a10 10 0 0 1 0-14.134",
      "M7.753 16.239a6 6 0 0 1 0-8.478",
      "M14 12a2 2 0 1 0 -4 0a2 2 0 1 0 4 0Z",
    )
  }
  val lock: ImageVector by lazy {
    lucideVector("lock",
      "M5,11 H19 A2,2 0 0,1 21,13 V20 A2,2 0 0,1 19,22 H5 A2,2 0 0,1 3,20 V13 A2,2 0 0,1 5,11 Z",
      "M7 11V7a5 5 0 0 1 10 0v4",
    )
  }
  val unlock: ImageVector by lazy {
    lucideVector("lock-open",
      "M5,11 H19 A2,2 0 0,1 21,13 V20 A2,2 0 0,1 19,22 H5 A2,2 0 0,1 3,20 V13 A2,2 0 0,1 5,11 Z",
      "M7 11V7a5 5 0 0 1 9.9-1",
    )
  }
  val power: ImageVector by lazy {
    lucideVector("power",
      "M12 2v10",
      "M18.4 6.6a9 9 0 1 1-12.77.04",
    )
  }
  val seat: ImageVector by lazy {
    lucideVector("package-open",
      "M12 22v-9",
      "M15.17 2.21a1.67 1.67 0 0 1 1.63 0L21 4.57a1.93 1.93 0 0 1 0 3.36L8.82 14.79a1.655 1.655 0 0 1-1.64 0L3 12.43a1.93 1.93 0 0 1 0-3.36z",
      "M20 13v3.87a2.06 2.06 0 0 1-1.11 1.83l-6 3.08a1.93 1.93 0 0 1-1.78 0l-6-3.08A2.06 2.06 0 0 1 4 16.87V13",
      "M21 12.43a1.93 1.93 0 0 0 0-3.36L8.83 2.2a1.64 1.64 0 0 0-1.63 0L3 4.57a1.93 1.93 0 0 0 0 3.36l12.18 6.86a1.636 1.636 0 0 0 1.63 0z",
    )
  }
  val bluetooth: ImageVector by lazy {
    lucideVector("bluetooth",
      "m7 7 10 10-5 5V2l5 5L7 17",
    )
  }
  val bluetoothOff: ImageVector by lazy {
    lucideVector("bluetooth-off",
      "m17 17-5 5V12l-5 5",
      "m2 2 20 20",
      "M14.5 9.5 17 7l-5-5v4.5",
    )
  }
  val wifi: ImageVector by lazy {
    lucideVector("wifi",
      "M12 20h.01",
      "M2 8.82a15 15 0 0 1 20 0",
      "M5 12.859a10 10 0 0 1 14 0",
      "M8.5 16.429a5 5 0 0 1 7 0",
    )
  }
  val cloud: ImageVector by lazy {
    lucideVector("cloud",
      "M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z",
    )
  }
  val channel: ImageVector get() = route
  val message: ImageVector by lazy {
    lucideVector("bell",
      "M10.268 21a2 2 0 0 0 3.464 0",
      "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326",
    )
  }
  val help: ImageVector by lazy {
    lucideVector("circle-question-mark",
      "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0",
      "M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3",
      "M12 17h.01",
    )
  }
  val info: ImageVector by lazy {
    lucideVector("info",
      "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0",
      "M12 16v-4",
      "M12 8h.01",
    )
  }
  val about: ImageVector by lazy {
    lucideVector("badge-info",
      "M3.85 8.62a4 4 0 0 1 4.78-4.77 4 4 0 0 1 6.74 0 4 4 0 0 1 4.78 4.78 4 4 0 0 1 0 6.74 4 4 0 0 1-4.77 4.78 4 4 0 0 1-6.75 0 4 4 0 0 1-4.78-4.77 4 4 0 0 1 0-6.76Z",
      "M12 16L12 12",
      "M12 8L12.01 8",
    )
  }
  val garage: ImageVector by lazy {
    lucideVector("warehouse",
      "M18 21V10a1 1 0 0 0-1-1H7a1 1 0 0 0-1 1v11",
      "M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 1.132-1.803l7.95-3.974a2 2 0 0 1 1.837 0l7.948 3.974A2 2 0 0 1 22 8z",
      "M6 13h12",
      "M6 17h12",
    )
  }
  val login: ImageVector by lazy {
    lucideVector("log-in",
      "m10 17 5-5-5-5",
      "M15 12H3",
      "M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4",
    )
  }
  val logout: ImageVector by lazy {
    lucideVector("log-out",
      "m16 17 5-5-5-5",
      "M21 12H9",
      "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4",
    )
  }
  val phone: ImageVector by lazy {
    lucideVector("phone",
      "M13.832 16.568a1 1 0 0 0 1.213-.303l.355-.465A2 2 0 0 1 17 15h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2A18 18 0 0 1 2 4a2 2 0 0 1 2-2h3a2 2 0 0 1 2 2v3a2 2 0 0 1-.8 1.6l-.468.351a1 1 0 0 0-.292 1.233 14 14 0 0 0 6.392 6.384",
    )
  }
  val key: ImageVector by lazy {
    lucideVector("key-round",
      "M2.586 17.414A2 2 0 0 0 2 18.828V21a1 1 0 0 0 1 1h3a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h1a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h.172a2 2 0 0 0 1.414-.586l.814-.814a6.5 6.5 0 1 0-4-4z",
      "M17 7.5a0.5 0.5 0 1 0 -1 0a0.5 0.5 0 1 0 1 0Z",
    )
  }
  val scan: ImageVector by lazy {
    lucideVector("scan-line",
      "M3 7V5a2 2 0 0 1 2-2h2",
      "M17 3h2a2 2 0 0 1 2 2v2",
      "M21 17v2a2 2 0 0 1-2 2h-2",
      "M7 21H5a2 2 0 0 1-2-2v-2",
      "M7 12h10",
    )
  }
  val plus: ImageVector by lazy {
    lucideVector("plus",
      "M5 12h14M12 5v14",
    )
  }
  val check: ImageVector by lazy {
    lucideVector("check",
      "M20 6 9 17l-5-5",
    )
  }
  val x: ImageVector by lazy {
    lucideVector("x",
      "M18 6 6 18",
      "m6 6 12 12",
    )
  }
  val alert: ImageVector by lazy {
    lucideVector("triangle-alert",
      "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3",
      "M12 9v4",
      "M12 17h.01",
    )
  }
  val zap: ImageVector by lazy {
    lucideVector("zap",
      "M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z",
    )
  }
  val activity: ImageVector by lazy {
    lucideVector("activity",
      "M22 12h-2.48a2 2 0 0 0-1.93 1.46l-2.35 8.36a.25.25 0 0 1-.48 0L9.24 2.18a.25.25 0 0 0-.48 0l-2.35 8.36A2 2 0 0 1 4.49 12H2",
    )
  }
  val compass: ImageVector by lazy {
    lucideVector("compass",
      "m16.24 7.76-1.804 5.411a2 2 0 0 1-1.265 1.265L7.76 16.24l1.804-5.411a2 2 0 0 1 1.265-1.265z",
      "M22 12a10 10 0 1 0 -20 0a10 10 0 1 0 20 0Z",
    )
  }
  val shield: ImageVector get() = fence
  val pulse: ImageVector by lazy {
    lucideVector("heart-pulse",
      "M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z",
      "M3.22 12H9.5l.5-1 2 4.5 2-7 1.5 3.5h5.27",
    )
  }
  val refresh: ImageVector by lazy {
    lucideVector("refresh-cw",
      "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
      "M21 3v5h-5",
      "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
      "M8 16H3v5",
    )
  }
  val copy: ImageVector by lazy {
    lucideVector("copy",
      "M10 8H20A2 2 0 0 1 22 10V20A2 2 0 0 1 20 22H10A2 2 0 0 1 8 20V10A2 2 0 0 1 10 8Z",
      "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2",
    )
  }
  val eye: ImageVector by lazy {
    lucideVector("eye",
      "M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0",
      "M15 12a3 3 0 1 0 -6 0a3 3 0 1 0 6 0Z",
    )
  }
  val eyeOff: ImageVector by lazy {
    lucideVector("eye-off",
      "M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49",
      "M14.084 14.158a3 3 0 0 1-4.242-4.242",
      "M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143",
      "m2 2 20 20",
    )
  }
  val link: ImageVector by lazy {
    lucideVector("link",
      "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71",
      "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71",
    )
  }
  val unplug: ImageVector by lazy {
    lucideVector("unplug",
      "m19 5 3-3",
      "m2 22 3-3",
      "M6.3 20.3a2.4 2.4 0 0 0 3.4 0L12 18l-6-6-2.3 2.3a2.4 2.4 0 0 0 0 3.4Z",
      "M7.5 13.5 10 11",
      "M10.5 16.5 13 14",
      "m12 6 6 6 2.3-2.3a2.4 2.4 0 0 0 0-3.4l-2.6-2.6a2.4 2.4 0 0 0-3.4 0Z",
    )
  }
  val spark: ImageVector by lazy {
    lucideVector("sparkles",
      "M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z",
      "M20 3v4",
      "M22 5h-4",
      "M4 17v2",
      "M5 18H3",
    )
  }
  val layers: ImageVector by lazy {
    lucideVector("layers",
      "M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83z",
      "M2 12a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 12",
      "M2 17a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 17",
    )
  }
  val wrench: ImageVector by lazy {
    lucideVector("wrench",
      "M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z",
    )
  }
  val stethoscope: ImageVector by lazy {
    lucideVector("stethoscope",
      "M11 2v2",
      "M5 2v2",
      "M5 3H4a2 2 0 0 0-2 2v4a6 6 0 0 0 12 0V5a2 2 0 0 0-2-2h-1",
      "M8 15a6 6 0 0 0 12 0v-3",
      "M22 10a2 2 0 1 0 -4 0a2 2 0 1 0 4 0Z",
    )
  }
  val userCircle: ImageVector by lazy {
    lucideVector("circle-user",
      "M22 12a10 10 0 1 0 -20 0a10 10 0 1 0 20 0Z",
      "M15 10a3 3 0 1 0 -6 0a3 3 0 1 0 6 0Z",
      "M7 20.662V19a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v1.662",
    )
  }
  val home: ImageVector by lazy {
    lucideVector("house",
      "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
      "M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
    )
  }
  val checkCircle: ImageVector by lazy {
    lucideVector("circle-check",
      "M22 12a10 10 0 1 0 -20 0a10 10 0 1 0 20 0Z",
      "m9 12 2 2 4-4",
    )
  }
  val plusCircle: ImageVector by lazy {
    lucideVector("circle-plus",
      "M22 12a10 10 0 1 0 -20 0a10 10 0 1 0 20 0Z",
      "M8 12h8",
      "M12 8v8",
    )
  }
  val bluetoothSearching: ImageVector by lazy {
    lucideVector("bluetooth-searching",
      "m7 7 10 10-5 5V2l5 5L7 17",
      "M20.83 14.83a4 4 0 0 0 0-5.66",
      "M18 12h.01",
    )
  }
  val stop: ImageVector by lazy {
    lucideVector("circle-stop",
      "M22 12a10 10 0 1 0 -20 0a10 10 0 1 0 20 0Z",
      "M10 9H14A1 1 0 0 1 15 10V14A1 1 0 0 1 14 15H10A1 1 0 0 1 9 14V10A1 1 0 0 1 10 9Z",
    )
  }
  val languages: ImageVector by lazy {
    lucideVector("languages",
      "m5 8 6 6",
      "m4 14 6-6 2-3",
      "M2 5h12",
      "M7 2h1",
      "m22 22-5-10-5 10",
      "M14 18h6",
    )
  }
  val ruler: ImageVector by lazy {
    lucideVector("ruler",
      "M21.3 15.3a2.4 2.4 0 0 1 0 3.4l-2.6 2.6a2.4 2.4 0 0 1-3.4 0L2.7 8.7a2.41 2.41 0 0 1 0-3.4l2.6-2.6a2.41 2.41 0 0 1 3.4 0Z",
      "m14.5 12.5 2-2",
      "m11.5 9.5 2-2",
      "m8.5 6.5 2-2",
      "m17.5 15.5 2-2",
    )
  }
  val type: ImageVector by lazy {
    lucideVector("type",
      "M12 4v16",
      "M4 7V5a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v2",
      "M9 20h6",
    )
  }
  val shieldCheck: ImageVector by lazy {
    lucideVector("shield-check",
      "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
      "m9 12 2 2 4-4",
    )
  }
  val fileText: ImageVector by lazy {
    lucideVector("file-text",
      "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z",
      "M14 2v4a2 2 0 0 0 2 2h4",
      "M10 9H8",
      "M16 13H8",
      "M16 17H8",
    )
  }
  val mail: ImageVector by lazy {
    lucideVector("mail",
      "m22 7-8.991 5.727a2 2 0 0 1-2.009 0L2 7",
      "M4 4H20A2 2 0 0 1 22 6V18A2 2 0 0 1 20 20H4A2 2 0 0 1 2 18V6A2 2 0 0 1 4 4Z",
    )
  }
  val megaphone: ImageVector by lazy {
    lucideVector("megaphone",
      "M11 6a13 13 0 0 0 8.4-2.8A1 1 0 0 1 21 4v12a1 1 0 0 1-1.6.8A13 13 0 0 0 11 14H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z",
      "M6 14a12 12 0 0 0 2.4 7.2 2 2 0 0 0 3.2-2.4A8 8 0 0 1 10 14",
      "M8 6v8",
    )
  }
  val batteryWarning: ImageVector by lazy {
    lucideVector("battery-warning",
      "M10 17h.01",
      "M10 7v6",
      "M14 6h2a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2",
      "M22 14v-4",
      "M6 18H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h2",
    )
  }
  val list: ImageVector by lazy {
    lucideVector("list",
      "M3 12h.01",
      "M3 18h.01",
      "M3 6h.01",
      "M8 12h13",
      "M8 18h13",
      "M8 6h13",
    )
  }
  val edit: ImageVector by lazy {
    lucideVector("square-pen",
      "M12 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7",
      "M18.375 2.625a1 1 0 0 1 3 3l-9.013 9.014a2 2 0 0 1-.853.505l-2.873.84a.5.5 0 0 1-.62-.62l.84-2.873a2 2 0 0 1 .506-.852z",
    )
  }
  val pointer: ImageVector by lazy {
    lucideVector("pointer",
      "M22 14a8 8 0 0 1-8 8",
      "M18 11v-1a2 2 0 0 0-2-2a2 2 0 0 0-2 2",
      "M14 10V9a2 2 0 0 0-2-2a2 2 0 0 0-2 2v1",
      "M10 9.5V4a2 2 0 0 0-2-2a2 2 0 0 0-2 2v10",
      "M18 11a2 2 0 1 1 4 0v3a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15",
    )
  }
  val thermometer: ImageVector by lazy {
    lucideVector("thermometer",
      "M14 4v10.54a4 4 0 1 1-4 0V4a2 2 0 0 1 4 0Z",
    )
  }
  val gauge: ImageVector by lazy {
    lucideVector("gauge",
      "m12 14 4-4",
      "M3.34 19a10 10 0 1 1 17.32 0",
    )
  }
  val rotateCcw: ImageVector by lazy {
    lucideVector("rotate-ccw",
      "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8",
      "M3 3v5h5",
    )
  }
  val clipboard: ImageVector by lazy {
    lucideVector("clipboard",
      "M9 2H15A1 1 0 0 1 16 3V5A1 1 0 0 1 15 6H9A1 1 0 0 1 8 5V3A1 1 0 0 1 9 2Z",
      "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2",
    )
  }
  val clipboardPaste: ImageVector by lazy {
    lucideVector("clipboard-paste",
      "M11 14h10",
      "M16 4h2a2 2 0 0 1 2 2v1.344",
      "m17 18 4-4-4-4",
      "M8 4H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 1.793-1.113",
      "M9 2H15A1 1 0 0 1 16 3V5A1 1 0 0 1 15 6H9A1 1 0 0 1 8 5V3A1 1 0 0 1 9 2Z",
    )
  }
  val map: ImageVector by lazy {
    lucideVector("map",
      "M14.106 5.553a2 2 0 0 0 1.788 0l3.659-1.83A1 1 0 0 1 21 4.619v12.764a1 1 0 0 1-.553.894l-4.553 2.277a2 2 0 0 1-1.788 0l-4.212-2.106a2 2 0 0 0-1.788 0l-3.659 1.83A1 1 0 0 1 3 19.381V6.618a1 1 0 0 1 .553-.894l4.553-2.277a2 2 0 0 1 1.788 0z",
      "M15 5.764v15",
      "M9 3.236v15",
    )
  }
  val navigation: ImageVector by lazy {
    lucideVector("navigation",
      "M3 11L22 2L13 21L11 13L3 11Z",
    )
  }
  val locate: ImageVector by lazy {
    lucideVector("locate",
      "M2 12L5 12",
      "M19 12L22 12",
      "M12 2L12 5",
      "M12 19L12 22",
      "M19 12a7 7 0 1 0 -14 0a7 7 0 1 0 14 0Z",
    )
  }
  val wifiOff: ImageVector by lazy {
    lucideVector("wifi-off",
      "M12 20h.01",
      "M8.5 16.429a5 5 0 0 1 7 0",
      "M5 12.859a10 10 0 0 1 5.17-2.69",
      "M19 12.859a10 10 0 0 0-2.007-1.523",
      "M2 8.82a15 15 0 0 1 4.177-2.643",
      "M22 8.82a15 15 0 0 0-11.288-3.764",
      "m2 2 20 20",
    )
  }
  val unlink: ImageVector by lazy {
    lucideVector("unlink",
      "m18.84 12.25 1.72-1.71h-.02a5.004 5.004 0 0 0-.12-7.07 5.006 5.006 0 0 0-6.95 0l-1.72 1.71",
      "m5.17 11.75-1.71 1.71a5.004 5.004 0 0 0 .12 7.07 5.006 5.006 0 0 0 6.95 0l1.71-1.71",
      "M8 2L8 5",
      "M2 8L5 8",
      "M16 19L16 22",
      "M19 16L22 16",
    )
  }
  val trash: ImageVector by lazy {
    lucideVector("trash-2",
      "M3 6h18",
      "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6",
      "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2",
      "M10 11L10 17",
      "M14 11L14 17",
    )
  }
  val badgeCheck: ImageVector by lazy {
    lucideVector("badge-check",
      "M3.85 8.62a4 4 0 0 1 4.78-4.77 4 4 0 0 1 6.74 0 4 4 0 0 1 4.78 4.78 4 4 0 0 1 0 6.74 4 4 0 0 1-4.77 4.78 4 4 0 0 1-6.75 0 4 4 0 0 1-4.78-4.77 4 4 0 0 1 0-6.76Z",
      "m9 12 2 2 4-4",
    )
  }
  val alertCircle: ImageVector by lazy {
    lucideVector("circle-alert",
      "M22 12a10 10 0 1 0 -20 0a10 10 0 1 0 20 0Z",
      "M12 8L12 12",
      "M12 16L12.01 16",
    )
  }
  val briefcase: ImageVector by lazy {
    lucideVector("briefcase",
      "M16 20V4a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16",
      "M4 6H20A2 2 0 0 1 22 8V18A2 2 0 0 1 20 20H4A2 2 0 0 1 2 18V8A2 2 0 0 1 4 6Z",
    )
  }
  val radar: ImageVector by lazy {
    lucideVector("radar",
      "M19.07 4.93A10 10 0 0 0 6.99 3.34",
      "M4 6h.01",
      "M2.29 9.62A10 10 0 1 0 21.31 8.35",
      "M16.24 7.76A6 6 0 1 0 8.23 16.67",
      "M12 18h.01",
      "M17.99 11.66A6 6 0 0 1 15.77 16.67",
      "M10,12 a2,2 0 1,0 4,0 a2,2 0 1,0 -4,0",
      "m13.41 10.59 5.66-5.66",
    )
  }
  val pin: ImageVector by lazy {
    lucideVector("pin",
      "M12 17v5",
      "M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z",
    )
  }
  val gamepad: ImageVector by lazy {
    lucideVector("gamepad-2",
      "M6 11L10 11",
      "M8 9L8 13",
      "M15 12L15.01 12",
      "M18 10L18.01 10",
      "M17.32 5H6.68a4 4 0 0 0-3.978 3.59c-.006.052-.01.101-.017.152C2.604 9.416 2 14.456 2 16a3 3 0 0 0 3 3c1 0 1.5-.5 2-1l1.414-1.414A2 2 0 0 1 9.828 16h4.344a2 2 0 0 1 1.414.586L17 18c.5.5 1 1 2 1a3 3 0 0 0 3-3c0-1.545-.604-6.584-.685-7.258-.007-.05-.011-.1-.017-.151A4 4 0 0 0 17.32 5z",
    )
  }
  val sensors: ImageVector get() = find
  val control: ImageVector by lazy {
    lucideVector("crosshair",
      "M22 12a10 10 0 1 0 -20 0a10 10 0 1 0 20 0Z",
      "M22 12L18 12",
      "M6 12L2 12",
      "M12 6L12 2",
      "M12 22L12 18",
    )
  }
  val nfc: ImageVector by lazy {
    lucideVector("nfc",
      "M6 8.32a7.43 7.43 0 0 1 0 7.36",
      "M9.46 6.21a11.76 11.76 0 0 1 0 11.58",
      "M12.91 4.1a15.91 15.91 0 0 1 .01 15.8",
      "M16.37 2a20.16 20.16 0 0 1 0 20",
    )
  }
  val history: ImageVector by lazy {
    lucideVector("history",
      "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8",
      "M3 3v5h5",
      "M12 7v5l4 2",
    )
  }
  val creditCard: ImageVector by lazy {
    lucideVector("credit-card",
      "M4 5H20A2 2 0 0 1 22 7V17A2 2 0 0 1 20 19H4A2 2 0 0 1 2 17V7A2 2 0 0 1 4 5Z",
      "M2 10L22 10",
    )
  }
  val watch: ImageVector by lazy {
    lucideVector("watch",
      "M12 10v2.2l1.6 1",
      "m16.13 7.66-.81-4.05a2 2 0 0 0-2-1.61h-2.68a2 2 0 0 0-2 1.61l-.78 4.05",
      "m7.88 16.36.8 4a2 2 0 0 0 2 1.61h2.72a2 2 0 0 0 2-1.61l.81-4.05",
      "M18 12a6 6 0 1 0 -12 0a6 6 0 1 0 12 0Z",
    )
  }
  val smartphone: ImageVector by lazy {
    lucideVector("smartphone",
      "M7 2H17A2 2 0 0 1 19 4V20A2 2 0 0 1 17 22H7A2 2 0 0 1 5 20V4A2 2 0 0 1 7 2Z",
      "M12 18h.01",
    )
  }
  val userPlus: ImageVector by lazy {
    lucideVector("user-plus",
      "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
      "M13 7a4 4 0 1 0 -8 0a4 4 0 1 0 8 0Z",
      "M19 8L19 14",
      "M22 11L16 11",
    )
  }
  val share: ImageVector by lazy {
    lucideVector("share-2",
      "M21 5a3 3 0 1 0 -6 0a3 3 0 1 0 6 0Z",
      "M9 12a3 3 0 1 0 -6 0a3 3 0 1 0 6 0Z",
      "M21 19a3 3 0 1 0 -6 0a3 3 0 1 0 6 0Z",
      "M8.59 13.51L15.42 17.49",
      "M15.41 6.51L8.59 10.49",
    )
  }
  val save: ImageVector by lazy {
    lucideVector("save",
      "M15.2 3a2 2 0 0 1 1.4.6l3.8 3.8a2 2 0 0 1 .6 1.4V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
      "M17 21v-7a1 1 0 0 0-1-1H8a1 1 0 0 0-1 1v7",
      "M7 3v4a1 1 0 0 0 1 1h7",
    )
  }
  val calendar: ImageVector by lazy {
    lucideVector("calendar",
      "M8 2v4",
      "M16 2v4",
      "M5 4H19A2 2 0 0 1 21 6V20A2 2 0 0 1 19 22H5A2 2 0 0 1 3 20V6A2 2 0 0 1 5 4Z",
      "M3 10h18",
    )
  }
  val bookmark: ImageVector by lazy {
    lucideVector("bookmark",
      "m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z",
    )
  }
  val clipboardList: ImageVector by lazy {
    lucideVector("clipboard-list",
      "M9 2H15A1 1 0 0 1 16 3V5A1 1 0 0 1 15 6H9A1 1 0 0 1 8 5V3A1 1 0 0 1 9 2Z",
      "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2",
      "M12 11h4",
      "M12 16h4",
      "M8 11h.01",
      "M8 16h.01",
    )
  }
  val ticket: ImageVector by lazy {
    lucideVector("ticket",
      "M2 9a3 3 0 0 1 0 6v2a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-2a3 3 0 0 1 0-6V7a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2Z",
      "M13 5v2",
      "M13 17v2",
      "M13 11v2",
    )
  }
  val leaf: ImageVector by lazy {
    lucideVector("leaf",
      "M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.48 19 2c1 2 2 4.18 2 8 0 5.5-4.78 10-10 10Z",
      "M2 21c0-3 1.85-5.36 5.08-6C9.5 14.52 12 13 13 12",
    )
  }
  val tree: ImageVector by lazy {
    lucideVector("tree-pine",
      "m17 14 3 3.3a1 1 0 0 1-.7 1.7H4.7a1 1 0 0 1-.7-1.7L7 14h-.3a1 1 0 0 1-.7-1.7L9 9h-.2A1 1 0 0 1 8 7.3L12 3l4 4.3a1 1 0 0 1-.8 1.7H15l3 3.3a1 1 0 0 1-.7 1.7H17Z",
      "M12 22v-3",
    )
  }
  val headphones: ImageVector by lazy {
    lucideVector("headphones",
      "M3 14h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a9 9 0 0 1 18 0v7a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3",
    )
  }
  val users: ImageVector by lazy {
    lucideVector("users",
      "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
      "M16 3.128a4 4 0 0 1 0 7.744",
      "M22 21v-2a4 4 0 0 0-3-3.87",
      "M13 7a4 4 0 1 0 -8 0a4 4 0 1 0 8 0Z",
    )
  }
  val userX: ImageVector by lazy {
    lucideVector("user-x",
      "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
      "M13 7a4 4 0 1 0 -8 0a4 4 0 1 0 8 0Z",
      "M17 8L22 13",
      "M22 8L17 13",
    )
  }
  val search: ImageVector by lazy {
    lucideVector("search",
      "m21 21-4.34-4.34",
      "M19 11a8 8 0 1 0 -16 0a8 8 0 1 0 16 0Z",
    )
  }
  val circle: ImageVector by lazy {
    lucideVector("circle",
      "M22 12a10 10 0 1 0 -20 0a10 10 0 1 0 20 0Z",
    )
  }
  val circleDot: ImageVector by lazy {
    lucideVector("circle-dot",
      "M22 12a10 10 0 1 0 -20 0a10 10 0 1 0 20 0Z",
      "M13 12a1 1 0 1 0 -2 0a1 1 0 1 0 2 0Z",
    )
  }
  val wallet: ImageVector by lazy {
    lucideVector("wallet",
      "M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1",
      "M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4",
    )
  }
  val calendarCheck: ImageVector by lazy {
    lucideVector("calendar-check",
      "M8 2v4",
      "M16 2v4",
      "M5 4H19A2 2 0 0 1 21 6V20A2 2 0 0 1 19 22H5A2 2 0 0 1 3 20V6A2 2 0 0 1 5 4Z",
      "M3 10h18",
      "m9 16 2 2 4-4",
    )
  }
  val receipt: ImageVector by lazy {
    lucideVector("receipt",
      "M4 2v20l2-1 2 1 2-1 2 1 2-1 2 1 2-1 2 1V2l-2 1-2-1-2 1-2-1-2 1-2-1-2 1Z",
      "M16 8h-6a2 2 0 1 0 0 4h4a2 2 0 1 1 0 4H8",
      "M12 17.5v-11",
    )
  }
  val ban: ImageVector by lazy {
    lucideVector("ban",
      "M22 12a10 10 0 1 0 -20 0a10 10 0 1 0 20 0Z",
      "m4.9 4.9 14.2 14.2",
    )
  }
  val upload: ImageVector by lazy {
    lucideVector("upload",
      "M12 3v12",
      "m17 8-5-5-5 5",
      "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4",
    )
  }
  val scrollText: ImageVector by lazy {
    lucideVector("scroll-text",
      "M15 12h-5",
      "M15 8h-5",
      "M19 17V5a2 2 0 0 0-2-2H4",
      "M8 21h12a2 2 0 0 0 2-2v-1a1 1 0 0 0-1-1H11a1 1 0 0 0-1 1v1a2 2 0 1 1-4 0V5a2 2 0 1 0-4 0v2a1 1 0 0 0 1 1h3",
    )
  }
  val lifeBuoy: ImageVector by lazy {
    lucideVector("life-buoy",
      "M22 12a10 10 0 1 0 -20 0a10 10 0 1 0 20 0Z",
      "m4.93 4.93 4.24 4.24",
      "m14.83 9.17 4.24-4.24",
      "m14.83 14.83 4.24 4.24",
      "m9.17 14.83-4.24 4.24",
      "M16 12a4 4 0 1 0 -8 0a4 4 0 1 0 8 0Z",
    )
  }
  val messageCircle: ImageVector by lazy {
    lucideVector("message-circle",
      "M7.9 20A9 9 0 1 0 4 16.1L2 22Z",
    )
  }
  val radioTower: ImageVector by lazy {
    lucideVector("radio-tower",
      "M4.9 16.1C1 12.2 1 5.8 4.9 1.9",
      "M7.8 4.7a6.14 6.14 0 0 0-.8 7.5",
      "M14 9a2 2 0 1 0 -4 0a2 2 0 1 0 4 0Z",
      "M16.2 4.8c2 2 2.26 5.11.8 7.47",
      "M19.1 1.9a9.96 9.96 0 0 1 0 14.1",
      "M9.5 18h5",
      "m8 22 4-11 4 11",
    )
  }
  val chartBar: ImageVector get() = chart
  val cloudOff: ImageVector by lazy {
    lucideVector("cloud-off",
      "m2 2 20 20",
      "M5.782 5.782A7 7 0 0 0 9 19h8.5a4.5 4.5 0 0 0 1.307-.193",
      "M21.532 16.5A4.5 4.5 0 0 0 17.5 10h-1.79A7.008 7.008 0 0 0 10 5.07",
    )
  }
  val keyOff: ImageVector get() = ban
  val support: ImageVector get() = headphones
  val privacy: ImageVector get() = shieldCheck
  val swapVert: ImageVector by lazy {
    lucideVector("arrow-up-down",
      "m21 16-4 4-4-4",
      "M17 20V4",
      "m3 8 4-4 4 4",
      "M7 4v16",
    )
  }
  val radioUnchecked: ImageVector get() = circle
  val tripOrigin: ImageVector get() = circleDot
  val explore: ImageVector get() = compass
  val locationSearching: ImageVector get() = control
  val groupOff: ImageVector get() = userX
  val arrowDown: ImageVector get() = chevronDown
  val download: ImageVector by lazy {
    lucideVector("download",
      "M12 15V3",
      "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4",
      "m7 10 5 5 5-5",
    )
  }
  val arrowUpRight: ImageVector by lazy {
    lucideVector("arrow-up-right",
      "M7 7h10v10",
      "M7 17 17 7",
    )
  }
  val arrowRight: ImageVector by lazy {
    lucideVector("arrow-right",
      "M5 12h14",
      "m12 5 7 7-7 7",
    )
  }
  val arrowUp: ImageVector by lazy {
    lucideVector("arrow-up",
      "m5 12 7-7 7 7",
      "M12 19V5",
    )
  }
  val timer: ImageVector by lazy {
    lucideVector("timer",
      "M10,2 L14,2",
      "M12,14 L15,11",
      "M4,14 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0",
    )
  }
  val fingerprint: ImageVector by lazy {
    lucideVector("fingerprint",
      "M12 10a2 2 0 0 0-2 2c0 1.02-.1 2.51-.26 4",
      "M14 13.12c0 2.38 0 6.38-1 8.88",
      "M17.29 21.02c.12-.6.43-2.3.5-3.02",
      "M2 12a10 10 0 0 1 18-6",
      "M2 16h.01",
      "M21.8 16c.2-2 .131-5.354 0-6",
      "M5 19.5C5.5 18 6 15 6 12a6 6 0 0 1 .34-2",
      "M8.65 22c.21-.66.45-1.32.57-2",
      "M9 6.8a6 6 0 0 1 9 5.2v2",
    )
  }
  val armchair: ImageVector by lazy {
    lucideVector("armchair",
      "M19 9V6a2 2 0 0 0-2-2H7a2 2 0 0 0-2 2v3",
      "M3 16a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-5a2 2 0 0 0-4 0v1.5a.5.5 0 0 1-.5.5h-9a.5.5 0 0 1-.5-.5V11a2 2 0 0 0-4 0z",
      "M5 18v2",
      "M19 18v2",
    )
  }
  val hexagon: ImageVector by lazy {
    lucideVector("hexagon",
      "M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z",
    )
  }
  val lightbulb: ImageVector by lazy {
    lucideVector("lightbulb",
      "M15 14c.2-1 .7-1.7 1.5-2.5 1-.9 1.5-2.2 1.5-3.5A6 6 0 0 0 6 8c0 1 .2 2.2 1.5 3.5.7.7 1.3 1.5 1.5 2.5",
      "M9 18h6",
      "M10 22h4",
    )
  }
  val bolt: ImageVector by lazy {
    lucideVector("bolt",
      "M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z",
      "M16 12a4 4 0 1 0 -8 0a4 4 0 1 0 8 0Z",
    )
  }
  val signal: ImageVector by lazy {
    lucideVector("signal",
      "M2 20h.01",
      "M7 20v-4",
      "M12 20v-8",
      "M17 20V8",
      "M22 4v16",
    )
  }
  val panelBottom: ImageVector by lazy {
    lucideVector("panel-bottom",
      "M5 3H19A2 2 0 0 1 21 5V19A2 2 0 0 1 19 21H5A2 2 0 0 1 3 19V5A2 2 0 0 1 5 3Z M3 15h18",
    )
  }
  val scaling: ImageVector by lazy {
    lucideVector("scaling",
      "M12 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7 M14 15H9v-5 M16 3h5v5 M21 3 9 15",
    )
  }
}

private fun lucideVector(name: String, vararg paths: String): ImageVector =
  ImageVector.Builder(
    name = "Lucide.$name",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
    autoMirror = name == "arrow-left" || name == "chevron-left" || name == "chevron-right",
  ).apply {
    paths.forEach { path ->
      addPath(
        pathData = PathParser().parsePathString(path).toNodes(),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
      )
    }
  }.build()

/** The registry contains flat, unfilled paths, so alternate stroke weights retain every contour. */
private fun ImageVector.withLucideStrokeWidth(width: Float): ImageVector {
  require(width.isFinite() && width > 0f) { "Lucide stroke width must be finite and positive" }
  return ImageVector.Builder(
    name = name,
    defaultWidth = defaultWidth,
    defaultHeight = defaultHeight,
    viewportWidth = viewportWidth,
    viewportHeight = viewportHeight,
    autoMirror = autoMirror,
  ).apply {
    root.forEach { node ->
      val path = node as VectorPath
      addPath(
        pathData = path.pathData,
        pathFillType = path.pathFillType,
        fill = null,
        stroke = path.stroke,
        strokeLineWidth = width,
        strokeLineCap = path.strokeLineCap,
        strokeLineJoin = path.strokeLineJoin,
      )
    }
  }.build()
}
