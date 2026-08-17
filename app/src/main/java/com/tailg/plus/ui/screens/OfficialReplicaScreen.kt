package com.tailg.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.cloud.OfficialCloudState
import com.tailg.plus.data.model.NfcKeyRecord
import com.tailg.plus.data.model.ShareMemberRecord
import com.tailg.plus.data.model.VehicleLocation
import com.tailg.plus.data.store.ReplicaFeatureStore
import com.tailg.plus.data.store.VehicleStore
import com.tailg.plus.log.LogCategory
import com.tailg.plus.log.LogService
import com.tailg.plus.service.BleNfcService
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.components.AppSnackbarHost
import com.tailg.plus.ui.components.AppSnack
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.components.cyberButtonShape
import com.tailg.plus.ui.components.cyberFilledButtonColors
import com.tailg.plus.ui.components.cyberOutlinedButtonBorder
import com.tailg.plus.ui.components.cyberOutlinedButtonColors
import com.tailg.plus.ui.components.cyberTextFieldColors
import com.tailg.plus.ui.components.cyberTextFieldShape
import com.tailg.plus.ui.theme.AppIconSizes
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppSpacing
import com.tailg.plus.ui.theme.AppTouchTargets
import com.tailg.plus.ui.theme.CyberHomeColors
import com.tailg.plus.util.formatDateText
import com.tailg.plus.util.formatDateMinuteText
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R

internal enum class ReplicaPage { NFC, FENCE, SHARE, RIDE }

/**
 * Port of `lib/pages/official_replica_pages.dart` — NFC keys, local fence
 * draft, share members, and ride record pages.
 *
 * The Dart file contains four page widgets (NfcKeyPage, ElectricFencePage,
 * ShareBikePage, RideRecordPage); this port collapses them into a single
 * screen with a page selector.
 */
@Composable
fun OfficialReplicaScreen(
  cloudService: OfficialCloudService,
  vehicleStore: VehicleStore,
  connectionManager: ConnectionManager,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  val log = remember { LogService() }
  val snackbarHostState = remember { SnackbarHostState() }
  val context = androidx.compose.ui.platform.LocalContext.current
  val store = remember(context) { ReplicaFeatureStore(context) }
  val bleNfc = remember(connectionManager) { BleNfcService(connectionManager) }

  val strKeyDeleted = stringResource(R.string.replica_key_deleted)
  val strKeyDeleteFailed = stringResource(R.string.replica_key_delete_failed)
  val strKeyTypeCard = stringResource(R.string.replica_key_type_card)
  val strKeyTypeWatch = stringResource(R.string.replica_key_type_watch)
  val strKeyWritten = stringResource(R.string.replica_key_written)
  val strKeyWriteFailed = stringResource(R.string.replica_key_write_failed)
  val strNotLoggedInLocal = stringResource(R.string.replica_not_logged_in_local)
  var page by remember { mutableStateOf(ReplicaPage.NFC) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = CyberHomeColors.pageBg,
    snackbarHost = { AppSnackbarHost(snackbarHostState) },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
    ) {
      ReplicaPageHeader(
        title = when (page) {
          ReplicaPage.NFC -> stringResource(R.string.replica_nfc_keys)
          ReplicaPage.FENCE -> stringResource(R.string.replica_local_fence)
          ReplicaPage.SHARE -> stringResource(R.string.replica_share_car)
          ReplicaPage.RIDE -> stringResource(R.string.replica_today_rides)
        },
        actionIcon = when (page) {
          ReplicaPage.NFC -> Lucide.plus
          ReplicaPage.FENCE -> Lucide.locate
          ReplicaPage.SHARE -> Lucide.userPlus
          ReplicaPage.RIDE -> null
        },
        actionLabel = when (page) {
          ReplicaPage.NFC -> stringResource(R.string.replica_add_key)
          ReplicaPage.FENCE -> stringResource(R.string.replica_use_last_location)
          ReplicaPage.SHARE -> stringResource(R.string.replica_add_member)
          ReplicaPage.RIDE -> null
        },
        onAction = when (page) {
          ReplicaPage.NFC -> { { page = ReplicaPage.NFC } }
          ReplicaPage.FENCE -> { {} }
          ReplicaPage.SHARE -> { {} }
          ReplicaPage.RIDE -> null
        },
        onBack = onBack,
      )
      // Page selector tabs.
      Row(
        modifier = Modifier
          .padding(horizontal = 20.dp, vertical = 8.dp)
          .clip(RoundedCornerShape(AppRadii.tile))
          .background(CyberHomeColors.control)
          .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
          .padding(4.dp),
      ) {
        ReplicaPage.entries.forEach { p ->
          val active = page == p
          AppPressable(
            onClick = { page = p },
            shape = RoundedCornerShape(AppRadii.tile),
            background = if (active) CyberHomeColors.card else androidx.compose.ui.graphics.Color.Transparent,
            semanticsLabel = p.name,
            modifier = Modifier.weight(1f),
          ) {
            Box(
              modifier = Modifier.height(AppTouchTargets.min),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = when (p) {
                  ReplicaPage.NFC -> "NFC"
                  ReplicaPage.FENCE -> stringResource(R.string.replica_tab_fence)
                  ReplicaPage.SHARE -> stringResource(R.string.replica_tab_share)
                  ReplicaPage.RIDE -> stringResource(R.string.replica_tab_ride)
                },
                style = androidx.compose.ui.text.TextStyle(
                  fontSize = 13.sp,
                  fontWeight = FontWeight.W700,
                  color = if (active) CyberHomeColors.ink else CyberHomeColors.inkMuted,
                ),
              )
            }
          }
        }
      }
      when (page) {
        ReplicaPage.NFC -> NfcKeyTab(
          store = store,
          bleNfc = bleNfc,
          snackbarHostState = snackbarHostState,
          scope = scope,
        )
        ReplicaPage.FENCE -> ElectricFenceTab(
          store = store,
          vehicleStore = vehicleStore,
          snackbarHostState = snackbarHostState,
          scope = scope,
        )
        ReplicaPage.SHARE -> ShareBikeTab(
          store = store,
          snackbarHostState = snackbarHostState,
          scope = scope,
        )
        ReplicaPage.RIDE -> RideRecordTab(
          cloudService = cloudService,
          vehicleStore = vehicleStore,
          log = log,
        )
      }
    }
  }
}
