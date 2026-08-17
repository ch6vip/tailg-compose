package com.tailg.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.data.cloud.OfficialCloudLoginValidator
import com.tailg.plus.data.cloud.OfficialCloudRedactor
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.cloud.OfficialCloudState
import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.components.AppSnackbarHost
import com.tailg.plus.ui.components.AppSnack
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.components.cyberButtonShape
import com.tailg.plus.ui.components.cyberFilledButtonColors
import com.tailg.plus.ui.components.cyberOutlinedButtonBorder
import com.tailg.plus.ui.components.cyberOutlinedButtonColors
import com.tailg.plus.ui.components.cyberTextFieldColors
import com.tailg.plus.ui.components.cyberTextFieldShape
import com.tailg.plus.ui.navigation.Routes
import com.tailg.plus.ui.theme.AppIconSizes
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppTouchTargets
import com.tailg.plus.ui.theme.CyberHomeColors
import com.tailg.plus.util.SmsCountdown
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R
import kotlinx.coroutines.launch

/**
 * Port of `lib/pages/official_cloud_page.dart` — official cloud vehicle list,
 * login, vehicle detail, and self-check pages.
 *
 * The Dart file contains four widgets (OfficialCloudPage,
 * OfficialVehicleDetailPage, OfficialVehicleSelfCheckPage, and their helpers);
 * this port collapses them into a single screen with sub-composables.
 */
@Composable
fun OfficialCloudScreen(
  cloudService: OfficialCloudService,
  onBack: () -> Unit,
  onNavigate: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  val log = remember { LogService() }
  val cloudState by cloudService.stateFlow.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }

  var phone by remember { mutableStateOf(cloudService.currentState.phone) }
  var smsCode by remember { mutableStateOf("") }
  val smsCountdown = remember { SmsCountdown(scope = scope) }
  val countdown by smsCountdown.remaining.collectAsState()

  val strSmsSent = stringResource(R.string.cloud_sms_sent)
  val strSmsFailed = stringResource(R.string.cloud_sms_failed)
  val strLoginSuccess = stringResource(R.string.cloud_login_success)
  val strLoginFailed = stringResource(R.string.cloud_login_failed)
  val strVehicleRefreshed = stringResource(R.string.cloud_vehicle_refreshed)
  val strVehicleRefreshFailed = stringResource(R.string.cloud_vehicle_refresh_failed)
  val strVehicleSwitched = stringResource(R.string.cloud_vehicle_switched)
  fun requestCode() {
    if (smsCountdown.isActive) return
    val normalizedPhone = OfficialCloudLoginValidator.compactPhone(phone)
    scope.launch {
      try {
        cloudService.requestSmsCode(normalizedPhone)
        smsCountdown.start()
        AppSnack.success(snackbarHostState, strSmsSent)
      } catch (e: Exception) {
        log.operation(strSmsFailed, detail = OfficialCloudRedactor.errorMessage(e), level = LogLevel.WARNING)
        AppSnack.error(snackbarHostState, OfficialCloudRedactor.errorMessage(e))
      }
    }
  }

  fun login() {
    val normalizedPhone = OfficialCloudLoginValidator.compactPhone(phone)
    scope.launch {
      try {
        cloudService.login(normalizedPhone, smsCode.trim())
        AppSnack.success(snackbarHostState, strLoginSuccess)
      } catch (e: Exception) {
        log.operation(strLoginFailed, detail = OfficialCloudRedactor.errorMessage(e), level = LogLevel.WARNING)
        AppSnack.error(snackbarHostState, OfficialCloudRedactor.errorMessage(e))
      }
    }
  }

  fun refresh() {
    scope.launch {
      try {
        cloudService.refreshVehicles()
        AppSnack.success(snackbarHostState, strVehicleRefreshed)
      } catch (e: Exception) {
        log.operation(strVehicleRefreshFailed, detail = OfficialCloudRedactor.errorMessage(e), level = LogLevel.WARNING)
        AppSnack.error(snackbarHostState, OfficialCloudRedactor.errorMessage(e))
      }
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = CyberHomeColors.pageBg,
    snackbarHost = { AppSnackbarHost(snackbarHostState) },
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 32.dp),
    ) {
      item {
        CloudHeader(
          title = stringResource(R.string.cloud_my_vehicle),
          actionIcon = if (cloudState.signedIn) Lucide.refresh else null,
          actionLabel = if (cloudState.signedIn) stringResource(R.string.cloud_refresh_vehicle) else null,
          onAction = if (cloudState.signedIn && !cloudState.loading) { { refresh() } } else null,
          onBack = onBack,
        )
      }
      item { Spacer(Modifier.height(12.dp)) }
      if (!cloudState.signedIn) {
        item {
          LoginCard(
            phone = phone,
            onPhoneChange = { value -> phone = value.filter { it.isDigit() }.take(11) },
            smsCode = smsCode,
            onSmsCodeChange = { value -> smsCode = value.filter { it.isDigit() }.take(8) },
            loading = cloudState.loading,
            countdown = countdown,
            onRequestCode = { requestCode() },
            onLogin = { login() },
          )
        }
      } else {
        item {
          VehicleListCard(
            state = cloudState,
            onNavigate = onNavigate,
            onSelectVehicle = { vehicle ->
              scope.launch {
                cloudService.selectVehicle(vehicle)
                AppSnack.success(snackbarHostState, strVehicleSwitched)
              }
            },
          )
        }
      }
      val error = cloudState.error
      if (error != null) {
        item { Spacer(Modifier.height(14.dp)) }
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(AppRadii.tile))
              .background(CyberHomeColors.alertSurface)
              .padding(16.dp),
          ) {
            Text(
              text = error,
              style = androidx.compose.ui.text.TextStyle(color = CyberHomeColors.danger, fontSize = 13.sp),
            )
          }
        }
      }
      item { Spacer(Modifier.height(14.dp)) }
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadii.tile))
            .background(CyberHomeColors.primarySoft)
            .padding(16.dp),
        ) {
          Text(
            text = stringResource(R.string.cloud_login_desc),
            style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 13.sp * 1.45f, color = CyberHomeColors.inkMuted),
          )
        }
      }
    }
  }
}

@Composable
private fun CloudHeader(
  title: String,
  actionIcon: androidx.compose.ui.graphics.vector.ImageVector?,
  actionLabel: String?,
  onAction: (() -> Unit)?,
  onBack: () -> Unit,
) {
  Row(
    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AppPressable(
      onClick = onBack,
      shape = CircleShape,
      background = CyberHomeColors.card,
      shadowElevation = 4.dp,
      shadowColor = CyberHomeColors.actionShadow,
      semanticsLabel = stringResource(R.string.common_back),
    ) {
      Box(modifier = Modifier.size(AppTouchTargets.min), contentAlignment = Alignment.Center) {
        LucideIcon(icon = Lucide.arrowLeft, size = 20.dp, color = CyberHomeColors.inkSecondary)
      }
    }
    Spacer(Modifier.width(12.dp))
    Text(
      text = title,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      modifier = Modifier.weight(1f),
    )
    if (actionIcon != null && actionLabel != null && onAction != null) {
      AppPressable(
        onClick = onAction,
        shape = CircleShape,
        semanticsLabel = actionLabel,
      ) {
        Box(modifier = Modifier.size(AppTouchTargets.min), contentAlignment = Alignment.Center) {
          LucideIcon(icon = actionIcon, size = 20.dp, color = CyberHomeColors.inkSecondary)
        }
      }
    }
  }
}

@Composable
private fun LoginCard(
  phone: String,
  onPhoneChange: (String) -> Unit,
  smsCode: String,
  onSmsCodeChange: (String) -> Unit,
  loading: Boolean,
  countdown: Int,
  onRequestCode: () -> Unit,
  onLogin: () -> Unit,
) {
  val normalizedPhone = OfficialCloudLoginValidator.compactPhone(phone)
  val validPhone = OfficialCloudLoginValidator.isValidPhone(normalizedPhone)
  val validSms = OfficialCloudLoginValidator.isValidSmsCode(smsCode.trim())
  val showPhoneError = phone.isNotEmpty() && !validPhone
  val showSmsError = smsCode.isNotEmpty() && !validSms
  val canRequestCode = !loading && validPhone
  val canLogin = !loading && validPhone && validSms

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(16.dp),
  ) {
    Text(
      text = stringResource(R.string.cloud_vehicle_hint),
      style = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
    )
    Spacer(Modifier.height(14.dp))
    OutlinedTextField(
      value = phone,
      onValueChange = onPhoneChange,
      singleLine = true,
      isError = showPhoneError,
      supportingText = if (showPhoneError) { { Text(stringResource(R.string.cloud_phone_hint)) } } else null,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
      placeholder = { Text(stringResource(R.string.cloud_phone)) },
      colors = cyberTextFieldColors(),
      shape = cyberTextFieldShape,
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.Top) {
      OutlinedTextField(
        value = smsCode,
        onValueChange = onSmsCodeChange,
        singleLine = true,
        isError = showSmsError,
        supportingText = if (showSmsError) { { Text(stringResource(R.string.cloud_sms_hint)) } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        placeholder = { Text(stringResource(R.string.cloud_sms)) },
        colors = cyberTextFieldColors(),
        shape = cyberTextFieldShape,
        modifier = Modifier.weight(1f),
      )
      Spacer(Modifier.width(10.dp))
      OutlinedButton(
        onClick = onRequestCode,
        enabled = canRequestCode && countdown == 0,
        shape = cyberButtonShape,
        colors = cyberOutlinedButtonColors(),
        border = cyberOutlinedButtonBorder,
        modifier = Modifier.height(48.dp),
      ) {
        Text(text = if (countdown > 0) "${countdown}s" else stringResource(R.string.cloud_sms_get))
      }
    }
    Spacer(Modifier.height(16.dp))
    Button(
      onClick = onLogin,
      enabled = canLogin,
      shape = cyberButtonShape,
      colors = cyberFilledButtonColors(),
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp),
    ) {
      if (loading) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = CyberHomeColors.white)
      } else {
        Text(text = stringResource(R.string.cloud_login_action), style = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = FontWeight.W700))
      }
    }
  }
}

@Composable
private fun VehicleListCard(
  state: OfficialCloudState,
  onNavigate: (String) -> Unit,
  onSelectVehicle: (OfficialVehicle) -> Unit,
) {
  if (state.loading && state.vehicles.isEmpty()) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(AppRadii.tile))
        .background(CyberHomeColors.card)
        .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
        .padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      CircularProgressIndicator(color = CyberHomeColors.primary)
    }
    return
  }
  if (state.vehicles.isEmpty()) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(AppRadii.tile))
        .background(CyberHomeColors.card)
        .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
        .padding(16.dp),
    ) {
      Text(
        text = stringResource(R.string.cloud_no_vehicle),
        style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 13.sp * 1.45f, color = CyberHomeColors.inkMuted),
      )
    }
    return
  }
  Column {
    state.vehicles.forEach { vehicle ->
      val selected = state.selectedVehicle?.key == vehicle.key
      Spacer(Modifier.height(12.dp))
      OfficialVehicleCard(
        vehicle = vehicle,
        selected = selected,
        onSelect = { onSelectVehicle(vehicle) },
        onDetail = { onNavigate(Routes.OFFICIAL_REPLICA) },
      )
    }
  }
}

@Composable
private fun OfficialVehicleCard(
  vehicle: OfficialVehicle,
  selected: Boolean,
  onSelect: () -> Unit,
  onDetail: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(if (selected) CyberHomeColors.primarySoft else CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .clickable { onSelect() }
      .padding(16.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = vehicle.displayName,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
        modifier = Modifier.weight(1f),
      )
      StatusChip(label = vehicle.onlineLabel, color = if (vehicle.online) CyberHomeColors.success else CyberHomeColors.inkFaint)
      if (selected) {
        Spacer(Modifier.width(6.dp))
        LucideIcon(icon = Lucide.checkCircle, color = CyberHomeColors.primary, size = AppIconSizes.sm)
      }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      StatusChip(label = vehicle.defenceLabel, color = CyberHomeColors.primary)
      StatusChip(label = vehicle.powerLabel, color = CyberHomeColors.warning)
      StatusChip(
        label = if (vehicle.electricQuantity == null) stringResource(R.string.cloud_electricity_placeholder) else stringResource(R.string.cloud_electricity_format, vehicle.electricQuantity),
        color = CyberHomeColors.success,
      )
      StatusChip(
        label = if (vehicle.voltage == null) stringResource(R.string.cloud_voltage_placeholder) else "${vehicle.voltage}V",
        color = CyberHomeColors.primary,
      )
    }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
      onClick = onDetail,
      shape = cyberButtonShape,
      colors = cyberOutlinedButtonColors(),
      border = cyberOutlinedButtonBorder,
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp),
    ) {
      LucideIcon(icon = Lucide.info, size = AppIconSizes.sm)
      Spacer(Modifier.width(6.dp))
      Text(text = stringResource(R.string.cloud_vehicle_detail))
    }
  }
}

@Composable
private fun StatusChip(label: String, color: androidx.compose.ui.graphics.Color) {
  Row(
    modifier = Modifier
      .clip(RoundedCornerShape(AppRadii.pill))
      .background(color.copy(alpha = 0.1f))
      .padding(horizontal = 9.dp, vertical = 4.dp),
  ) {
    Text(
      text = label,
      style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontWeight = FontWeight.W600, color = color),
    )
  }
}

@Composable
private fun DetailLine(
  label: String,
  value: String,
  trailing: @Composable (() -> Unit)? = null,
  onTap: (() -> Unit)? = null,
) {
  val text = value.trim()
  val display = if (text.isEmpty()) stringResource(R.string.cloud_not_returned) else text
  val row = Row(
    modifier = Modifier
      .padding(vertical = 7.dp)
      .then(if (onTap != null) Modifier.clickable { onTap() } else Modifier),
    verticalAlignment = Alignment.Top,
  ) {
    Text(
      text = label,
      style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
      modifier = Modifier.width(92.dp),
    )
    Text(
      text = display,
      textAlign = TextAlign.End,
      style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.ink),
      modifier = Modifier.weight(1f),
    )
    if (trailing != null) {
      Spacer(Modifier.width(8.dp))
      trailing()
    }
  }
  row
}
