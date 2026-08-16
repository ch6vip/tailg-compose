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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    if (!signedIn) "立即登录"
    else cloudState.userProfile?.displayName?.trim()?.ifEmpty { null } ?: "台铃用户"
  }
  val avatarGlyph = remember(nickname) {
    if (nickname.isEmpty() || nickname == "立即登录") "登" else nickname.first().toString()
  }
  val avatarUrl = if (signedIn) cloudState.userProfile?.avatarUrl else null
  val rawPhone = cloudState.phone.trim().ifEmpty { null }
  val maskedPhone = if (rawPhone == null) {
    if (signedIn) "已登录" else "登录后同步车辆和消息"
  } else {
    SensitiveValueMasker.phone(rawPhone, minMaskLength = 11)
  }
  val vehicle = if (signedIn) cloudState.selectedVehicle else null
  val battery = BatterySnapshot.fromSources(
    officialVehicle = vehicle,
    officialBatteryInfo = cloudState.batteryInfo,
  )
  val vehicleName = vehicle?.displayName ?: "暂无车辆"
  val vehicleOnlineLabel = if (vehicle == null) {
    if (signedIn) "未绑定" else "未登录"
  } else {
    if (vehicle.online) "在线" else "离线"
  }
  val vehicleOnline = vehicle?.online ?: false
  val batteryLabel = run {
    val p = battery.percent ?: vehicle?.electricQuantity
    if (p == null) "--" else "$p%"
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = CyberHomeColors.pageBg,
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
        text = "我的",
        modifier = Modifier.padding(start = 20.dp, top = 12.dp),
        style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      )
      ProfileHeader(
        avatarGlyph = avatarGlyph,
        avatarUrl = avatarUrl,
        nickname = nickname,
        phoneLine = maskedPhone,
        memberLabel = if (signedIn) "已登录" else "游客",
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
      MineSectionLabel("账户与支持")
      SupportCard(
        messageBadge = if (signedIn && unreadCount > 0) unreadCount else null,
        onSettings = { onNavigate(Routes.SETTINGS) },
        onMessages = {
          val sel = cloudState.selectedVehicle
          if (!signedIn) onNavigate(Routes.LOGIN)
          else onNavigate(Routes.VEHICLE_MESSAGE.replace("{${Routes.ARG_VEHICLE_ID}}", sel?.key ?: ""))
        },
        onAbout = { onNavigate(Routes.APP_PREFERENCES) },
      )
      AccountCard(
        phoneValue = if (signedIn) maskedPhone else "未绑定",
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
            AppSnack.success(snackbarHostState, "已退出")
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
          scope.launch { AppSnack.info(snackbarHostState, "昵称不能为空") }
          return@EditNicknameDialog
        }
        val current = cloudState.userProfile?.displayName ?: ""
        if (trimmed == current) return@EditNicknameDialog
        scope.launch {
          try {
            cloudService.updateUserNickname(trimmed)
            AppSnack.success(snackbarHostState, "昵称已更新")
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
      semanticsLabel = "编辑资料",
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
      semanticsLabel = "编辑",
    ) {
      Box(
        modifier = Modifier.height(AppTouchTargets.min).padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = "编辑",
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
    semanticsLabel = "切换默认车辆 $name",
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
            text = "切换",
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
    SupportRowData(Lucide.tune, "设置", onSettings, null),
    SupportRowData(Lucide.message, "消息中心", onMessages, messageBadge),
    SupportRowData(Lucide.info, "关于我们", onAbout, null),
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
        text = "手机号",
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
        semanticsLabel = "退出登录",
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(52.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "退出登录",
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
        text = "退出登录？",
        style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      )
      Spacer(Modifier.height(6.dp))
      Text(
        text = "下次登录需验证手机号。本机车辆缓存会保留。",
        textAlign = TextAlign.Center,
        style = TextStyle(fontSize = 13.sp, color = CyberHomeColors.inkMuted, lineHeight = 13.sp * 1.5f),
        modifier = Modifier.padding(horizontal = 12.dp),
      )
      Spacer(Modifier.height(16.dp))
      AppPressable(
        onClick = onConfirm,
        shape = RoundedCornerShape(AppRadii.tile),
        background = CyberHomeColors.danger,
        semanticsLabel = "确认退出",
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "退出",
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.white),
          )
        }
      }
      Spacer(Modifier.height(8.dp))
      AppPressable(
        onClick = onDismiss,
        shape = RoundedCornerShape(AppRadii.tile),
        background = CyberHomeColors.cardMuted,
        semanticsLabel = "取消",
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "取消",
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
    title = { Text("修改昵称", style = TextStyle(color = CyberHomeColors.ink, fontWeight = FontWeight.W700)) },
    text = {
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        singleLine = true,
        textStyle = TextStyle(color = CyberHomeColors.ink),
        placeholder = { Text("输入昵称") },
        colors = cyberTextFieldColors(),
        shape = RoundedCornerShape(AppRadii.tile),
      )
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(name) },
        colors = cyberFilledButtonColors(),
        shape = cyberButtonShape,
      ) { Text("保存") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("取消") }
    },
  )
}
