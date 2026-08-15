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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.ui.components.AppSnackbarHost
import com.tailg.plus.ui.components.AppSnack
import com.tailg.plus.ui.components.CyberPageHeader
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.CyberHomeColors
import kotlinx.coroutines.launch

/**
 * Port of `lib/pages/notification_prefs_page.dart` — official-cloud message
 * push preferences.
 *
 * The Dart page is a `StatefulWidget` that loads a `Map<String, bool>` from
 * `OfficialCloudService.getMessageControl()`, renders one `SwitchListTile`
 * per entry, and persists changes via `setMessagePushConfig()`. Here the same
 * flow is reproduced with a `remember`-held mutable map and a coroutine scope.
 *
 * Service access: [OfficialCloudService] requires storage / api client / vehicle
 * store collaborators that are not yet wired through Hilt at this screen's call
 * site (the NavHost invokes it with only `onBack`). When [cloudService] is null
 * the screen renders a placeholder with a TODO; once Hilt provides the shared
 * instance, pass it here (mirrors `VehicleSettingsScreen`).
 *
 * Token mapping (Dart → Compose): `CyberHomeColors.card/line/primary/...` are
 * used 1:1; `AppRadii.tile` for card radius; `Lucide.*` for row icons.
 */
@Composable
fun NotificationPrefsScreen(
  onBack: () -> Unit,
  cloudService: OfficialCloudService? = null,
) {
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  var config by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
  var loading by remember { mutableStateOf(true) }
  var saving by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  // Dart `initState` → `unawaited(_load())`.
  LaunchedEffect(cloudService) {
    if (cloudService == null) {
      loading = false
      return@LaunchedEffect
    }
    loading = true
    error = null
    try {
      config = cloudService.getMessageControl()
    } catch (e: Exception) {
      error = "加载失败"
    } finally {
      loading = false
    }
  }

  Scaffold(
    containerColor = CyberHomeColors.pageBg,
    snackbarHost = { AppSnackbarHost(snackbarHostState) },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
    ) {
      CyberPageHeader(title = "通知偏好", onBack = onBack)
      Box(modifier = Modifier.fillMaxSize()) {
        when {
          cloudService == null -> NotificationPlaceholder(
            icon = Lucide.cloudOff,
            title = "通知偏好暂未开放",
            subtitle = "官方云端服务尚未接入，待 Hilt 注入后可加载消息开关。",
          )
          loading -> LoadingState()
          error != null -> NotificationState(
            icon = Lucide.wifiOff,
            title = "通知偏好加载失败",
            subtitle = error!!,
            actionLabel = "重试",
            onAction = {
              scope.launch {
                loading = true
                error = null
                try {
                  config = cloudService.getMessageControl()
                } catch (e: Exception) {
                  error = "加载失败"
                } finally {
                  loading = false
                }
              }
            },
          )
          config.isEmpty() -> NotificationState(
            icon = Lucide.message,
            title = "暂无可配置项",
            subtitle = "当前账号没有可同步的消息开关",
          )
          else -> NotificationList(
            entries = config.entries.toList(),
            saving = saving,
            onToggle = { key, value ->
              config = config.toMutableMap().apply { put(key, value) }
            },
            onSave = {
              scope.launch {
                saving = true
                try {
                  cloudService.setMessagePushConfig(config)
                  AppSnack.success(snackbarHostState, "通知偏好已保存")
                } catch (e: Exception) {
                  AppSnack.error(snackbarHostState, "保存失败，请重试")
                } finally {
                  saving = false
                }
              }
            },
          )
        }
      }
    }
  }
}

/** Dart `_buildBody` list + save bar. */
@Composable
private fun NotificationList(
  entries: List<Map.Entry<String, Boolean>>,
  saving: Boolean,
  onToggle: (String, Boolean) -> Unit,
  onSave: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(
        start = 20.dp,
        end = 20.dp,
        top = 12.dp,
        bottom = 24.dp,
      ),
    ) {
      item {
        Text(
          text = "消息推送",
          modifier = Modifier.padding(start = 2.dp, bottom = 9.dp),
          style = TextStyle(
            fontSize = 12.sp,
            fontWeight = FontWeight.W700,
            color = CyberHomeColors.inkMuted,
          ),
        )
      }
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadii.tile))
            .background(CyberHomeColors.card)
            .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile)),
        ) {
          entries.forEachIndexed { index, entry ->
            PreferenceRow(
              configKey = entry.key,
              label = labelFor(entry.key),
              value = entry.value,
              showDivider = index < entries.size - 1,
              onChanged = { value -> onToggle(entry.key, value) },
            )
          }
        }
      }
    }
    SaveBar(saving = saving, onSave = onSave)
  }
}

/** Dart `_PreferenceRow` — icon tile + label + switch. */
@Composable
private fun PreferenceRow(
  configKey: String,
  label: String,
  value: Boolean,
  showDivider: Boolean,
  onChanged: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .clip(CircleShape)
        .background(if (value) CyberHomeColors.primarySoft else CyberHomeColors.control),
      contentAlignment = Alignment.Center,
    ) {
      LucideIcon(
        icon = iconFor(configKey),
        size = 20.dp,
        color = if (value) CyberHomeColors.primary else CyberHomeColors.inkMuted,
      )
    }
    Spacer(Modifier.width(12.dp))
    Text(
      text = label,
      modifier = Modifier.weight(1f),
      maxLines = 2,
      style = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.W600,
        color = CyberHomeColors.ink,
      ),
    )
    Switch(
      checked = value,
      onCheckedChange = onChanged,
      colors = SwitchDefaults.colors(
        checkedThumbColor = CyberHomeColors.white,
        checkedTrackColor = CyberHomeColors.primary,
        uncheckedThumbColor = CyberHomeColors.white,
        uncheckedTrackColor = CyberHomeColors.controlStrong,
      ),
    )
  }
  if (showDivider) {
    HorizontalDivider(
      thickness = 1.dp,
      color = CyberHomeColors.line,
      modifier = Modifier.padding(start = 66.dp),
    )
  }
}

/** Dart `_NotificationState` — centered icon + title + subtitle + optional action. */
@Composable
private fun NotificationState(
  icon: ImageVector,
  title: String,
  subtitle: String,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 36.dp, vertical = 28.dp),
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
      LucideIcon(icon = icon, size = 28.dp, color = CyberHomeColors.inkMuted)
    }
    Spacer(Modifier.height(16.dp))
    Text(
      text = title,
      textAlign = TextAlign.Center,
      style = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.W700,
        color = CyberHomeColors.ink,
      ),
    )
    Spacer(Modifier.height(7.dp))
    Text(
      text = subtitle,
      textAlign = TextAlign.Center,
      style = TextStyle(
        fontSize = 13.sp,
        lineHeight = 13.sp * 1.45f,
        color = CyberHomeColors.inkMuted,
      ),
    )
    if (actionLabel != null && onAction != null) {
      Spacer(Modifier.height(18.dp))
      androidx.compose.material3.Button(
        onClick = onAction,
        modifier = Modifier.width(148.dp).height(46.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
          containerColor = CyberHomeColors.primary,
          contentColor = CyberHomeColors.white,
        ),
        shape = RoundedCornerShape(AppRadii.tile),
      ) {
        Text(actionLabel)
      }
    }
  }
}

/** Placeholder shown when the cloud service is not yet wired (TODO: Hilt). */
@Composable
private fun NotificationPlaceholder(
  icon: ImageVector,
  title: String,
  subtitle: String,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 36.dp, vertical = 28.dp),
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
      LucideIcon(icon = icon, size = 28.dp, color = CyberHomeColors.inkMuted)
    }
    Spacer(Modifier.height(16.dp))
    Text(
      text = title,
      textAlign = TextAlign.Center,
      style = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.W700,
        color = CyberHomeColors.ink,
      ),
    )
    Spacer(Modifier.height(7.dp))
    Text(
      text = subtitle,
      textAlign = TextAlign.Center,
      style = TextStyle(
        fontSize = 13.sp,
        lineHeight = 13.sp * 1.45f,
        color = CyberHomeColors.inkMuted,
      ),
    )
  }
}

@Composable
private fun LoadingState() {
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
  ) {
    CircularProgressIndicator(
      color = CyberHomeColors.primary,
      strokeWidth = 2.dp,
      modifier = Modifier.size(36.dp),
    )
  }
}

/** Dart save bar — full-width primary button with check icon. */
@Composable
private fun SaveBar(saving: Boolean, onSave: () -> Unit) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(CyberHomeColors.pageBg)
      .padding(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 16.dp),
  ) {
    androidx.compose.material3.Button(
      onClick = onSave,
      enabled = !saving,
      modifier = Modifier
        .fillMaxWidth()
        .height(50.dp),
      colors = androidx.compose.material3.ButtonDefaults.buttonColors(
        containerColor = CyberHomeColors.primary,
        contentColor = CyberHomeColors.white,
        disabledContainerColor = CyberHomeColors.controlStrong,
        disabledContentColor = CyberHomeColors.inkFaint,
      ),
      shape = RoundedCornerShape(AppRadii.tile),
    ) {
      if (saving) {
        CircularProgressIndicator(
          color = CyberHomeColors.white,
          strokeWidth = 2.dp,
          modifier = Modifier.size(19.dp),
        )
      } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
          LucideIcon(icon = Lucide.check, size = 18.dp, color = CyberHomeColors.white)
          Spacer(Modifier.width(8.dp))
          Text(
            text = "保存设置",
            style = TextStyle(
              fontSize = 15.sp,
              fontWeight = FontWeight.W700,
              color = CyberHomeColors.white,
            ),
          )
        }
      }
    }
  }
}

/** Dart `_labelFor` — friendly Chinese label per config key. */
private fun labelFor(key: String): String = when (key) {
  "carMsg" -> "车辆消息通知"
  "sysMsg" -> "系统消息通知"
  "alarm" -> "报警通知"
  "fence" -> "围栏通知"
  "lowBattery" -> "低电量提醒"
  "maintenance" -> "保养提醒"
  else -> key
}

/** Dart `_iconFor` — Lucide icon per config key. */
private fun iconFor(key: String): ImageVector = when (key) {
  "carMsg" -> Lucide.message
  "sysMsg" -> Lucide.megaphone
  "alarm" -> Lucide.alert
  "fence" -> Lucide.mapPin
  "lowBattery" -> Lucide.batteryWarning
  "maintenance" -> Lucide.settings
  else -> Lucide.message
}
