package com.tailg.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.components.CyberPageHeader
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.theme.AppIconSizes
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.CyberHomeColors
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R

/**
 * Port of `lib/pages/add_vehicle_page.dart` — the "add vehicle" hub.
 *
 * Three entry points mirror the Dart page:
 * - stringResource(R.string.add_vehicle_my) → official cloud vehicles page ([onOpenOfficialVehicles])
 * - stringResource(R.string.add_vehicle_imei) → [onOpenImeiBind]
 * - stringResource(R.string.add_vehicle_scan) → BLE scan page ([onOpenBleScan])
 *
 * The Dart page navigates via `Navigator.push` / `openScanTab`; the Compose
 * port exposes plain callbacks so the host can wire navigation.
 */
@Composable
fun AddVehicleScreen(
  onBack: () -> Unit,
  onOpenOfficialVehicles: () -> Unit,
  onOpenImeiBind: () -> Unit,
  onOpenBleScan: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = CyberHomeColors.pageBg,
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp),
    ) {
      CyberPageHeader(title = stringResource(R.string.add_vehicle_title), onBack = onBack)
      Spacer(Modifier.height(10.dp))
      AddVehicleHero()
      Spacer(Modifier.height(18.dp))
      AddVehicleSectionLabel(stringResource(R.string.add_vehicle_existing))
      Spacer(Modifier.height(8.dp))
      AddVehicleAction(
        icon = Lucide.cloud,
        title = stringResource(R.string.add_vehicle_my),
        subtitle = stringResource(R.string.add_vehicle_sync_desc),
        onTap = onOpenOfficialVehicles,
      )
      Spacer(Modifier.height(18.dp))
      AddVehicleSectionLabel(stringResource(R.string.add_vehicle_bind_new))
      Spacer(Modifier.height(8.dp))
      AddVehicleAction(
        icon = Lucide.pin,
        title = stringResource(R.string.add_vehicle_imei),
        subtitle = stringResource(R.string.add_vehicle_imei_desc),
        onTap = onOpenImeiBind,
      )
      Spacer(Modifier.height(18.dp))
      AddVehicleSectionLabel(stringResource(R.string.add_vehicle_ble))
      Spacer(Modifier.height(8.dp))
      AddVehicleAction(
        icon = Lucide.bluetoothSearching,
        title = stringResource(R.string.add_vehicle_scan),
        subtitle = stringResource(R.string.add_vehicle_scan_desc),
        onTap = onOpenBleScan,
      )
      Spacer(Modifier.height(14.dp))
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(AppRadii.tile))
          .background(CyberHomeColors.primarySoft)
          .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
          .padding(16.dp),
      ) {
        Text(
          text = stringResource(R.string.add_vehicle_support_desc),
          style = TextStyle(
            fontSize = 13.sp,
            lineHeight = 13.sp * 1.45f,
            color = CyberHomeColors.inkMuted,
          ),
        )
      }
      Spacer(Modifier.height(32.dp))
    }
  }
}

@Composable
private fun AddVehicleHero() {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .size(58.dp)
        .clip(CircleShape)
        .background(CyberHomeColors.primarySoft),
      contentAlignment = Alignment.Center,
    ) {
      LucideIcon(icon = Lucide.vehicle, color = CyberHomeColors.primary, size = AppIconSizes.lg)
    }
    Spacer(Modifier.size(14.dp))
    Column {
      Text(
        text = stringResource(R.string.add_vehicle_sync_your),
        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(4.dp))
      Text(
        text = stringResource(R.string.add_vehicle_login_desc),
        style = TextStyle(
          fontSize = 13.sp,
          lineHeight = 13.sp * 1.45f,
          color = CyberHomeColors.inkMuted,
        ),
      )
    }
  }
}

@Composable
private fun AddVehicleSectionLabel(text: String) {
  Text(
    text = text,
    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.inkMuted),
  )
}

@Composable
private fun AddVehicleAction(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  onTap: () -> Unit,
) {
  AppPressable(
    onClick = onTap,
    haptic = false,
    pressedBackground = CyberHomeColors.cardMuted,
    shape = RoundedCornerShape(AppRadii.tile),
    background = CyberHomeColors.card,
    borderWidth = 1.dp,
    borderColor = CyberHomeColors.line,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(42.dp)
          .clip(RoundedCornerShape(AppRadii.tile))
          .background(CyberHomeColors.primarySoft),
        contentAlignment = Alignment.Center,
      ) {
        LucideIcon(icon = icon, color = CyberHomeColors.primary, size = 22.dp)
      }
      Spacer(Modifier.size(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
          text = subtitle,
          style = TextStyle(
            fontSize = 13.sp,
            lineHeight = 13.sp * 1.45f,
            color = CyberHomeColors.inkMuted,
          ),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(Modifier.size(8.dp))
      LucideIcon(icon = Lucide.chevronRight, color = CyberHomeColors.inkFaint, size = AppIconSizes.md)
    }
  }
}
