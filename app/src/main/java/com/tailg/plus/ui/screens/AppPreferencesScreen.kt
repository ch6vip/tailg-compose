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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.preferences.AppLanguagePreference
import com.tailg.plus.data.preferences.AppPreferencesService
import com.tailg.plus.data.preferences.DistanceUnitPreference
import com.tailg.plus.data.store.VehicleStore
import com.tailg.plus.log.LogService
import com.tailg.plus.service.DiagnosticExportService
import com.tailg.plus.ui.components.AppSnack
import com.tailg.plus.ui.components.CyberCard
import com.tailg.plus.ui.components.CyberPageHeader
import com.tailg.plus.ui.components.CyberSectionLabel
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.cyberBodyStyle
import com.tailg.plus.ui.components.cyberButtonShape
import com.tailg.plus.ui.components.cyberCaptionStyle
import com.tailg.plus.ui.components.cyberFilledButtonColors
import com.tailg.plus.ui.components.cyberItemTitleStyle
import com.tailg.plus.ui.theme.AppIconSizes
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.CyberHomeColors
import com.tailg.plus.util.ClipboardText
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R

private const val APP_VERSION = "0.1.0"
private val BUILD_COMMIT: String = "local"

/**
 * Port of `lib/pages/app_preferences_pages.dart` → `AppPreferencesScreen.kt`.
 *
 * The Dart file defines three pages: `LanguageSettingsPage`,
 * `UnitSettingsPage`, and `AboutAppPage`. They are kept as separate
 * `@Composable` functions here so the route graph can wire each one
 * independently under its own route.
 */

/** Dart `LanguageSettingsPage`. */
@Composable
fun LanguageSettingsScreen(
  onBack: () -> Unit,
  preferencesService: AppPreferencesService? = null,
) {
  val prefs = preferencesService
    ?: com.tailg.plus.di.rememberTailgEntryPoint().appPreferences()
  var selected by remember { mutableStateOf(AppLanguagePreference.System) }
  var saving by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  LaunchedEffect(Unit) {
    prefs.init()
    selected = prefs.language.value
  }

  val confirm: () -> Unit = {
    if (!saving) {
      saving = true
      scope.launch {
        prefs.setLanguage(selected)
        saving = false
        onBack()
      }
    }
  }

  Scaffold(
    containerColor = CyberHomeColors.pageBg,
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
    ) {
      CyberPageHeader(title = stringResource(R.string.prefs_language_setting), onBack = onBack)
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .padding(bottom = 24.dp),
      ) {
        CyberSectionLabel(stringResource(R.string.prefs_language))
        CyberCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
          Column {
            AppLanguagePreference.entries.forEachIndexed { index, preference ->
              if (index > 0) InsetDivider()
              OptionRow(
                title = preference.localizedLabel(),
                selected = selected == preference,
                onClick = { selected = preference },
              )
            }
          }
        }
      }
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
      ) {
        Button(
          onClick = confirm,
          enabled = !saving,
          shape = cyberButtonShape,
          colors = cyberFilledButtonColors(),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        ) {
          Text(if (saving) stringResource(R.string.prefs_saving) else stringResource(R.string.prefs_confirm))
        }
      }
    }
  }
}

/** Dart `UnitSettingsPage`. */
@Composable
fun UnitSettingsScreen(
  onBack: () -> Unit,
  preferencesService: AppPreferencesService? = null,
) {
  val prefs = preferencesService
    ?: com.tailg.plus.di.rememberTailgEntryPoint().appPreferences()
  var selected by remember { mutableStateOf(DistanceUnitPreference.Metric) }
  val scope = rememberCoroutineScope()

  LaunchedEffect(Unit) {
    prefs.init()
    selected = prefs.distanceUnit.value
  }

  val select: (DistanceUnitPreference) -> Unit = { preference ->
    selected = preference
    scope.launch { prefs.setDistanceUnit(preference) }
  }

  Scaffold(
    containerColor = CyberHomeColors.pageBg,
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
    ) {
      CyberPageHeader(title = stringResource(R.string.prefs_unit_setting), onBack = onBack)
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .padding(bottom = 24.dp),
      ) {
        CyberSectionLabel(stringResource(R.string.prefs_distance_unit))
        CyberCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
          Column {
            DistanceUnitPreference.entries.forEachIndexed { index, preference ->
              if (index > 0) InsetDivider()
              OptionRow(
                title = preference.localizedLabel(),
                subtitle = preference.hint,
                selected = selected == preference,
                onClick = { select(preference) },
              )
            }
          }
        }
      }
    }
  }
}

/** Dart `AboutAppPage`. */
@Composable
fun AboutAppScreen(
  onBack: () -> Unit,
  logService: LogService? = null,
  cloudService: OfficialCloudService? = null,
) {
  val entryPoint = com.tailg.plus.di.rememberTailgEntryPoint()
  val log = logService ?: entryPoint.logService()
  val clipboard = entryPoint.clipboardText()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val strCopiedReport = stringResource(R.string.prefs_copied_report)
  val strTerms = stringResource(R.string.prefs_terms)
  val strPrivacy = stringResource(R.string.prefs_privacy)

  val cloud = cloudService ?: entryPoint.cloudService()
  val vehicleStore = entryPoint.vehicleStore()
  val exportService = remember {
    DiagnosticExportService(
      logService = log,
      vehicleStore = vehicleStore,
      officialCloudService = cloud,
    )
  }

  val copyDiagnosticReport: () -> Unit = {
    scope.launch {
      val report = exportService.buildReport(log.all)
      clipboard.writeClipboardText(report)
      AppSnack.success(snackbarHostState, strCopiedReport)
    }
  }

  Scaffold(
    containerColor = CyberHomeColors.pageBg,
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
    ) {
      CyberPageHeader(title = stringResource(R.string.prefs_about), onBack = onBack)
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .padding(bottom = 24.dp),
      ) {
        Spacer(Modifier.height(18.dp))
        CyberCard {
          Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Box(
              modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(AppRadii.tile))
                .background(CyberHomeColors.primarySoft),
              contentAlignment = Alignment.Center,
            ) {
              LucideIcon(icon = Lucide.vehicle, color = CyberHomeColors.primary, size = AppIconSizes.xl)
            }
            Spacer(Modifier.height(14.dp))
            Text(
              text = stringResource(R.string.prefs_app_name),
              style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
            )
            Spacer(Modifier.height(4.dp))
            Text(text = stringResource(R.string.prefs_app_desc), style = cyberCaptionStyle)
          }
        }
        CyberSectionLabel(stringResource(R.string.prefs_version))
        CyberCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
          Column {
            InfoRow(label = stringResource(R.string.prefs_app_version), value = APP_VERSION)
            InsetDivider()
            InfoRow(label = stringResource(R.string.prefs_git_commit), value = BUILD_COMMIT)
          }
        }
        CyberSectionLabel(stringResource(R.string.prefs_service_support))
        CyberCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
          Column {
            ActionRow(
              icon = Lucide.support,
              title = stringResource(R.string.prefs_service_diag),
              subtitle = stringResource(R.string.prefs_service_diag_desc),
              onClick = copyDiagnosticReport,
            )
            InsetDivider()
            ActionRow(
              icon = Lucide.fileText,
              title = stringResource(R.string.prefs_terms),
              subtitle = stringResource(R.string.prefs_terms_desc),
              onClick = { AppSnack.notYetOpen(scope, snackbarHostState, strTerms) },
            )
            InsetDivider()
            ActionRow(
              icon = Lucide.privacy,
              title = stringResource(R.string.prefs_privacy),
              subtitle = stringResource(R.string.prefs_privacy_desc),
              onClick = { AppSnack.notYetOpen(scope, snackbarHostState, strPrivacy) },
            )
          }
        }
        Spacer(Modifier.height(20.dp))
        Text(
          text = "Copyright 2026",
          style = cyberCaptionStyle,
          modifier = Modifier.fillMaxWidth(),
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

/** Dart `_OptionRow`: selectable row with a check/radio glyph. */
@Composable
private fun OptionRow(
  title: String,
  subtitle: String? = null,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, style = cyberItemTitleStyle)
      if (subtitle != null) {
        Spacer(Modifier.height(4.dp))
        Text(text = subtitle, style = cyberCaptionStyle)
      }
    }
    LucideIcon(
      icon = if (selected) Lucide.checkCircle else Lucide.radioUnchecked,
      color = if (selected) CyberHomeColors.primary else CyberHomeColors.inkFaint,
    )
  }
}

/** Dart `_ActionRow`: icon + title/subtitle + chevron, pressable. */
@Composable
private fun ActionRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .size(38.dp)
        .clip(RoundedCornerShape(AppRadii.tile))
        .background(CyberHomeColors.primarySoft),
      contentAlignment = Alignment.Center,
    ) {
      LucideIcon(icon = icon, color = CyberHomeColors.primary, size = AppIconSizes.md)
    }
    Spacer(Modifier.width(14.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, style = cyberItemTitleStyle)
      Spacer(Modifier.height(4.dp))
      Text(text = subtitle, style = cyberCaptionStyle)
    }
    LucideIcon(
      icon = Lucide.chevronRight,
      color = CyberHomeColors.inkFaint,
      size = AppIconSizes.md,
    )
  }
}

/** Dart `_InfoRow`: label + right-aligned value. */
@Composable
private fun InfoRow(label: String, value: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 13.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = label, style = cyberBodyStyle, modifier = Modifier.weight(1f))
    Spacer(Modifier.width(12.dp))
    Text(
      text = value,
      modifier = Modifier.weight(1f),
      textAlign = TextAlign.End,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = cyberItemTitleStyle.copy(fontSize = 13.sp),
    )
  }
}

/** Dart `_InsetDivider`: a thin divider inset from both edges. */
@Composable
private fun InsetDivider() {
  HorizontalDivider(
    thickness = 1.dp,
    color = CyberHomeColors.line,
    modifier = Modifier.padding(start = 16.dp, end = 16.dp),
  )
}
