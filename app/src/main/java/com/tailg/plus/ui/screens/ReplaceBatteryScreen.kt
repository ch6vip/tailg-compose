package com.tailg.plus.ui.screens

import kotlinx.coroutines.flow.distinctUntilChanged

import kotlinx.coroutines.flow.map

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.tailg.plus.ui.components.material.LucideDatePicker
import com.tailg.plus.ui.components.material.isLucideDateSelectable
import com.tailg.plus.ui.components.material.lucideUtcDate
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppTouchTargets
import com.tailg.plus.ui.theme.CyberHomeColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R

/**
 * Port of `lib/pages/replace_battery_page.dart` — official stringResource(R.string.replace_battery_title)
 * flow (`ReplaceBatteryActivity` / `affirmBatteryInfo`).
 *
 * Bootstraps battery types + specs from the official cloud, pre-fills from
 * the selected vehicle, and submits an [AffirmBatteryInfoRequest]. The Dart
 * page uses `showDatePicker`; the Compose port retains Material date state
 * with the app's Lucide calendar and numeric date entry.
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
  val log = com.tailg.plus.di.rememberTailgEntryPoint().logService()
  val strSelectVehicle = stringResource(R.string.replace_battery_select_vehicle)
  val strNoTypes = stringResource(R.string.replace_battery_no_types)
  val strSelectType = stringResource(R.string.replace_battery_select_type)
  val strNoCarId = stringResource(R.string.replace_battery_no_car_id)
  val strEnterVoltage = stringResource(R.string.replace_battery_enter_voltage)
  val strEnterAh = stringResource(R.string.replace_battery_enter_ah)
  val strSelectSpec = stringResource(R.string.replace_battery_select_spec)
  val strSelectDate = stringResource(R.string.replace_battery_select_date)
  val strUpdated = stringResource(R.string.replace_battery_updated)
  val strUpdateFailed = stringResource(R.string.replace_battery_update_failed)

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

  val vehicleFlow = remember(cloudService) {
    cloudService.stateFlow.map { it.selectedVehicle }.distinctUntilChanged()
  }
  val vehicle by vehicleFlow.collectAsStateWithLifecycle(initialValue = cloudService.currentState.selectedVehicle)

  LaunchedEffect(vehicle?.key) {
    loadingTypes = true
    selectedType = null
    types = emptyList()
    val v = vehicle
    if (v == null) {
      loadingTypes = false
      error = strSelectVehicle
      return@LaunchedEffect
    }
    bootstrap(
      vehicle = v,
      cloudService = cloudService,
      onTypes = { loaded, selected ->
        types = loaded
        selectedType = selected
        loadingTypes = false
        error = if (loaded.isEmpty()) strNoTypes else null
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

  // Changing battery type cancels the previous request before it can publish its specs.
  LaunchedEffect(vehicle?.key, selectedType?.type) {
    specs = emptyList()
    selectedSpec = null
    loadingSpecs = false
    val type = selectedType ?: return@LaunchedEffect
    if (type.isCustom) return@LaunchedEffect
    loadingSpecs = true
    error = null
    try {
      val loaded = cloudService.fetchBatterySpecsByType(type.type)
      val code = vehicle?.raw?.get("batterySpecCode")?.toString()?.trim()
      specs = loaded
      selectedSpec = loaded.firstOrNull { it.code == code } ?: loaded.firstOrNull()
    } catch (e: Exception) {
      if (e is kotlinx.coroutines.CancellationException) throw e
      error = OfficialCloudRedactor.errorMessage(e)
    } finally {
      loadingSpecs = false
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
      CyberPageHeader(title = stringResource(R.string.replace_battery_title), onBack = { onBack(false) })
      if (loadingTypes) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .weight(1f),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(color = CyberHomeColors.primary)
        }
      } else {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .weight(1f)
            .imePadding()
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

          SectionCard(title = stringResource(R.string.replace_battery_type)) {
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
                  val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "battery-type-menu")
                  LucideIcon(Lucide.chevronDown, modifier = Modifier.rotate(rotation))
                },
                colors = cyberTextFieldColors(),
                shape = cyberTextFieldShape,
                modifier = Modifier
                  .fillMaxWidth()
                  .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable),
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
                    },
                  )
                }
              }
            }
          }
          Spacer(Modifier.height(12.dp))

          if (custom) {
            SectionCard(title = stringResource(R.string.replace_battery_custom)) {
              Row {
                OutlinedTextField(
                  value = voltage,
                  onValueChange = { value -> voltage = value.filter { it.isDigit() || it == '.' } },
                  enabled = !submitting,
                  singleLine = true,
                  label = { Text(stringResource(R.string.replace_battery_voltage)) },
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
                  label = { Text(stringResource(R.string.replace_battery_ah)) },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                  colors = cyberTextFieldColors(),
                  shape = cyberTextFieldShape,
                  modifier = Modifier.weight(1f),
                )
              }
            }
          } else {
            SectionCard(title = stringResource(R.string.replace_battery_spec)) {
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
                      val rotation by animateFloatAsState(if (specExpanded) 180f else 0f, label = "battery-spec-menu")
                      LucideIcon(Lucide.chevronDown, modifier = Modifier.rotate(rotation))
                    },
                    colors = cyberTextFieldColors(),
                    shape = cyberTextFieldShape,
                    modifier = Modifier
                      .fillMaxWidth()
                      .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable),
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

          SectionCard(title = stringResource(R.string.replace_battery_bind_date)) {
            val dateLabel = bindDate?.format(DateTimeFormatter.ISO_LOCAL_DATE) ?: strSelectDate
            AppPressable(
              onClick = {
                if (!submitting) {
                  showDatePicker = true
                }
              },
              enabled = !submitting,
              shape = RoundedCornerShape(AppRadii.tile),
              semanticsLabel = stringResource(R.string.replace_battery_select_date_format, dateLabel),
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
                scope.launch { AppSnack.error(snackbarHostState, strSelectVehicle) }
                return@Button
              }
              if (tt == null) {
                scope.launch { AppSnack.error(snackbarHostState, strSelectType) }
                return@Button
              }
              val carId = vv.carId.trim()
              if (carId.isEmpty()) {
                scope.launch { AppSnack.error(snackbarHostState, strNoCarId) }
                return@Button
              }
              val request: AffirmBatteryInfoRequest
              if (tt.isCustom) {
                val vText = voltage.trim()
                val ahText = ah.trim()
                if (vText.isEmpty()) {
                  scope.launch { AppSnack.error(snackbarHostState, strEnterVoltage) }
                  return@Button
                }
                if (ahText.isEmpty()) {
                  scope.launch { AppSnack.error(snackbarHostState, strEnterAh) }
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
                  scope.launch { AppSnack.error(snackbarHostState, strSelectSpec) }
                  return@Button
                }
                if (bindDate == null) {
                  scope.launch { AppSnack.error(snackbarHostState, strSelectDate) }
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
                  AppSnack.success(snackbarHostState, strUpdated)
                  onBack(true)
                } catch (e: Exception) {
                  if (e is kotlinx.coroutines.CancellationException) throw e
                  log.operation(
                    strUpdateFailed,
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
                text = stringResource(R.string.replace_battery_submit),
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.W700),
              )
            }
          }
          Spacer(Modifier.height(12.dp))
          Text(
            text = stringResource(R.string.replace_battery_submit_desc),
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

  } catch (e: Exception) {
    if (e is kotlinx.coroutines.CancellationException) throw e
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
    val spec = if (vehicle.batterySpecLabel.isEmpty()) stringResource(R.string.replace_battery_no_spec) else vehicle.batterySpecLabel
    val date = if (vehicle.batteryBindDate.isEmpty()) "" else " · ${vehicle.batteryBindDate}"
    Text(
      text = stringResource(R.string.replace_battery_current_format, spec, date),
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
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp)
        .widthIn(max = 400.dp).fillMaxWidth().heightIn(max = 620.dp),
      shape = MaterialTheme.shapes.extraLarge,
      color = CyberHomeColors.card,
    ) {
      Column {
        LucideDatePicker(
          state = state,
          modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        )
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
          TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
          Button(
            onClick = {
              state.selectedDateMillis?.let { millis ->
                val date = lucideUtcDate(millis)
                if (state.isLucideDateSelectable(date)) onPick(date)
              }
            },
            enabled = state.selectedDateMillis?.let { state.isLucideDateSelectable(lucideUtcDate(it)) } == true,
            colors = cyberFilledButtonColors(),
          ) { Text(stringResource(R.string.common_confirm)) }
        }
      }
    }
  }
}
