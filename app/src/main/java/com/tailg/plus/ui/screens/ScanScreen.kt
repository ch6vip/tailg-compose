package com.tailg.plus.ui.screens

import android.Manifest
import android.bluetooth.BluetoothManager
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SweepGradient
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.components.CyberPageHeader
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.theme.AppIconSizes
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppTouchTargets
import com.tailg.plus.ui.theme.CyberHomeColors

/**
 * Port of `lib/pages/scan_page.dart` — BLE device scan + connect.
 *
 * The Dart page uses `flutter_blue_plus` for scanning and connecting. The
 * Compose port would use Android's native BluetoothLeScanner; until a BLE
 * service wrapper lands in the project, this shows the full UI (radar, device
 * list, scan FAB) with a placeholder device list and permission handling.
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

  var bluetoothOn by remember { mutableStateOf(isBluetoothEnabled(context)) }
  var hasPermissions by remember { mutableStateOf(hasBleScanPermissions(context)) }
  var scanning by remember { mutableStateOf(false) }
  var connectingRemoteId by remember { mutableStateOf<String?>(null) }
  // TODO: replace with real BLE scan results once a BluetoothLeScanner wrapper
  // is available. Placeholder stays empty so the UI renders the "no devices"
  // state correctly.
  var results by remember { mutableStateOf<List<ScanDevice>>(emptyList()) }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions(),
  ) { granted ->
    hasPermissions = granted.values.all { it }
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
            connectingRemoteId = device.id
            // TODO: call the real BLE connection manager. For now, delegate
            // to the host callback and clear the connecting state.
            onConnectDevice(device.id, device.name)
            connectingRemoteId = null
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
              // TODO: stop BluetoothLeScanner
            } else {
              if (!hasPermissions) {
                permissionLauncher.launch(bleScanPermissionArray())
                return@ScanFab
              }
              if (!bluetoothOn) return@ScanFab
              scanning = true
              // TODO: start BluetoothLeScanner and populate [results].
            }
          },
        )
      }
    }
  }
}

// ── Helpers ──────────────────────────────────────────────────────────────

/** Placeholder data class for a BLE scan result. */
data class ScanDevice(
  val id: String,
  val name: String,
  val rssi: Int,
)

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
            brush = SweepGradient(
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
