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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tailg.plus.data.preferences.AppLanguagePreference
import com.tailg.plus.data.preferences.AppPreferencesService
import com.tailg.plus.data.preferences.DistanceUnitPreference
import com.tailg.plus.ui.components.CyberCard
import com.tailg.plus.ui.components.CyberPageHeader
import com.tailg.plus.ui.components.CyberSectionLabel
import com.tailg.plus.ui.components.LocalBottomNavigationPadding
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.cyberCaptionStyle
import com.tailg.plus.ui.components.cyberItemTitleStyle
import com.tailg.plus.ui.components.material.OffsetAnchoredExpressiveMenu
import com.tailg.plus.ui.components.material.trackPressPosition
import com.tailg.plus.ui.navigation.Routes
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.UiMode
import com.tailg.plus.ui.theme.CyberHomeColors
import kotlinx.coroutines.launch
import androidx.compose.material3.SnackbarHostState
import com.tailg.plus.ui.components.AppSnack
import com.tailg.plus.ui.components.AppSnackbarHost
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  vehicleRouteId: String,
  onBack: () -> Unit,
  onNavigate: (String) -> Unit,
  preferencesService: AppPreferencesService? = null,
  showBack: Boolean = true,
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val prefs = preferencesService
    ?: com.tailg.plus.di.rememberTailgEntryPoint().appPreferences()
  val language by prefs.language.collectAsStateWithLifecycle(AppLanguagePreference.System)
  val distanceUnit by prefs.distanceUnit.collectAsStateWithLifecycle(DistanceUnitPreference.Metric)
  val respectTextScale by prefs.respectSystemTextScale.collectAsStateWithLifecycle(true)
  val uiMode by prefs.uiMode.collectAsStateWithLifecycle(initialValue = UiMode.CYBER.value)
  val currentUiMode = UiMode.fromValue(uiMode)
  val scope = androidx.compose.runtime.rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  Scaffold(
    containerColor = CyberHomeColors.pageBg,
    snackbarHost = { AppSnackbarHost(snackbarHostState) },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(padding)
        .padding(bottom = 32.dp + LocalBottomNavigationPadding.current),
    ) {
      CyberPageHeader(title = stringResource(R.string.settings_title), showBack = showBack, onBack = onBack)
      CyberSectionLabel(stringResource(R.string.settings_account_vehicle))
      SettingsGroup(
        settingItemModel(
          icon = Lucide.garage,
          title = stringResource(R.string.settings_my_vehicle),
          subtitle = stringResource(R.string.settings_account_vehicle_desc),
          onClick = { onNavigate(Routes.GARAGE) },
        ),
      )
      CyberSectionLabel(stringResource(R.string.settings_vehicle_usage))
      SettingsGroup(
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
      SettingsGroup(
        settingItemModel(
          icon = Lucide.languages,
          title = stringResource(R.string.settings_language_setting),
          subtitle = language.localizedLabel(),
          onClick = { onNavigate(Routes.LANGUAGE_SETTINGS) },
        ),
        settingItemModel(
          icon = Lucide.ruler,
          title = stringResource(R.string.settings_unit_setting),
          subtitle = "${distanceUnit.localizedLabel()} · ${distanceUnit.hint}",
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
                  AppSnack.runAction(snackbarHostState) { prefs.setRespectSystemTextScale(value) }
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
      CyberSectionLabel(stringResource(R.string.settings_appearance))
      // 界面风格 — KernelSU-style: the row opens an expressive dropdown menu
      // anchored at the press position (SegmentedDropdownItem UX), not a sheet.
      var showUiModeMenu by remember { mutableStateOf(false) }
      var menuAnchorOffset by remember { mutableStateOf(IntOffset.Zero) }
      val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
      Box(modifier = Modifier.trackPressPosition { menuAnchorOffset = it.round() }) {
        SettingsGroup(
          settingItemModel(
            icon = Lucide.spark,
            title = stringResource(R.string.settings_ui_mode),
            subtitle = uiModeLabel(currentUiMode),
            onClick = { showUiModeMenu = true },
          ),
          settingItemModel(
            icon = Lucide.tune,
            title = stringResource(R.string.settings_theme),
            subtitle = stringResource(R.string.settings_theme_desc),
            onClick = { onNavigate(Routes.THEME) },
          ),
        )
        OffsetAnchoredExpressiveMenu(
          expanded = showUiModeMenu,
          onDismissRequest = { showUiModeMenu = false },
          anchorOffset = menuAnchorOffset,
        ) {
          UiMode.entries.forEachIndexed { index, mode ->
            SelectableDropdownMenuItem(
              text = { Text(uiModeLabel(mode)) },
              selected = mode == currentUiMode,
              onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                scope.launch { AppSnack.runAction(snackbarHostState) { prefs.setUiMode(mode.value) } }
                showUiModeMenu = false
              },
              shapes = MenuDefaults.itemShape(index = index, count = UiMode.entries.size),
              selectedLeadingIcon = {
                Icon(
                  Icons.Filled.Check,
                  contentDescription = null,
                  modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                )
              },
            )
          }
        }
      }
      CyberSectionLabel(stringResource(R.string.settings_about))
      SettingsGroup(
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

/** Label for the UI-style picker (Cyber / 九号). */
@Composable
internal fun uiModeLabel(mode: UiMode): String = when (mode) {
  UiMode.CYBER -> stringResource(R.string.theme_ui_mode_cyber)
  UiMode.NINEBOT -> stringResource(R.string.theme_ui_mode_ninebot)
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
      SettingsGroup(
        settingItemModel(
          icon = Lucide.stethoscope,
          title = stringResource(R.string.settings_fault_diagnostics),
          subtitle = stringResource(R.string.settings_fault_diagnostics_desc),
          onClick = { onNavigate(Routes.faultDiagnostic(vehicleRouteId)) },
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
internal fun SettingsGroup(vararg items: SettingItemModel) {
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
internal data class SettingItemModel(
  val icon: androidx.compose.ui.graphics.vector.ImageVector,
  val title: String,
  val subtitle: String? = null,
  val trailing: @Composable (() -> Unit)? = null,
  val onClick: (() -> Unit)? = null,
  val showChevron: Boolean = true,
)

internal fun settingItemModel(
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
      .clickable(
        enabled = item.onClick != null,
        role = Role.Button,
      ) { item.onClick?.invoke() }
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
