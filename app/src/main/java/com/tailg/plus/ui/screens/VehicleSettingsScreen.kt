package com.tailg.plus.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.data.cloud.OfficialCloudRedactor
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.components.AppSnackbarHost
import com.tailg.plus.ui.components.AppSnack
import com.tailg.plus.ui.components.CyberPageHeader
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.theme.AppIconSizes
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.CyberHomeColors
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R

/**
 * Port of `lib/pages/vehicle_settings_page.dart` — vehicle detail / settings.
 *
 * Observes [OfficialCloudService.stateFlow] for the selected vehicle. The Dart
 * page pushes `NotificationPrefsPage` / `InductionSettingsPage` via
 * `Navigator.push`; the Compose port exposes [onOpenNotificationPrefs] and
 * [onOpenInductionSettings] callbacks.
 */
@Composable
fun VehicleSettingsScreen(
  cloudService: OfficialCloudService,
  onBack: () -> Unit,
  onOpenNotificationPrefs: () -> Unit,
  onOpenInductionSettings: () -> Unit,
  onAddVehicle: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val state by cloudService.stateFlow.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val log = remember { LogService() }
  var showUnbindDialog by remember { mutableStateOf(false) }
  val strUnboundRefreshed = stringResource(R.string.vehicle_settings_unbound_refreshed)
  val strUnbindFailed = stringResource(R.string.vehicle_settings_unbind_failed)

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = CyberHomeColors.pageBg,
    snackbarHost = { AppSnackbarHost(snackbarHostState) },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
    ) {
      CyberPageHeader(title = stringResource(R.string.vehicle_settings_title), onBack = onBack)
      val vehicle = state.selectedVehicle
      if (vehicle == null) {
        SettingsEmptyState(
          signedIn = state.signedIn,
          onAddVehicle = onAddVehicle,
          modifier = Modifier.weight(1f),
        )
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .weight(1f),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 12.dp,
            bottom = 32.dp,
          ),
        ) {
          item(key = "summary") {
            VehicleSummary(vehicle = vehicle)
          }
          item {
            SettingsSectionLabel(stringResource(R.string.vehicle_settings_functions))
          }
          item {
            SettingsActionGroup {
              SettingsActionRow(
                icon = Lucide.message,
                title = stringResource(R.string.vehicle_settings_notifications),
                subtitle = stringResource(R.string.vehicle_settings_notifications_desc),
                showDivider = true,
                onTap = onOpenNotificationPrefs,
              )
              SettingsActionRow(
                icon = Lucide.sensors,
                title = stringResource(R.string.vehicle_settings_induction),
                subtitle = stringResource(R.string.vehicle_settings_induction_desc),
                showDivider = false,
                onTap = onOpenInductionSettings,
              )
            }
          }
          item {
            SettingsSectionLabel(stringResource(R.string.vehicle_settings_management))
          }
          item {
            DangerActionRow(onTap = { showUnbindDialog = true })
          }
        }
      }
    }
  }

  if (showUnbindDialog) {
    val vehicle = state.selectedVehicle
    if (vehicle != null) {
      UnbindConfirmDialog(
        vehicleName = vehicle.displayName,
        onDismiss = { showUnbindDialog = false },
        onConfirm = {
          showUnbindDialog = false
          scope.launch {
            try {
              cloudService.unbindVehicle(carId = vehicle.carId)
              AppSnack.success(snackbarHostState, strUnboundRefreshed)
            } catch (e: Exception) {
              log.operation(
                strUnbindFailed,
                detail = e.toString(),
                level = LogLevel.WARNING,
              )
              AppSnack.error(snackbarHostState, OfficialCloudRedactor.errorMessage(e))
            }
          }
        },
      )
    }
  }
}

@Composable
private fun VehicleSummary(vehicle: OfficialVehicle) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(16.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = vehicle.displayName,
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      )
      Spacer(Modifier.width(12.dp))
      VehicleStatusBadge(label = vehicle.onlineLabel, active = vehicle.online)
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      LucideIcon(icon = Lucide.shield, size = 15.dp, color = CyberHomeColors.inkMuted)
      Spacer(Modifier.width(6.dp))
      Text(
        text = vehicle.defenceLabel,
        style = TextStyle(fontSize = 13.sp, color = CyberHomeColors.inkMuted),
      )
    }
    Spacer(Modifier.height(16.dp))
    HorizontalDivider(thickness = 1.dp, color = CyberHomeColors.line)
    Spacer(Modifier.height(10.dp))
    VehicleInfoRow(label = stringResource(R.string.vehicle_settings_frame_no), value = vehicle.frame.ifEmpty { stringResource(R.string.vehicle_settings_unknown) })
    VehicleInfoRow(label = "IMEI", value = vehicle.imei.ifEmpty { stringResource(R.string.vehicle_settings_unknown) })
    VehicleInfoRow(label = stringResource(R.string.vehicle_settings_model_id), value = vehicle.modelType?.toString() ?: "-", isLast = true)
  }
}

@Composable
private fun VehicleStatusBadge(label: String, active: Boolean) {
  Row(
    modifier = Modifier
      .clip(RoundedCornerShape(AppRadii.pill))
      .background(if (active) CyberHomeColors.success.copy(alpha = 0.1f) else CyberHomeColors.control)
      .padding(horizontal = 9.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .size(6.dp)
        .clip(CircleShape)
        .background(if (active) CyberHomeColors.success else CyberHomeColors.inkFaint),
    )
    Spacer(Modifier.width(6.dp))
    Text(
      text = label,
      style = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.W600,
        color = if (active) CyberHomeColors.inkSecondary else CyberHomeColors.inkMuted,
      ),
    )
  }
}

@Composable
private fun VehicleInfoRow(label: String, value: String, isLast: Boolean = false) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 7.dp, bottom = if (isLast) 0.dp else 7.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.Top,
  ) {
    Text(
      text = label,
      modifier = Modifier.width(76.dp),
      style = TextStyle(fontSize = 13.sp, color = CyberHomeColors.inkFaint),
    )
    Text(
      text = value,
      modifier = Modifier.weight(1f),
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      textAlign = androidx.compose.ui.text.style.TextAlign.End,
      style = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.W600,
        color = CyberHomeColors.inkSecondary,
      ),
    )
  }
}

@Composable
private fun SettingsSectionLabel(label: String) {
  Text(
    text = label,
    modifier = Modifier.padding(start = 2.dp, top = 22.dp, end = 2.dp, bottom = 9.dp),
    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.inkMuted),
  )
}

@Composable
private fun SettingsActionGroup(content: @Composable () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile)),
  ) {
    content()
  }
}

@Composable
private fun SettingsActionRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  showDivider: Boolean,
  onTap: () -> Unit,
) {
  AppPressable(
    onClick = onTap,
    semanticsLabel = "$title，$subtitle",
    semanticsButton = true,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(72.dp)
        .padding(start = 14.dp, end = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(CyberHomeColors.control),
        contentAlignment = Alignment.Center,
      ) {
        LucideIcon(icon = icon, size = 20.dp, color = CyberHomeColors.inkSecondary)
      }
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.ink),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
          text = subtitle,
          style = TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      LucideIcon(icon = Lucide.chevronRight, size = 18.dp, color = CyberHomeColors.inkFaint)
    }
    if (showDivider) {
      HorizontalDivider(
        thickness = 1.dp,
        color = CyberHomeColors.line,
        modifier = Modifier.padding(start = 66.dp),
      )
    }
  }
}

@Composable
private fun DangerActionRow(onTap: () -> Unit) {
  AppPressable(
    onClick = onTap,
    semanticsLabel = stringResource(R.string.vehicle_settings_unbind_desc),
    semanticsButton = true,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(72.dp)
        .padding(horizontal = 14.dp)
        .clip(RoundedCornerShape(AppRadii.tile))
        .background(CyberHomeColors.danger.copy(alpha = 0.05f))
        .border(
          1.dp,
          CyberHomeColors.danger.copy(alpha = 0.18f),
          RoundedCornerShape(AppRadii.tile),
        ),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(CyberHomeColors.danger.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
      ) {
        LucideIcon(icon = Lucide.unlink, size = 20.dp, color = CyberHomeColors.danger)
      }
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(R.string.vehicle_settings_unbind),
          style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.danger),
        )
        Spacer(Modifier.height(4.dp))
        Text(
          text = stringResource(R.string.vehicle_settings_unbind_hint),
          style = TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
        )
      }
      LucideIcon(icon = Lucide.chevronRight, size = 18.dp, color = CyberHomeColors.danger)
    }
  }
}

@Composable
private fun SettingsEmptyState(
  signedIn: Boolean,
  onAddVehicle: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Box(
      modifier = Modifier
        .size(64.dp)
        .clip(CircleShape)
        .background(CyberHomeColors.card),
      contentAlignment = Alignment.Center,
    ) {
      LucideIcon(icon = Lucide.vehicle, size = 28.dp, color = CyberHomeColors.inkMuted)
    }
    Spacer(Modifier.height(16.dp))
    Text(
      text = stringResource(R.string.vehicle_settings_no_vehicle),
      style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
    )
    Spacer(Modifier.height(7.dp))
    Text(
      text = stringResource(R.string.vehicle_settings_login_first),
      style = TextStyle(fontSize = 13.sp, color = CyberHomeColors.inkMuted),
    )
    Spacer(Modifier.height(18.dp))
    Button(
      onClick = onAddVehicle,
      colors = ButtonDefaults.buttonColors(
        containerColor = CyberHomeColors.primary,
        contentColor = CyberHomeColors.white,
      ),
    ) {
      Text(text = if (signedIn) stringResource(R.string.vehicle_settings_add_vehicle) else stringResource(R.string.vehicle_settings_login_action))
    }
  }
}

@Composable
private fun UnbindConfirmDialog(
  vehicleName: String,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = CyberHomeColors.card,
    shape = RoundedCornerShape(AppRadii.tile),
    title = {
      Text(
        text = stringResource(R.string.vehicle_settings_unbind),
        style = TextStyle(color = CyberHomeColors.ink, fontWeight = FontWeight.W700),
      )
    },
    text = {
      Text(
        text = stringResource(R.string.vehicle_settings_unbind_confirm_format, vehicleName),
        style = TextStyle(color = CyberHomeColors.inkMuted, lineHeight = 13.sp * 1.5f),
      )
    },
    confirmButton = {
      Button(
        onClick = onConfirm,
        colors = ButtonDefaults.buttonColors(
          containerColor = CyberHomeColors.danger,
          contentColor = CyberHomeColors.white,
        ),
      ) {
        Text(stringResource(R.string.vehicle_settings_confirm_unbind))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.common_cancel), color = CyberHomeColors.inkMuted)
      }
    },
  )
}
