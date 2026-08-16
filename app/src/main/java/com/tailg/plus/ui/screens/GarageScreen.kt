package com.tailg.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.data.cloud.OfficialCloudRedactor
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.components.AppSnackbarHost
import com.tailg.plus.ui.components.AppSnack
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.components.VehicleStage
import com.tailg.plus.ui.components.cyberButtonShape
import com.tailg.plus.ui.components.cyberFilledButtonColors
import com.tailg.plus.ui.components.cyberTextFieldColors
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppTouchTargets
import com.tailg.plus.ui.theme.CyberHomeColors
import kotlinx.coroutines.launch

/**
 * Port of `lib/pages/garage_page.dart` — official GarageV2 with the CyberHome
 * skin. Search by frame number or share-phone, scan vehicle code, select /
 * rename / unbind vehicles, paginated list.
 *
 * Navigation: [onBack] pops, [onNavigate] routes to add-vehicle / login /
 * scanner pages via [Routes].
 */
@Composable
fun GarageScreen(
  onBack: () -> Unit,
  onNavigate: (String) -> Unit,
  cloudService: OfficialCloudService? = null,
  modifier: Modifier = Modifier,
  mqttService: com.tailg.plus.data.mqtt.OfficialMqttService? = null,
  connectionManager: com.tailg.plus.data.ble.platform.ConnectionManager? = null,
) {
  val cloudService = cloudService ?: rememberOfficialCloudService()
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  val cloudState by cloudService.stateFlow.collectAsState()

  var searchQuery by remember { mutableStateOf("") }
  var activeQuery by remember { mutableStateOf("") }
  var searchType by remember { mutableStateOf(GarageSearchType.FRAME) }
  var vehicles by remember { mutableStateOf<List<OfficialVehicle>>(emptyList()) }
  var loading by remember { mutableStateOf(false) }
  var loadingMore by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var busyVehicleKey by remember { mutableStateOf<String?>(null) }
  var pageIndex by remember { mutableStateOf(0) }
  var hasNext by remember { mutableStateOf(false) }
  var showSearchTypeSheet by remember { mutableStateOf(false) }
  var showRenameDialog by remember { mutableStateOf<OfficialVehicle?>(null) }
  var showUnbindDialog by remember { mutableStateOf<OfficialVehicle?>(null) }
  var showVehicleCodeSheet by remember { mutableStateOf<OfficialVehicle?>(null) }
  var pendingSwitchVehicle by remember { mutableStateOf<OfficialVehicle?>(null) }

  val signedIn = cloudState.signedIn
  val listState = rememberLazyListState()

  // Sync vehicles from cloud state when it first arrives.
  LaunchedEffect(cloudState.vehicles) {
    if (vehicles.isEmpty() && cloudState.vehicles.isNotEmpty()) {
      vehicles = cloudState.vehicles
    }
    if (!signedIn) {
      vehicles = emptyList()
      pageIndex = 0
      hasNext = false
    }
  }

  // Initial load when signed in.
  LaunchedEffect(cloudState.token) {
    if (signedIn) {
      loadGaragePage(
        cloudService = cloudService,
        refresh = true,
        searchType = searchType,
        activeQuery = activeQuery,
        onLoading = { loading = it },
        onLoadingMore = { loadingMore = it },
        onError = { error = it },
        onVehicles = { vehicles = it },
        onPageIndex = { pageIndex = it },
        onHasNext = { hasNext = it },
      )
    }
  }

  // Infinite scroll.
  LaunchedEffect(listState) {
    snapshotFlow {
      val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
      val total = listState.layoutInfo.totalItemsCount
      lastVisible to total
    }.collect { (lastVisible, total) ->
      if (hasNext && !loading && !loadingMore && total > 0 && lastVisible >= total - 2) {
        loadGaragePage(
          cloudService = cloudService,
          refresh = false,
          searchType = searchType,
          activeQuery = activeQuery,
          onLoading = { loading = it },
          onLoadingMore = { loadingMore = it },
          onError = { error = it },
          onVehicles = { vehicles = it },
          onPageIndex = { pageIndex = it },
          onHasNext = { hasNext = it },
          existingVehicles = vehicles,
        )
      }
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = CyberHomeColors.pageBg,
    snackbarHost = { AppSnackbarHost(snackbarHostState) },
    bottomBar = {
      GarageAddBar(
        label = if (signedIn) "添加爱车" else "登录并添加爱车",
        onTap = {
          if (signedIn) onNavigate(com.tailg.plus.ui.navigation.Routes.ADD_VEHICLE)
          else onNavigate(com.tailg.plus.ui.navigation.Routes.LOGIN)
        },
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
    ) {
      GarageSearchHeader(
        showBack = true,
        query = searchQuery,
        onQueryChange = { searchQuery = it },
        type = searchType,
        onBack = onBack,
        onChooseType = { showSearchTypeSheet = true },
        onScan = { onNavigate(com.tailg.plus.ui.navigation.Routes.GARAGE_CODE_SCANNER) },
        onSearch = {
          val query = searchQuery.trim()
          if (query.isEmpty()) {
            scope.launch {
              AppSnack.error(
                snackbarHostState,
                if (searchType == GarageSearchType.FRAME) "车架号不能为空" else "被分享人手机号不能为空",
              )
            }
            return@GarageSearchHeader
          }
          activeQuery = query
          scope.launch {
            loadGaragePage(
              cloudService = cloudService,
              refresh = true,
              searchType = searchType,
              activeQuery = query,
              onLoading = { loading = it },
              onLoadingMore = { loadingMore = it },
              onError = { error = it },
              onVehicles = { vehicles = it },
              onPageIndex = { pageIndex = it },
              onHasNext = { hasNext = it },
            )
          }
        },
        onClear = {
          searchQuery = ""
          if (activeQuery.isNotEmpty()) {
            activeQuery = ""
            scope.launch {
              loadGaragePage(
                cloudService = cloudService,
                refresh = true,
                searchType = searchType,
                activeQuery = "",
                onLoading = { loading = it },
                onLoadingMore = { loadingMore = it },
                onError = { error = it },
                onVehicles = { vehicles = it },
                onPageIndex = { pageIndex = it },
                onHasNext = { hasNext = it },
              )
            }
          }
        },
      )
      Spacer(Modifier.height(8.dp))
      Text(
        text = "点击卡片选择设备",
        style = TextStyle(fontSize = 13.sp, color = CyberHomeColors.inkMuted),
        modifier = Modifier.padding(start = 24.dp, bottom = 10.dp),
      )
      Box(modifier = Modifier.fillMaxSize()) {
        when {
          !signedIn -> GarageMessage(
            icon = Lucide.login,
            title = "登录后查看我的车库",
            subtitle = "车辆搜索、切换、改名和解绑需要官方账号。",
            actionLabel = "登录账号",
            onAction = { onNavigate(com.tailg.plus.ui.navigation.Routes.LOGIN) },
          )
          loading && vehicles.isEmpty() -> GarageListSkeleton()
          error != null && vehicles.isEmpty() -> GarageMessage(
            icon = Lucide.cloudOff,
            title = "车库加载失败",
            subtitle = error!!,
            actionLabel = "重新加载",
            onAction = {
              scope.launch {
                loadGaragePage(
                  cloudService = cloudService,
                  refresh = true,
                  searchType = searchType,
                  activeQuery = activeQuery,
                  onLoading = { loading = it },
                  onLoadingMore = { loadingMore = it },
                  onError = { error = it },
                  onVehicles = { vehicles = it },
                  onPageIndex = { pageIndex = it },
                  onHasNext = { hasNext = it },
                )
              }
            },
          )
          vehicles.isEmpty() -> GarageMessage(
            icon = if (activeQuery.isNotEmpty()) Lucide.search else Lucide.garage,
            title = if (activeQuery.isNotEmpty()) "未找到车辆" else "车库暂无车辆",
            subtitle = if (activeQuery.isNotEmpty()) "请检查搜索信息后重试。" else "添加爱车后会显示在这里。",
          )
          else -> LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
          ) {
            items(vehicles, key = { it.key }) { vehicle ->
              val isUsing = vehicle.isUsing || (cloudState.selectedVehicle?.let { sel ->
                sel.carId.isNotEmpty() && vehicle.carId == sel.carId
              } ?: false)
              GarageVehicleCard(
                vehicle = vehicle,
                isUsing = isUsing,
                busy = busyVehicleKey == vehicle.key,
                onTap = {
                  if (isUsing) {
                    scope.launch { AppSnack.info(snackbarHostState, "该车辆已是当前使用车辆") }
                    return@GarageVehicleCard
                  }
                  pendingSwitchVehicle = vehicle
                },
                onRename = { showRenameDialog = vehicle },
                onVehicleCode = { showVehicleCodeSheet = vehicle },
                onUnbind = { showUnbindDialog = vehicle },
              )
            }
            if (loadingMore) {
              item {
                Box(
                  modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = CyberHomeColors.primary,
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  // Search type bottom sheet.
  if (showSearchTypeSheet) {
    GarageSearchTypeSheet(
      selected = searchType,
      onSelect = { selected ->
        searchType = selected
        activeQuery = ""
        searchQuery = ""
        showSearchTypeSheet = false
        scope.launch {
          loadGaragePage(
            cloudService = cloudService,
            refresh = true,
            searchType = selected,
            activeQuery = "",
            onLoading = { loading = it },
            onLoadingMore = { loadingMore = it },
            onError = { error = it },
            onVehicles = { vehicles = it },
            onPageIndex = { pageIndex = it },
            onHasNext = { hasNext = it },
          )
        }
      },
      onDismiss = { showSearchTypeSheet = false },
    )
  }

  // Rename dialog.
  showRenameDialog?.let { vehicle ->
    GarageRenameDialog(
      initialName = vehicle.displayName,
      onDismiss = { showRenameDialog = null },
      onConfirm = { nickName ->
        val trimmed = nickName.trim()
        if (trimmed.isEmpty() || trimmed == vehicle.displayName) {
          showRenameDialog = null
          return@GarageRenameDialog
        }
        val target = vehicle
        showRenameDialog = null
        scope.launch {
          busyVehicleKey = target.key
          try {
            cloudService.updateCarNickName(carId = target.carId, carNickName = trimmed)
            vehicles = vehicles.map { item ->
              if (item.key == target.key) item.copyWith(carNickName = trimmed) else item
            }
            AppSnack.success(snackbarHostState, "车辆名称已修改")
          } catch (e: Exception) {
            AppSnack.error(snackbarHostState, OfficialCloudRedactor.errorMessage(e))
          } finally {
            busyVehicleKey = null
          }
        }
      },
    )
  }

  // Vehicle switch confirmation (Dart: disconnect MQTT + BLE, then changeUsingVehicle).
  pendingSwitchVehicle?.let { vehicle ->
    AlertDialog(
      onDismissRequest = { pendingSwitchVehicle = null },
      title = { androidx.compose.material3.Text("切换车辆") },
      text = {
        androidx.compose.material3.Text("切换到「${vehicle.displayName}」？将断开当前车辆的蓝牙与云端连接。")
      },
      confirmButton = {
        androidx.compose.material3.TextButton(
          onClick = {
            val target = vehicle
            pendingSwitchVehicle = null
            scope.launch {
              busyVehicleKey = target.key
              try {
                try {
                  mqttService?.disconnect()
                } catch (_: Exception) {
                  // Best-effort channel teardown before the switch.
                }
                try {
                  connectionManager?.disconnect()
                } catch (_: Exception) {
                  // Best-effort channel teardown before the switch.
                }
                cloudService.changeUsingVehicle(target)
                AppSnack.success(snackbarHostState, "已切换到 ${target.displayName}")
              } catch (e: Exception) {
                AppSnack.error(snackbarHostState, OfficialCloudRedactor.errorMessage(e))
              } finally {
                busyVehicleKey = null
              }
            }
          },
        ) {
          androidx.compose.material3.Text("切换")
        }
      },
      dismissButton = {
        androidx.compose.material3.TextButton(onClick = { pendingSwitchVehicle = null }) {
          androidx.compose.material3.Text("取消")
        }
      },
    )
  }

  // Unbind verification dialog.
  showUnbindDialog?.let { vehicle ->
    val phone = cloudState.phone.trim()
    if (!phone.matches(Regex("^\\d{11}$"))) {
      // Cannot unbind without a complete phone; dismiss with error.
      LaunchedEffect(vehicle) {
        AppSnack.error(snackbarHostState, "账号手机号不完整，请使用手机号重新登录后解绑")
        showUnbindDialog = null
      }
    } else {
      val masked = "${phone.substring(0, 3)}****${phone.substring(7)}"
      GarageUnbindDialog(
        maskedPhone = masked,
        onDismiss = { showUnbindDialog = null },
        onConfirm = { input ->
          showUnbindDialog = null
          if (input != phone.substring(3, 7)) {
            scope.launch { AppSnack.error(snackbarHostState, "手机号验证失败") }
            return@GarageUnbindDialog
          }
          val target = vehicle
          scope.launch {
            busyVehicleKey = target.key
            try {
              cloudService.unbindVehicle(
                carId = target.carId,
                unbindType = if (target.shareCarFlag) 2 else 1,
              )
              AppSnack.success(snackbarHostState, "车辆已解绑")
              loadGaragePage(
                cloudService = cloudService,
                refresh = true,
                searchType = searchType,
                activeQuery = activeQuery,
                onLoading = { loading = it },
                onLoadingMore = { loadingMore = it },
                onError = { error = it },
                onVehicles = { vehicles = it },
                onPageIndex = { pageIndex = it },
                onHasNext = { hasNext = it },
              )
            } catch (e: Exception) {
              AppSnack.error(snackbarHostState, OfficialCloudRedactor.errorMessage(e))
            } finally {
              busyVehicleKey = null
            }
          }
        },
      )
    }
  }

  // Vehicle code QR sheet.
  showVehicleCodeSheet?.let { vehicle ->
    if (vehicle.frame.isEmpty()) {
      LaunchedEffect(vehicle) {
        AppSnack.error(snackbarHostState, "该车辆暂无车架号")
        showVehicleCodeSheet = null
      }
    } else {
      // QR sheet renders the scannable frame-number QR via zxing.
      GarageVehicleCodeSheet(
        frame = vehicle.frame,
        onDismiss = { showVehicleCodeSheet = null },
      )
    }
  }
}

// ── Search type ───────────────────────────────────────────────────────────

enum class GarageSearchType(val label: String, val hint: String) {
  FRAME("车架号", "请输入车架号"),
  SHARE_PHONE("被分享人手机号", "请输入手机号"),
}

// ── Load helper ───────────────────────────────────────────────────────────

private suspend fun loadGaragePage(
  cloudService: OfficialCloudService,
  refresh: Boolean,
  searchType: GarageSearchType,
  activeQuery: String,
  existingVehicles: List<OfficialVehicle> = emptyList(),
  onLoading: (Boolean) -> Unit,
  onLoadingMore: (Boolean) -> Unit,
  onError: (String?) -> Unit,
  onVehicles: (List<OfficialVehicle>) -> Unit,
  onPageIndex: (Int) -> Unit,
  onHasNext: (Boolean) -> Unit,
) {
  if (!cloudService.currentState.signedIn) return
  if (refresh) onLoading(true) else onLoadingMore(true)
  onError(null)
  val nextPage = if (refresh) 1 else (cloudService.currentState.vehicles.size / 5) + 1
  try {
    val result = cloudService.fetchGaragePage(
      pageIndex = nextPage,
      frame = if (searchType == GarageSearchType.FRAME) activeQuery else "",
      shareUserPhone = if (searchType == GarageSearchType.SHARE_PHONE) activeQuery else "",
    )
    onVehicles(if (refresh) result.vehicles else existingVehicles + result.vehicles)
    onPageIndex(result.pageIndex)
    onHasNext(result.hasNext)
    onError(null)
  } catch (e: Exception) {
    onError(OfficialCloudRedactor.errorMessage(e))
  } finally {
    onLoading(false)
    onLoadingMore(false)
  }
}

// ── Search header ──────────────────────────────────────────────────────────

@Composable
private fun GarageSearchHeader(
  showBack: Boolean,
  query: String,
  onQueryChange: (String) -> Unit,
  type: GarageSearchType,
  onBack: () -> Unit,
  onChooseType: () -> Unit,
  onScan: () -> Unit,
  onSearch: () -> Unit,
  onClear: () -> Unit,
) {
  Row(
    modifier = Modifier.padding(
      start = if (showBack) 8.dp else 16.dp,
      top = 10.dp,
      end = 16.dp,
      bottom = 4.dp,
    ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (showBack) {
      AppPressable(
        onClick = onBack,
        shape = CircleShape,
        semanticsLabel = "返回",
      ) {
        Box(
          modifier = Modifier.size(AppTouchTargets.min),
          contentAlignment = Alignment.Center,
        ) {
          LucideIcon(icon = Lucide.arrowLeft, size = 20.dp, color = CyberHomeColors.inkSecondary)
        }
      }
      Spacer(Modifier.width(4.dp))
    }
    Row(
      modifier = Modifier
        .height(52.dp)
        .clip(RoundedCornerShape(AppRadii.tile))
        .background(CyberHomeColors.card)
        .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
        .shadow(4.dp, RoundedCornerShape(AppRadii.tile), clip = false, spotColor = CyberHomeColors.actionShadow),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      AppPressable(
        onClick = onChooseType,
        semanticsLabel = "搜索方式：${type.label}",
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = type.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.inkSecondary),
          )
          Spacer(Modifier.width(3.dp))
          LucideIcon(icon = Lucide.chevronDown, size = 14.dp, color = CyberHomeColors.inkMuted)
        }
      }
      Box(
        modifier = Modifier
          .width(1.dp)
          .height(24.dp)
          .background(CyberHomeColors.lineStrong),
      )
      AppPressable(
        onClick = onScan,
        semanticsLabel = "扫描车架码",
      ) {
        Box(
          modifier = Modifier.size(AppTouchTargets.min),
          contentAlignment = Alignment.Center,
        ) {
          LucideIcon(icon = Lucide.scan, size = 19.dp, color = CyberHomeColors.primary)
        }
      }
      OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.weight(1f),
        singleLine = true,
        textStyle = TextStyle(fontSize = 13.sp, color = CyberHomeColors.ink),
        placeholder = {
          Text(
            text = type.hint,
            style = TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
          )
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = cyberTextFieldColors(),
        shape = RoundedCornerShape(0.dp),
      )
      if (query.isNotEmpty()) {
        AppPressable(
          onClick = onClear,
          semanticsLabel = "清空搜索",
        ) {
          Box(
            modifier = Modifier.size(width = 36.dp, height = 52.dp),
            contentAlignment = Alignment.Center,
          ) {
            LucideIcon(icon = Lucide.x, size = 16.dp, color = CyberHomeColors.inkFaint)
          }
        }
      }
      AppPressable(
        onClick = onSearch,
        semanticsLabel = "搜索",
      ) {
        Box(
          modifier = Modifier.size(width = 48.dp, height = 52.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "搜索",
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.primary),
          )
        }
      }
    }
  }
}

// ── Search type sheet ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GarageSearchTypeSheet(
  selected: GarageSearchType,
  onSelect: (GarageSearchType) -> Unit,
  onDismiss: () -> Unit,
) {
  androidx.compose.material3.ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = CyberHomeColors.card,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
    ) {
      Text(
        text = "搜索方式",
        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      )
      Spacer(Modifier.height(10.dp))
      GarageSearchType.values().forEach { type ->
        AppPressable(
          onClick = { onSelect(type) },
          semanticsLabel = type.label,
          pressedBackground = CyberHomeColors.cardMuted,
          shape = RoundedCornerShape(AppRadii.tile),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
              .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            LucideIcon(
              icon = if (type == GarageSearchType.FRAME) Lucide.scan else Lucide.phone,
              size = 19.dp,
              color = if (type == selected) CyberHomeColors.primary else CyberHomeColors.inkMuted,
            )
            Spacer(Modifier.width(12.dp))
            Text(
              text = type.label,
              modifier = Modifier.weight(1f),
              style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.ink),
            )
            if (type == selected) {
              LucideIcon(icon = Lucide.check, size = 19.dp, color = CyberHomeColors.primary)
            }
          }
        }
      }
    }
  }
}

// ── Vehicle card ──────────────────────────────────────────────────────────

@Composable
private fun GarageVehicleCard(
  vehicle: OfficialVehicle,
  isUsing: Boolean,
  busy: Boolean,
  onTap: () -> Unit,
  onRename: () -> Unit,
  onVehicleCode: () -> Unit,
  onUnbind: () -> Unit,
) {
  val shared = vehicle.shareCarFlag
  val borderColor = if (isUsing) CyberHomeColors.primary else CyberHomeColors.line
  val borderWidth = if (isUsing) 1.5.dp else 1.dp
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(borderWidth, borderColor, RoundedCornerShape(AppRadii.tile)),
  ) {
    AppPressable(
      onClick = if (busy) null else onTap,
      enabled = !busy,
      haptic = false,
      shape = RoundedCornerShape(AppRadii.tile),
      pressedBackground = CyberHomeColors.cardMuted,
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 14.dp),
      ) {
        Row(verticalAlignment = Alignment.Top) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = vehicle.displayName,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              if (isUsing) {
                GarageBadge(text = "使用中", color = CyberHomeColors.primary, background = CyberHomeColors.primarySoft)
              }
              GarageStatus(online = vehicle.online)
            }
          }
          Spacer(Modifier.width(12.dp))
          Column(horizontalAlignment = Alignment.End) {
            LucideIcon(
              icon = if (shared) Lucide.users else Lucide.userCircle,
              size = 22.dp,
              color = if (shared) CyberHomeColors.warning else CyberHomeColors.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
              text = if (shared) "好友车辆" else "车主车辆",
              style = TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkMuted),
            )
          }
        }
        Spacer(Modifier.height(10.dp))
        VehicleStage(
          batteryLevel = (vehicle.electricQuantity?.toFloat()?.div(100f)) ?: 0.72f,
          height = 210.dp,
          imageUrl = vehicle.carPhoto.trim().ifEmpty { null },
        )
        if (vehicle.shareCount > 0) {
          Spacer(Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            LucideIcon(icon = Lucide.share, size = 14.dp, color = CyberHomeColors.primary)
            Spacer(Modifier.width(5.dp))
            Text(
              text = "已分享 ${vehicle.shareCount} 次",
              style = TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkMuted),
            )
          }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (!shared) {
            GarageCardAction(icon = Lucide.scan, label = "车辆码", onTap = onVehicleCode)
          }
          Spacer(Modifier.weight(1f))
          Text(
            text = vehicle.carName.ifEmpty { "台铃智能车辆" },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.inkSecondary),
          )
          Spacer(Modifier.width(4.dp))
          GarageCardAction(icon = Lucide.edit, label = "修改", compact = true, onTap = onRename)
          Spacer(Modifier.weight(1f))
          if (!shared) {
            GarageCardAction(icon = Lucide.unlink, label = "解绑", danger = true, onTap = onUnbind)
          }
        }
      }
    }
    if (busy) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .clip(RoundedCornerShape(AppRadii.tile))
          .background(CyberHomeColors.white75),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator(color = CyberHomeColors.primary)
      }
    }
  }
}

@Composable
private fun GarageCardAction(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  onTap: () -> Unit,
  danger: Boolean = false,
  compact: Boolean = false,
) {
  val color = if (danger) CyberHomeColors.danger else CyberHomeColors.primary
  AppPressable(
    onClick = onTap,
    haptic = false,
    semanticsLabel = label,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = if (compact) 4.dp else 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LucideIcon(icon = icon, size = 15.dp, color = color)
      Spacer(Modifier.width(4.dp))
      Text(
        text = label,
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.W600, color = color),
      )
    }
  }
}

@Composable
private fun GarageBadge(text: String, color: Color, background: Color) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(background)
      .padding(horizontal = 9.dp, vertical = 4.dp),
  ) {
    Text(
      text = text,
      style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.W600, color = color),
    )
  }
}

@Composable
private fun GarageStatus(online: Boolean) {
  val color = if (online) CyberHomeColors.success else CyberHomeColors.inkFaint
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
      modifier = Modifier
        .size(7.dp)
        .clip(CircleShape)
        .background(color),
    )
    Spacer(Modifier.width(5.dp))
    Text(
      text = if (online) "在线" else "离线",
      style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.W600, color = color),
    )
  }
}

// ── Add bar ───────────────────────────────────────────────────────────────

@Composable
private fun GarageAddBar(label: String, onTap: () -> Unit) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(CyberHomeColors.pageBg)
      .padding(start = 40.dp, top = 10.dp, end = 40.dp, bottom = 14.dp),
  ) {
    Button(
      onClick = onTap,
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp),
      shape = cyberButtonShape,
      colors = cyberFilledButtonColors(),
    ) {
      LucideIcon(icon = Lucide.plus, size = 19.dp, color = CyberHomeColors.white)
      Spacer(Modifier.width(8.dp))
      Text(text = label)
    }
  }
}

// ── Skeleton ──────────────────────────────────────────────────────────────

@Composable
private fun GarageListSkeleton() {
  Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
    repeat(2) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(250.dp)
          .clip(RoundedCornerShape(AppRadii.tile))
          .background(CyberHomeColors.card)
          .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
          .padding(18.dp),
      ) {
        // TODO: AppSkeleton placeholders when skeleton component is available in CyberHome.
        CircularProgressIndicator(
          modifier = Modifier.align(Alignment.Center).size(24.dp),
          strokeWidth = 2.dp,
          color = CyberHomeColors.primary,
        )
      }
      Spacer(Modifier.height(18.dp))
    }
  }
}

// ── Message state ──────────────────────────────────────────────────────────

@Composable
private fun GarageMessage(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
) {
  Box(
    modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
        modifier = Modifier
          .size(58.dp)
          .clip(CircleShape)
          .background(CyberHomeColors.primarySoft),
        contentAlignment = Alignment.Center,
      ) {
        LucideIcon(icon = icon, size = 27.dp, color = CyberHomeColors.primary)
      }
      Spacer(Modifier.height(14.dp))
      Text(
        text = title,
        textAlign = TextAlign.Center,
        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      )
      Spacer(Modifier.height(6.dp))
      Text(
        text = subtitle,
        textAlign = TextAlign.Center,
        style = TextStyle(fontSize = 13.sp, color = CyberHomeColors.inkMuted, lineHeight = 13.sp * 1.45f),
      )
      if (actionLabel != null && onAction != null) {
        Spacer(Modifier.height(18.dp))
        Button(
          onClick = onAction,
          colors = cyberFilledButtonColors(),
          shape = cyberButtonShape,
        ) {
          Text(actionLabel)
        }
      }
    }
  }
}

// ── Rename dialog ─────────────────────────────────────────────────────────

@Composable
private fun GarageRenameDialog(
  initialName: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var name by remember { mutableStateOf(initialName) }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = CyberHomeColors.card,
    title = { Text("修改设备名称") },
    text = {
      TextField(
        value = name,
        onValueChange = { name = it },
        singleLine = true,
        textStyle = TextStyle(color = CyberHomeColors.ink),
        placeholder = { Text("请输入设备名称") },
        colors = cyberTextFieldColors(),
      )
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(name) },
        colors = cyberFilledButtonColors(),
        shape = cyberButtonShape,
      ) { Text("保存") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("取消") }
    },
  )
}

// ── Unbind dialog ─────────────────────────────────────────────────────────

@Composable
private fun GarageUnbindDialog(
  maskedPhone: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var input by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = CyberHomeColors.card,
    title = { Text("验证后解绑") },
    text = {
      Column {
        Text("请输入绑定号码 $maskedPhone 的中间 4 位")
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
          value = input,
          onValueChange = { value -> input = value.filter { it.isDigit() }.take(4) },
          singleLine = true,
          textStyle = TextStyle(color = CyberHomeColors.ink),
          placeholder = { Text("手机号中间 4 位") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          colors = cyberTextFieldColors(),
          shape = RoundedCornerShape(AppRadii.tile),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(input) },
        colors = cyberFilledButtonColors(),
        shape = cyberButtonShape,
      ) { Text("确认解绑") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("取消") }
    },
  )
}

// ── Vehicle code sheet ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GarageVehicleCodeSheet(
  frame: String,
  onDismiss: () -> Unit,
) {
  androidx.compose.material3.ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = CyberHomeColors.card,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = "车辆码",
        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      )
      Spacer(Modifier.height(18.dp))
      Box(
        modifier = Modifier
          .size(220.dp)
          .clip(RoundedCornerShape(AppRadii.tile))
          .background(CyberHomeColors.white)
          .padding(12.dp),
        contentAlignment = Alignment.Center,
      ) {
        com.tailg.plus.ui.components.QrImage(
          content = frame,
          modifier = Modifier.fillMaxWidth(),
        )
      }
      Spacer(Modifier.height(14.dp))
      Text(
        text = "车架号：$frame",
        textAlign = TextAlign.Center,
        style = TextStyle(fontSize = 14.sp, color = CyberHomeColors.inkSecondary),
      )
    }
  }
}
