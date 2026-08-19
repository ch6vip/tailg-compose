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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.data.cloud.OfficialCloudRedactor
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.service.FirmwareOtaPhase
import com.tailg.plus.service.FirmwareOtaProgress
import com.tailg.plus.service.FirmwareOtaService
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R

/**
 * Port of `lib/pages/firmware_ota_page.dart` → `FirmwareOtaScreen.kt`.
 *
 * P3-5 experimental official OTA flow: query → download → writeOtaOrder →
 * writeOtaFileChunk. The Dart `StatefulWidget` listens to a `Stream<FirmwareOtaProgress>`;
 * here we collect the cold [FirmwareOtaService.run] `Flow` in a `LaunchedEffect`.
 */
@Composable
fun FirmwareOtaScreen(
  onBack: () -> Unit,
  cloudService: OfficialCloudService,
  connectionManager: ConnectionManager? = null,
) {
  val context = LocalContext.current
  val cloud = cloudService
  val connection = remember(connectionManager) { connectionManager ?: ConnectionManager(context) }
  val ota = remember(cloud, connection) { FirmwareOtaService(cloud = cloud, connectionManager = connection) }
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  val strOtaStandby = stringResource(R.string.ota_standby)
  val strOtaStarting = stringResource(R.string.ota_starting)

  var progress by remember {
    mutableStateOf(FirmwareOtaProgress(FirmwareOtaPhase.IDLE, 0.0, strOtaStandby))
  }
  var running by remember { mutableStateOf(false) }

  val start: () -> Unit = {
    if (!running) {
    running = true
    progress = FirmwareOtaProgress(FirmwareOtaPhase.QUERYING, 0.0, strOtaStarting)
    scope.launch {
      try {
        ota.run().collectLatest { p ->
          progress = p
          if (p.phase == FirmwareOtaPhase.COMPLETED) {
            running = false
            AppSnack.success(snackbarHostState, p.message)
          } else if (p.phase == FirmwareOtaPhase.FAILED) {
            running = false
            AppSnack.error(snackbarHostState, p.message)
          }
        }
      } catch (e: Exception) {
        running = false
        val msg = OfficialCloudRedactor.errorMessage(e)
        progress = FirmwareOtaProgress(FirmwareOtaPhase.FAILED, progress.fraction, msg)
        AppSnack.error(snackbarHostState, msg)
      }
    }
    }
  }

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
          Text(
            text = "${progress.phase.name.lowercase()} · ${progress.message}",
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
