package com.tailg.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tailg.plus.data.preferences.AppLanguagePreference
import com.tailg.plus.data.preferences.AppPreferencesService
import com.tailg.plus.data.preferences.DistanceUnitPreference
import com.tailg.plus.ui.components.CyberCard
import com.tailg.plus.ui.components.CyberPageHeader
import com.tailg.plus.ui.components.CyberSectionLabel
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.cyberCaptionStyle
import com.tailg.plus.ui.components.cyberItemTitleStyle
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.CyberHomeColors
import kotlinx.coroutines.launch

/**
 * Port of `lib/pages/settings_page.dart` → `SettingsScreen.kt`.
 *
 * The Dart page is a `StatefulWidget` whose only state is the
 * `AppPreferencesService` (already initialized in `main()`). Here the service
 * is constructed once per composition via `remember` and its `StateFlow`s are
 * observed with `collectAsStateWithLifecycle`, replacing the Dart
 * `StreamBuilder`s.
 *
 * Navigation: the Dart page pushes routes inline; Compose call sites pass an
 * [onNavigate] lambda that receives a [Routes] key. The advanced-diagnostics
 * sub-page is folded into a separate [AdvancedDiagnosticsScreen] composable
 * (same file) so the route graph can wire it directly.
 */
@Composable
fun SettingsScreen(
  onBack: () -> Unit,
  onNavigate: (String) -> Unit,
  preferencesService: AppPreferencesService? = null,
) {
  val prefs = preferencesService ?: remember { AppPreferencesService(androidx.compose.ui.platform.LocalContext.current) }
  val language by prefs.language.collectAsStateWithLifecycle(AppLanguagePreference.System)
  val distanceUnit by prefs.distanceUnit.collectAsStateWithLifecycle(DistanceUnitPreference.Metric)
  val respectTextScale by prefs.respectSystemTextScale.collectAsStateWithLifecycle(true)
  val scope = androidx.compose.runtime.rememberCoroutineScope()

  Scaffold(
    containerColor = CyberHomeColors.pageBg,
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(padding)
        .padding(bottom = 32.dp),
    ) {
      CyberPageHeader(title = "设置", onBack = onBack)
      CyberSectionLabel("账号与车辆")
      settingsGroup(
        settingItemModel(
          icon = Lucide.garage,
          title = "我的车辆",
          subtitle = "账号车辆、默认车辆与同步",
          onClick = { onNavigate(Routes.GARAGE) },
        ),
        settingItemModel(
          icon = Lucide.message,
          title = "消息中心",
          subtitle = "系统消息、设备消息和安全提醒",
          onClick = { onNavigate(Routes.VEHICLE_MESSAGE) },
        ),
      )
      CyberSectionLabel("用车设置")
      settingsGroup(
        settingItemModel(
          icon = Lucide.tune,
          title = "车辆设置",
          subtitle = "声音、灵敏度、车辆功能、骑行设置",
          onClick = { onNavigate(Routes.VEHICLE_SETTINGS) },
        ),
        settingItemModel(
          icon = Lucide.battery,
          title = "电池/BMS",
          subtitle = "电量、电压、温度、故障和预留 BMS 数据",
          onClick = { onNavigate(Routes.BATTERY_DETAILS) },
        ),
      )
      CyberSectionLabel("通用")
      settingsGroup(
        settingItemModel(
          icon = Lucide.languages,
          title = "语言设置",
          subtitle = language.label,
          onClick = { onNavigate(Routes.APP_PREFERENCES) },
        ),
        settingItemModel(
          icon = Lucide.ruler,
          title = "单位设置",
          subtitle = "${distanceUnit.label} · ${distanceUnit.hint}",
          onClick = { onNavigate(Routes.APP_PREFERENCES) },
        ),
        settingItemModel(
          icon = Lucide.type,
          title = "跟随系统字号",
          subtitle = if (respectTextScale) "允许系统字号设置生效（限 0.9-1.3 倍）" else "关闭后忽略系统字号",
          trailing = {
            Switch(
              checked = respectTextScale,
              onCheckedChange = { value ->
                // Fire-and-forget; the StateFlow will reflect the new value.
                scope.launch {
                  prefs.setRespectSystemTextScale(value)
                }
              },
              colors = SwitchDefaults.colors(
                checkedThumbColor = CyberHomeColors.white,
                checkedTrackColor = CyberHomeColors.primary,
                uncheckedThumbColor = CyberHomeColors.white,
                uncheckedTrackColor = CyberHomeColors.controlStrong,
              ),
            )
          },
        ),
      )
      CyberSectionLabel("高级")
      settingsGroup(
        settingItemModel(
          icon = Lucide.shieldCheck,
          title = "高级诊断",
          subtitle = "设备信息、日志、协议和升级前检测",
          onClick = { onNavigate(Routes.DIAGNOSTIC) },
        ),
        settingItemModel(
          icon = Lucide.key,
          title = "官方会话 / Token",
          subtitle = "调试用：粘贴或复制官方登录凭证",
          onClick = { onNavigate(Routes.CLOUD_TOKEN) },
        ),
      )
      CyberSectionLabel("关于")
      settingsGroup(
        settingItemModel(
          icon = Lucide.info,
          title = "关于台铃智能",
          subtitle = "版本信息、用户协议和隐私政策",
          onClick = { onNavigate(Routes.APP_PREFERENCES) },
        ),
      )
    }
  }
}

/**
 * Port of the Dart `_AdvancedDiagnosticsPage` (private widget in
 * `settings_page.dart`). Kept as a separate composable so the route graph can
 * register it under [Routes.DIAGNOSTIC] without a nested navigator.
 */
@Composable
fun AdvancedDiagnosticsScreen(
  onBack: () -> Unit,
  onNavigate: (String) -> Unit,
) {
  Scaffold(
    containerColor = CyberHomeColors.pageBg,
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(padding)
        .padding(bottom = 32.dp),
    ) {
      CyberPageHeader(title = "高级诊断", onBack = onBack)
      Spacer(Modifier.height(4.dp))
      settingsGroup(
        settingItemModel(
          icon = Lucide.stethoscope,
          title = "故障诊断",
          subtitle = "读取车辆错误码",
          onClick = { onNavigate(Routes.DIAGNOSTIC) },
        ),
        settingItemModel(
          icon = Lucide.fileText,
          title = "日志",
          subtitle = "查看操作记录",
          onClick = { onNavigate(Routes.LOG) },
        ),
      )
    }
  }
}

/** Dart `_group`: a [CyberCard] that stacks [items] with inset dividers between them. */
@Composable
private fun settingsGroup(vararg items: SettingItemModel) {
  CyberCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
    Column {
      items.forEachIndexed { index, item ->
        if (index > 0) {
          HorizontalDivider(
            thickness = 1.dp,
            color = CyberHomeColors.line,
            modifier = Modifier.padding(start = 66.dp),
          )
        }
        SettingItemRow(item)
      }
    }
  }
}

/** Plain data describing one settings row (keeps the call sites declarative). */
private data class SettingItemModel(
  val icon: androidx.compose.ui.graphics.vector.ImageVector,
  val title: String,
  val subtitle: String? = null,
  val trailing: @Composable (() -> Unit)? = null,
  val onClick: (() -> Unit)? = null,
  val showChevron: Boolean = true,
)

private fun settingItemModel(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String? = null,
  trailing: @Composable (() -> Unit)? = null,
  onClick: (() -> Unit)? = null,
  showChevron: Boolean = true,
): SettingItemModel = SettingItemModel(icon, title, subtitle, trailing, onClick, showChevron)

/** Dart `_settingItem`: icon tile + title/subtitle + trailing or chevron. */
@Composable
private fun SettingItemRow(item: SettingItemModel) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(enabled = item.onClick != null) { item.onClick?.invoke() }
      .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .size(38.dp)
        .clip(RoundedCornerShape(AppRadii.tile))
        .background(CyberHomeColors.primarySoft),
      contentAlignment = Alignment.Center,
    ) {
      LucideIcon(icon = item.icon, size = 20.dp, color = CyberHomeColors.primary)
    }
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(text = item.title, style = cyberItemTitleStyle)
      if (item.subtitle != null) {
        Spacer(Modifier.height(2.dp))
        Text(text = item.subtitle, style = cyberCaptionStyle)
      }
    }
    if (item.trailing != null) {
      item.trailing()
    } else if (item.showChevron) {
      LucideIcon(
        icon = Lucide.chevronRight,
        size = 18.dp,
        color = CyberHomeColors.inkFaint,
      )
    }
  }
}
