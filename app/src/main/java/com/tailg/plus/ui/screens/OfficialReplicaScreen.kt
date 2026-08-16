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

private enum class ReplicaPage { NFC, FENCE, SHARE, RIDE }

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
          ReplicaPage.NFC -> "NFC钥匙"
          ReplicaPage.FENCE -> "本地草稿围栏"
          ReplicaPage.SHARE -> "分享用车"
          ReplicaPage.RIDE -> "今日骑行记录"
        },
        actionIcon = when (page) {
          ReplicaPage.NFC -> Lucide.plus
          ReplicaPage.FENCE -> Lucide.locate
          ReplicaPage.SHARE -> Lucide.userPlus
          ReplicaPage.RIDE -> null
        },
        actionLabel = when (page) {
          ReplicaPage.NFC -> "添加钥匙"
          ReplicaPage.FENCE -> "使用最后位置"
          ReplicaPage.SHARE -> "添加成员"
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
                  ReplicaPage.FENCE -> "围栏"
                  ReplicaPage.SHARE -> "分享"
                  ReplicaPage.RIDE -> "骑行"
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

@Composable
private fun ReplicaPageHeader(
  title: String,
  actionIcon: androidx.compose.ui.graphics.vector.ImageVector?,
  actionLabel: String?,
  onAction: (() -> Unit)?,
  onBack: () -> Unit,
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
      semanticsLabel = "返回",
    ) {
      Box(modifier = Modifier.size(AppTouchTargets.min), contentAlignment = Alignment.Center) {
        LucideIcon(icon = Lucide.arrowLeft, size = 20.dp, color = CyberHomeColors.inkSecondary)
      }
    }
    Spacer(Modifier.width(12.dp))
    Text(
      text = title,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      modifier = Modifier.weight(1f),
    )
    if (actionIcon != null && actionLabel != null && onAction != null) {
      AppPressable(
        onClick = onAction,
        shape = CircleShape,
        semanticsLabel = actionLabel,
      ) {
        Box(modifier = Modifier.size(AppTouchTargets.min), contentAlignment = Alignment.Center) {
          LucideIcon(icon = actionIcon, size = 20.dp, color = CyberHomeColors.inkSecondary)
        }
      }
    }
  }
}

@Composable
private fun NfcKeyTab(
  store: ReplicaFeatureStore,
  bleNfc: BleNfcService,
  snackbarHostState: SnackbarHostState,
  scope: kotlinx.coroutines.CoroutineScope,
) {
  var records by remember { mutableStateOf<List<NfcKeyRecord>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var showEditDialog by remember { mutableStateOf<NfcKeyRecord?>(null) }
  var showAddDialog by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    records = store.loadNfcKeys()
    loading = false
  }

  val canBle = bleNfc.canWriteOfficialNfc

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 24.dp),
  ) {
    item {
      Text(
        text = "官方 / 本地",
        style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.inkMuted),
      )
    }
    item { Spacer(Modifier.height(8.dp)) }
    item {
      ReplicaNotice(
        icon = Lucide.nfc,
        title = if (canBle) "官方 BLE NFC 可用" else "官方 NFC 待 LOGIN",
        subtitle = if (canBle) {
          "当前 standard 协议已 LOGIN：添加/删除将下发官方 writeData 帧（TailgBleConfig NFC 头），并同步本地列表。"
        } else {
          "未 standard LOGIN 时仅维护本地列表，不会写车。请先 BLE 连接并完成协议登录。"
        },
      )
    }
    item { Spacer(Modifier.height(14.dp)) }
    if (loading) {
      item {
        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = CyberHomeColors.primary)
        }
      }
    } else if (records.isEmpty()) {
      item {
        EmptyReplicaCard(icon = Lucide.keyOff, title = "暂无钥匙", subtitle = "添加后可在这里查看钥匙名称和类型。")
      }
    } else {
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadii.tile))
            .background(CyberHomeColors.card)
            .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile)),
        ) {
          records.forEachIndexed { i, record ->
            NfcKeyTile(
              record = record,
              onEdit = { showEditDialog = record },
              onDelete = {
                scope.launch {
                  if (bleNfc.canWriteOfficialNfc) {
                    val ok = bleNfc.delNfc("01")
                    AppSnack.info(snackbarHostState, if (ok) "已发送官方删钥匙指令" else "官方删钥匙失败，仅移除本地列表")
                  }
                  records = records.filter { it.id != record.id }
                  store.saveNfcKeys(records)
                }
              },
            )
            if (i != records.lastIndex) {
              HorizontalDivider(modifier = Modifier.padding(start = 68.dp), color = CyberHomeColors.line)
            }
          }
        }
      }
    }
  }

  if (showAddDialog) {
    NfcKeyEditDialog(
      record = null,
      onDismiss = { showAddDialog = false },
      onSave = { name, type ->
        scope.launch {
          val newRecord = store.createNfcKey(name = name, type = type)
          if (bleNfc.canWriteOfficialNfc) {
            val ok = if (type == "卡片") {
              bleNfc.addCard("01")
            } else {
              bleNfc.addUserKey(keyType = if (type == "手表") 2 else 1, type = "1")
            }
            if (ok) {
              AppSnack.success(snackbarHostState, "已向车辆发送官方 NFC 写钥匙指令")
            } else {
              AppSnack.info(snackbarHostState, "官方 NFC 写入失败，仅保存本地列表")
            }
          } else {
            AppSnack.info(snackbarHostState, "未 standard LOGIN：仅本地列表（不会写车）")
          }
          val next = records + newRecord
          records = next
          store.saveNfcKeys(next)
        }
        showAddDialog = false
      },
    )
  }

  showEditDialog?.let { record ->
    NfcKeyEditDialog(
      record = record,
      onDismiss = { showEditDialog = null },
      onSave = { name, type ->
        val updated = record.copyWith(name = name, type = type)
        val next = records.map { if (it.id == updated.id) updated else it }
        records = next
        scope.launch { store.saveNfcKeys(next) }
        showEditDialog = null
      },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NfcKeyEditDialog(
  record: NfcKeyRecord?,
  onDismiss: () -> Unit,
  onSave: (String, String) -> Unit,
) {
  var name by remember { mutableStateOf(record?.name ?: "") }
  var type by remember { mutableStateOf(record?.type ?: "手机") }
  var expanded by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = CyberHomeColors.card,
    shape = RoundedCornerShape(AppRadii.tile),
    title = {
      Text(
        text = if (record == null) "添加钥匙" else "编辑钥匙",
        style = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      )
    },
    text = {
      Column {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          singleLine = true,
          placeholder = { Text("钥匙名称") },
          colors = cyberTextFieldColors(),
          shape = cyberTextFieldShape,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        ExposedDropdownMenuBox(
          expanded = expanded,
          onExpandedChange = { expanded = it },
        ) {
          OutlinedTextField(
            value = type,
            onValueChange = {},
            readOnly = true,
            label = { Text("钥匙类型") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = cyberTextFieldColors(),
            shape = cyberTextFieldShape,
            modifier = Modifier
              .fillMaxWidth()
              .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
          )
          androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
          ) {
            listOf("手机", "手表", "卡片").forEach { item ->
              DropdownMenuItem(
                text = { Text(item) },
                onClick = { type = item; expanded = false },
              )
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val trimmed = name.trim()
          if (trimmed.isNotEmpty()) onSave(trimmed, type)
        },
        shape = cyberButtonShape,
        colors = cyberFilledButtonColors(),
      ) {
        Text("保存")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("取消", color = CyberHomeColors.inkMuted)
      }
    },
  )
}

@Composable
private fun NfcKeyTile(
  record: NfcKeyRecord,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  var showMenu by remember { mutableStateOf(false) }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(68.dp)
      .padding(horizontal = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CircleIcon(
      icon = when (record.type) {
        "卡片" -> Lucide.creditCard
        "手表" -> Lucide.watch
        else -> Lucide.smartphone
      },
    )
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = record.name,
        style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      )
      Text(
        text = "${record.type} · ${formatDateText(java.time.LocalDateTime.ofInstant(record.createdAt, java.time.ZoneId.systemDefault()))}",
        style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
      )
    }
    AppPressable(
      onClick = { showMenu = true },
      shape = CircleShape,
      semanticsLabel = "钥匙操作",
    ) {
      Box(modifier = Modifier.size(AppTouchTargets.min), contentAlignment = Alignment.Center) {
        LucideIcon(icon = Lucide.more, color = CyberHomeColors.inkMuted)
      }
    }
    androidx.compose.material3.DropdownMenu(
      expanded = showMenu,
      onDismissRequest = { showMenu = false },
    ) {
      androidx.compose.material3.DropdownMenuItem(
        text = { Text("重命名") },
        onClick = { showMenu = false; onEdit() },
      )
      androidx.compose.material3.DropdownMenuItem(
        text = { Text("删除") },
        onClick = { showMenu = false; onDelete() },
      )
    }
  }
}

@Composable
private fun ElectricFenceTab(
  store: ReplicaFeatureStore,
  vehicleStore: VehicleStore,
  snackbarHostState: SnackbarHostState,
  scope: kotlinx.coroutines.CoroutineScope,
) {
  var enabled by remember { mutableStateOf(false) }
  var latText by remember { mutableStateOf("") }
  var lngText by remember { mutableStateOf("") }
  var radiusText by remember { mutableStateOf("500") }
  var loading by remember { mutableStateOf(true) }
  var lastLocation by remember { mutableStateOf<VehicleLocation?>(null) }
  val context = androidx.compose.ui.platform.LocalContext.current

  LaunchedEffect(Unit) {
    vehicleStore.init()
    val config = store.loadFenceConfig()
    lastLocation = vehicleStore.defaultVehicle?.lastLocation
    val latitude = config?.latitude ?: lastLocation?.latitude
    val longitude = config?.longitude ?: lastLocation?.longitude
    enabled = config?.enabled ?: false
    latText = latitude?.let { "%.6f".format(it) } ?: ""
    lngText = longitude?.let { "%.6f".format(it) } ?: ""
    radiusText = (config?.radiusMeters ?: 500).toString()
    loading = false
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(bottom = 24.dp),
  ) {
    Text(
      text = "本地草稿（非官方）",
      style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.inkMuted),
      modifier = Modifier.padding(horizontal = AppSpacing.screenX),
    )
    Spacer(Modifier.height(8.dp))
    ReplicaNotice(
      icon = Lucide.locationSearching,
      title = "非官方云围栏",
      subtitle = "此页只写本地草稿，不会同步官方电子围栏。正式围栏请用定位页「电子围栏」云端能力。",
    )
    Spacer(Modifier.height(14.dp))
    if (loading) {
      Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = CyberHomeColors.primary)
      }
    } else {
      Column(
        modifier = Modifier
          .padding(horizontal = AppSpacing.screenX)
          .clip(RoundedCornerShape(AppRadii.tile))
          .background(CyberHomeColors.card)
          .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
          .padding(16.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Switch(
            checked = enabled,
            onCheckedChange = { enabled = it },
            colors = androidx.compose.material3.SwitchDefaults.colors(
              checkedThumbColor = CyberHomeColors.white,
              checkedTrackColor = CyberHomeColors.primary,
              uncheckedThumbColor = CyberHomeColors.white,
              uncheckedTrackColor = CyberHomeColors.controlStrong,
            ),
          )
          Spacer(Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "启用围栏",
              style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
            )
            Text(
              text = "开启后保存当前围栏设置",
              style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = CyberHomeColors.inkMuted),
            )
          }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
          value = latText,
          onValueChange = { latText = it },
          singleLine = true,
          label = { Text("中心纬度") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          colors = cyberTextFieldColors(),
          shape = cyberTextFieldShape,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
          value = lngText,
          onValueChange = { lngText = it },
          singleLine = true,
          label = { Text("中心经度") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          colors = cyberTextFieldColors(),
          shape = cyberTextFieldShape,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
          value = radiusText,
          onValueChange = { radiusText = it.filter { c -> c.isDigit() } },
          singleLine = true,
          label = { Text("半径（米）") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          colors = cyberTextFieldColors(),
          shape = cyberTextFieldShape,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedButton(
            onClick = {
              lastLocation?.let {
                latText = "%.6f".format(it.latitude)
                lngText = "%.6f".format(it.longitude)
              }
            },
            enabled = lastLocation != null,
            shape = cyberButtonShape,
            colors = cyberOutlinedButtonColors(),
            border = cyberOutlinedButtonBorder,
            modifier = Modifier
              .weight(1f)
              .height(48.dp),
          ) {
            LucideIcon(icon = Lucide.locate, size = 18.dp)
            Spacer(Modifier.width(6.dp))
            Text("使用最后位置")
          }
          OutlinedButton(
            onClick = {
              val lat = latText.trim().toDoubleOrNull()
              val lng = lngText.trim().toDoubleOrNull()
              if (lat == null || lng == null) {
                scope.launch { AppSnack.info(snackbarHostState, "请输入有效坐标") }
              } else {
                val geoUri = android.net.Uri.parse("geo:$lat,$lng?q=$lat,$lng")
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, geoUri)
                try {
                  context.startActivity(intent)
                } catch (e: android.content.ActivityNotFoundException) {
                  scope.launch { AppSnack.info(snackbarHostState, "未找到地图应用") }
                }
              }
            },
            shape = cyberButtonShape,
            colors = cyberOutlinedButtonColors(),
            border = cyberOutlinedButtonBorder,
            modifier = Modifier
              .weight(1f)
              .height(48.dp),
          ) {
            LucideIcon(icon = Lucide.map, size = 18.dp)
            Spacer(Modifier.width(6.dp))
            Text("打开地图")
          }
        }
        Spacer(Modifier.height(10.dp))
        Button(
          onClick = {
            val latitude = latText.trim().toDoubleOrNull()
            val longitude = lngText.trim().toDoubleOrNull()
            val radius = radiusText.trim().toIntOrNull() ?: 500
            if (latitude == null || longitude == null) {
              scope.launch { AppSnack.info(snackbarHostState, "请输入有效坐标") }
              return@Button
            }
            if (radius < 100 || radius > 10000) {
              scope.launch { AppSnack.info(snackbarHostState, "半径建议设置在 100-10000 米") }
              return@Button
            }
            scope.launch {
              store.saveFenceConfig(store.createFenceConfig(enabled = enabled, latitude = latitude, longitude = longitude, radiusMeters = radius))
              AppSnack.info(snackbarHostState, "已保存为本地草稿（未同步官方围栏）")
            }
          },
          shape = cyberButtonShape,
          colors = cyberFilledButtonColors(),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        ) {
          LucideIcon(icon = Lucide.save, size = 18.dp, color = CyberHomeColors.white)
          Spacer(Modifier.width(6.dp))
          Text("保存围栏", style = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = FontWeight.W700))
        }
      }
    }
  }
}

@Composable
private fun ShareBikeTab(
  store: ReplicaFeatureStore,
  snackbarHostState: SnackbarHostState,
  scope: kotlinx.coroutines.CoroutineScope,
) {
  var members by remember { mutableStateOf<List<ShareMemberRecord>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var showAddDialog by remember { mutableStateOf(false) }
  var editMember by remember { mutableStateOf<ShareMemberRecord?>(null) }

  LaunchedEffect(Unit) {
    members = store.loadShareMembers()
    loading = false
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 24.dp),
  ) {
    item {
      Text(
        text = "家庭共享（本地演示）",
        style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.inkMuted),
      )
    }
    item { Spacer(Modifier.height(8.dp)) }
    item {
      ReplicaNotice(
        icon = Lucide.share,
        title = "本地演示 · 非官方家庭共享",
        subtitle = "仅本机记录联系人草稿，不会调用官方家庭共享 API。正式分享请使用官方 App 授权流程。",
      )
    }
    item { Spacer(Modifier.height(14.dp)) }
    if (loading) {
      item {
        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = CyberHomeColors.primary)
        }
      }
    } else if (members.isEmpty()) {
      item {
        EmptyReplicaCard(icon = Lucide.groupOff, title = "暂无共享成员", subtitle = "添加成员后可在这里查看共享联系人。")
      }
    } else {
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadii.tile))
            .background(CyberHomeColors.card)
            .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile)),
        ) {
          members.forEachIndexed { i, member ->
            ShareMemberTile(
              member = member,
              onEdit = { editMember = member },
              onDelete = {
                scope.launch {
                  members = members.filter { it.id != member.id }
                  store.saveShareMembers(members)
                }
              },
            )
            if (i != members.lastIndex) {
              HorizontalDivider(modifier = Modifier.padding(start = 68.dp), color = CyberHomeColors.line)
            }
          }
        }
      }
    }
  }

  if (showAddDialog) {
    ShareMemberEditDialog(
      member = null,
      onDismiss = { showAddDialog = false },
      onSave = { name, phone ->
        scope.launch {
          val newMember = store.createShareMember(name = name, phone = phone)
          val next = members + newMember
          members = next
          store.saveShareMembers(next)
        }
        showAddDialog = false
      },
    )
  }

  editMember?.let { member ->
    ShareMemberEditDialog(
      member = member,
      onDismiss = { editMember = null },
      onSave = { name, phone ->
        val updated = member.copyWith(name = name, phone = phone)
        val next = members.map { if (it.id == updated.id) updated else it }
        members = next
        scope.launch { store.saveShareMembers(next) }
        editMember = null
      },
    )
  }
}

@Composable
private fun ShareMemberEditDialog(
  member: ShareMemberRecord?,
  onDismiss: () -> Unit,
  onSave: (String, String) -> Unit,
) {
  var name by remember { mutableStateOf(member?.name ?: "") }
  var phone by remember { mutableStateOf(member?.phone ?: "") }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = CyberHomeColors.card,
    shape = RoundedCornerShape(AppRadii.tile),
    title = {
      Text(
        text = if (member == null) "添加成员" else "编辑成员",
        style = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      )
    },
    text = {
      Column {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          singleLine = true,
          label = { Text("成员名称") },
          colors = cyberTextFieldColors(),
          shape = cyberTextFieldShape,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
          value = phone,
          onValueChange = { phone = it },
          singleLine = true,
          label = { Text("手机号/备注") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
          colors = cyberTextFieldColors(),
          shape = cyberTextFieldShape,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val trimmed = name.trim()
          if (trimmed.isNotEmpty()) onSave(trimmed, phone.trim())
        },
        shape = cyberButtonShape,
        colors = cyberFilledButtonColors(),
      ) {
        Text("保存")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("取消", color = CyberHomeColors.inkMuted)
      }
    },
  )
}

@Composable
private fun ShareMemberTile(
  member: ShareMemberRecord,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  var showMenu by remember { mutableStateOf(false) }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(68.dp)
      .padding(horizontal = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CircleIcon(icon = Lucide.mine)
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = member.name,
        style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      )
      Text(
        text = if (member.phone.isEmpty()) {
          "待邀请 · ${formatDateText(java.time.LocalDateTime.ofInstant(member.createdAt, java.time.ZoneId.systemDefault()))}"
        } else {
          "${member.phone} · 待邀请"
        },
        style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
      )
    }
    AppPressable(
      onClick = { showMenu = true },
      shape = CircleShape,
      semanticsLabel = "成员操作",
    ) {
      Box(modifier = Modifier.size(AppTouchTargets.min), contentAlignment = Alignment.Center) {
        LucideIcon(icon = Lucide.more, color = CyberHomeColors.inkMuted)
      }
    }
    androidx.compose.material3.DropdownMenu(
      expanded = showMenu,
      onDismissRequest = { showMenu = false },
    ) {
      androidx.compose.material3.DropdownMenuItem(
        text = { Text("编辑") },
        onClick = { showMenu = false; onEdit() },
      )
      androidx.compose.material3.DropdownMenuItem(
        text = { Text("移除") },
        onClick = { showMenu = false; onDelete() },
      )
    }
  }
}

@Composable
private fun RideRecordTab(
  cloudService: OfficialCloudService,
  vehicleStore: VehicleStore,
  log: LogService,
) {
  val cloudState by cloudService.stateFlow.collectAsState()
  val vehicles by vehicleStore.vehiclesFlow.collectAsState()
  val vehicle = vehicleStore.defaultVehicle
  val location = vehicle?.lastLocation
  val cloudVehicle = if (cloudState.signedIn) cloudState.selectedVehicle else null
  val displayName = vehicle?.displayName ?: cloudVehicle?.displayName ?: "未绑定"
  val logs = remember(log) {
    log.byCategory(LogCategory.OPERATION).takeLast(12).reversed()
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
  ) {
    item {
      Text(
        text = "今日概览",
        style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.inkMuted),
        modifier = Modifier.padding(horizontal = AppSpacing.screenX),
      )
    }
    item {
      Row(
        modifier = Modifier
          .padding(horizontal = AppSpacing.screenX, vertical = 8.dp)
          .clip(RoundedCornerShape(AppRadii.tile))
          .background(CyberHomeColors.card)
          .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
          .padding(16.dp),
      ) {
        MetricBlock(label = "默认车辆", value = displayName, modifier = Modifier.weight(1f))
        MetricBlock(label = "本次日志", value = logs.size.toString(), modifier = Modifier.weight(1f))
      }
    }
    item {
      Row(
        modifier = Modifier
          .padding(horizontal = AppSpacing.screenX, vertical = 8.dp)
          .clip(RoundedCornerShape(AppRadii.tile))
          .background(CyberHomeColors.card)
          .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
          .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        LucideIcon(icon = Lucide.mapPin, color = CyberHomeColors.primary)
        Spacer(Modifier.width(12.dp))
        Text(
          text = if (location == null) {
            "暂无最后位置记录"
          } else {
            "${location.coordinateText} · ${formatDateMinuteText(java.time.LocalDateTime.ofInstant(location.recordedAt, java.time.ZoneId.systemDefault()))}"
          },
          style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 13.sp * 1.45f, color = CyberHomeColors.inkMuted),
          modifier = Modifier.weight(1f),
        )
      }
    }
    item {
      Text(
        text = "最近操作",
        style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.inkMuted),
        modifier = Modifier.padding(horizontal = AppSpacing.screenX),
      )
    }
    if (logs.isEmpty()) {
      item {
        EmptyReplicaCard(
          icon = Lucide.route,
          title = "暂无骑行记录",
          subtitle = "控车、定位、诊断等本地事件会出现在这里。",
        )
      }
    } else {
      item {
        Column(
          modifier = Modifier
            .padding(horizontal = AppSpacing.screenX)
            .clip(RoundedCornerShape(AppRadii.tile))
            .background(CyberHomeColors.card)
            .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile)),
        ) {
          logs.forEachIndexed { i, entry ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              LucideIcon(icon = Lucide.history, color = CyberHomeColors.inkMuted)
              Spacer(Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = entry.message,
                  style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
                )
                Text(
                  text = buildString {
                    append(formatDateMinuteText(entry.time))
                    entry.detail?.let { append("  $it") }
                  },
                  style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
                )
              }
            }
            if (i != logs.lastIndex) {
              HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = CyberHomeColors.line)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ReplicaNotice(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.primarySoft)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(16.dp),
    verticalAlignment = Alignment.Top,
  ) {
    CircleIcon(icon = icon)
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      )
      Spacer(Modifier.height(4.dp))
      Text(
        text = subtitle,
        style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 13.sp * 1.45f, color = CyberHomeColors.inkMuted),
      )
    }
  }
}

@Composable
private fun EmptyReplicaCard(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = AppSpacing.screenX)
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(20.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    LucideIcon(icon = icon, size = AppIconSizes.xl, color = CyberHomeColors.inkFaint)
    Spacer(Modifier.height(10.dp))
    Text(
      text = title,
      style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
    )
    Spacer(Modifier.height(4.dp))
    Text(
      text = subtitle,
      textAlign = TextAlign.Center,
      style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 13.sp * 1.45f, color = CyberHomeColors.inkMuted),
    )
  }
}

@Composable
private fun MetricBlock(label: String, value: String, modifier: Modifier = Modifier) {
  Column(modifier = modifier) {
    Text(
      text = label,
      style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 13.sp * 1.45f, color = CyberHomeColors.inkMuted),
    )
    Spacer(Modifier.height(6.dp))
    Text(
      text = value,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
    )
  }
}

@Composable
private fun CircleIcon(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  color: androidx.compose.ui.graphics.Color = CyberHomeColors.primary,
) {
  Box(
    modifier = Modifier
      .size(42.dp)
      .clip(CircleShape)
      .background(color.copy(alpha = 0.1f)),
    contentAlignment = Alignment.Center,
  ) {
    LucideIcon(icon = icon, color = color, size = AppIconSizes.md)
  }
}
