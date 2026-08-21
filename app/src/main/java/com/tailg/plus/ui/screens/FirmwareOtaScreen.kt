package com.tailg.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tailg.plus.R
import com.tailg.plus.service.FirmwareOtaPhase
import com.tailg.plus.ui.components.AppSnack
import com.tailg.plus.ui.components.CyberCard
import com.tailg.plus.ui.components.CyberPageHeader
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.cyberBodyStyle
import com.tailg.plus.ui.components.cyberButtonShape
import com.tailg.plus.ui.components.cyberCaptionStyle
import com.tailg.plus.ui.components.cyberFilledButtonColors
import com.tailg.plus.ui.components.cyberItemTitleStyle
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppTouchTargets
import com.tailg.plus.ui.theme.CyberHomeColors
import androidx.compose.ui.res.stringResource

/**
 * Port of `lib/pages/firmware_ota_page.dart` → `FirmwareOtaScreen.kt`.
 *
 * P3-5 experimental official OTA flow: query → download → writeOtaOrder →
 * writeOtaFileChunk. The [FirmwareOtaViewModel] owns the [FirmwareOtaService]
 * flow collection and running/progress state; this composable is a thin,
 * stateless renderer that resolves the transient snackbar only.
 */
@Composable
fun FirmwareOtaScreen(
  onBack: () -> Unit,
  viewModel: FirmwareOtaViewModel = hiltViewModel(),
) {
  val snackbarHostState = remember { SnackbarHostState() }
  val running by viewModel.running.collectAsStateWithLifecycle()
  val progress by viewModel.progress.collectAsStateWithLifecycle()

  // Resolve the transient OTA outcome snackbar from the phase transition.
  LaunchedEffect(progress.phase) {
    when (progress.phase) {
      FirmwareOtaPhase.COMPLETED -> AppSnack.success(snackbarHostState, progress.message)
      FirmwareOtaPhase.FAILED -> AppSnack.error(snackbarHostState, progress.message)
      else -> Unit
    }
  }

  val start: () -> Unit = { viewModel.start() }

  Scaffold(
    containerColor = CyberHomeColors.pageBg,
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .imePadding()
        .verticalScroll(rememberScrollState())
        .padding(bottom = 24.dp),
    ) {
      CyberPageHeader(title = stringResource(R.string.ota_title), onBack = onBack)
      Spacer(Modifier.height(8.dp))
      CyberCard {
        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(AppTouchTargets.min)
                .clip(CircleShape)
                .background(CyberHomeColors.primarySoft),
              contentAlignment = Alignment.Center,
            ) {
              LucideIcon(icon = Lucide.download, size = 20.dp, color = CyberHomeColors.primary)
            }
            Spacer(Modifier.width(12.dp))
            Text(text = stringResource(R.string.ota_vehicle_firmware), style = cyberItemTitleStyle)
          }
          Spacer(Modifier.height(14.dp))
          Text(
            text = stringResource(R.string.ota_description),
            style = cyberBodyStyle,
          )
          Spacer(Modifier.height(18.dp))
          LinearProgressIndicator(
            progress = { progress.fraction.coerceIn(0.0, 1.0).toFloat() },
            modifier = Modifier
              .fillMaxWidth()
              .height(6.dp)
              .clip(RoundedCornerShape(AppRadii.pill)),
            color = CyberHomeColors.primary,
            trackColor = CyberHomeColors.controlStrong,
          )
          Spacer(Modifier.height(10.dp))
          val captionText =
            if (progress.phase == FirmwareOtaPhase.IDLE) {
              stringResource(R.string.ota_standby)
            } else {
              val phaseName = progress.phase.name.lowercase()
              "$phaseName \u00b7 ${progress.message}"
            }
          Text(
            text = captionText,
            style = cyberCaptionStyle,
          )
          Spacer(Modifier.height(16.dp))
          Button(
            onClick = start,
            enabled = !running,
            shape = cyberButtonShape,
            colors = cyberFilledButtonColors(),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(if (running) stringResource(R.string.ota_in_progress) else stringResource(R.string.ota_check_upgrade))
          }
        }
      }
    }
  }
}
