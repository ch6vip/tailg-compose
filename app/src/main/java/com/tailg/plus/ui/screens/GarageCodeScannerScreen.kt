package com.tailg.plus.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.theme.AppTouchTargets
import com.tailg.plus.ui.theme.CyberHomeColors

/**
 * Port of `lib/pages/garage_code_scanner_page.dart` — QR / barcode scanner.
 *
 * The Dart page uses `mobile_scanner` (MobileScannerController) with QR,
 * Code128 and Code39 formats. The Compose port would use CameraX + ML Kit
 * barcode scanning; until those deps land, this shows a permission-aware
 * placeholder with the same overlay chrome (back + torch actions, scan hint).
 *
 * When a real scanner is wired in, [onScanned] should be called with the
 * trimmed raw value and the host pops the screen (Dart `Navigator.pop(value)`).
 */
@Composable
fun GarageCodeScannerScreen(
  onBack: () -> Unit,
  onScanned: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED,
    )
  }
  var torchOn by remember { mutableStateOf(false) }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted -> hasCameraPermission = granted }

  LaunchedEffect(Unit) {
    if (!hasCameraPermission) {
      permissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = CyberHomeColors.ink,
  ) { padding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .background(CyberHomeColors.ink),
    ) {
      // TODO: CameraX preview + ML Kit barcode scanner. Until the deps land,
      // show a centered placeholder so the chrome is visible and testable.
      ScannerPlaceholder(hasPermission = hasCameraPermission, onOpenSettings = {
        permissionLauncher.launch(Manifest.permission.CAMERA)
      })

      // Overlay chrome (back + torch + hint).
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          ScannerAction(
            label = "返回",
            icon = Lucide.arrowLeft,
            onTap = onBack,
          )
          ScannerAction(
            label = if (torchOn) "关闭手电筒" else "打开手电筒",
            icon = Lucide.zap,
            active = torchOn,
            onTap = {
              // TODO: toggle CameraX torch. Placeholder toggles state only.
              torchOn = !torchOn
            },
          )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
          text = "扫描车辆二维码或车架条码",
          modifier = Modifier.fillMaxWidth(),
          textAlign = TextAlign.Center,
          style = TextStyle(
            color = CyberHomeColors.white,
            fontSize = 15.sp,
            fontWeight = FontWeight.W700,
          ),
        )
      }
    }
  }
}

@Composable
private fun ScannerPlaceholder(
  hasPermission: Boolean,
  onOpenSettings: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    LucideIcon(
      icon = Lucide.alertCircle,
      size = 32.dp,
      color = CyberHomeColors.white,
    )
    Spacer(Modifier.height(12.dp))
    Text(
      text = if (hasPermission) "扫码功能即将上线" else "需要相机权限才能扫码",
      textAlign = TextAlign.Center,
      style = TextStyle(
        color = CyberHomeColors.white,
        fontSize = 15.sp,
        fontWeight = FontWeight.W700,
      ),
    )
    if (!hasPermission) {
      Spacer(Modifier.height(16.dp))
      Button(
        onClick = onOpenSettings,
        colors = ButtonDefaults.buttonColors(
          containerColor = CyberHomeColors.primary,
          contentColor = CyberHomeColors.white,
        ),
      ) {
        Text("打开设置")
      }
    }
  }
}

@Composable
private fun ScannerAction(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  onTap: () -> Unit,
  active: Boolean = false,
) {
  AppPressable(
    onClick = onTap,
    semanticsLabel = label,
    semanticsButton = true,
    shape = CircleShape,
    background = if (active) CyberHomeColors.primary else CyberHomeColors.ink.copy(alpha = 0.56f),
  ) {
    Box(
      modifier = Modifier.size(AppTouchTargets.min),
      contentAlignment = Alignment.Center,
    ) {
      LucideIcon(icon = icon, color = CyberHomeColors.white, size = 20.dp)
    }
  }
}
