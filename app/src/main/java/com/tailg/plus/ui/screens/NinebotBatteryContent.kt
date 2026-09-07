package com.tailg.plus.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.R
import com.tailg.plus.data.model.BatteryDataSource
import com.tailg.plus.data.model.BatterySnapshot
import com.tailg.plus.data.model.BmsField
import com.tailg.plus.ui.components.AnimatedValueText
import com.tailg.plus.ui.components.MotionPolicy
import com.tailg.plus.ui.components.NinebotIcon
import com.tailg.plus.ui.components.NinebotLucide
import com.tailg.plus.ui.theme.LocalDistanceUnitPreference
import com.tailg.plus.util.distanceUnitSuffix
import com.tailg.plus.util.formatDistanceKilometersText
import com.tailg.plus.util.formatDistanceKilometersValue
import com.tailg.plus.util.formatFixed

internal val BatteryNumberFont = FontFamily(Font(R.font.space_grotesk))
private val BatteryNumberPattern = Regex("-?\\d+(\\.\\d+)?")
private fun batteryNumber(raw: String?): Double? = raw?.let { BatteryNumberPattern.find(it)?.value?.toDoubleOrNull() }

@Immutable
internal data class NinebotBatteryState(
  val snapshot: BatterySnapshot,
  val signedIn: Boolean,
  val batteryLoading: Boolean = false,
  val bmsLoading: Boolean = false,
  val batteryError: String? = null,
  val bmsError: String? = null,
  val lastSync: String? = null,
  val coulomb: NinebotCoulombState? = null,
) {
  val loading: Boolean get() = batteryLoading || bmsLoading
  val canRefresh: Boolean get() = signedIn && snapshot.officialVehicle != null && !loading
}

@Immutable
internal data class NinebotCoulombState(
  val busy: Boolean,
  val enabled: Boolean?,
  val message: String?,
  val bleReady: Boolean,
)

@Immutable
internal data class BatteryPalette(val dark: Boolean) {
  val page = if (dark) Color(0xFF111419) else Color(0xFFF4F5F7)
  val surface = if (dark) Color(0xFF1D232C) else Color.White
  val soft = if (dark) Color(0xFF171C24) else Color(0xFFEBEEF3)
  val ink = if (dark) Color(0xFFF4F6FA) else Color(0xFF171D29)
  val muted = if (dark) Color(0xFFA0ABBC) else Color(0xFF5F6B7D)
  val line = if (dark) Color(0xFF303947) else Color(0xFFDCE1E9)
  val blue = if (dark) Color(0xFF93B8FF) else Color(0xFF285DD1)
  val blueSoft = if (dark) Color(0xFF202F49) else Color(0xFFE4ECFF)
  val warning = if (dark) Color(0xFFFFC478) else Color(0xFF935512)
  val danger = if (dark) Color(0xFFFFA298) else Color(0xFFB74437)
}

private enum class BatteryHelp { CYCLES, SCORE, CARE, SWAP }

/** Ninebot's presentation layer. All vehicle operations stay in BatteryDetailsScreen. */
@Composable
internal fun NinebotBatteryContent(
  state: NinebotBatteryState,
  onBack: () -> Unit,
  onRefresh: () -> Unit,
  onCorrect: () -> Unit,
  onAccount: () -> Unit,
  onCoulombToggle: (Boolean) -> Unit,
  onCoulombRefresh: () -> Unit,
  modifier: Modifier = Modifier,
  snackbarHost: @Composable () -> Unit = {},
) {
  val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val palette = remember(dark) { BatteryPalette(dark) }
  val listState = rememberLazyListState()
  val heroMotionActive by remember(listState) {
    derivedStateOf { !listState.isScrollInProgress && listState.firstVisibleItemIndex == 0 }
  }
  var tab by rememberSaveable(state.snapshot.officialVehicle?.key) { mutableIntStateOf(0) }
  var help by rememberSaveable { mutableStateOf<BatteryHelp?>(null) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = palette.page,
    snackbarHost = snackbarHost,
    topBar = {
      Column(Modifier.background(palette.page).windowInsetsPadding(WindowInsets.statusBars)) {
        BatteryToolbar(palette, state.loading, state.canRefresh, onBack, onRefresh)
      }
    },
  ) { insets ->
    Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
      LazyColumn(
        state = listState,
        modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("ninebot-battery-list"),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 28.dp),
      ) {
        item(key = "hero", contentType = "hero") {
          NinebotBatteryHero(state.snapshot, palette, motionActive = heroMotionActive && help == null)
        }
        item(key = "sync", contentType = "sync") {
          BatterySyncStatus(state, palette, onRefresh, onAccount)
          Spacer(Modifier.height(26.dp))
        }
        item(key = "tabs", contentType = "tabs") {
          BatteryTabs(tab, palette) { tab = it }
          Spacer(Modifier.height(24.dp))
        }
        if (tab == 0) {
          item(key = "metrics", contentType = "metrics") {
            BatterySectionHeading(stringResource(R.string.nb_battery_metrics), "02", palette)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.nb_battery_metrics_caption), color = palette.muted, fontSize = 12.sp)
            Spacer(Modifier.height(20.dp))
            BatteryMetrics(state.snapshot, palette) { help = BatteryHelp.CYCLES }
            Spacer(Modifier.height(20.dp))
          }
          item(key = "health", contentType = "health") {
            BatteryHealth(state.snapshot, palette) { help = BatteryHelp.SCORE }
            Spacer(Modifier.height(30.dp))
          }
        } else {
          item(key = "binding", contentType = "binding") {
            BatteryBinding(state.snapshot, palette)
            Spacer(Modifier.height(20.dp))
          }
          item(key = "bms", contentType = "bms") {
            BatteryBmsDetails(state, palette, onRefresh)
            Spacer(Modifier.height(20.dp))
          }
          state.coulomb?.let { coulomb ->
            item(key = "coulomb", contentType = "coulomb") {
              BatteryCoulomb(coulomb, palette, onCoulombToggle, onCoulombRefresh)
              Spacer(Modifier.height(20.dp))
            }
          }
        }
        item(key = "services", contentType = "services") {
          BatteryEyebrow(stringResource(R.string.nb_battery_services_eyebrow), palette.muted)
          Spacer(Modifier.height(8.dp))
          BatterySectionHeading(stringResource(R.string.nb_battery_services), null, palette)
          Spacer(Modifier.height(16.dp))
          Column(Modifier.clip(RoundedCornerShape(24.dp)).background(palette.surface)) {
            BatteryServiceRow(
              NinebotLucide.slidersHorizontal,
              stringResource(R.string.nb_battery_correct),
              stringResource(R.string.nb_battery_correct_note),
              palette,
              enabled = state.signedIn && state.snapshot.officialVehicle != null,
              onClick = onCorrect,
            )
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = palette.line)
            BatteryServiceRow(
              NinebotLucide.circleHelp,
              stringResource(R.string.nb_battery_care),
              stringResource(R.string.nb_battery_care_note),
              palette,
              onClick = { help = BatteryHelp.CARE },
            )
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = palette.line)
            BatteryServiceRow(
              NinebotLucide.swapHorizontal,
              stringResource(R.string.nb_battery_swap),
              stringResource(if (state.snapshot.officialVehicle?.shareCarFlag == true) R.string.nb_battery_swap_shared else R.string.nb_battery_swap_note),
              palette,
              enabled = state.signedIn && state.snapshot.officialVehicle != null && state.snapshot.officialVehicle.shareCarFlag != true,
              onClick = { help = BatteryHelp.SWAP },
            )
          }
          Spacer(Modifier.height(30.dp))
          Text(
            stringResource(R.string.nb_battery_footer),
            modifier = Modifier.fillMaxWidth(),
            color = palette.muted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
          )
          Spacer(Modifier.height(8.dp))
          BatteryEyebrow(stringResource(R.string.nb_battery_footer_label), palette.muted, Modifier.fillMaxWidth(), TextAlign.Center)
        }
      }
    }
  }
  help?.let { selected -> BatteryHelpSheet(selected, palette) { help = null } }
}

@Composable
private fun BatteryToolbar(p: BatteryPalette, loading: Boolean, canRefresh: Boolean, onBack: () -> Unit, onRefresh: () -> Unit) {
  Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Row(Modifier.widthIn(max = 768.dp).fillMaxWidth().height(60.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
      BatteryIconButton(NinebotLucide.arrowLeft, stringResource(R.string.common_back), p, onClick = onBack)
      Text(
        stringResource(R.string.nb_battery_title),
        modifier = Modifier.weight(1f),
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = p.ink,
        textAlign = TextAlign.Center,
      )
      if (loading) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(Modifier.size(20.dp), color = p.blue, strokeWidth = 1.8.dp)
        }
      } else {
        BatteryIconButton(NinebotLucide.refreshCw, stringResource(R.string.nb_battery_sync), p, canRefresh, onRefresh)
      }
    }
  }
}

@Composable
private fun NinebotBatteryHero(snapshot: BatterySnapshot, p: BatteryPalette, motionActive: Boolean) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    BatteryEyebrow(stringResource(R.string.nb_battery_eyebrow), p.blue)
    Spacer(Modifier.width(12.dp))
    Text(
      snapshot.officialVehicle?.displayName ?: stringResource(R.string.nb_battery_title),
      modifier = Modifier.weight(1f),
      color = p.muted,
      fontSize = 12.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.End,
    )
  }
  Spacer(Modifier.height(14.dp))
  Text(
    stringResource(R.string.nb_battery_headline),
    color = p.ink,
    fontSize = 29.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = (-1).sp,
    modifier = Modifier.semantics { heading() },
  )
  val percent = snapshot.percent
  val accent = when {
    snapshot.faults.isNotEmpty() -> p.danger
    percent == null -> p.muted
    percent <= 20 -> p.warning
    else -> p.blue
  }
  val status = stringResource(when {
    snapshot.faults.isNotEmpty() -> R.string.nb_battery_attention
    percent == null -> R.string.nb_battery_waiting
    percent <= 20 -> R.string.nb_battery_low
    else -> R.string.nb_battery_ready
  })
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val stacked = maxWidth < 300.dp || LocalDensity.current.fontScale > 1.2f
    val numberSize = when {
      stacked -> 96.sp
      percent == 100 -> (maxWidth.value * 0.25f).coerceAtMost(96f).sp
      else -> (maxWidth.value * 0.32f).coerceAtMost(122f).sp
    }
    val number: @Composable () -> Unit = {
      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.size(5.dp).background(accent, CircleShape))
          Spacer(Modifier.width(7.dp))
          Text(status, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.Bottom) {
          AnimatedValueText(
            value = percent?.toString() ?: "—",
            style = TextStyle(
              fontFamily = BatteryNumberFont,
              fontSize = numberSize,
              lineHeight = numberSize,
              letterSpacing = (-6).sp,
              color = p.ink,
              fontFeatureSettings = "tnum",
            ),
            maxLines = 1,
          )
          if (percent != null) Text("%", color = p.muted, fontFamily = BatteryNumberFont, fontSize = 24.sp, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.nb_battery_charge), color = p.muted, fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
        BatterySegmentMeter(percent?.div(100f), accent, p.line, Modifier.width(112.dp).height(14.dp))
      }
    }
    if (stacked) {
      Column(Modifier.padding(top = 24.dp)) {
        number()
        NinebotBatteryVisual(percent, p, motionActive, Modifier.fillMaxWidth().height(254.dp))
      }
    } else {
      Row(Modifier.fillMaxWidth().height(254.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { number() }
        NinebotBatteryVisual(percent, p, motionActive, Modifier.weight(1f).height(254.dp))
      }
    }
  }
  BatteryRange(snapshot)
  Spacer(Modifier.height(14.dp))
}

@Composable
private fun BatteryRange(snapshot: BatterySnapshot) {
  val unit = LocalDistanceUnitPreference.current
  val distance = batteryNumber(snapshot.remainingMileage)?.takeIf { it.isFinite() && it >= 0 }
  val value = distance?.let { formatDistanceKilometersValue(it, unit) } ?: "—"
  Box(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
      .background(Brush.linearGradient(listOf(Color(0xFF214EC5), Color(0xFF2969E8))))
      .testTag("ninebot-battery-range"),
  ) {
    Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
      repeat(6) { index ->
        val x = size.width * 0.80f + index * 22.dp.toPx()
        val path = Path().apply {
          moveTo(x, -20.dp.toPx())
          cubicTo(x - 110.dp.toPx(), size.height * 0.35f, x + 60.dp.toPx(), size.height * 0.7f, x - 60.dp.toPx(), size.height + 24.dp.toPx())
        }
        drawPath(path, Color.White.copy(alpha = 0.12f - index * 0.012f), style = Stroke(1.dp.toPx()))
      }
    }
    Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(stringResource(R.string.nb_battery_range), color = Color.White, fontSize = 12.sp)
          Row(verticalAlignment = Alignment.Bottom) {
            AnimatedValueText(value, TextStyle(fontFamily = BatteryNumberFont, fontSize = 42.sp, color = Color.White, letterSpacing = (-1.5).sp))
            Spacer(Modifier.width(7.dp))
            Text(distanceUnitSuffix(unit), color = Color.White, fontFamily = BatteryNumberFont, fontSize = 16.sp, modifier = Modifier.padding(bottom = 7.dp))
          }
        }
        Box(Modifier.size(46.dp).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape), contentAlignment = Alignment.Center) {
          NinebotIcon(NinebotLucide.route, size = 22.dp, color = Color.White)
        }
      }
      Text(
        stringResource(when {
          distance == null -> R.string.nb_battery_read_pending
          batteryNumber(snapshot.officialBatteryInfo?.remainingMileage) == null -> R.string.nb_battery_estimate_note
          else -> R.string.nb_battery_range_note
        }),
        color = Color.White,
        fontSize = 10.sp,
        lineHeight = 15.sp,
      )
    }
  }
}

@Composable
private fun BatterySyncStatus(state: NinebotBatteryState, p: BatteryPalette, onRefresh: () -> Unit, onAccount: () -> Unit) {
  val hasVehicle = state.snapshot.officialVehicle != null
  val hasError = state.batteryError != null || state.bmsError != null
  val title = when {
    !state.signedIn -> stringResource(R.string.nb_battery_sign_in)
    !hasVehicle -> stringResource(R.string.nb_battery_choose_vehicle)
    state.loading -> stringResource(R.string.nb_battery_syncing)
    hasError -> stringResource(R.string.nb_battery_sync_error)
    state.lastSync != null -> stringResource(R.string.nb_battery_sync_last, state.lastSync)
    state.snapshot.hasOfficialBatteryInfo || state.snapshot.hasOfficialBmsInfo -> stringResource(R.string.nb_battery_sync_ready)
    state.snapshot.percent != null -> stringResource(R.string.nb_battery_vehicle_data)
    else -> stringResource(R.string.nb_battery_waiting)
  }
  val subtitle = stringResource(when {
    !state.signedIn -> R.string.nb_battery_sign_in_note
    !hasVehicle -> R.string.nb_battery_choose_vehicle_note
    state.loading -> R.string.nb_battery_syncing_note
    hasError -> R.string.nb_battery_sync_error_note
    !state.snapshot.hasOfficialBatteryInfo && !state.snapshot.hasOfficialBmsInfo && state.snapshot.percent != null -> R.string.nb_battery_vehicle_data_note
    else -> R.string.nb_battery_sync_note
  })
  val color = if (hasError && !state.loading) p.warning else p.blue
  Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.soft)
      .clickable(enabled = !state.loading, role = Role.Button) { if (state.signedIn && hasVehicle) onRefresh() else onAccount() }
      .padding(horizontal = 14.dp, vertical = 13.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 1.5.dp, color = color)
    else NinebotIcon(if (hasError) NinebotLucide.triangleAlert else NinebotLucide.cloudDownload, color = color, size = 18.dp)
    Spacer(Modifier.width(11.dp))
    Column(Modifier.weight(1f)) {
      Text(title, color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
      Spacer(Modifier.height(3.dp))
      Text(subtitle, color = p.muted, fontSize = 10.sp, lineHeight = 15.sp)
    }
    Spacer(Modifier.width(8.dp))
    NinebotIcon(NinebotLucide.chevronRight, color = p.muted, size = 16.dp)
  }
}

@Composable
private fun BatteryTabs(selected: Int, p: BatteryPalette, onSelect: (Int) -> Unit) {
  val reduceMotion = MotionPolicy.reduceMotion()
  val position = animateFloatAsState(selected.toFloat(), if (reduceMotion) snap() else spring(dampingRatio = 0.82f, stiffness = 450f), label = "batteryTab")
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val tabWidth = maxWidth / 2
    Column {
      Row(Modifier.fillMaxWidth().selectableGroup()) {
        listOf(R.string.nb_battery_overview, R.string.nb_battery_dossier).forEachIndexed { index, label ->
          Row(
            Modifier.weight(1f).heightIn(min = 52.dp).selectable(selected == index, role = Role.Tab, onClick = { onSelect(index) })
              .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("0${index + 1}", fontFamily = BatteryNumberFont, fontSize = 10.sp, color = if (selected == index) p.blue else p.muted)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(label), fontSize = 15.sp, fontWeight = if (selected == index) FontWeight.SemiBold else FontWeight.Normal, color = if (selected == index) p.ink else p.muted)
          }
        }
      }
      Box(Modifier.fillMaxWidth().height(2.dp).background(p.line)) {
        Box(Modifier.width(tabWidth).height(2.dp).graphicsLayer { translationX = tabWidth.toPx() * position.value }.background(p.blue))
      }
    }
  }
}

@Composable
private fun BatteryMetrics(snapshot: BatterySnapshot, p: BatteryPalette, onCycles: () -> Unit) {
  val missing = stringResource(R.string.nb_battery_read_pending)
  val today = batteryNumber(snapshot.consumePowerPercent)?.takeIf { it.isFinite() && it >= 0.0 }
  val cycleCount = BatterySnapshot.displayMetric(snapshot.loopCount, missing = "—")
  val voltage = snapshot.voltage?.takeIf { it.isFinite() }?.let { formatFixed(it, 1) } ?: "—"
  val temperature = snapshot.temperature?.takeIf { it.isFinite() }?.let { formatFixed(it, if (it % 1.0 == 0.0) 0 else 1) } ?: "—"
  BoxWithConstraints {
    val stacked = maxWidth < 300.dp || LocalDensity.current.fontScale > 1.35f
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.surface)) {
      val metrics = listOf(
        BatteryMetric(NinebotLucide.zap, stringResource(R.string.nb_battery_voltage), voltage, if (voltage == "—") missing else "V"),
        BatteryMetric(NinebotLucide.thermometer, stringResource(R.string.nb_battery_temperature), temperature, if (temperature == "—") missing else "°C"),
        BatteryMetric(NinebotLucide.rotateCcw, stringResource(R.string.nb_battery_cycles), cycleCount, if (cycleCount == "—") missing else stringResource(R.string.nb_battery_times), onCycles),
        BatteryMetric(NinebotLucide.activity, stringResource(R.string.nb_battery_today), today?.let { formatFixed(it, if (it % 1.0 == 0.0) 0 else 1) } ?: "—", if (today == null) missing else "%"),
      )
      metrics.chunked(if (stacked) 1 else 2).forEachIndexed { rowIndex, row ->
        if (rowIndex != 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = p.line)
        Row(Modifier.fillMaxWidth()) {
          row.forEachIndexed { index, metric ->
            Box(Modifier.weight(1f)) { BatteryMetricCell(metric, p) }
            if (index == 0 && !stacked) Box(Modifier.padding(top = 22.dp).width(1.dp).height(76.dp).background(p.line))
          }
        }
      }
    }
  }
}

private data class BatteryMetric(@DrawableRes val icon: Int, val label: String, val value: String, val unit: String, val onHelp: (() -> Unit)? = null)

@Composable
private fun BatteryMetricCell(metric: BatteryMetric, p: BatteryPalette) {
  val helpLabel = stringResource(R.string.nb_battery_cycles_details)
  Column(
    Modifier.fillMaxWidth().then(if (metric.onHelp != null) Modifier.clickable(role = Role.Button, onClickLabel = helpLabel, onClick = metric.onHelp) else Modifier)
      .padding(horizontal = 18.dp, vertical = 19.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      NinebotIcon(metric.icon, size = 16.dp, color = p.blue)
      Spacer(Modifier.width(8.dp))
      Text(metric.label, color = p.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
      if (metric.onHelp != null) NinebotIcon(NinebotLucide.circleHelp, size = 13.dp, color = p.muted)
    }
    Spacer(Modifier.height(13.dp))
    Text(metric.value, color = p.ink, fontFamily = BatteryNumberFont, fontSize = 32.sp, letterSpacing = (-1).sp)
    Text(metric.unit, color = p.muted, fontSize = 11.sp)
  }
}

@Composable
private fun BatteryHealth(snapshot: BatterySnapshot, p: BatteryPalette, onClick: () -> Unit) {
  val score = batteryNumber(snapshot.batteryScore)?.takeIf { it.isFinite() && it in 0.0..100.0 }
  val faulted = snapshot.faults.isNotEmpty()
  val accent = if (faulted) p.danger else p.blue
  Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.blueSoft)
      .clickable(role = Role.Button, onClickLabel = stringResource(R.string.nb_battery_score_details), onClick = onClick)
      .padding(20.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          NinebotIcon(if (faulted) NinebotLucide.triangleAlert else NinebotLucide.activity, size = 18.dp, color = accent)
          Spacer(Modifier.width(8.dp))
          Text(stringResource(R.string.nb_battery_score), color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
          Spacer(Modifier.width(6.dp))
          NinebotIcon(NinebotLucide.circleHelp, color = p.muted, size = 13.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
          if (faulted) snapshot.faults.joinToString(" · ") else stringResource(if (score == null) R.string.nb_battery_score_pending else R.string.nb_battery_score_note),
          color = if (faulted) p.danger else p.muted,
          fontSize = 11.sp,
          lineHeight = 17.sp,
        )
      }
      Spacer(Modifier.width(16.dp))
      Column(horizontalAlignment = Alignment.End) {
        Text(score?.let { formatFixed(it, if (it % 1.0 == 0.0) 0 else 1) } ?: "—", color = p.ink, fontFamily = BatteryNumberFont, fontSize = 38.sp, letterSpacing = (-2).sp)
        if (score != null) Text(stringResource(R.string.nb_battery_score_unit), color = p.muted, fontSize = 10.sp)
      }
    }
    Spacer(Modifier.height(18.dp))
    BatterySegmentMeter(score?.div(100)?.toFloat(), accent, p.line, Modifier.fillMaxWidth().height(20.dp))
  }
}

@Composable
private fun BatteryBinding(snapshot: BatterySnapshot, p: BatteryPalette) {
  val vehicle = snapshot.officialVehicle
  val pending = stringResource(R.string.nb_battery_read_pending)
  val unit = LocalDistanceUnitPreference.current
  BatterySectionHeading(stringResource(R.string.nb_battery_dossier), "02", p)
  Spacer(Modifier.height(18.dp))
  Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.surface).padding(20.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      NinebotIcon(NinebotLucide.battery, color = p.blue, size = 22.dp)
      Spacer(Modifier.width(10.dp))
      Text(stringResource(R.string.nb_battery_spec), color = p.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
      if (vehicle != null) Text(stringResource(R.string.nb_battery_bound), color = p.blue, fontSize = 10.sp, modifier = Modifier.clip(CircleShape).background(p.blueSoft).padding(horizontal = 10.dp, vertical = 5.dp))
    }
    Spacer(Modifier.height(16.dp))
    SelectionContainer {
      Text(vehicle?.batterySpecLabel?.takeIf { it.isNotBlank() } ?: pending, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = p.ink)
    }
    Spacer(Modifier.height(18.dp))
    HorizontalDivider(color = p.line)
    BatteryDataRow(stringResource(R.string.nb_battery_capacity), BatterySnapshot.displayMetric(snapshot.capacitance, missing = pending), p)
    BatteryDataRow(stringResource(R.string.nb_battery_bind_date), vehicle?.batteryBindDate?.takeIf { it.isNotBlank() } ?: pending, p)
    BatteryDataRow(stringResource(R.string.nb_battery_type_id), vehicle?.batteryTypeId?.takeIf { it.isNotBlank() } ?: pending, p)
    BatteryDataRow(stringResource(R.string.nb_battery_total_distance), formatDistanceKilometersText(snapshot.totalMileage, unit, missing = pending), p)
  }
}

@Composable
private fun BatteryBmsDetails(state: NinebotBatteryState, p: BatteryPalette, onRefresh: () -> Unit) {
  var expanded by rememberSaveable(state.snapshot.officialVehicle?.key) { mutableStateOf(false) }
  val fields = remember(state.snapshot) { state.snapshot.bms.fields }
  val reduceMotion = MotionPolicy.reduceMotion()
  val expandedDescription = stringResource(if (expanded) R.string.nb_battery_expanded else R.string.nb_battery_collapsed)
  val chevron = animateFloatAsState(if (expanded) 180f else 0f, if (reduceMotion) snap() else tween(220), label = "bmsChevron")
  Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.surface)) {
    Row(
      Modifier.fillMaxWidth().semantics { stateDescription = expandedDescription }.clickable(role = Role.Button) { expanded = !expanded }.padding(20.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      NinebotIcon(NinebotLucide.cpu, size = 22.dp, color = p.blue)
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text(stringResource(R.string.nb_battery_bms), color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.nb_battery_bms_count, fields.count { BatterySnapshot.displayMetric(it.value, missing = "").isNotEmpty() }, fields.size), color = p.muted, fontSize = 11.sp)
      }
      if (state.bmsLoading) CircularProgressIndicator(Modifier.size(18.dp), color = p.blue, strokeWidth = 1.5.dp)
      else NinebotIcon(NinebotLucide.chevronDown, Modifier.graphicsLayer { rotationZ = chevron.value }, color = p.muted, size = 18.dp)
    }
    if (state.bmsError != null) {
      Row(
        Modifier.fillMaxWidth().clickable(enabled = state.canRefresh, role = Role.Button, onClick = onRefresh).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        NinebotIcon(NinebotLucide.triangleAlert, color = p.warning, size = 16.dp)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(if (state.snapshot.hasOfficialBmsInfo) R.string.nb_battery_bms_stale else R.string.nb_battery_bms_error), color = p.warning, fontSize = 12.sp, modifier = Modifier.weight(1f))
      }
    }
    AnimatedVisibility(
      expanded,
      enter = fadeIn(tween(if (reduceMotion) 0 else 180)) + expandVertically(tween(if (reduceMotion) 0 else 260)),
      exit = fadeOut(tween(if (reduceMotion) 0 else 120)) + shrinkVertically(tween(if (reduceMotion) 0 else 220)),
    ) {
      Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 10.dp)) {
        fields.forEach { field ->
          HorizontalDivider(color = p.line)
          BatteryBmsRow(field, p)
        }
      }
    }
  }
}

@Composable
private fun BatteryBmsRow(field: BmsField, p: BatteryPalette) {
  val label = when (field.label) {
    "估算容量" -> R.string.nb_battery_est_capacity
    "SOC" -> R.string.nb_battery_soc
    "SOH" -> R.string.nb_battery_soh
    "当前电压" -> R.string.nb_battery_voltage
    "充电状态" -> R.string.nb_battery_charge_state
    "电池容量" -> R.string.nb_battery_capacity
    "电池电流" -> R.string.nb_battery_current
    "环境温度" -> R.string.nb_battery_ambient
    "循环次数" -> R.string.nb_battery_cycles
    "电池温度" -> R.string.nb_battery_temperature
    "电池类型" -> R.string.nb_battery_type
    "硬件版本" -> R.string.nb_battery_hardware
    "软件版本" -> R.string.nb_battery_software
    else -> null
  }
  val source = stringResource(when {
    BatterySnapshot.displayMetric(field.value, missing = "").isEmpty() -> R.string.nb_battery_source_pending
    field.source == BatteryDataSource.OFFICIAL_VEHICLE -> R.string.nb_battery_source_vehicle
    field.source == BatteryDataSource.OFFICIAL_BATTERY -> R.string.nb_battery_source_battery
    field.source == BatteryDataSource.OFFICIAL_BMS -> R.string.nb_battery_source_bms
    else -> R.string.nb_battery_source_pending
  })
  Column(Modifier.padding(vertical = 12.dp)) {
    Text(source, color = p.muted, fontSize = 10.sp)
    Spacer(Modifier.height(4.dp))
    BatteryDataRow(
      label?.let { stringResource(it) } ?: field.label,
      BatterySnapshot.displayMetric(field.value, unit = field.unit ?: "", missing = stringResource(R.string.nb_battery_read_pending)),
      p,
      padding = 0.dp,
    )
  }
}

@Composable
private fun BatteryCoulomb(state: NinebotCoulombState, p: BatteryPalette, onToggle: (Boolean) -> Unit, onRefresh: () -> Unit) {
  val label = stringResource(R.string.nb_battery_coulomb)
  Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.surface).padding(20.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      NinebotIcon(NinebotLucide.zap, size = 20.dp, color = p.blue)
      Spacer(Modifier.width(10.dp))
      Text(stringResource(R.string.nb_battery_coulomb), color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
      if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), color = p.blue, strokeWidth = 2.dp)
      else Switch(
        checked = state.enabled == true,
        onCheckedChange = onToggle,
        enabled = state.bleReady && state.enabled != null,
        modifier = Modifier.semantics { contentDescription = label },
        colors = SwitchDefaults.colors(checkedTrackColor = p.blue, checkedThumbColor = p.page, uncheckedTrackColor = p.soft, uncheckedBorderColor = p.line),
      )
    }
    Text(stringResource(R.string.nb_battery_coulomb_note), color = p.muted, fontSize = 12.sp, lineHeight = 18.sp)
    Spacer(Modifier.height(10.dp))
    Text(
      if (!state.bleReady) stringResource(R.string.nb_battery_coulomb_ble) else state.message ?: if (state.enabled == null) stringResource(R.string.nb_battery_coulomb_unknown) else "",
      color = p.warning,
      fontSize = 12.sp,
    )
    Row(
      Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = state.bleReady && !state.busy, role = Role.Button, onClick = onRefresh).padding(horizontal = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      NinebotIcon(NinebotLucide.refreshCw, color = if (state.bleReady) p.blue else p.muted, size = 16.dp)
      Spacer(Modifier.width(8.dp))
      Text(stringResource(R.string.nb_battery_coulomb_read), color = if (state.bleReady) p.blue else p.muted, fontSize = 12.sp)
    }
  }
}

@Composable
private fun BatteryServiceRow(@DrawableRes icon: Int, title: String, subtitle: String, p: BatteryPalette, enabled: Boolean = true, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 21.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    NinebotIcon(icon, color = if (enabled) p.ink else p.muted, size = 21.dp)
    Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f)) {
      Text(title, color = if (enabled) p.ink else p.muted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
      Spacer(Modifier.height(5.dp))
      Text(subtitle, color = p.muted, fontSize = 11.sp, lineHeight = 17.sp)
    }
    Spacer(Modifier.width(8.dp))
    NinebotIcon(NinebotLucide.arrowUpRight, color = if (enabled) p.ink else p.muted, size = 18.dp)
  }
}

@Composable
private fun BatteryDataRow(label: String, value: String, p: BatteryPalette, padding: Dp = 13.dp) {
  Row(Modifier.fillMaxWidth().padding(vertical = padding), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
    Text(label, color = p.muted, fontSize = 12.sp, modifier = Modifier.weight(0.43f))
    SelectionContainer(Modifier.weight(0.57f)) {
      Text(value, color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
    }
  }
}

@Composable
private fun BatterySectionHeading(title: String, index: String?, p: BatteryPalette) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(title, color = p.ink, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).semantics { heading() })
    if (index != null) Text(index, color = p.muted, fontFamily = BatteryNumberFont, fontSize = 12.sp)
  }
}

@Composable
internal fun BatteryEyebrow(text: String, color: Color, modifier: Modifier = Modifier, textAlign: TextAlign = TextAlign.Start) {
  Text(text, color = color, fontFamily = BatteryNumberFont, fontSize = 10.sp, letterSpacing = 1.7.sp, modifier = modifier, textAlign = textAlign)
}

@Composable
private fun BatteryIconButton(@DrawableRes icon: Int, label: String, p: BatteryPalette, enabled: Boolean = true, onClick: () -> Unit) {
  Box(
    Modifier.size(48.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = label },
    contentAlignment = Alignment.Center,
  ) { NinebotIcon(icon, color = if (enabled) p.ink else p.muted.copy(alpha = 0.5f), size = 21.dp) }
}

@Composable
private fun BatterySegmentMeter(fraction: Float?, accent: Color, track: Color, modifier: Modifier) {
  val reduceMotion = MotionPolicy.reduceMotion()
  val progress = animateFloatAsState(fraction ?: 0f, if (reduceMotion) snap() else spring(dampingRatio = 1f, stiffness = 90f), label = "batteryMeter")
  Canvas(modifier.then(if (fraction == null) Modifier.clearAndSetSemantics {} else Modifier.semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) })) {
    val count = 32
    val gap = minOf(3.dp.toPx(), size.width / (count * 2))
    val width = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
    repeat(count) { index ->
      val fill = (progress.value * count - index).coerceIn(0f, 1f)
      drawRoundRect(track, Offset(index * (width + gap), 0f), Size(width, size.height), CornerRadius(1.dp.toPx()))
      if (fill > 0f) drawRoundRect(accent.copy(alpha = fill), Offset(index * (width + gap), 0f), Size(width, size.height), CornerRadius(1.dp.toPx()))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatteryHelpSheet(help: BatteryHelp, p: BatteryPalette, onDismiss: () -> Unit) {
  val title = stringResource(when (help) {
    BatteryHelp.CYCLES -> R.string.nb_battery_cycles_title
    BatteryHelp.SCORE -> R.string.nb_battery_score_title
    BatteryHelp.CARE -> R.string.nb_battery_care_title
    BatteryHelp.SWAP -> R.string.nb_battery_swap
  })
  val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = p.page, contentColor = p.ink) {
    LazyColumn(modifier = Modifier.testTag("ninebot-battery-help"), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
      item {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(title, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = p.ink, modifier = Modifier.weight(1f).semantics { heading() })
          BatteryIconButton(NinebotLucide.x, stringResource(R.string.nb_battery_close), p, onClick = onDismiss)
        }
        Spacer(Modifier.height(20.dp))
      }
      if (help == BatteryHelp.CARE) {
        val sections = listOf(
          Triple(NinebotLucide.zap, R.string.nb_battery_care_charging, R.string.nb_battery_care_charging_body),
          Triple(NinebotLucide.thermometer, R.string.nb_battery_care_temperature, R.string.nb_battery_care_temperature_body),
          Triple(NinebotLucide.activity, R.string.nb_battery_care_service, R.string.nb_battery_care_service_body),
        )
        items(sections.size) { index ->
          val section = sections[index]
          Row(verticalAlignment = Alignment.CenterVertically) {
            NinebotIcon(section.first, color = p.blue, size = 20.dp)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(section.second), color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
          }
          Spacer(Modifier.height(10.dp))
          Text(stringResource(section.third), color = p.muted, fontSize = 14.sp, lineHeight = 24.sp)
          Spacer(Modifier.height(26.dp))
        }
      } else {
        item {
          Text(stringResource(when (help) {
            BatteryHelp.CYCLES -> R.string.nb_battery_cycles_body
            BatteryHelp.SWAP -> R.string.nb_battery_swap_body
            else -> R.string.nb_battery_score_body
          }), color = p.muted, fontSize = 14.sp, lineHeight = 25.sp)
        }
      }
    }
  }
}

/** Keep transient operation feedback in the same Lucide and color system. */
@Composable
internal fun NinebotBatterySnackbarHost(state: SnackbarHostState) {
  val p = BatteryPalette(MaterialTheme.colorScheme.background.luminance() < 0.5f)
  val dismiss = stringResource(R.string.nb_battery_dismiss)
  SnackbarHost(state, Modifier.padding(16.dp)) { data ->
    Surface(shape = RoundedCornerShape(20.dp), color = p.ink, contentColor = p.page) {
      Row(Modifier.padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        NinebotIcon(NinebotLucide.info, color = p.page, size = 20.dp)
        Spacer(Modifier.width(12.dp))
        Text(data.visuals.message, color = p.page, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
        Box(
          Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = { data.dismiss() })
            .semantics { contentDescription = dismiss },
          contentAlignment = Alignment.Center,
        ) { NinebotIcon(NinebotLucide.x, color = p.page, size = 18.dp) }
      }
    }
  }
}
