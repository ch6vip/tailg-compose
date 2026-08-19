package com.tailg.plus.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.tailg.plus.data.preferences.DistanceUnitPreference

val LocalDistanceUnitPreference = staticCompositionLocalOf {
  DistanceUnitPreference.Metric
}
