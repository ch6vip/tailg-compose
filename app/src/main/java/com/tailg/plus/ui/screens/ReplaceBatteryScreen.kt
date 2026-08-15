package com.tailg.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.data.cloud.OfficialCloudRedactor
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.model.AffirmBatteryInfoRequest
import com.tailg.plus.data.model.OfficialBatterySpec
import com.tailg.plus.data.model.OfficialBatteryType
import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.components.AppSnackbarHost
import com.tailg.plus.ui.components.AppSnack
import com.tailg.plus.ui.components.CyberPageHeader
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.components.cyberButtonShape
import com.tailg.plus.ui.components.cyberFilledButtonColors
import com.tailg.plus.ui.components.cyberTextFieldColors
import com.tailg.plus.ui.components.cyberTextFieldShape
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppTouchTargets
import com.tailg.plus.ui.theme.CyberHomeColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * Port of `lib/pages/replace_battery_page.dart` — official "更正电池信息"
 * flow (`ReplaceBatteryActivity` / `affirmBatteryInfo`).
 *
 * Bootstraps battery types + specs from the official cloud, pre-fills from
 * the selected vehicle, and submits an [AffirmBatteryInfoRequest]. The Dart
 * page uses `showDatePicker`; the Compose port uses a simple date picker
 * dialog via `DatePickerDialog` (Material3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplaceBatteryScreen(
  cloudService: OfficialCloudService,
  onBack: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  val log = remember { LogService() }

  var loadingTypes by remember { mutableStateOf(true) }
  var loadingSpecs by remember { mutableStateOf(false) }
  var submitting by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  var types by remember { mutableStateOf<List<OfficialBatteryType>>(emptyList()) }
  var specs by remember { mutableStateOf<List<OfficialBatterySpec>>(emptyList()) }
  var selectedType by remember { mutableStateOf<OfficialBatteryType?>(null) }
  var selectedSpec by remember { mutableStateOf<OfficialBatterySpec?>(null) }
  var bindDate by remember { mutableStateOf<LocalDate?>(null) }

  var voltage by remember { mutableStateOf("") }
  var ah by remember { mutableStateOf("") }
  var showDatePicker by remember { mutableStateOf(false) }

  val vehicle = cloudService.currentState.selectedVehicle

  LaunchedEffect(vehicle?.key) {
    val v = vehicle
    if (v == null) {
      loadingTypes = false
      error = "请先选择车辆"
      return@LaunchedEffect
    }
    bootstrap(
      vehicle = v,
      cloudService = cloudService,
      onTypes = { loaded, selected ->
        types = loaded
        selectedType = selected
        loadingTypes = false
        error = if (loaded.isEmpty()) "未获取到电池类型列表" else null
      },
      onSpecs = { loaded, selected ->
        specs = loaded
        selectedSpec = selected
        loadingSpecs = false
      },
      onPrefill = { vText, ahText, date ->
        voltage = vText
        ah = ahText
        bindDate = date
      },
      onError = { msg ->
        loadingTypes = false
        loadingSpecs = false
        error = msg
      },
    )
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
      CyberPageHeader(title = "更正电池信息", onBack = { onBack(false) })
      if (loadingTypes) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(color = CyberHomeColors.primary)
        }
      } else {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 24.dp),
        ) {
          val v = vehicle
          if (v != null) {
            VehicleBatterySummary(vehicle = v)
            Spacer(Modifier.height(16.dp))
          }
          error?.let { msg ->
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppRadii.tile))
                .background(CyberHomeColors.warning.copy(alpha = 0.1f))
                .padding(12.dp),
            ) {
              Text(text = msg, style = TextStyle(color = CyberHomeColors.warning, fontSize = 13.sp))
            }
            Spacer(Modifier.height(12.dp))
          }

          val type = selectedType
          val custom = type?.isCustom == true

          SectionCard(title = "电池类型") {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
              expanded = expanded,
              onExpandedChange = { expanded = !expanded },
            ) {
              OutlinedTextField(
                value = type?.name ?: "",
                onValueChange = {},
                readOnly = true,
                enabled = !submitting,
                singleLine = true,
                trailingIcon = {
                  ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
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
                types.forEach { t ->
                  DropdownMenuItem(
                    text = { Text(t.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                      selectedType = t
                      selectedSpec = null
                      specs = emptyList()
                      expanded = false
                      if (!t.isCustom) {
                        loadingSpecs = true
                        scope.launch {
                          try {
                            val loaded = cloudService.fetchBatterySpecsByType(t.type)
                            selectedSpec = loaded.firstOrNull()
                            specs = loaded
                          } catch (e: Exception) {
                            error = OfficialCloudRedactor.errorMessage(e)
                          } finally {
                            loadingSpecs = false
                          }
                        }
                      }
                    },
                  )
                }
              }
            }
          }
          Spacer(Modifier.height(12.dp))

          if (custom) {
            SectionCard(title = "自定义电压 / 安时") {
              Row {
                OutlinedTextField(
                  value = voltage,
                  onValueChange = { value -> voltage = value.filter { it.isDigit() || it == '.' } },
                  enabled = !submitting,
                  singleLine = true,
                  label = { Text("电压 (V)") },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                  colors = cyberTextFieldColors(),
                  shape = cyberTextFieldShape,
                  modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                  value = ah,
                  onValueChange = { value -> ah = value.filter { it.isDigit() || it == '.' } },
                  enabled = !submitting,
                  singleLine = true,
                  label = { Text("安时 (AH)") },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                  colors = cyberTextFieldColors(),
                  shape = cyberTextFieldShape,
                  modifier = Modifier.weight(1f),
                )
              }
            }
          } else {
            SectionCard(title = "电池规格") {
              if (loadingSpecs) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                  )
                }
              } else {
                var specExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                  expanded = specExpanded,
                  onExpandedChange = { specExpanded = !specExpanded },
                ) {
                  OutlinedTextField(
                    value = selectedSpec?.spec ?: "",
                    onValueChange = {},
                    readOnly = true,
                    enabled = !submitting,
                    singleLine = true,
                    trailingIcon = {
                      ExposedDropdownMenuDefaults.TrailingIcon(expanded = specExpanded)
                    },
                    colors = cyberTextFieldColors(),
                    shape = cyberTextFieldShape,
                    modifier = Modifier
                      .fillMaxWidth()
                      .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
                  )
                  androidx.compose.material3.DropdownMenu(
                    expanded = specExpanded,
                    onDismissRequest = { specExpanded = false },
                  ) {
                    specs.forEach { s ->
                      DropdownMenuItem(
                        text = { Text(s.spec, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = {
                          selectedSpec = s
                          specExpanded = false
                        },
                      )
                    }
                  }
                }
              }
            }
          }
          Spacer(Modifier.height(12.dp))

          SectionCard(title = "绑定日期") {
            val dateLabel = bindDate?.format(DateTimeFormatter.ISO_LOCAL_DATE) ?: "请选择绑定日期"
            AppPressable(
              onClick = {
                if (!submitting) {
                  showDatePicker = true
                }
              },
              enabled = !submitting,
              shape = RoundedCornerShape(AppRadii.tile),
              semanticsLabel = "选择绑定日期，$dateLabel",
              semanticsButton = true,
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(AppTouchTargets.min)
                  .clip(RoundedCornerShape(AppRadii.tile))
                  .background(CyberHomeColors.cardMuted)
                  .border(1.dp, CyberHomeColors.lineStrong, RoundedCornerShape(AppRadii.tile))
                  .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = dateLabel,
                  modifier = Modifier.weight(1f),
                  style = TextStyle(color = CyberHomeColors.ink),
                )
                LucideIcon(icon = Lucide.calendar, color = CyberHomeColors.primary, size = 20.dp)
              }
            }
          }
          Spacer(Modifier.height(20.dp))

          Button(
            onClick = {
              if (submitting) return@Button
              val vv = vehicle
              val tt = selectedType
              if (vv == null) {
                scope.launch { AppSnack.error(snackbarHostState, "请先选择车辆") }
                return@Button
              }
              if (tt == null) {
                scope.launch { AppSnack.error(snackbarHostState, "请选择电池类型") }
                return@Button
              }
              val carId = vv.carId.trim()
              if (carId.isEmpty()) {
                scope.launch { AppSnack.error(snackbarHostState, "车辆缺少 carId，无法提交") }
                return@Button
              }
              val request: AffirmBatteryInfoRequest
              if (tt.isCustom) {
                val vText = voltage.trim()
                val ahText = ah.trim()
                if (vText.isEmpty()) {
                  scope.launch { AppSnack.error(snackbarHostState, "请输入电压") }
                  return@Button
                }
                if (ahText.isEmpty()) {
                  scope.launch { AppSnack.error(snackbarHostState, "请输入安时数") }
                  return@Button
                }
                request = AffirmBatteryInfoRequest(
                  carId = carId,
                  batteryType = tt.type,
                  batteryVoltage = vText,
                  batteryCapacity = ahText,
                  bindDate = bindDate?.format(DateTimeFormatter.ISO_LOCAL_DATE),
                )
              } else {
                val spec = selectedSpec
                if (spec == null) {
                  scope.launch { AppSnack.error(snackbarHostState, "请选择电池规格") }
                  return@Button
                }
                if (bindDate == null) {
                  scope.launch { AppSnack.error(snackbarHostState, "请选择绑定日期") }
                  return@Button
                }
                request = AffirmBatteryInfoRequest(
                  carId = carId,
                  batteryCode = spec.code,
                  bindDate = bindDate!!.format(DateTimeFormatter.ISO_LOCAL_DATE),
                )
              }
              submitting = true
              scope.launch {
                try {
                  cloudService.affirmBatteryInfo(request)
                  AppSnack.success(snackbarHostState, "电池信息已更正")
                  onBack(true)
                } catch (e: Exception) {
                  log.operation(
                    "更正电池失败",
                    detail = e.toString(),
                    level = LogLevel.WARNING,
                  )
                  AppSnack.error(snackbarHostState, OfficialCloudRedactor.errorMessage(e))
                } finally {
                  submitting = false
                }
              }
            },
            enabled = !submitting,
            shape = cyberButtonShape,
            colors = cyberFilledButtonColors(),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp),
          ) {
            if (submitting) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = CyberHomeColors.white,
              )
            } else {
              Text(
                text = "确认提交",
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.W700),
              )
            }
          }
          Spacer(Modifier.height(12.dp))
          Text(
            text = "提交后将调用官方 batterySetUp 接口，并自动刷新车辆与电池信息。",
            style = TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
          )
        }
      }
    }
  }

  if (showDatePicker) {
    BindDatePickerDialog(
      currentDate = bindDate,
      onDismiss = { showDatePicker = false },
      onPick = { picked ->
        bindDate = picked
        showDatePicker = false
      },
    )
  }
}

private suspend fun bootstrap(
  vehicle: OfficialVehicle,
  cloudService: OfficialCloudService,
  onTypes: (List<OfficialBatteryType>, OfficialBatteryType?) -> Unit,
  onSpecs: (List<OfficialBatterySpec>, OfficialBatterySpec?) -> Unit,
  onPrefill: (String, String, LocalDate?) -> Unit,
  onError: (String) -> Unit,
) {
  // Prefill bind date from vehicle if present.
  val rawBind = vehicle.batteryBindDate.trim()
  val date = if (rawBind.length >= 10) {
    runCatching { LocalDate.parse(rawBind.substring(0, 10)) }.getOrNull()
  } else null

  // Prefill custom V/AH if type is custom and label looks like "48V20AH".
  val label = vehicle.batterySpecLabel.trim().uppercase()
  val match = Regex("""(\d+(?:\.\d+)?)\s*V\s*(\d+(?:\.\d+)?)\s*A?H?""").find(label)
  val vText = match?.groupValues?.getOrNull(1) ?: ""
  val ahText = match?.groupValues?.getOrNull(2) ?: ""
  onPrefill(vText, ahText, date)

  try {
    val types = cloudService.fetchBatteryTypes()
    var selected: OfficialBatteryType? = null
    val currentTypeId = vehicle.batteryTypeId.trim()
    if (currentTypeId.isNotEmpty()) {
      selected = types.firstOrNull { it.type == currentTypeId }
    }
    if (selected == null && types.isNotEmpty()) selected = types.first()
    onTypes(types, selected)

    if (selected != null && !selected.isCustom) {
      val specs = cloudService.fetchBatterySpecsByType(selected.type)
      val code = vehicle.raw["batterySpecCode"]?.toString()?.trim() ?: ""
      var selectedSpec: OfficialBatterySpec? = null
      if (code.isNotEmpty()) {
        selectedSpec = specs.firstOrNull { it.code == code }
      }
      if (selectedSpec == null && specs.isNotEmpty()) selectedSpec = specs.first()
      onSpecs(specs, selectedSpec)
    }
  } catch (e: Exception) {
    onError(OfficialCloudRedactor.errorMessage(e))
  }
}

@Composable
private fun VehicleBatterySummary(vehicle: OfficialVehicle) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(16.dp),
  ) {
    Text(
      text = vehicle.displayName,
      style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(4.dp))
    val spec = if (vehicle.batterySpecLabel.isEmpty()) "未设置规格" else vehicle.batterySpecLabel
    val date = if (vehicle.batteryBindDate.isEmpty()) "" else " · ${vehicle.batteryBindDate}"
    Text(
      text = "当前：$spec$date",
      style = TextStyle(fontSize = 13.sp, color = CyberHomeColors.inkMuted),
    )
  }
}

@Composable
private fun SectionCard(
  title: String,
  content: @Composable () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(14.dp),
  ) {
    Text(
      text = title,
      style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.inkSecondary),
    )
    Spacer(Modifier.height(10.dp))
    content()
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BindDatePickerDialog(
  currentDate: LocalDate?,
  onDismiss: () -> Unit,
  onPick: (LocalDate) -> Unit,
) {
  val state = rememberDatePickerState(
    initialSelectedDateMillis = currentDate?.toEpochDay()?.let { it * 24L * 60L * 60L * 1000L },
  )
  DatePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = @Composable {
      Button(
        onClick = {
          val millis = state.selectedDateMillis
          if (millis != null) {
            onPick(LocalDate.ofEpochDay(millis / (24L * 60L * 60L * 1000L)))
          }
        },
        colors = cyberFilledButtonColors(),
        content = { Text("确定") },
      )
    },
    dismissButton = @Composable {
      TextButton(
        onClick = onDismiss,
        content = { Text("取消", color = CyberHomeColors.inkMuted) },
      )
    },
  )
}
