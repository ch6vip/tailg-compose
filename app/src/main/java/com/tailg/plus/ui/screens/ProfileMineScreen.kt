package com.tailg.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.R
import com.tailg.plus.data.cloud.OfficialCloudRedactor
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.model.BatterySnapshot
import com.tailg.plus.data.model.OfficialCloudMessageCategory
import com.tailg.plus.data.store.MessageReadStore
import com.tailg.plus.ui.components.AppPressable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import com.tailg.plus.ui.components.AppSnackbarHost
import com.tailg.plus.ui.components.AppSnack
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.components.VehicleSwitchSheet
import com.tailg.plus.ui.components.cyberButtonShape
import com.tailg.plus.ui.components.cyberFilledButtonColors
import com.tailg.plus.ui.components.cyberTextFieldColors
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppTouchTargets
import com.tailg.plus.ui.theme.CyberHomeColors
import com.tailg.plus.ui.navigation.Routes
import com.tailg.plus.util.SensitiveValueMasker
import kotlinx.coroutines.launch

/**
 * Port of `lib/pages/profile_mine_page.dart` — the "我的" tab.
 *
 * Flat profile header (avatar + nickname + phone), default vehicle card,
 * account-and-support list, account row (phone + logout), version footer.
 *
 * Navigation: [onNavigate] routes to settings / messages / garage / login;
 * [onBack] pops the tab (rarely used since this is a tab root).
 */
@Composable
fun ProfileMineScreen(
  onNavigate: (String) -> Unit,
  onBack: () -> Unit,
  cloudService: OfficialCloudService? = null,
  modifier: Modifier = Modifier,
  onSignedOut: () -> Unit = { onNavigate(com.tailg.plus.ui.navigation.Routes.LOGIN) },
) {
  val cloudService = cloudService ?: rememberOfficialCloudService()
  val context = androidx.compose.ui.platform.LocalContext.current
  val messageReadStore = remember { MessageReadStore(context) }
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  val cloudState by cloudService.stateFlow.collectAsState()
  val unreadCount by messageReadStore.unreadCount.collectAsState()

  var showLogoutSheet by remember { mutableStateOf(false) }
  var showEditNickname by remember { mutableStateOf(false) }
  var showVehicleSwitch by remember { mutableStateOf(false) }

  val signedIn = cloudState.signedIn

  // String resources resolved in composition (usable from coroutine lambdas).
  val strNicknameEmpty = stringResource(R.string.profile_nickname_empty)
  val strNicknameUpdated = stringResource(R.string.profile_nickname_updated)
  val strLoginNow = stringResource(R.string.profile_login_now)
  val strDefaultName = stringResource(R.string.profile_default_name)

  // Sync message badge + silent profile refresh.
  LaunchedEffect(cloudState) {
    if (!signedIn) {
      messageReadStore.setUnreadCount(0)
    } else {
      messageReadStore.syncFromCloudMessages(
        vehicleMessages = cloudState.vehicleMessages,
        systemMessages = cloudState.systemMessages,
      )
      cloudService.refreshUserProfile(silent = true)
    }
  }

  val nickname = remember(cloudState) {
    if (!signedIn) strLoginNow
    else cloudState.userProfile?.displayName?.trim()?.ifEmpty { null } ?: strDefaultName
  }
  val avatarGlyph = remember(nickname) {
    if (nickname.isEmpty() || nickname == strLoginNow) "登" else nickname.first().toString()
  }
  val avatarUrl = if (signedIn) cloudState.userProfile?.avatarUrl else null
  val rawPhone = cloudState.phone.trim().ifEmpty { null }
  val maskedPhone = if (rawPhone == null) {
    if (signedIn) stringResource(R.string.profile_logged_in) else stringResource(R.string.profile_login_sync)
  } else {
    SensitiveValueMasker.phone(rawPhone, minMaskLength = 11)
  }
  val vehicle = if (signedIn) cloudState.selectedVehicle else null
  val battery = remember(vehicle, cloudState.batteryInfo) {
    BatterySnapshot.fromSources(
      officialVehicle = vehicle,
      officialBatteryInfo = cloudState.batteryInfo,
    )
  }
  val vehicleName = vehicle?.displayName ?: stringResource(R.string.profile_no_vehicle)
  val vehicleOnlineLabel = if (vehicle == null) {
    if (signedIn) stringResource(R.string.profile_unbound) else stringResource(R.string.profile_not_logged_in)
  } else {
    if (vehicle.online) stringResource(R.string.common_online) else stringResource(R.string.common_offline)
  }
  val vehicleOnline = vehicle?.online ?: false
  val batteryLabel = run {
    val p = battery.percent ?: vehicle?.electricQuantity
    if (p == null) "--" else "$p%"
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = CyberHomeColors.pageBg,
    contentWindowInsets = WindowInsets.statusBars,
    snackbarHost = { AppSnackbarHost(snackbarHostState) },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(top = 6.dp),
    ) {
      Text(
        text = stringResource(R.string.nav_mine),
        modifier = Modifier.padding(start = 20.dp, top = 12.dp),
        style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      )
      ProfileHeader(
        avatarGlyph = avatarGlyph,
        avatarUrl = avatarUrl,
        nickname = nickname,
        phoneLine = maskedPhone,
        memberLabel = if (signedIn) stringResource(R.string.profile_logged_in) else stringResource(R.string.profile_guest),
        onAvatarTap = {
          if (!signedIn) onNavigate(Routes.LOGIN) else showEditNickname = true
        },
        onEditTap = {
          if (!signedIn) onNavigate(Routes.LOGIN) else showEditNickname = true
        },
      )
      VehicleCard(
        name = vehicleName,
        online = vehicleOnline,
        statusLabel = vehicleOnlineLabel,
        batteryLabel = batteryLabel,
        onTap = {
          if (!signedIn) {
            onNavigate(Routes.LOGIN)
          } else if (cloudState.vehicles.size > 1) {
            showVehicleSwitch = true
          } else {
            onNavigate(Routes.GARAGE)
          }
        },
      )
      MineSectionLabel(stringResource(R.string.profile_section_account))
      SupportCard(
        messageBadge = if (signedIn && unreadCount > 0) unreadCount else null,
        onSettings = { onNavigate(Routes.SETTINGS) },
        onMessages = {
          val sel = cloudState.selectedVehicle
          if (!signedIn) onNavigate(Routes.LOGIN)
          else onNavigate(Routes.vehicleMessage(sel?.key?.takeIf { it.isNotBlank() } ?: "current"))
        },
        onAbout = { onNavigate(Routes.ABOUT_APP) },
      )
      AccountCard(
        phoneValue = if (signedIn) maskedPhone else stringResource(R.string.profile_unbound),
        showLogout = signedIn,
        onLogoutTap = { showLogoutSheet = true },
      )
      Text(
        text = "Tailg Cloud · VOID",
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 6.dp),
        textAlign = TextAlign.Center,
        style = TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkFaint),
      )
    }
  }

  // Logout sheet.
  if (showLogoutSheet) {
    LogoutSheet(
      onConfirm = {
        showLogoutSheet = false
        scope.launch {
          try {
            cloudService.logout()
            onSignedOut()
          } catch (e: Exception) {
            AppSnack.error(snackbarHostState, OfficialCloudRedactor.errorMessage(e))
          }
        }
      },
      onDismiss = { showLogoutSheet = false },
    )
  }

  // Edit nickname dialog.
  if (showEditNickname) {
    EditNicknameDialog(
      initialName = cloudState.userProfile?.displayName ?: "",
      onDismiss = { showEditNickname = false },
      onConfirm = { next ->
        showEditNickname = false
        val trimmed = next.trim()
        if (trimmed.isEmpty()) {
          scope.launch { AppSnack.info(snackbarHostState, strNicknameEmpty) }
          return@EditNicknameDialog
        }
        val current = cloudState.userProfile?.displayName ?: ""
        if (trimmed == current) return@EditNicknameDialog
        scope.launch {
          try {
            cloudService.updateUserNickname(trimmed)
            AppSnack.success(snackbarHostState, strNicknameUpdated)
          } catch (e: Exception) {
            AppSnack.error(snackbarHostState, OfficialCloudRedactor.errorMessage(e))
          }
        }
      },
    )
  }

  // Vehicle switch sheet.
  if (showVehicleSwitch) {
    val vehicles = cloudState.vehicles
    val selectedKey = cloudState.selectedVehicle?.key
    VehicleSwitchSheet(
      vehicles = vehicles,
      selectedKey = selectedKey,
      onSelect = { target ->
        return@VehicleSwitchSheet try {
          cloudService.changeUsingVehicle(target)
          true
        } catch (e: Exception) {
          scope.launch { AppSnack.error(snackbarHostState, OfficialCloudRedactor.errorMessage(e)) }
          false
        }
      },
      onDismiss = { showVehicleSwitch = false },
    )
  }
}

// ── Section label ──────────────────────────────────────────────────────────

@Composable
private fun MineSectionLabel(text: String) {
  Text(
    text = text,
    modifier = Modifier.padding(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 8.dp),
    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.inkMuted),
  )
}

// ── Profile header ─────────────────────────────────────────────────────────

@Composable
private fun ProfileHeader(
  avatarGlyph: String,
  avatarUrl: String?,
  nickname: String,
  phoneLine: String,
  memberLabel: String,
  onAvatarTap: () -> Unit,
  onEditTap: () -> Unit,
) {
  Row(
    modifier = Modifier.padding(start = 20.dp, top = 10.dp, end = 16.dp, bottom = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AppPressable(
      onClick = onAvatarTap,
      shape = CircleShape,
      semanticsLabel = stringResource(R.string.common_edit),
    ) {
      Box(
        modifier = Modifier
          .size(64.dp)
          .clip(CircleShape)
          .background(CyberHomeColors.primarySoft),
        contentAlignment = Alignment.Center,
      ) {
        if (avatarUrl != null && avatarUrl.isNotEmpty()) {
          // TODO: load avatar image (needs Coil dependency); show glyph for now.
          Text(
            text = avatarGlyph,
            style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.primary),
          )
        } else {
          Text(
            text = avatarGlyph,
            style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.primary),
          )
        }
      }
    }
    Spacer(Modifier.width(14.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = nickname,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      )
      Spacer(Modifier.height(5.dp))
      Text(
        text = phoneLine,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontSize = 13.sp, color = CyberHomeColors.inkMuted),
      )
      Spacer(Modifier.height(10.dp))
      Box(
        modifier = Modifier
          .height(22.dp)
          .clip(RoundedCornerShape(AppRadii.pill))
          .background(CyberHomeColors.primarySoft)
          .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = memberLabel,
          style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.primary),
        )
      }
    }
    AppPressable(
      onClick = onEditTap,
      semanticsLabel = stringResource(R.string.common_edit),
    ) {
      Box(
        modifier = Modifier.height(AppTouchTargets.min).padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = stringResource(R.string.common_edit),
          style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.inkSecondary),
        )
      }
    }
  }
}

// ── Vehicle card ──────────────────────────────────────────────────────────

@Composable
private fun VehicleCard(
  name: String,
  online: Boolean,
  statusLabel: String,
  batteryLabel: String,
  onTap: () -> Unit,
) {
  AppPressable(
    onClick = onTap,
    shape = RoundedCornerShape(AppRadii.tile),
    semanticsLabel = stringResource(R.string.profile_switch_vehicle) + " $name",
  ) {
    Box(
      modifier = Modifier
        .padding(start = 20.dp, top = 14.dp, end = 20.dp)
        .fillMaxWidth()
        .clip(RoundedCornerShape(AppRadii.tile))
        .background(CyberHomeColors.card)
        .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
        .padding(16.dp),
    ) {
      Row(verticalAlignment = Alignment.Top) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(48.dp)
              .clip(RoundedCornerShape(AppRadii.tile))
              .background(CyberHomeColors.primarySoft),
            contentAlignment = Alignment.Center,
          ) {
            LucideIcon(icon = Lucide.vehicle, size = 22.dp, color = CyberHomeColors.primary)
          }
          Spacer(Modifier.width(12.dp))
          Column {
            Text(
              text = name,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(6.dp)
                  .clip(CircleShape)
                  .background(if (online) CyberHomeColors.success else CyberHomeColors.inkFaint),
              )
              Spacer(Modifier.width(6.dp))
              Text(
                text = statusLabel,
                style = TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkMuted),
              )
              Box(
                modifier = Modifier
                  .width(1.dp)
                  .height(10.dp)
                  .padding(horizontal = 6.dp)
                  .background(CyberHomeColors.line),
              )
              Text(
                text = batteryLabel,
                style = TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkMuted),
              )
            }
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = stringResource(R.string.common_switch),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.inkSecondary),
          )
          LucideIcon(icon = Lucide.chevronRight, size = 16.dp, color = CyberHomeColors.inkFaint)
        }
      }
    }
  }
}

// ── Support card ──────────────────────────────────────────────────────────

@Composable
private fun SupportCard(
  messageBadge: Int?,
  onSettings: () -> Unit,
  onMessages: () -> Unit,
  onAbout: () -> Unit,
) {
  val rows = listOf(
    SupportRowData(Lucide.tune, stringResource(R.string.profile_settings), onSettings, null),
    SupportRowData(Lucide.message, stringResource(R.string.profile_message_center), onMessages, messageBadge),
    SupportRowData(Lucide.info, stringResource(R.string.profile_about_us), onAbout, null),
  )
  Column(
    modifier = Modifier
      .padding(start = 20.dp, top = 12.dp, end = 20.dp)
      .fillMaxWidth()
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile)),
  ) {
    rows.forEachIndexed { index, data ->
      if (index > 0) {
        HorizontalDivider(thickness = 1.dp, color = CyberHomeColors.line)
      }
      SupportRow(data = data)
    }
  }
}

private data class SupportRowData(
  val icon: androidx.compose.ui.graphics.vector.ImageVector,
  val title: String,
  val onTap: () -> Unit,
  val badge: Int?,
)

@Composable
private fun SupportRow(data: SupportRowData) {
  AppPressable(
    onClick = data.onTap,
    pressedBackground = CyberHomeColors.cardMuted,
    semanticsLabel = data.title,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(52.dp)
        .padding(horizontal = 16.dp, vertical = 15.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(CyberHomeColors.primarySoft),
        contentAlignment = Alignment.Center,
      ) {
        LucideIcon(icon = data.icon, size = 18.dp, color = CyberHomeColors.primary)
      }
      Spacer(Modifier.width(12.dp))
      Text(
        text = data.title,
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W500, color = CyberHomeColors.ink),
      )
      val badge = data.badge
      if (badge != null && badge > 0) {
        Box(
          modifier = Modifier
            .heightIn(18.dp)
            .clip(RoundedCornerShape(AppRadii.pill))
          .background(CyberHomeColors.danger)
            .padding(horizontal = 5.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = if (badge > 99) "99+" else "$badge",
            style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.white),
          )
        }
        Spacer(Modifier.width(4.dp))
      }
      LucideIcon(icon = Lucide.chevronRight, size = 16.dp, color = CyberHomeColors.inkFaint)
    }
  }
}

// ── Account card ──────────────────────────────────────────────────────────

@Composable
private fun AccountCard(
  phoneValue: String,
  showLogout: Boolean,
  onLogoutTap: () -> Unit,
) {
  Column(
    modifier = Modifier
      .padding(start = 20.dp, top = 12.dp, end = 20.dp)
      .fillMaxWidth()
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile)),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(52.dp)
        .padding(horizontal = 16.dp, vertical = 15.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.profile_phone),
        modifier = Modifier.weight(1f),
        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W500, color = CyberHomeColors.ink),
      )
      Text(
        text = phoneValue,
        style = TextStyle(fontSize = 14.sp, color = CyberHomeColors.inkMuted),
      )
    }
    if (showLogout) {
      HorizontalDivider(thickness = 1.dp, color = CyberHomeColors.line)
      AppPressable(
        onClick = onLogoutTap,
        pressedBackground = CyberHomeColors.cardMuted,
        semanticsLabel = stringResource(R.string.common_logout),
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(52.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = stringResource(R.string.common_logout),
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.danger),
          )
        }
      }
    }
  }
}

// ── Logout sheet ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogoutSheet(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState()
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = CyberHomeColors.card,
    dragHandle = null,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
        modifier = Modifier
          .size(width = 36.dp, height = 4.dp)
          .clip(RoundedCornerShape(AppRadii.pill))
          .background(CyberHomeColors.lineStrong),
      )
      Spacer(Modifier.height(14.dp))
      Text(
        text = stringResource(R.string.profile_logout_title),
        style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      )
      Spacer(Modifier.height(6.dp))
      Text(
        text = stringResource(R.string.profile_logout_message),
        textAlign = TextAlign.Center,
        style = TextStyle(fontSize = 13.sp, color = CyberHomeColors.inkMuted, lineHeight = 13.sp * 1.5f),
        modifier = Modifier.padding(horizontal = 12.dp),
      )
      Spacer(Modifier.height(16.dp))
      AppPressable(
        onClick = onConfirm,
        shape = RoundedCornerShape(AppRadii.tile),
        background = CyberHomeColors.danger,
        semanticsLabel = stringResource(R.string.common_confirm),
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = stringResource(R.string.profile_logout),
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.white),
          )
        }
      }
      Spacer(Modifier.height(8.dp))
      AppPressable(
        onClick = onDismiss,
        shape = RoundedCornerShape(AppRadii.tile),
        background = CyberHomeColors.cardMuted,
        semanticsLabel = stringResource(R.string.common_cancel),
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = stringResource(R.string.common_cancel),
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.inkSecondary),
          )
        }
      }
    }
  }
}

// ── Edit nickname dialog ──────────────────────────────────────────────────

@Composable
private fun EditNicknameDialog(
  initialName: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var name by remember { mutableStateOf(initialName) }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = CyberHomeColors.card,
    title = { Text(stringResource(R.string.profile_edit_nickname), style = TextStyle(color = CyberHomeColors.ink, fontWeight = FontWeight.W700)) },
    text = {
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        singleLine = true,
        textStyle = TextStyle(color = CyberHomeColors.ink),
        placeholder = { Text(stringResource(R.string.profile_nickname_hint)) },
        colors = cyberTextFieldColors(),
        shape = RoundedCornerShape(AppRadii.tile),
      )
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(name) },
        colors = cyberFilledButtonColors(),
        shape = cyberButtonShape,
      ) { Text(stringResource(R.string.common_save)) }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
    },
  )
}
