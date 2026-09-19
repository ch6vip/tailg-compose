package com.tailg.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tailg.plus.R
import com.tailg.plus.data.preferences.AppLanguagePreference
import com.tailg.plus.data.preferences.AppPreferencesService
import com.tailg.plus.data.preferences.DistanceUnitPreference
import com.tailg.plus.di.rememberTailgEntryPoint
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.components.AppSnack
import com.tailg.plus.ui.components.AppSnackbarHost
import com.tailg.plus.ui.components.LocalBottomNavigationPadding
import com.tailg.plus.ui.components.NinebotIcon
import com.tailg.plus.ui.components.NinebotLucide
import com.tailg.plus.ui.components.SettingFeatureTile
import com.tailg.plus.ui.components.SettingTileEmphasis
import com.tailg.plus.ui.components.SettingsGroup
import com.tailg.plus.ui.components.SettingsSectionLabel
import com.tailg.plus.ui.components.material.ExpressiveSwitch
import com.tailg.plus.ui.components.ninebotPageBackground
import com.tailg.plus.ui.components.ninebotSettingsCardColor
import com.tailg.plus.ui.components.settingItemModel
import com.tailg.plus.ui.navigation.Routes
import com.tailg.plus.ui.theme.AppTouchTargets
import com.tailg.plus.ui.theme.CyberHomeColors
import kotlinx.coroutines.launch

/**
 * Settings hub in the 九号 visual language. Vehicle and BMS sit as a two-up
 * core pair; language, units, appearance and about remain ordinary grouped rows.
 *
 * Note: 设置页核心/普通分层与 SettingTile — 见 .agents/notes/implemented/feature/2026-09-20-settings-hierarchy.md
 */
@Composable
fun SettingsScreen(
  vehicleRouteId: String,
  onBack: () -> Unit,
  onNavigate: (String) -> Unit,
  preferencesService: AppPreferencesService? = null,
  showBack: Boolean = true,
) {
  val prefs = preferencesService ?: rememberTailgEntryPoint().appPreferences()
  val language by prefs.language.collectAsStateWithLifecycle(AppLanguagePreference.System)
  val distanceUnit by prefs.distanceUnit.collectAsStateWithLifecycle(DistanceUnitPreference.Metric)
  val respectTextScale by prefs.respectSystemTextScale.collectAsStateWithLifecycle(true)
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  val unitHint = stringResource(
    if (distanceUnit == DistanceUnitPreference.Metric) R.string.prefs_unit_metric_hint else R.string.prefs_unit_imperial_hint,
  )

  Scaffold(
    containerColor = ninebotPageBackground(),
    snackbarHost = { AppSnackbarHost(snackbarHostState) },
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .windowInsetsPadding(WindowInsets.statusBars)
        .verticalScroll(rememberScrollState())
        .padding(bottom = 12.dp + LocalBottomNavigationPadding.current),
    ) {
      NinebotSettingsHeader(
        title = stringResource(R.string.settings_title),
        subtitle = stringResource(R.string.settings_hub_subtitle),
        showBack = showBack,
        onBack = onBack,
      )

      SettingsSectionLabel(stringResource(R.string.settings_core_section))
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        SettingFeatureTile(
          icon = NinebotLucide.slidersHorizontal,
          title = stringResource(R.string.settings_vehicle_settings),
          subtitle = stringResource(R.string.settings_vehicle_settings_short),
          onClick = { onNavigate(Routes.vehicleSettings(vehicleRouteId)) },
          modifier = Modifier.weight(1f),
        )
        SettingFeatureTile(
          icon = NinebotLucide.batteryCharging,
          title = stringResource(R.string.settings_battery_bms),
          subtitle = stringResource(R.string.settings_battery_bms_short),
          onClick = { onNavigate(Routes.batteryDetails(vehicleRouteId)) },
          modifier = Modifier.weight(1f),
        )
      }

      SettingsSectionLabel(stringResource(R.string.settings_account_vehicle))
      SettingsGroup(
        settingItemModel(
          icon = NinebotLucide.warehouse,
          title = stringResource(R.string.settings_my_vehicle),
          subtitle = stringResource(R.string.settings_account_vehicle_desc),
          emphasis = SettingTileEmphasis.Core,
          onClick = { onNavigate(Routes.GARAGE) },
        ),
        modifier = Modifier.padding(horizontal = 20.dp),
      )

      SettingsSectionLabel(stringResource(R.string.settings_preferences_section))
      SettingsGroup(
        settingItemModel(
          icon = NinebotLucide.languages,
          title = stringResource(R.string.settings_language_setting),
          subtitle = language.localizedLabel(),
          onClick = { onNavigate(Routes.LANGUAGE_SETTINGS) },
        ),
        settingItemModel(
          icon = NinebotLucide.ruler,
          title = stringResource(R.string.settings_unit_setting),
          subtitle = "${distanceUnit.localizedLabel()} · $unitHint",
          onClick = { onNavigate(Routes.UNIT_SETTINGS) },
        ),
        settingItemModel(
          icon = NinebotLucide.type,
          title = stringResource(R.string.settings_follow_system_font),
          subtitle = stringResource(
            if (respectTextScale) R.string.settings_follow_system_font_desc else R.string.settings_ignore_system_font,
          ),
          showChevron = false,
          trailing = {
            ExpressiveSwitch(
              checked = respectTextScale,
              onCheckedChange = { value ->
                scope.launch {
                  AppSnack.runAction(snackbarHostState) { prefs.setRespectSystemTextScale(value) }
                }
              },
            )
          },
        ),
        settingItemModel(
          icon = NinebotLucide.sparkles,
          title = stringResource(R.string.settings_theme),
          subtitle = stringResource(R.string.settings_theme_desc),
          onClick = { onNavigate(Routes.THEME) },
        ),
        modifier = Modifier.padding(horizontal = 20.dp),
      )

      SettingsSectionLabel(stringResource(R.string.settings_about))
      SettingsGroup(
        settingItemModel(
          icon = NinebotLucide.badgeInfo,
          title = stringResource(R.string.settings_about_app),
          subtitle = stringResource(R.string.settings_about_app_desc),
          onClick = { onNavigate(Routes.ABOUT_APP) },
        ),
        modifier = Modifier.padding(horizontal = 20.dp),
      )
    }
  }
}

@Composable
fun AdvancedDiagnosticsScreen(
  vehicleRouteId: String,
  onBack: () -> Unit,
  onNavigate: (String) -> Unit,
) {
  Scaffold(
    containerColor = ninebotPageBackground(),
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .windowInsetsPadding(WindowInsets.statusBars)
        .verticalScroll(rememberScrollState())
        .padding(bottom = 24.dp),
    ) {
      NinebotSettingsHeader(
        title = stringResource(R.string.settings_diagnostics),
        showBack = true,
        onBack = onBack,
      )
      Spacer(Modifier.height(4.dp))
      SettingsGroup(
        settingItemModel(
          icon = NinebotLucide.stethoscope,
          title = stringResource(R.string.settings_fault_diagnostics),
          subtitle = stringResource(R.string.settings_fault_diagnostics_desc),
          onClick = { onNavigate(Routes.faultDiagnostic(vehicleRouteId)) },
        ),
        settingItemModel(
          icon = NinebotLucide.fileText,
          title = stringResource(R.string.settings_logs),
          subtitle = stringResource(R.string.settings_logs_desc),
          onClick = { onNavigate(Routes.LOG) },
        ),
        modifier = Modifier.padding(horizontal = 20.dp),
      )
    }
  }
}

@Composable
private fun NinebotSettingsHeader(
  title: String,
  subtitle: String? = null,
  showBack: Boolean,
  onBack: () -> Unit,
) {
  Row(
    modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 20.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (showBack) {
      AppPressable(
        onClick = onBack,
        shape = CircleShape,
        semanticsLabel = stringResource(R.string.common_back),
      ) {
        Box(
          modifier = Modifier
            .size(AppTouchTargets.min)
            .clip(CircleShape)
            .background(ninebotSettingsCardColor()),
          contentAlignment = Alignment.Center,
        ) {
          NinebotIcon(
            icon = NinebotLucide.arrowLeft,
            size = 20.dp,
            color = CyberHomeColors.ink,
          )
        }
      }
    }
    Column(modifier = Modifier.padding(start = 8.dp)) {
      Text(
        text = title,
        fontSize = 24.sp,
        fontWeight = FontWeight.W700,
        color = CyberHomeColors.ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (!subtitle.isNullOrBlank()) {
        Spacer(Modifier.height(2.dp))
        Text(
          text = subtitle,
          fontSize = 13.sp,
          color = CyberHomeColors.inkMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
