package com.tailg.plus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.ui.components.CyberPageHeader
import com.tailg.plus.ui.theme.CyberHomeColors
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R

/**
 * Port of `lib/pages/qgj_settings_page.dart`.
 *
 * The Dart file is a one-line barrel re-export of `induction_settings_page.dart`
 * (`export 'induction_settings_page.dart';`), kept only for backward-compatible
 * navigation sites that pushed `QgjSettingsPage`. The Compose route graph has a
 * dedicated [Routes.QGJ_SETTINGS] entry, so this screen is a thin stub that
 * forwards to the induction settings experience.
 *
 * Navigation: [Routes.QGJ_SETTINGS] carries a `{vehicleId}` segment; the stub
 * surfaces a placeholder and a TODO to either redirect to
 * [InductionSettingsScreen] or render the QGJ-specific subset once the route
 * graph supports cross-screen forwarding.
 */
@Composable
fun QgjSettingsScreen(
  vehicleId: String,
  onBack: () -> Unit,
) {
  Scaffold(
    containerColor = CyberHomeColors.pageBg,
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
    ) {
      CyberPageHeader(title = stringResource(R.string.qgj_induction_title), onBack = onBack)
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 36.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Text(
          text = stringResource(R.string.qgj_induction_full_title),
          textAlign = TextAlign.Center,
          style = TextStyle(
            fontSize = 18.sp,
            fontWeight = FontWeight.W700,
            color = CyberHomeColors.ink,
          ),
        )
        Spacer(Modifier.height(7.dp))
        Text(
          text = stringResource(R.string.qgj_merged_hint),
          textAlign = TextAlign.Center,
          style = TextStyle(
            fontSize = 13.sp,
            color = CyberHomeColors.inkMuted,
            lineHeight = 13.sp * 1.45f,
          ),
        )
      }
    }
  }
}
