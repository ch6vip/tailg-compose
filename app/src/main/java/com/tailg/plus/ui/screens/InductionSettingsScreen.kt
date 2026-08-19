package com.tailg.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.permission.AppPermissionService
import com.tailg.plus.service.DataStoreInductionPrefs
import com.tailg.plus.service.InductionModeService
import com.tailg.plus.service.InductionModeSnapshot
import com.tailg.plus.service.InductionStack
import com.tailg.plus.service.ManualModeService
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.components.AppSnackbarHost
import com.tailg.plus.ui.components.AppSnack
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppTouchTargets
import com.tailg.plus.ui.theme.CyberHomeColors
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R

/**
 * Port of `lib/pages/induction_settings_page.dart` — unified induction /
 * proximity unlock settings (QGJ / TLink / RSSI).
 *
 * The Dart `StatefulWidget` subscribes to `inductionModeService.snapshotStream`
 * and `manualModeService.enabledStream`, mirrors the latest snapshot into UI
 * state, and drives `setEnabled` / `setDistance` / `refresh` from button +
 * slider callbacks. Here the same flow is reproduced with `StateFlow`
 * collection (`collectAsState`) and a coroutine scope.
 *
 * Service access: [InductionModeService] needs a [ConnectionManager] (Android
 * BLE wrapper) and an [com.tailg.plus.service.InductionPrefs]; both are
 * constructable from the current [android.content.Context]. [OfficialCloudService]
 * is injected from the navigation host and supplies the
 * selected vehicle for bindVehicle. [AppPermissionService] is used only on the
 * RSSI path to request notification permission.
 *
 * Token mapping (Dart → Compose): `CyberHomeColors.*` 1:1; `AppRadii.tile` for
 * card radius; `Lucide.sensors/pointer/bluetooth/alertCircle/refresh` for
 * icons. The Dart `SegmentedButton` is rendered as two side-by-side
 * [AppPressable] segments (Compose M3 `SegmentedButton` is experimental and
 * its API differs; a manual pair keeps the visual contract without opting into
 * experimental APIs).
 */
@Composable
fun InductionSettingsScreen(
  vehicleId: String,
  onBack: () -> Unit,
  cloudService: OfficialCloudService,
  connectionManager: ConnectionManager? = null,
) {
  val context = LocalContext.current
  val cloudService = cloudService
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  val strInductionSyncing = stringResource(R.string.induction_syncing)
  val strInductionNotLoggedIn = stringResource(R.string.induction_not_logged_in)
  val strInductionManualConfirm = stringResource(R.string.induction_manual_confirm)
  val strPairingHint = stringResource(R.string.induction_pairing_hint)
  val strNotifPerm = stringResource(R.string.induction_notification_perm)
  val strEnabledPairing = stringResource(R.string.induction_enabled_pairing)
  val strInductionStatusRefreshed = stringResource(R.string.induction_status_refreshed)
  val strInductionDistanceFailed = stringResource(R.string.induction_distance_failed)
  val strInductionDistanceUpdated = stringResource(R.string.induction_distance_updated)
  val strInductionNeedBleRead = stringResource(R.string.induction_need_ble_read)
  val strInductionDisableFailed = stringResource(R.string.induction_disable_failed)
  val strManualSwitched = stringResource(R.string.induction_manual_switched)
  val strUnsupported = stringResource(R.string.induction_unsupported)
  val strIdentifyAfterBle = stringResource(R.string.induction_identify_after_ble)
  val strEnableBleFirst = stringResource(R.string.induction_enable_ble_first)
  val strEnableFailed = stringResource(R.string.induction_enable_failed)
  val strInductionEnabled = stringResource(R.string.induction_enabled)

  // Shared Hilt singleton when injected; a private instance would always be
  // DISCONNECTED and permanently report stringResource(R.string.induction_ble_required).
  val connectionManager = connectionManager ?: remember { ConnectionManager(context) }
  val prefs = remember { DataStoreInductionPrefs(context) }
  val manualModeService = remember { ManualModeService(prefs) }
  val inductionService = remember {
    InductionModeService(
      cm = connectionManager,
      context = context,
      manual = manualModeService,
      cloud = cloudService,
      prefs = prefs,
    )
  }
  val permissionService = remember { AppPermissionService(context) }

  // The mode service owns a coroutine scope + connection collector.
  DisposableEffect(inductionService) {
    onDispose { inductionService.dispose() }
  }

  val snapshot by inductionService.snapshotFlow.collectAsState()
  val manualEnabled by manualModeService.enabledFlow.collectAsState()

  var busy by remember { mutableStateOf(false) }
  var distanceDraft by remember {
    mutableFloatStateOf(InductionModeService.DEFAULT_DISTANCE_LEVEL.toFloat())
  }

  // Dart `initState`: bind vehicle, seed snapshot, init manual mode, refresh.
  LaunchedEffect(Unit) {
    val vehicle = cloudService.currentState.selectedVehicle
    inductionService.bindVehicle(
      modelType = vehicle?.modelType,
      carId = vehicle?.carId,
      vehicleRaw = vehicle?.raw,
    )
    manualModeService.init()
    inductionService.refresh(force = true)
  }

  // Keep the distance draft in sync when the snapshot reports a new distance.
  LaunchedEffect(snapshot.distance) {
    val d = snapshot.distance
    if (d != null) {
      distanceDraft = d.toFloat()
    }
  }

  val supportsInduction = snapshot.stack != InductionStack.NONE
  val showDistanceSlider =
    snapshot.stack == InductionStack.QGJ || snapshot.stack == InductionStack.TLINK
  val maxDistanceLevel = if (snapshot.stack == InductionStack.QGJ) 10
    else InductionModeService.MAX_DISTANCE_LEVEL
  val canWrite = snapshot.bleReady && supportsInduction
  val anyBusy = busy || snapshot.busy

  // true = induction, false = manual, null = unknown / reading.
  val unlockSelection: Boolean? = when {
    manualEnabled -> false
    !supportsInduction -> false
    else -> snapshot.unlockSelection
  }

  val helpText = helpTextFor(snapshot.stack)
  val statusLine = unlockStatusLine(
    snapshot = snapshot,
    supportsInduction = supportsInduction,
    manualEnabled = manualEnabled,
    unlockSelection = unlockSelection,
  )

  Scaffold(
    containerColor = CyberHomeColors.pageBg,
    snackbarHost = { AppSnackbarHost(snackbarHostState) },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
    ) {
      InductionHeader(
        busy = anyBusy,
        onBack = onBack,
        onRefresh = {
          if (busy) return@InductionHeader
          scope.launch {
            busy = true
            inductionService.refresh(force = true)
            busy = false
            val err = inductionService.snapshot.lastError
            if (err != null) {
              AppSnack.error(snackbarHostState, err)
            } else {
              AppSnack.success(snackbarHostState, strInductionStatusRefreshed)
            }
          }
        },
      )
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .weight(1f),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
          start = 20.dp,
          end = 20.dp,
          top = 12.dp,
          bottom = 32.dp,
        ),
      ) {
        item {
          SectionLabel(stringResource(R.string.induction_current_capability))
        }
        item {
          CapabilityCard(
            stack = snapshot.stack,
            helpText = helpText,
            statusLine = statusLine,
            bondIncomplete = snapshot.bondIncomplete,
          )
        }
        if (!snapshot.bleReady && supportsInduction) {
          item {
            Spacer(Modifier.height(10.dp))
            ConnectionNotice(
              protocolLoggedIn = connectionManager.isProtocolLoggedIn,
            )
          }
        }
        item {
          Spacer(Modifier.height(22.dp))
          SectionLabel(stringResource(R.string.induction_unlock_mode))
        }
        item {
          UnlockModeCard(
            selection = unlockSelection,
            supportsInduction = supportsInduction,
            anyBusy = anyBusy,
            showDistanceSlider = showDistanceSlider,
            canWrite = canWrite,
            distanceDraft = distanceDraft,
            maxDistanceLevel = maxDistanceLevel,
            onSelect = { induction ->
              scope.launch {
                selectUnlockMode(
                  induction = induction,
                  busy = busy,
                  snapshot = snapshot,
                  supportsInduction = supportsInduction,
                  manualEnabled = manualEnabled,
                  inductionService = inductionService,
                  manualModeService = manualModeService,
                  permissionService = permissionService,
                  hostActivity = context as? androidx.activity.ComponentActivity,
                  snackbarHostState = snackbarHostState,
                  setBusy = { busy = it },
                  strNeedBleRead = strInductionNeedBleRead,
                  strManualConfirm = strInductionManualConfirm,
                  strDisableFailed = strInductionDisableFailed,
                  strManualSwitched = strManualSwitched,
                  strUnsupported = strUnsupported,
                  strIdentifyAfterBle = strIdentifyAfterBle,
                  strEnableBleFirst = strEnableBleFirst,
                  strEnableFailed = strEnableFailed,
                  strInductionEnabled = strInductionEnabled,
                  strPairingHint = strPairingHint,
                  strNotifPerm = strNotifPerm,
                  strEnabledPairing = strEnabledPairing,
                )
              }
            },
            onDistanceChange = { value -> distanceDraft = value },
            onDistanceCommit = { level ->
              scope.launch {
                if (busy) return@launch
                busy = true
                val ok = inductionService.setDistance(level)
                busy = false
                if (!ok) {
                  AppSnack.error(snackbarHostState, inductionService.snapshot.lastError ?: strInductionDistanceFailed)
                } else {
                  AppSnack.success(snackbarHostState, strInductionDistanceUpdated)
                }
              }
            },
          )
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

/** Dart `_InductionHeader` — back circle + title + refresh action. */
@Composable
private fun InductionHeader(
  busy: Boolean,
  onBack: () -> Unit,
  onRefresh: () -> Unit,
) {
  Row(
    modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AppPressable(
      onClick = onBack,
      shape = CircleShape,
      background = CyberHomeColors.card,
      shadowElevation = 4.dp,
      shadowColor = CyberHomeColors.actionShadow,
      semanticsLabel = stringResource(R.string.common_back),
    ) {
      Box(
        modifier = Modifier.size(AppTouchTargets.min),
        contentAlignment = Alignment.Center,
      ) {
        LucideIcon(icon = Lucide.arrowLeft, size = 20.dp, color = CyberHomeColors.ink)
      }
    }
    Spacer(Modifier.width(12.dp))
    Text(
      text = stringResource(R.string.induction_title),
      modifier = Modifier.weight(1f),
      maxLines = 1,
      overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
      style = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.W700,
        color = CyberHomeColors.ink,
      ),
    )
    AppPressable(
      onClick = if (busy) null else onRefresh,
      enabled = !busy,
      shape = CircleShape,
      semanticsLabel = stringResource(R.string.induction_refresh),
    ) {
      Box(
        modifier = Modifier.size(AppTouchTargets.min),
        contentAlignment = Alignment.Center,
      ) {
        if (busy) {
          CircularProgressIndicator(
            color = CyberHomeColors.primary,
            strokeWidth = 1.8.dp,
            modifier = Modifier.size(18.dp),
          )
        } else {
          LucideIcon(icon = Lucide.refresh, size = 20.dp, color = CyberHomeColors.inkSecondary)
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Section label
// ---------------------------------------------------------------------------

/** Dart `_SectionLabel`. */
@Composable
private fun SectionLabel(label: String) {
  Text(
    text = label,
    modifier = Modifier.padding(start = 2.dp, bottom = 9.dp),
    style = TextStyle(
      fontSize = 12.sp,
      fontWeight = FontWeight.W700,
      color = CyberHomeColors.inkMuted,
    ),
  )
}

// ---------------------------------------------------------------------------
// Capability card
// ---------------------------------------------------------------------------

/** Dart `_CapabilityCard`. */
@Composable
private fun CapabilityCard(
  stack: InductionStack,
  helpText: String,
  statusLine: String,
  bondIncomplete: Boolean,
) {
  val title = when (stack) {
    InductionStack.QGJ, InductionStack.TLINK -> stringResource(R.string.induction_vehicle_mode)
    InductionStack.RSSI -> stringResource(R.string.induction_ble_signal_mode)
    InductionStack.NONE -> stringResource(R.string.induction_manual_mode)
  }
  val icon = when (stack) {
    InductionStack.QGJ, InductionStack.TLINK -> Lucide.sensors
    InductionStack.RSSI -> Lucide.bluetooth
    InductionStack.NONE -> Lucide.pointer
  }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(16.dp),
  ) {
    Row(verticalAlignment = Alignment.Top) {
      Box(
        modifier = Modifier
          .size(42.dp)
          .clip(CircleShape)
          .background(CyberHomeColors.primarySoft),
        contentAlignment = Alignment.Center,
      ) {
        LucideIcon(icon = icon, size = 21.dp, color = CyberHomeColors.primary)
      }
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = TextStyle(
            fontSize = 16.sp,
            fontWeight = FontWeight.W700,
            color = CyberHomeColors.ink,
          ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
          text = statusLine,
          style = TextStyle(
            fontSize = 12.sp,
            lineHeight = 12.sp * 1.4f,
            color = if (bondIncomplete) CyberHomeColors.warning else CyberHomeColors.inkMuted,
          ),
        )
      }
    }
    Spacer(Modifier.height(14.dp))
    HorizontalDivider(thickness = 1.dp, color = CyberHomeColors.line)
    Spacer(Modifier.height(12.dp))
    Text(
      text = helpText,
      style = TextStyle(
        fontSize = 12.sp,
        lineHeight = 12.sp * 1.55f,
        color = CyberHomeColors.inkMuted,
      ),
    )
  }
}

// ---------------------------------------------------------------------------
// Connection notice
// ---------------------------------------------------------------------------

/** Dart `_ConnectionNotice`. */
@Composable
private fun ConnectionNotice(protocolLoggedIn: Boolean) {
  val strInductionSyncing = stringResource(R.string.induction_syncing)
  val strInductionNotLoggedIn = stringResource(R.string.induction_not_logged_in)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.warning.copy(alpha = 0.08f))
      .border(1.dp, CyberHomeColors.warning.copy(alpha = 0.2f), RoundedCornerShape(AppRadii.tile))
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.Top,
  ) {
    LucideIcon(icon = Lucide.alertCircle, size = 18.dp, color = CyberHomeColors.warning)
    Spacer(Modifier.width(9.dp))
    Text(
      text = if (protocolLoggedIn) strInductionSyncing
        else strInductionNotLoggedIn,
      modifier = Modifier.weight(1f),
      style = TextStyle(
        fontSize = 12.sp,
        lineHeight = 12.sp * 1.45f,
        color = CyberHomeColors.inkMuted,
      ),
    )
  }
}

// ---------------------------------------------------------------------------
// Unlock mode card (segmented button + distance slider)
// ---------------------------------------------------------------------------

/** Dart unlock-mode card with `SegmentedButton` + distance slider. */
@Composable
private fun UnlockModeCard(
  selection: Boolean?,
  supportsInduction: Boolean,
  anyBusy: Boolean,
  showDistanceSlider: Boolean,
  canWrite: Boolean,
  distanceDraft: Float,
  maxDistanceLevel: Int,
  onSelect: (Boolean) -> Unit,
  onDistanceChange: (Float) -> Unit,
  onDistanceCommit: (Int) -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(16.dp),
  ) {
    Row(modifier = Modifier.fillMaxWidth()) {
      SegmentButton(
        label = stringResource(R.string.induction_sensor),
        icon = Lucide.sensors,
        selected = selection == true,
        enabled = supportsInduction && !anyBusy,
        modifier = Modifier.weight(1f),
        onClick = { onSelect(true) },
      )
      Spacer(Modifier.width(8.dp))
      SegmentButton(
        label = stringResource(R.string.induction_manual),
        icon = Lucide.pointer,
        selected = selection == false || (selection == null && !supportsInduction),
        enabled = !anyBusy,
        modifier = Modifier.weight(1f),
        onClick = { onSelect(false) },
      )
    }
    if (showDistanceSlider && selection == true) {
      Spacer(Modifier.height(18.dp))
      HorizontalDivider(thickness = 1.dp, color = CyberHomeColors.line)
      Spacer(Modifier.height(16.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = stringResource(R.string.induction_distance),
          modifier = Modifier.weight(1f),
          style = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.W700,
            color = CyberHomeColors.ink,
          ),
        )
        DistanceBadge(level = distanceDraft.toInt())
      }
      Spacer(Modifier.height(4.dp))
      Text(
        text = stringResource(R.string.induction_distance_desc),
        style = TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
      )
      Spacer(Modifier.height(6.dp))
      val clamped = distanceDraft.coerceIn(0f, maxDistanceLevel.toFloat())
      Slider(
        value = clamped,
        onValueChange = onDistanceChange,
        enabled = !anyBusy && canWrite,
        valueRange = 0f..maxDistanceLevel.toFloat(),
        steps = if (maxDistanceLevel > 0) maxDistanceLevel - 1 else 0,
        colors = SliderDefaults.colors(
          thumbColor = CyberHomeColors.primary,
          activeTrackColor = CyberHomeColors.primary,
          inactiveTrackColor = CyberHomeColors.controlStrong,
        ),
        onValueChangeFinished = {
          if (!anyBusy && canWrite) {
            onDistanceCommit(clamped.toInt())
          }
        },
      )
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = stringResource(R.string.induction_near),
          style = TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkFaint),
        )
        Text(
          text = stringResource(R.string.induction_far),
          style = TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkFaint),
        )
      }
    }
  }
}

/** One segment of the Dart `SegmentedButton` pair. */
@Composable
private fun SegmentButton(
  label: String,
  icon: ImageVector,
  selected: Boolean,
  enabled: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val bg = if (selected) CyberHomeColors.primary else CyberHomeColors.control
  val fg = if (selected) CyberHomeColors.white else CyberHomeColors.inkMuted
  val border = if (selected) CyberHomeColors.primary else CyberHomeColors.line
  AppPressable(
    onClick = if (enabled) onClick else null,
    enabled = enabled,
    modifier = modifier,
    shape = RoundedCornerShape(AppRadii.xs),
    background = bg,
    borderWidth = 1.dp,
    borderColor = border,
    semanticsLabel = label,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(44.dp)
        .padding(horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      LucideIcon(icon = icon, size = 16.dp, color = fg)
      Spacer(Modifier.width(6.dp))
      Text(
        text = label,
        style = TextStyle(
          fontSize = 13.sp,
          fontWeight = FontWeight.W600,
          color = fg,
        ),
      )
    }
  }
}

/** Dart `_DistanceBadge`. */
@Composable
private fun DistanceBadge(level: Int) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(AppRadii.pill))
      .background(CyberHomeColors.primarySoft)
      .padding(horizontal = 9.dp, vertical = 5.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = stringResource(R.string.induction_level_format, level),
      style = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.W700,
        color = CyberHomeColors.primary,
      ),
    )
  }
}

// ---------------------------------------------------------------------------
// Business logic helpers (port of the Dart `_selectUnlockMode` / `_setDistance`
// / `_read` methods, lifted out of the composable for testability)
// ---------------------------------------------------------------------------

/**
 * Port of Dart `_selectUnlockMode`.
 *
 * The activity is required only on the RSSI path to request notification
 * permission; it is resolved from the current context at call time.
 */
private suspend fun selectUnlockMode(
  induction: Boolean,
  busy: Boolean,
  snapshot: InductionModeSnapshot,
  supportsInduction: Boolean,
  manualEnabled: Boolean,
  inductionService: InductionModeService,
  manualModeService: ManualModeService,
  permissionService: AppPermissionService,
  hostActivity: androidx.activity.ComponentActivity?,
  snackbarHostState: SnackbarHostState,
  setBusy: (Boolean) -> Unit,
  strNeedBleRead: String,
  strManualConfirm: String,
  strDisableFailed: String,
  strManualSwitched: String,
  strUnsupported: String,
  strIdentifyAfterBle: String,
  strEnableBleFirst: String,
  strEnableFailed: String,
  strInductionEnabled: String,
  strPairingHint: String,
  strNotifPerm: String,
  strEnabledPairing: String,
) {
  if (busy || snapshot.busy) return

  if (!induction) {
    // Switch to manual mode.
    if (manualEnabled && snapshot.enabled != true) return
    val vehicleManaged =
      snapshot.stack == InductionStack.QGJ || snapshot.stack == InductionStack.TLINK
    if (vehicleManaged && snapshot.enabled == null) {
      AppSnack.info(snackbarHostState, strNeedBleRead)
      return
    }
    if (vehicleManaged && snapshot.enabled == true && !snapshot.bleReady) {
      AppSnack.info(snackbarHostState, strManualConfirm)
      return
    }
    setBusy(true)
    if (snapshot.enabled == true) {
      val closed = inductionService.setEnabled(false)
      if (!closed) {
        setBusy(false)
        AppSnack.error(snackbarHostState, inductionService.snapshot.lastError ?: strDisableFailed)
        return
      }
    }
    manualModeService.setEnabled(true)
    setBusy(false)
    val err = inductionService.snapshot.lastError
    if (err != null) {
      AppSnack.info(snackbarHostState, err)
    } else {
      AppSnack.success(snackbarHostState, strManualSwitched)
    }
    return
  }

  // Switch to induction mode.
  if (!supportsInduction) {
    AppSnack.info(
      snackbarHostState,
      if (snapshot.bleReady) strUnsupported else strIdentifyAfterBle,
    )
    return
  }
  if (!snapshot.bleReady) {
    AppSnack.info(snackbarHostState, strEnableBleFirst)
    return
  }
  if (snapshot.enabled == true && !manualEnabled) {
    if (snapshot.bondIncomplete) {
      AppSnack.info(snackbarHostState, strPairingHint)
    }
    return
  }

  if (snapshot.stack == InductionStack.RSSI) {
    // Dart gates the RSSI stack on POST_NOTIFICATIONS (Android 13+): the
    // foreground-service notification is how the user sees/kills induction.
    // Soft gate — a denial warns but still allows the (silent) FGS to run.
    if (hostActivity != null) {
      val notif = permissionService.requestNotificationPermission(hostActivity, request = true)
      if (!notif.granted) {
        AppSnack.info(
          snackbarHostState,
          notif.message ?: strNotifPerm,
        )
      }
    }
  }

  setBusy(true)
  val ok = inductionService.setEnabled(true, clearManualMode = true)
  setBusy(false)
  if (!ok) {
    AppSnack.error(snackbarHostState, inductionService.snapshot.lastError ?: strEnableFailed)
    return
  }
  val err = inductionService.snapshot.lastError
  when {
    err != null -> AppSnack.info(snackbarHostState, err)
    inductionService.snapshot.bondIncomplete ->
      AppSnack.info(snackbarHostState, inductionService.snapshot.lastError ?: strEnabledPairing)
    else -> AppSnack.success(snackbarHostState, strInductionEnabled)
  }
}

/** Dart `_helpText` getter. */
@Composable
private fun helpTextFor(stack: InductionStack): String = when (stack) {
  InductionStack.QGJ, InductionStack.TLINK ->
    stringResource(R.string.induction_desc_1) +
      stringResource(R.string.induction_desc_2) +
      stringResource(R.string.induction_desc_3)
  InductionStack.RSSI ->
    stringResource(R.string.induction_desc_4) +
      stringResource(R.string.induction_desc_5) +
      stringResource(R.string.induction_desc_6)
  InductionStack.NONE ->
    stringResource(R.string.induction_unsupported_desc)
}

/** Dart `_unlockStatusLine` getter. */
@Composable
private fun unlockStatusLine(
  snapshot: InductionModeSnapshot,
  supportsInduction: Boolean,
  manualEnabled: Boolean,
  unlockSelection: Boolean?,
): String {
  if (!supportsInduction) {
    return if (snapshot.bleReady) stringResource(R.string.induction_manual_only) else stringResource(R.string.induction_identify_after_ble)
  }
  if (unlockSelection == null) {
    return if (snapshot.bleReady) stringResource(R.string.induction_reading_mode) else stringResource(R.string.induction_connect_ble_hint)
  }
  if (unlockSelection == false) {
    return stringResource(R.string.induction_manual_on_desc)
  }
  if (!snapshot.bleReady) return stringResource(R.string.induction_enable_ble_hint)
  if (snapshot.bondIncomplete) {
    return stringResource(R.string.induction_on_pairing_hint)
  }
  val dist = snapshot.distance?.let { stringResource(R.string.induction_distance_suffix, it) } ?: ""
  return stringResource(R.string.induction_on_desc, dist)
}
