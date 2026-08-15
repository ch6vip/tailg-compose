package com.tailg.plus.ui.screens

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tailg.plus.data.ble.BleTimings
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.components.CyberPageHeader
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.theme.AppIconSizes
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppTouchTargets
import com.tailg.plus.ui.theme.CyberHomeColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Port of `lib/pages/scan_page.dart` — BLE device scan + connect.
 *
 * The Dart page uses `flutter_blue_plus` for scanning and connecting. The
 * Compose port uses Android's native [android.bluetooth.le.BluetoothLeScanner]
 * directly: a [ScanCallback] feeds a [MutableStateFlow] of stabilized results
 * (strongest RSSI per device, sorted by signal strength), and a
 * [LaunchedEffect] starts/stops scanning based on the [scanning] flag.
 *
 * The Dart page connects via `connectionManager.connect(device)` and upserts
 * a `VehicleProfile`; the Compose port exposes [onConnectDevice] so the host
 * can wire the real BLE stack.
 */
@Composable
fun ScanScreen(
  onBack: () -> Unit,
  onConnectDevice: (deviceId: String, deviceName: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var bluetoothOn by remember { mutableStateOf(isBluetoothEnabled(context)) }
  var hasPermissions by remember { mutableStateOf(hasBleScanPermissions(context)) }
  var scanning by remember { mutableStateOf(false) }
  var connectingRemoteId by remember { mutableStateOf<String?>(null) }
  var results by remember { mutableStateOf<List<ScanDevice>>(emptyList()) }

  // Stabilized scan-result accumulator (Dart `_resultsNotifier` equivalent).
  // The ScanCallback runs on a binder thread; it upserts into this map keyed by
  // device address (keeping the strongest RSSI), then pokes [rawResults] so the
  // composition-scope collector re-stabilizes + sorts the display list.
  val discovered = remember { java.util.concurrent.ConcurrentHashMap<String, ScanResult>() }

  // Trigger flow: every scan callback bumps this so the collector re-runs.
  val rawResults = remember { MutableStateFlow(0) }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions(),
  ) { granted ->
    hasPermissions = granted.values.all { it }
    // If the user just granted everything, kick off a scan immediately so they
    // don't have to tap the FAB twice (mirrors the Dart `_startScan` flow).
    if (hasPermissions && isBluetoothEnabled(context) && !scanning) {
      scanning = true
    }
  }

  // Re-check bluetooth state when the screen resumes (Dart uses a stream; here
  // a fresh read on recomposition is enough for the manual-scan page).
  bluetoothOn = isBluetoothEnabled(context)
  hasPermissions = hasBleScanPermissions(context)

  // Stabilize + sort raw scan results on the composition scope (Dart
  // `_stabilizeScanResults`): keep the strongest RSSI per device id, preserve
  // existing order for already-seen devices, append newcomers sorted by RSSI.
  // Throttled via collectLatest so rapid scan callbacks don't flood recompose.
  LaunchedEffect(Unit) {
    rawResults.collectLatest {
      val snapshot = discovered.values.toList()
      if (snapshot.isEmpty()) {
        if (results.isNotEmpty()) results = emptyList()
        return@collectLatest
      }
      val byId = LinkedHashMap<String, ScanResult>()
      for (result in snapshot) {
        val id = try {
          result.device.address
        } catch (_: SecurityException) {
          continue
        }
        val current = byId[id]
        if (current == null || result.rssi > current.rssi) byId[id] = result
      }
      val stable = ArrayList<ScanDevice>(byId.size)
      // Preserve order of previously displayed devices.
      for (previous in results) {
        val latest = byId.remove(previous.id)
        if (latest != null) {
          stable.add(latest.toScanDevice())
        }
      }
      // Newcomers sorted by RSSI desc, then address asc (Dart tiebreak).
      val newcomers = byId.values.sortedWith(
        compareByDescending<ScanResult> { it.rssi }
          .thenBy { runCatching { it.device.address }.getOrDefault("") },
      )
      for (n in newcomers) {
        stable.add(n.toScanDevice())
      }
      results = stable
    }
  }

  // Start/stop the real BluetoothLeScanner based on the [scanning] flag.
  // DisposableEffect ensures the scan is always torn down when the composable
  // leaves the composition (e.g. back navigation) — mirrors the Dart
  // `dispose` path that cancels `FlutterBluePlus.stopScan()`.
  DisposableEffect(scanning) {
    if (!scanning) {
      return@DisposableEffect onDispose { /* nothing to stop when we never started */ }
    }
    val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    val scanner = adapter?.bluetoothLeScanner
    if (scanner == null) {
      scanning = false
      return@DisposableEffect onDispose { }
    }
    val callback = object : ScanCallback() {
      override fun onScanResult(callbackType: Int, result: ScanResult) {
        upsertDiscovered(discovered, result)
        rawResults.value = rawResults.value + 1
      }

      override fun onBatchScanResults(results: MutableList<ScanResult>) {
        for (r in results) upsertDiscovered(discovered, r)
        rawResults.value = rawResults.value + 1
      }

      override fun onScanFailed(errorCode: Int) {
        scanning = false
      }
    }
    val settings = ScanSettings.Builder()
      .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
      .build()
    // startScan can throw SecurityException if permissions were revoked between
    // the permission check and the call; guard so the UI never crashes.
    val started = try {
      scanner.startScan(null, settings, callback)
      true
    } catch (_: SecurityException) {
      hasPermissions = false
      scanning = false
      false
    }
    onDispose {
      if (started) {
        try {
          scanner.stopScan(callback)
        } catch (_: SecurityException) {
          // Permissions revoked during scan — nothing more to do.
        }
      }
    }
  }

  // Auto-stop after the manual scan timeout (Dart `FlutterBluePlus.startScan`
  // passes `timeout: BleTimings.manualScanTimeout`). Using a separate
  // LaunchedEffect keyed on `scanning` keeps the timer tied to the scan state.
  LaunchedEffect(scanning) {
    if (!scanning) return@LaunchedEffect
    delay(BleTimings.manualScanTimeout.inWholeMilliseconds)
    if (scanning) scanning = false
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = CyberHomeColors.pageBg,
  ) { padding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState()),
      ) {
        CyberPageHeader(title = "搜索设备", onBack = onBack)
        if (!bluetoothOn) {
          ScanHintCard(
            icon = Lucide.bluetoothOff,
            title = "蓝牙未开启",
            subtitle = "开启蓝牙后即可搜索附近车辆",
          )
        }
        Spacer(Modifier.height(16.dp))
        RadarWidget(scanning = scanning)
        Spacer(Modifier.height(20.dp))
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
            text = when {
              !bluetoothOn -> "等待蓝牙开启"
              scanning -> "正在搜索附近设备..."
              else -> "点击下方按钮开始搜索"
            },
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
          )
          Spacer(Modifier.height(4.dp))
          Text(
            text = "请确保蓝牙已开启且靠近车辆",
            style = TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
          )
        }
        Spacer(Modifier.height(20.dp))
        DeviceList(
          results = results,
          connectingRemoteId = connectingRemoteId,
          onTap = { device ->
            if (connectingRemoteId != null) return@DeviceList
            // Stop scanning before connecting (Dart `_stopScan` in `_connectDevice`).
            scanning = false
            connectingRemoteId = device.id
            // Delegate to the host callback; clear the connecting state once
            // the host returns. The real ConnectionManager.connect() is a
            // suspend call the host can await before popping the back stack.
            scope.launch {
              try {
                onConnectDevice(device.id, device.name)
              } finally {
                connectingRemoteId = null
              }
            }
          },
        )
        Spacer(Modifier.height(80.dp))
      }

      // Scan FAB.
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(bottom = 16.dp),
        contentAlignment = Alignment.BottomCenter,
      ) {
        ScanFab(
          scanning = scanning,
          enabled = bluetoothOn,
          onTap = {
            if (scanning) {
              scanning = false
            } else {
              if (!hasPermissions) {
                permissionLauncher.launch(bleScanPermissionArray())
                return@ScanFab
              }
              if (!bluetoothOn) return@ScanFab
              // Clear stale results when starting a fresh scan (Dart resets
              // `_resultsNotifier` via `FlutterBluePlus.startScan`).
              discovered.clear()
              results = emptyList()
              scanning = true
            }
          },
        )
      }
    }
  }
}

// ── Helpers ──────────────────────────────────────────────────────────────

/** Data class for a stabilized BLE scan result (Dart `ScanResult` projection). */
data class ScanDevice(
  val id: String,
  val name: String,
  val rssi: Int,
)

/**
 * Upsert a [ScanResult] into the discovered map keyed by device address,
 * keeping the entry with the strongest RSSI (Dart `_stabilizeScanResults`
 * per-device merge). Called from the binder-thread [ScanCallback].
 *
 * `BluetoothDevice.getName()` / `getAddress()` can throw
 * [SecurityException] on Android 12+ if `BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN`
 * were revoked between `startScan` and the callback; guard so the binder
 * thread never crashes.
 */
private fun upsertDiscovered(
  discovered: java.util.concurrent.ConcurrentHashMap<String, ScanResult>,
  result: ScanResult,
) {
  val address = try {
    result.device.address
  } catch (_: SecurityException) {
    return
  }
  val existing = discovered[address]
  if (existing == null || result.rssi > existing.rssi) {
    discovered[address] = result
  }
}

/**
 * Project a [ScanResult] into a [ScanDevice] for display. `getName()` /
 * `getAddress()` are guarded against [SecurityException] (permissions can be
 * revoked mid-scan on Android 12+).
 */
private fun ScanResult.toScanDevice(): ScanDevice {
  val name = try {
    device.name ?: ""
  } catch (_: SecurityException) {
    ""
  }
  val id = try {
    device.address
  } catch (_: SecurityException) {
    ""
  }
  return ScanDevice(id = id, name = name, rssi = rssi)
}

private fun isBluetoothEnabled(context: Context): Boolean {
  val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
  val adapter = manager?.adapter
  return adapter?.isEnabled == true
}

private fun hasBleScanPermissions(context: Context): Boolean {
  val permissions = bleScanPermissionArray()
  return permissions.all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
  }
}

private fun bleScanPermissionArray(): Array<String> {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(
      Manifest.permission.BLUETOOTH_SCAN,
      Manifest.permission.BLUETOOTH_CONNECT,
      Manifest.permission.ACCESS_FINE_LOCATION,
    )
  } else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
  }
}

// ── Radar ────────────────────────────────────────────────────────────────

@Composable
private fun RadarWidget(scanning: Boolean) {
  val transition = rememberInfiniteTransition(label = "radar")
  val sweep by transition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 2000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "radarSweep",
  )
  val activeSweep = if (scanning) sweep else 0f

  Box(
    modifier = Modifier.size(180.dp),
    contentAlignment = Alignment.Center,
  ) {
    androidx.compose.foundation.Canvas(
      modifier = Modifier.fillMaxSize(),
    ) {
      val center = Offset(size.width / 2f, size.height / 2f)
      // Rings.
      drawCircle(
        color = CyberHomeColors.primarySoft,
        radius = 30f,
        center = center,
        style = Stroke(width = 1.5f),
      )
      drawCircle(
        color = CyberHomeColors.primarySoft,
        radius = 55f,
        center = center,
        style = Stroke(width = 1.5f),
      )
      drawCircle(
        color = CyberHomeColors.primarySoft,
        radius = 80f,
        center = center,
        style = Stroke(width = 1.5f),
      )
      // Sweep arc.
      if (scanning) {
        rotate(activeSweep, pivot = center) {
          drawArc(
            brush = Brush.sweepGradient(
              colors = listOf(CyberHomeColors.primarySoft, Color.Transparent),
            ),
            startAngle = -60f,
            sweepAngle = 60f,
            useCenter = true,
            topLeft = Offset(center.x - 80f, center.y - 80f),
            size = Size(160f, 160f),
          )
        }
      }
    }
    // Center button.
    Box(
      modifier = Modifier
        .size(AppTouchTargets.min)
        .shadow(
          elevation = 4.dp,
          shape = CircleShape,
          clip = false,
          ambientColor = Color.Transparent,
          spotColor = CyberHomeColors.actionShadow,
        )
        .clip(CircleShape)
        .background(CyberHomeColors.primary),
      contentAlignment = Alignment.Center,
    ) {
      LucideIcon(icon = Lucide.bluetoothSearching, color = CyberHomeColors.white, size = AppIconSizes.md)
    }
  }
}

// ── Hint card ───────────────────────────────────────────────────────────

@Composable
private fun ScanHintCard(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 8.dp)
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(CyberHomeColors.primarySoft),
      contentAlignment = Alignment.Center,
    ) {
      LucideIcon(icon = icon, color = CyberHomeColors.primary, size = AppIconSizes.md)
    }
    Spacer(Modifier.width(12.dp))
    Column {
      Text(
        text = title,
        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      )
      Spacer(Modifier.height(2.dp))
      Text(
        text = subtitle,
        style = TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
      )
    }
  }
}

// ── Device list ─────────────────────────────────────────────────────────

@Composable
private fun DeviceList(
  results: List<ScanDevice>,
  connectingRemoteId: String?,
  onTap: (ScanDevice) -> Unit,
) {
  if (results.isEmpty()) return
  LazyColumn(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    items(results, key = { it.id }) { device ->
      DeviceCard(
        device = device,
        connecting = connectingRemoteId == device.id,
        disabled = connectingRemoteId != null && connectingRemoteId != device.id,
        onTap = { onTap(device) },
      )
    }
  }
}

@Composable
private fun DeviceCard(
  device: ScanDevice,
  connecting: Boolean,
  disabled: Boolean,
  onTap: () -> Unit,
) {
  val name = if (device.name.isNotEmpty()) device.name else "未知设备"
  val isTailg = name.lowercase().contains("tl") || name.lowercase().contains("tailg")
  val strength = when {
    device.rssi > -60 -> SignalStrength.STRONG
    device.rssi > -80 -> SignalStrength.MEDIUM
    else -> SignalStrength.WEAK
  }
  val interactive = !disabled && !connecting

  AppPressable(
    onClick = if (interactive) onTap else null,
    enabled = interactive,
    haptic = false,
    shape = RoundedCornerShape(AppRadii.tile),
    background = if (disabled) CyberHomeColors.cardMuted else CyberHomeColors.card,
    borderWidth = 1.dp,
    borderColor = CyberHomeColors.line,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(AppTouchTargets.min)
          .clip(RoundedCornerShape(AppRadii.tile))
          .background(if (isTailg) CyberHomeColors.primarySoft else CyberHomeColors.control),
        contentAlignment = Alignment.Center,
      ) {
        LucideIcon(
          icon = if (isTailg) Lucide.vehicle else Lucide.bluetooth,
          size = AppIconSizes.md,
          color = if (isTailg) CyberHomeColors.primary else CyberHomeColors.inkFaint,
        )
      }
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = name,
          style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
          text = device.id,
          style = TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (connecting) {
        Column(horizontalAlignment = Alignment.End) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = CyberHomeColors.primary,
          )
          Spacer(Modifier.height(6.dp))
          Text(
            text = "连接中",
            style = TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkFaint),
          )
        }
      } else {
        Column(horizontalAlignment = Alignment.End) {
          SignalBars(strength = strength)
          Spacer(Modifier.height(6.dp))
          Text(
            text = if (disabled) "等待" else "连接绑定",
            style = TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkFaint),
          )
        }
      }
    }
  }
}

private enum class SignalStrength { STRONG, MEDIUM, WEAK }

@Composable
private fun SignalBars(strength: SignalStrength) {
  val heights = listOf(6.dp, 10.dp, 14.dp, 20.dp)
  val activeCount = when (strength) {
    SignalStrength.STRONG -> 4
    SignalStrength.MEDIUM -> 3
    SignalStrength.WEAK -> 2
  }
  val activeColor = if (strength == SignalStrength.WEAK) CyberHomeColors.warning else CyberHomeColors.success
  Row(
    horizontalArrangement = Arrangement.End,
    verticalAlignment = Alignment.Bottom,
  ) {
    heights.forEachIndexed { i, h ->
      Box(
        modifier = Modifier
          .padding(start = if (i > 0) 2.dp else 0.dp)
          .size(width = 4.dp, height = h)
          .clip(RoundedCornerShape(1.dp))
          .background(if (i < activeCount) activeColor else CyberHomeColors.lineStrong),
      )
    }
  }
}

// ── Scan FAB ────────────────────────────────────────────────────────────

@Composable
private fun ScanFab(
  scanning: Boolean,
  enabled: Boolean,
  onTap: () -> Unit,
) {
  AppPressable(
    onClick = if (enabled) onTap else null,
    enabled = enabled,
    haptic = false,
    semanticsLabel = if (scanning) "停止扫描" else "扫描",
    semanticsButton = true,
    shape = RoundedCornerShape(AppRadii.tile),
    background = when {
      !enabled -> CyberHomeColors.controlStrong
      scanning -> CyberHomeColors.inkSecondary
      else -> CyberHomeColors.primary
    },
    shadowElevation = 4.dp,
    shadowColor = CyberHomeColors.actionShadow,
  ) {
    Row(
      modifier = Modifier
        .padding(horizontal = 24.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LucideIcon(
        icon = if (scanning) Lucide.stop else Lucide.bluetoothSearching,
        color = if (enabled) CyberHomeColors.white else CyberHomeColors.inkFaint,
        size = AppIconSizes.md,
      )
      Spacer(Modifier.width(8.dp))
      Text(
        text = if (scanning) "停止" else "扫描",
        style = TextStyle(
          color = if (enabled) CyberHomeColors.white else CyberHomeColors.inkFaint,
          fontSize = 15.sp,
          fontWeight = FontWeight.W600,
        ),
      )
    }
  }
}
