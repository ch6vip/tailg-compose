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
import com.tailg.plus.ui.navigation.Routes
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.CyberHomeColors
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R

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
  vehicleRouteId: String,
  onBack: () -> Unit,
  onNavigate: (String) -> Unit,
  preferencesService: AppPreferencesService? = null,
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val prefs = preferencesService ?: remember { AppPreferencesService(context) }
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
      CyberPageHeader(title = stringResource(R.string.settings_title), onBack = onBack)
      CyberSectionLabel(stringResource(R.string.settings_account_vehicle))
      settingsGroup(
        settingItemModel(
          icon = Lucide.garage,
          title = stringResource(R.string.settings_my_vehicle),
          subtitle = stringResource(R.string.settings_account_vehicle_desc),
          onClick = { onNavigate(Routes.GARAGE) },
        ),
        settingItemModel(
          icon = Lucide.message,
          title = stringResource(R.string.settings_message_center),
          subtitle = stringResource(R.string.settings_message_center_desc),
          onClick = { onNavigate(Routes.vehicleMessage(vehicleRouteId)) },
        ),
      )
      CyberSectionLabel(stringResource(R.string.settings_vehicle_usage))
      settingsGroup(
        settingItemModel(
          icon = Lucide.tune,
          title = stringResource(R.string.settings_vehicle_settings),
          subtitle = stringResource(R.string.settings_vehicle_settings_desc),
          onClick = { onNavigate(Routes.vehicleSettings(vehicleRouteId)) },
        ),
        settingItemModel(
          icon = Lucide.battery,
          title = stringResource(R.string.settings_battery_bms),
          subtitle = stringResource(R.string.settings_battery_bms_desc),
          onClick = { onNavigate(Routes.batteryDetails(vehicleRouteId)) },
        ),
      )
      CyberSectionLabel(stringResource(R.string.settings_general))
      settingsGroup(
        settingItemModel(
          icon = Lucide.languages,
          title = stringResource(R.string.settings_language_setting),
          subtitle = language.label,
          onClick = { onNavigate(Routes.LANGUAGE_SETTINGS) },
        ),
        settingItemModel(
          icon = Lucide.ruler,
          title = stringResource(R.string.settings_unit_setting),
          subtitle = "${distanceUnit.label} · ${distanceUnit.hint}",
          onClick = { onNavigate(Routes.UNIT_SETTINGS) },
        ),
        settingItemModel(
          icon = Lucide.type,
          title = stringResource(R.string.settings_follow_system_font),
          subtitle = if (respectTextScale) stringResource(R.string.settings_follow_system_font_desc) else stringResource(R.string.settings_ignore_system_font),
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
      CyberSectionLabel(stringResource(R.string.settings_advanced))
      settingsGroup(
        settingItemModel(
          icon = Lucide.shieldCheck,
          title = stringResource(R.string.settings_diagnostics),
          subtitle = stringResource(R.string.settings_diagnostics_desc),
          onClick = { onNavigate(Routes.diagnostic(vehicleRouteId)) },
        ),
        settingItemModel(
          icon = Lucide.key,
          title = stringResource(R.string.settings_official_token),
          subtitle = stringResource(R.string.settings_official_token_desc),
          onClick = { onNavigate(Routes.CLOUD_TOKEN) },
        ),
      )
      CyberSectionLabel(stringResource(R.string.settings_about))
      settingsGroup(
        settingItemModel(
          icon = Lucide.info,
          title = stringResource(R.string.settings_about_app),
          subtitle = stringResource(R.string.settings_about_app_desc),
          onClick = { onNavigate(Routes.ABOUT_APP) },
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
  vehicleRouteId: String,
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
      CyberPageHeader(title = stringResource(R.string.settings_diagnostics), onBack = onBack)
      Spacer(Modifier.height(4.dp))
      settingsGroup(
        settingItemModel(
          icon = Lucide.stethoscope,
          title = stringResource(R.string.settings_fault_diagnostics),
          subtitle = stringResource(R.string.settings_fault_diagnostics_desc),
          onClick = { onNavigate(Routes.diagnostic(vehicleRouteId)) },
        ),
        settingItemModel(
          icon = Lucide.fileText,
          title = stringResource(R.string.settings_logs),
          subtitle = stringResource(R.string.settings_logs_desc),
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
