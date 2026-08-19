package com.tailg.plus.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R
import com.tailg.plus.data.preferences.AppLanguagePreference
import com.tailg.plus.data.preferences.DistanceUnitPreference

@Composable
internal fun AppLanguagePreference.localizedLabel(): String = when (this) {
  AppLanguagePreference.System -> stringResource(R.string.prefs_language_system)
  AppLanguagePreference.SimplifiedChinese -> stringResource(R.string.prefs_language_zh_hans)
  AppLanguagePreference.English -> stringResource(R.string.prefs_language_en)
}

@Composable
internal fun DistanceUnitPreference.localizedLabel(): String = when (this) {
  DistanceUnitPreference.Metric -> stringResource(R.string.prefs_unit_metric)
  DistanceUnitPreference.Imperial -> stringResource(R.string.prefs_unit_imperial)
}
