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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.data.cloud.OfficialCloudMessages
import com.tailg.plus.data.cloud.OfficialCloudRedactor
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.cloud.OfficialCloudState
import com.tailg.plus.data.cloud.resolveVehicleLocation
import com.tailg.plus.data.cloud.ResolvedVehicleLocation
import com.tailg.plus.data.model.OfficialFenceData
import com.tailg.plus.data.model.OfficialTravelDay
import com.tailg.plus.data.model.OfficialTravelRecord
import com.tailg.plus.data.model.formatCoordinateText
import com.tailg.plus.data.model.sumTravelMileageKm
import com.tailg.plus.data.model.sumTravelDurationSeconds
import com.tailg.plus.data.model.formatCompactDuration
import com.tailg.plus.data.model.googleMapsSearchUri
import com.tailg.plus.data.store.VehicleStore
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.components.AppSnackbarHost
import com.tailg.plus.ui.components.AppSnack
import com.tailg.plus.ui.components.CyberMapView
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.components.ScaleToFit
import com.tailg.plus.ui.theme.AppIconSizes
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppTouchTargets
import com.tailg.plus.ui.theme.CyberHomeColors
import com.tailg.plus.util.formatCompactDecimal
import com.tailg.plus.util.formatDistanceMeters
import com.tailg.plus.util.formatTravelMileageMetersText
import com.tailg.plus.util.parseTravelMileageMeters
import com.tailg.plus.util.travelMetersToKm
import com.tailg.plus.util.formatDateText
import com.tailg.plus.util.formatDateMinuteText
import com.tailg.plus.util.normalizeOfficialDateKey
import com.tailg.plus.util.parseMonthText
import com.tailg.plus.util.shiftMonthDate
import com.tailg.plus.util.formatHourMinuteText
import com.tailg.plus.util.formatDecimalDown
import com.tailg.plus.util.ClipboardText
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R

internal enum class LocationTab { MAP, TRAVEL, FENCE }

/** Dart `LocationInitialTab` — service-hub entries deep-link into a tab. */
enum class LocationInitialTab { MAP, TRAVEL, FENCE }

/**
 * Port of `lib/pages/location_page.dart` (+ location_map_tab.dart,
 * location_travel_tab.dart, location_fence_tab.dart) — single file with tabs.
 *
 * Maps render through the shared osmdroid composable [CyberMapView]
 * (Dart `_MapPanel` equivalent); tiles come from `MapTileConfig`.
 */
@Composable
fun LocationScreen(
  cloudService: OfficialCloudService,
  vehicleStore: VehicleStore,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  initialTab: LocationInitialTab = LocationInitialTab.MAP,
) {
  val scope = rememberCoroutineScope()
  val entryPoint = com.tailg.plus.di.rememberTailgEntryPoint()
  val log = entryPoint.logService()
  val cloudState by cloudService.stateFlow.collectAsStateWithLifecycle()
  val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
  val ctx = androidx.compose.ui.platform.LocalContext.current
  val clipboard = entryPoint.clipboardText()

  var tabIndex by remember { mutableStateOf(LocationTab.valueOf(initialTab.name)) }
  var localLoading by remember { mutableStateOf(false) }
  var localError by remember { mutableStateOf<String?>(null) }

  val strLocationRefreshFailed = stringResource(R.string.location_refresh_failed)
  val strSyncData = stringResource(R.string.location_sync_data)
  val strDataSynced = stringResource(R.string.location_data_synced)
  val strSyncedNoCoords = stringResource(R.string.location_synced_no_coords)
  val strSyncTravel = stringResource(R.string.location_sync_travel)
  val strSyncedNoTravel = stringResource(R.string.location_synced_no_travel)
  val strTravelSyncedFormat = stringResource(R.string.location_travel_synced_format)
  val strTravelRefreshFailed = stringResource(R.string.location_travel_refresh_failed)
  val strSyncFence = stringResource(R.string.location_sync_fence)
  val strFenceSyncedFormat = stringResource(R.string.location_fence_synced_format)
  val strSyncedNoFence = stringResource(R.string.location_synced_no_fence)
  val strFenceRefreshFailed = stringResource(R.string.location_fence_refresh_failed)
  val strCoordsCopied = stringResource(R.string.location_coordinates_copied)
  val strLocationTitle = stringResource(R.string.location_title)
  val strNoMapApp = stringResource(R.string.location_no_map_app)
  val strReadTravel = stringResource(R.string.location_read_travel)
  val strTravelMissingId = stringResource(R.string.location_travel_missing_id)
  val strTravelLoadedFormat = stringResource(R.string.location_travel_loaded_format)
  val strTravelNoPoints = stringResource(R.string.location_travel_no_points)
  val strTravelDetailFailed = stringResource(R.string.location_travel_detail_failed)
  val localVehicle = vehicleStore.defaultVehicle
  val cloudVehicle = if (cloudState.signedIn) cloudState.selectedVehicle else null
  val location = remember(cloudState, localVehicle) {
    resolveVehicleLocation(cloudState = cloudState, localVehicle = localVehicle)
  }
  val loading = localLoading || cloudState.loading || cloudState.vehicleLocationLoading ||
    cloudState.travelLoading || cloudState.fenceLoading

  val title = when (tabIndex) {
    LocationTab.MAP -> stringResource(R.string.location_tab_map)
    LocationTab.TRAVEL -> stringResource(R.string.location_travel_title)
    LocationTab.FENCE -> stringResource(R.string.location_fence_title)
  }

  LaunchedEffect(Unit) {
    if (cloudService.currentState.signedIn) {
      try {
        cloudService.refreshVehicles(silent = true, refreshReplicaDetails = false)
        cloudService.refreshVehicleLocation(silent = true)
        cloudService.refreshFenceData(silent = true)
        cloudService.refreshTravelHistory(silent = true)
      } catch (e: Exception) {
        log.operation(strLocationRefreshFailed, detail = e.toString(), level = LogLevel.WARNING)
      }
    }
  }

  fun refreshOfficial(silent: Boolean = false) {
    if (!cloudService.currentState.signedIn) {
      if (!silent) {
        scope.launch { AppSnack.error(snackbarHostState, OfficialCloudMessages.signInRequiredBefore(strSyncData)) }
      }
      return
    }
    scope.launch {
      try {
        cloudService.refreshVehicles(silent = silent, refreshReplicaDetails = false)
        cloudService.refreshVehicleLocation(silent = silent)
        cloudService.refreshFenceData(silent = silent)
        cloudService.refreshTravelHistory(silent = silent)
        if (!silent) {
          val hasLocation = resolveVehicleLocation(cloudState = cloudService.currentState, localVehicle = localVehicle) != null
          AppSnack.info(snackbarHostState, if (hasLocation) strDataSynced else strSyncedNoCoords)
        }
      } catch (e: Exception) {
        log.operation(strLocationRefreshFailed, detail = e.toString(), level = LogLevel.WARNING)
        if (!silent) {
          val message = OfficialCloudRedactor.errorMessage(e)
          localError = message
          AppSnack.error(snackbarHostState, message)
        }
      }
    }
  }

  fun refreshTravelHistory(month: String? = null) {
    if (!cloudService.currentState.signedIn) {
      scope.launch { AppSnack.error(snackbarHostState, OfficialCloudMessages.signInRequiredBefore(strSyncTravel)) }
      return
    }
    scope.launch {
      try {
        cloudService.refreshTravelHistory(month = month, force = true)
        val days = cloudService.currentState.travelDays
        val count = days.sumOf { it.records.size }
        AppSnack.info(snackbarHostState, if (count == 0) strSyncedNoTravel else strTravelSyncedFormat.format(count))
      } catch (e: Exception) {
        log.operation(strTravelRefreshFailed, detail = e.toString(), level = LogLevel.WARNING)
        AppSnack.error(snackbarHostState, OfficialCloudRedactor.errorMessage(e))
      }
    }
  }

  fun changeTravelMonth(delta: Int) {
    val current = parseMonthText(cloudService.currentState.travelMonth) ?: LocalDateTime.now()
    val nextMonth = shiftMonthDate(current, delta)
    if (nextMonth != null) refreshTravelHistory(month = nextMonth)
  }

  fun refreshFenceData() {
    if (!cloudService.currentState.signedIn) {
      scope.launch { AppSnack.error(snackbarHostState, OfficialCloudMessages.signInRequiredBefore(strSyncFence)) }
      return
    }
    scope.launch {
      try {
        cloudService.refreshFenceData(force = true)
        val fence = cloudService.currentState.fenceData
        if (fence?.hasData == true) {
          AppSnack.info(snackbarHostState, strFenceSyncedFormat.format(fence.statusLabel, fence.radiusLabel))
        } else {
          AppSnack.info(snackbarHostState, strSyncedNoFence)
        }
      } catch (e: Exception) {
        log.operation(strFenceRefreshFailed, detail = e.toString(), level = LogLevel.WARNING)
        AppSnack.error(snackbarHostState, OfficialCloudRedactor.errorMessage(e))
      }
    }
  }

  fun refreshAll() {
    if (localVehicle != null && tabIndex == LocationTab.MAP) {
      // Local location refresh would go here; for now just refresh official.
    }
    refreshOfficial()
  }

  fun copyLocation(loc: ResolvedVehicleLocation) {
    clipboard.writeClipboardText(loc.coordinateText)
    scope.launch { AppSnack.info(snackbarHostState, strCoordsCopied) }
  }

  fun openMap(loc: ResolvedVehicleLocation) {
    val lat = loc.latitude ?: return
    val lng = loc.longitude ?: return
    val label = android.net.Uri.encode(loc.address.ifEmpty { strLocationTitle })
    val geoUri = android.net.Uri.parse("geo:$lat,$lng?q=$lat,$lng($label)")
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, geoUri)
    try {
      ctx.startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
      clipboard.writeClipboardText(googleMapsSearchUri(lat, lng).toString())
      scope.launch { AppSnack.info(snackbarHostState, strNoMapApp) }
    }
  }

  fun loadTravelDetail(record: OfficialTravelRecord) {
    if (!cloudService.currentState.signedIn) {
      scope.launch { AppSnack.error(snackbarHostState, OfficialCloudMessages.signInRequiredBefore(strReadTravel)) }
      return
    }
    val travelId = record.deviceTravelId.trim()
    if (travelId.isEmpty()) {
      scope.launch { AppSnack.info(snackbarHostState, strTravelMissingId) }
      return
    }
    scope.launch {
      try {
        cloudService.refreshTravelDetail(travelId)
        val points = cloudService.currentState.travelDetails[travelId]?.size ?: 0
        if (points >= 2) {
          AppSnack.success(snackbarHostState, strTravelLoadedFormat.format(points))
        } else {
          AppSnack.info(snackbarHostState, strTravelNoPoints)
        }
      } catch (e: Exception) {
        log.operation(strTravelDetailFailed, detail = e.toString(), level = LogLevel.WARNING)
        AppSnack.error(snackbarHostState, OfficialCloudRedactor.errorMessage(e))
      }
    }
  }

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
      if (tabIndex != LocationTab.FENCE) {
        LocationHeader(
          title = title,
          showBack = true,
          loading = loading,
          onBack = onBack,
          onRefresh = { if (!loading) refreshAll() },
        )
        LocationSegmentedTabs(index = tabIndex.ordinal, onChanged = { tabIndex = LocationTab.entries[it] })
      }
      when (tabIndex) {
        LocationTab.MAP -> MapTab(
          vehicleName = localVehicle?.displayName ?: cloudVehicle?.displayName,
          location = location,
          cloudState = cloudState,
          error = localError ?: cloudState.vehicleLocationError,
          loading = loading,
          onRefresh = { refreshAll() },
          onCopy = if (location != null) { { copyLocation(location) } } else null,
          onOpenMap = if (location != null) { { openMap(location) } } else null,
          modifier = Modifier.weight(1f),
        )
        LocationTab.TRAVEL -> TravelTab(
          cloudState = cloudState,
          onRefresh = { refreshTravelHistory() },
          onChangeMonth = { changeTravelMonth(it) },
          onRecordTap = { loadTravelDetail(it) },
          modifier = Modifier.weight(1f),
        )
        LocationTab.FENCE -> FenceTab(
          cloudState = cloudState,
          location = location,
          onRefresh = { refreshFenceData() },
          onTabChanged = { tabIndex = LocationTab.entries[it] },
          scope = scope,
          cloudService = cloudService,
          snackbarHostState = snackbarHostState,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun LocationHeader(
  title: String,
  showBack: Boolean,
  loading: Boolean,
  onBack: () -> Unit,
  onRefresh: () -> Unit,
) {
  Row(
    modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (showBack) {
      AppPressable(
        onClick = onBack,
        shape = CircleShape,
        background = CyberHomeColors.card,
        shadowElevation = 4.dp,
        shadowColor = CyberHomeColors.actionShadow,
        semanticsLabel = stringResource(R.string.common_back),
      ) {
        Box(modifier = Modifier.size(AppTouchTargets.min), contentAlignment = Alignment.Center) {
          LucideIcon(icon = Lucide.arrowLeft, size = 20.dp, color = CyberHomeColors.inkSecondary)
        }
      }
      Spacer(Modifier.width(12.dp))
    }
    Text(
      text = title,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      modifier = Modifier.weight(1f),
    )
    AppPressable(
      onClick = { if (!loading) onRefresh() },
      enabled = !loading,
      shape = CircleShape,
      semanticsLabel = stringResource(R.string.location_refresh),
    ) {
      Box(modifier = Modifier.size(AppTouchTargets.min), contentAlignment = Alignment.Center) {
        if (loading) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 1.8.dp, color = CyberHomeColors.primary)
        } else {
          LucideIcon(icon = Lucide.refresh, size = 20.dp, color = CyberHomeColors.inkSecondary)
        }
      }
    }
  }
}

@Composable
internal fun LocationSegmentedTabs(index: Int, onChanged: (Int) -> Unit) {
  val tabs = listOf(Triple(Lucide.mapPin, stringResource(R.string.location_tab_label_position), 0), Triple(Lucide.route, stringResource(R.string.location_tab_label_travel), 1), Triple(Lucide.radar, stringResource(R.string.location_tab_label_fence), 2))
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 20.dp, top = 14.dp, end = 20.dp)
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.control)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(4.dp),
  ) {
    tabs.forEach { (icon, label, i) ->
      val active = index == i
      AppPressable(
        onClick = { onChanged(i) },
        shape = RoundedCornerShape(AppRadii.tile),
        background = if (active) CyberHomeColors.card else Color.Transparent,
        pressedBackground = if (active) CyberHomeColors.cardMuted else CyberHomeColors.controlStrong,
        semanticsLabel = label,
        modifier = Modifier.weight(1f),
      ) {
        Row(
          modifier = Modifier.height(AppTouchTargets.min),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          LucideIcon(icon = icon, size = AppIconSizes.sm, color = if (active) CyberHomeColors.ink else CyberHomeColors.inkMuted)
          Spacer(Modifier.width(5.dp))
          ScaleToFit(
            modifier = Modifier
              .weight(1f)
              .height(20.dp),
            contentAlignment = Alignment.CenterStart,
          ) {
            Text(
              text = label,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
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
  }
}

@Composable
private fun MapTab(
  vehicleName: String?,
  location: ResolvedVehicleLocation?,
  cloudState: OfficialCloudState,
  error: String?,
  loading: Boolean,
  onRefresh: () -> Unit,
  onCopy: (() -> Unit)?,
  onOpenMap: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 24.dp),
  ) {
    item {
      MiniMapPlaceholder(location = location, fence = cloudState.fenceData)
    }
    item { Spacer(Modifier.height(14.dp)) }
    item {
      LocationDetailCard(
        vehicleName = vehicleName,
        location = location,
        error = error,
        loading = loading,
        signedIn = cloudState.signedIn,
        onRefresh = onRefresh,
        onCopy = onCopy,
        onOpenMap = onOpenMap,
      )
    }
    item { Spacer(Modifier.height(14.dp)) }
    item {
      ReadOnlyNotice(title = stringResource(R.string.location_service), subtitle = stringResource(R.string.location_service_desc) + stringResource(R.string.location_refresh_hint))
    }
  }
}

@Composable
private fun MiniMapPlaceholder(location: ResolvedVehicleLocation?, fence: OfficialFenceData?) {
  val hasCoordinate = location != null && location.hasCoordinate
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(340.dp)
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.mapPlaceholder)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile)),
  ) {
    CyberMapView(
      latitude = if (hasCoordinate) location.latitude else null,
      longitude = if (hasCoordinate) location.longitude else null,
      modifier = Modifier.fillMaxSize(),
      fenceRadiusMeters = if (hasCoordinate) fence?.radiusMeters else null,
      fenceEnabled = fence?.enabled ?: true,
    )
    if (hasCoordinate) {
      Text(
        text = "%.5f, %.5f".format(location.latitude ?: 0.0, location.longitude ?: 0.0),
        style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = CyberHomeColors.inkFaint),
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = 34.dp)
          .background(CyberHomeColors.card.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
          .padding(horizontal = 6.dp, vertical = 2.dp),
      )
    }
    Row(
      modifier = Modifier
        .align(Alignment.BottomStart)
        .fillMaxWidth()
        .background(CyberHomeColors.card.copy(alpha = 0.92f))
        .padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LucideIcon(icon = Lucide.mapPin, size = 13.dp, color = CyberHomeColors.primary)
      Spacer(Modifier.width(5.dp))
      Text(
        text = location?.address?.ifEmpty { null } ?: location?.coordinateText ?: stringResource(R.string.location_no_data),
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkMuted),
      )
    }
  }
}

@Composable
private fun LocationDetailCard(
  vehicleName: String?,
  location: ResolvedVehicleLocation?,
  error: String?,
  loading: Boolean,
  signedIn: Boolean,
  onRefresh: () -> Unit,
  onCopy: (() -> Unit)?,
  onOpenMap: (() -> Unit)?,
) {
  val title = vehicleName ?: stringResource(R.string.location_no_vehicle)
  val addressText = if (location == null) {
    if (signedIn) stringResource(R.string.location_no_parking) else stringResource(R.string.location_login_required)
  } else {
    location.address.ifEmpty { location.coordinateText }
  }

  Column(
    modifier = Modifier
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(18.dp),
  ) {
    Row(verticalAlignment = Alignment.Top) {
      Box(
        modifier = Modifier
          .size(AppTouchTargets.min)
          .clip(RoundedCornerShape(AppRadii.tile))
          .background(CyberHomeColors.primarySoft),
        contentAlignment = Alignment.Center,
      ) {
        LucideIcon(icon = Lucide.mapPin, color = CyberHomeColors.primary, size = AppIconSizes.lg)
      }
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
        )
        Spacer(Modifier.height(3.dp))
        Text(
          text = addressText,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 13.sp * 1.4f, color = CyberHomeColors.inkMuted),
        )
        if (location != null) {
          Spacer(Modifier.height(8.dp))
          LocationStatusTag(source = location.source)
        }
      }
    }
    if (location != null) {
      Spacer(Modifier.height(16.dp))
      Row {
        LocationMetaBox(value = location.source, label = stringResource(R.string.location_source), modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        LocationMetaBox(
          value = if (location.timeLabel.isEmpty()) stringResource(R.string.location_pending) else location.timeLabel,
          label = stringResource(R.string.location_recent_update),
          modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        LocationMetaBox(
          value = if (location.accuracy > 0) "±${location.accuracy.toInt()}m" else "—",
          label = stringResource(R.string.location_accuracy),
          modifier = Modifier.weight(1f),
        )
      }
    }
    if (error != null) {
      Spacer(Modifier.height(12.dp))
      Text(text = error, style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.warning))
    }
    Spacer(Modifier.height(16.dp))
    Row {
      LocationActionButton(icon = Lucide.locate, label = stringResource(R.string.location_refresh), loading = loading, onTap = if (!loading) onRefresh else null, modifier = Modifier.weight(1f))
      Spacer(Modifier.width(10.dp))
      LocationActionButton(icon = Lucide.copy, label = stringResource(R.string.location_copy), onTap = onCopy, modifier = Modifier.weight(1f))
      Spacer(Modifier.width(10.dp))
      LocationActionButton(icon = Lucide.navigation, label = stringResource(R.string.location_navigate), primary = true, onTap = onOpenMap, modifier = Modifier.weight(1f))
    }
  }
}

@Composable
private fun LocationStatusTag(source: String) {
  Row(
    modifier = Modifier
      .clip(RoundedCornerShape(AppRadii.pill))
      .background(CyberHomeColors.primarySoft)
      .padding(horizontal = 10.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(CyberHomeColors.primary))
    Spacer(Modifier.width(6.dp))
    Text(
      text = source,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = androidx.compose.ui.text.TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.primary),
    )
  }
}

@Composable
private fun LocationMetaBox(value: String, label: String, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.cardMuted)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(horizontal = 12.dp, vertical = 12.dp),
  ) {
    Text(
      text = value,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W800, color = CyberHomeColors.ink),
    )
    Spacer(Modifier.height(4.dp))
    Text(
      text = label,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkFaint),
    )
  }
}

@Composable
private fun LocationActionButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  modifier: Modifier = Modifier,
  primary: Boolean = false,
  loading: Boolean = false,
  onTap: (() -> Unit)?,
) {
  val enabled = onTap != null
  val bg = if (primary) CyberHomeColors.primary else CyberHomeColors.cardMuted
  val fg = if (primary) CyberHomeColors.white else CyberHomeColors.ink
  Box(
    modifier = modifier
      .height(48.dp)
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(bg)
      .then(if (!primary) Modifier.border(1.dp, CyberHomeColors.lineStrong, RoundedCornerShape(AppRadii.tile)) else Modifier)
      .clickable(enabled = enabled) { onTap?.invoke() },
    contentAlignment = Alignment.Center,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (loading) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = fg)
      } else {
        LucideIcon(icon = icon, size = AppIconSizes.sm, color = fg)
      }
      Spacer(Modifier.width(7.dp))
      ScaleToFit(
        modifier = Modifier
          .weight(1f)
          .height(20.dp),
        contentAlignment = Alignment.CenterStart,
      ) {
        Text(
          text = label,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W700, color = fg),
        )
      }
    }
  }
}

@Composable
internal fun ReadOnlyNotice(title: String, subtitle: String) {
  Row(
    modifier = Modifier
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.primarySoft)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(16.dp),
    verticalAlignment = Alignment.Top,
  ) {
    LucideIcon(icon = Lucide.lock, color = CyberHomeColors.primary, size = AppIconSizes.sm)
    Spacer(Modifier.width(10.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      )
      Spacer(Modifier.height(4.dp))
      Text(
        text = subtitle,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
        style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 13.sp * 1.4f, color = CyberHomeColors.inkMuted),
      )
    }
  }
}
