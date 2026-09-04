package com.tailg.plus.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tailg.plus.R
import com.tailg.plus.data.cloud.OfficialCloudRedactor
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.model.OfficialRidePeriod
import com.tailg.plus.data.model.OfficialRideStatistics
import com.tailg.plus.data.model.carbonTitle
import com.tailg.plus.data.model.mileageTitle
import com.tailg.plus.data.model.tabLabel
import com.tailg.plus.data.preferences.DistanceUnitPreference
import com.tailg.plus.ui.components.AnimatedValueText
import com.tailg.plus.ui.components.AppMotion
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.components.CyberHeaderAction
import com.tailg.plus.ui.components.CyberPageHeader
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.components.MotionPolicy
import com.tailg.plus.ui.components.ScaleToFit
import com.tailg.plus.ui.components.cyberButtonShape
import com.tailg.plus.ui.components.cyberCaptionStyle
import com.tailg.plus.ui.components.cyberFilledButtonColors
import com.tailg.plus.ui.navigation.Routes
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.CyberHomeColors
import com.tailg.plus.ui.theme.LocalDistanceUnitPreference
import com.tailg.plus.util.distanceUnitSuffix
import com.tailg.plus.util.formatSpeedKilometersPerHourValue
import com.tailg.plus.util.speedUnitSuffix
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import kotlin.math.cos
import kotlin.math.sin

@Composable
private fun rideNotice(): String =
  stringResource(R.string.ride_stats_desc_1) +
    stringResource(R.string.ride_stats_desc_2) +
    stringResource(R.string.ride_stats_desc_3)

/**
 * Redesign of `lib/pages/ride_stats_page.dart` — ride statistics as a
 * data-driven cockpit: a freeform radial mileage dial floats over the page
 * backdrop, and a single rounded "sheet" caps it holding the period selector,
 * the day/week/month mileage breakdown, the eco impact, and the metrics grid.
 *
 * Design notes:
 * - Hero dial is a hand-drawn [Canvas] arc whose sweep tracks
 *   `period mileage / max(day, week, month)` so the ring always reads as a
 *   filled gauge; a tip dot marks the live end of the arc.
 * - Entrance is a single clock-driven cascade ([entranceSection]) — hero first,
 *   then the sheet slides up — honouring [MotionPolicy.reduceMotion].
 * - Numbers use [AnimatedValueText] so a period switch cross-fades values.
 * - All glyphs come from the [Lucide] map; no emoji anywhere.
 *
 * Navigation: [onBack] pops; gates route to login / add-vehicle via [Routes].
 */
@Composable
fun RideStatsScreen(
  vehicleId: String,
  onBack: () -> Unit,
  cloudService: OfficialCloudService,
  modifier: Modifier = Modifier,
  onNavigate: (String) -> Unit = {},
) {
  val scope = rememberCoroutineScope()
  val cloudState by cloudService.stateFlow.collectAsStateWithLifecycle()

  var period by remember { mutableStateOf(OfficialRidePeriod.DAY) }
  var statistics by remember { mutableStateOf<OfficialRideStatistics?>(null) }
  var loading by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var gate by remember { mutableStateOf(RideStatsGate.READY) }
  var showInfoSheet by remember { mutableStateOf<InfoSheetContent?>(null) }
  val strRideNotice = rideNotice()
  val strHelp = stringResource(R.string.ride_stats_help)
  val strCarbonHelp = stringResource(R.string.ride_stats_carbon_help)
  val strCarbonDesc = stringResource(R.string.ride_stats_carbon_desc)
  val strTreeHelp = stringResource(R.string.ride_stats_tree_help)
  val strTreeDesc = stringResource(R.string.ride_stats_tree_desc)

  // Initial load.
  LaunchedEffect(Unit) {
    val cloud = cloudService.currentState
    if (cloud.ridePeriod == period) {
      statistics = cloud.rideStatistics
    }
    loadStatistics(
      scope = scope,
      cloudService = cloudService,
      period = period,
      onGate = { gate = it },
      onLoading = { loading = it },
      onError = { error = it },
      onStatistics = { statistics = it },
    )
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = CyberHomeColors.pageBg,
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
    ) {
      CyberPageHeader(
        title = stringResource(R.string.ride_stats_title),
        onBack = onBack,
        actions = {
          CyberHeaderAction(
            icon = Lucide.help,
            label = stringResource(R.string.ride_stats_view_help),
            onTap = { showInfoSheet = InfoSheetContent(strHelp, strRideNotice) },
          )
        },
      )
      Box(
        modifier = Modifier
          .fillMaxSize()
          .weight(1f),
      ) {
        when {
          gate == RideStatsGate.NEED_LOGIN -> GateState(
            title = stringResource(R.string.ride_stats_login_required),
            actionLabel = stringResource(R.string.ride_stats_login_action),
            onAction = { onNavigate(Routes.LOGIN) },
          )
          gate == RideStatsGate.NEED_VEHICLE -> GateState(
            title = stringResource(R.string.ride_stats_no_vehicle),
            actionLabel = stringResource(R.string.ride_stats_add_vehicle),
            onAction = { onNavigate(Routes.ADD_VEHICLE) },
          )
          error != null && statistics == null -> ErrorState(
            message = error!!,
            onRetry = {
              loadStatistics(
                scope = scope,
                cloudService = cloudService,
                period = period,
                onGate = { gate = it },
                onLoading = { loading = it },
                onError = { error = it },
                onStatistics = { statistics = it },
              )
            },
          )
          else -> RideStatsContent(
            period = period,
            statistics = statistics,
            onPeriodSelected = { next ->
              if (period == next) return@RideStatsContent
              period = next
              statistics = null
              error = null
              loadStatistics(
                scope = scope,
                cloudService = cloudService,
                period = next,
                onGate = { gate = it },
                onLoading = { loading = it },
                onError = { error = it },
                onStatistics = { statistics = it },
              )
            },
            onCarbonHelp = {
              showInfoSheet = InfoSheetContent(strCarbonHelp, strCarbonDesc)
            },
            onTreeHelp = {
              showInfoSheet = InfoSheetContent(strTreeHelp, strTreeDesc)
            },
          )
        }
        if (loading) {
          LinearProgressIndicator(
            modifier = Modifier
              .fillMaxWidth()
              .align(Alignment.TopCenter),
            color = CyberHomeColors.primary,
            trackColor = CyberHomeColors.primarySoft,
          )
        }
      }
    }
  }

  // Info sheet.
  showInfoSheet?.let { content ->
    InfoSheet(
      title = content.title,
      content = content.text,
      onDismiss = { showInfoSheet = null },
    )
  }
}

// ── Gate ──────────────────────────────────────────────────────────────────

enum class RideStatsGate { READY, NEED_LOGIN, NEED_VEHICLE }

private data class InfoSheetContent(val title: String, val text: String)

// ── Load helper ───────────────────────────────────────────────────────────

private fun loadStatistics(
  scope: kotlinx.coroutines.CoroutineScope,
  cloudService: com.tailg.plus.data.cloud.OfficialCloudService,
  period: OfficialRidePeriod,
  onGate: (RideStatsGate) -> Unit,
  onLoading: (Boolean) -> Unit,
  onError: (String?) -> Unit,
  onStatistics: (OfficialRideStatistics?) -> Unit,
) {
  val cloud = cloudService.currentState
  if (!cloud.signedIn) {
    onGate(RideStatsGate.NEED_LOGIN)
    onLoading(false)
    onError(null)
    onStatistics(null)
    return
  }
  if (cloud.selectedVehicle == null) {
    onGate(RideStatsGate.NEED_VEHICLE)
    onLoading(false)
    onError(null)
    onStatistics(null)
    return
  }
  onGate(RideStatsGate.READY)
  onLoading(true)
  onError(null)
  scope.launch {
    try {
      cloudService.refreshRideStatistics(period = period, force = true)
      val state = cloudService.currentState
      val requestError = state.rideStatisticsError?.trim()
      onStatistics(if (state.ridePeriod == period) state.rideStatistics else null)
      onLoading(false)
      onError(if (requestError.isNullOrEmpty()) null else requestError)
    } catch (e: Exception) {
      onError(OfficialCloudRedactor.errorMessage(e))
      onLoading(false)
    }
  }
}

/** Raw mileage fields are meters; parse loosely, non-numeric/empty → 0. */
private fun parseMeters(raw: String?): Float = raw?.trim()?.toFloatOrNull() ?: 0f

/**
 * One shared entrance clock drives every section: section [index] reveals over
 * a sliding window of [progress] so the whole page cascades in with a single
 * `Animatable` (no per-section timers). When reduce-motion is on the caller
 * seeds [progress] at 1f so this collapses to a no-op.
 */
@Composable
private fun Modifier.entranceSection(progress: Float, index: Int): Modifier {
  val offsetPx = with(LocalDensity.current) { 30.dp.toPx() }
  val local = ((progress - index * 0.14f) / 0.55f).coerceIn(0f, 1f)
  val eased = AppMotion.entranceCurve.transform(local)
  return this.graphicsLayer {
    alpha = eased
    translationY = (1f - eased) * offsetPx
  }
}

// ── Content ───────────────────────────────────────────────────────────────

@Composable
private fun RideStatsContent(
  period: OfficialRidePeriod,
  statistics: OfficialRideStatistics?,
  onPeriodSelected: (OfficialRidePeriod) -> Unit,
  onCarbonHelp: () -> Unit,
  onTreeHelp: () -> Unit,
) {
  val reduceMotion = MotionPolicy.reduceMotion()
  val entrance = remember { Animatable(if (reduceMotion) 1f else 0f) }
  LaunchedEffect(Unit) {
    if (!reduceMotion) {
      entrance.animateTo(1f, tween(AppMotion.reveal * 2, easing = AppMotion.entranceCurve))
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(bottom = 28.dp),
  ) {
    Box(modifier = Modifier.entranceSection(entrance.value, 0)) {
      HeroDial(period = period, statistics = statistics)
    }
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .entranceSection(entrance.value, 1)
        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
        .background(CyberHomeColors.card)
        .padding(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 10.dp),
    ) {
      PeriodSelector(selected = period, onSelected = onPeriodSelected)
      Spacer(Modifier.height(22.dp))
      MileageBreakdown(statistics = statistics, selected = period)
      Spacer(Modifier.height(22.dp))
      EcoStats(
        period = period,
        statistics = statistics,
        onCarbonHelp = onCarbonHelp,
        onTreeHelp = onTreeHelp,
      )
      Spacer(Modifier.height(22.dp))
      MetricsGrid(statistics = statistics)
      Spacer(Modifier.height(18.dp))
      MileageNotice()
    }
  }
}

// ── Hero dial ─────────────────────────────────────────────────────────────

@Composable
private fun HeroDial(
  period: OfficialRidePeriod,
  statistics: OfficialRideStatistics?,
) {
  val distanceUnit = LocalDistanceUnitPreference.current
  val periodValue = OfficialRideStatistics.formatMileage(statistics?.mileageFor(period) ?: "", distanceUnit)
  val totalValue = OfficialRideStatistics.formatMileage(statistics?.totalMileage ?: "", distanceUnit)
  val unit = distanceUnitSuffix(distanceUnit)

  val dayM = parseMeters(statistics?.dayMileage)
  val weekM = parseMeters(statistics?.weekMileage)
  val monthM = parseMeters(statistics?.monthsMileage)
  val periodM = parseMeters(statistics?.mileageFor(period))
  val maxM = maxOf(dayM, weekM, monthM)
  val targetFraction = if (maxM > 0f && periodM > 0f) (periodM / maxM).coerceIn(0.05f, 1f) else 0f

  val reduceMotion = MotionPolicy.reduceMotion()
  val sweep = remember { Animatable(if (reduceMotion) targetFraction else 0f) }
  LaunchedEffect(targetFraction, reduceMotion) {
    if (reduceMotion) {
      sweep.snapTo(targetFraction)
    } else {
      sweep.animateTo(targetFraction, tween(AppMotion.reveal, easing = AppMotion.entranceCurve))
    }
  }

  val track = CyberHomeColors.line
  val primary = CyberHomeColors.primary
  val sky = Color(0xFF4FC3FF)
  val accent = CyberHomeColors.rideAccent
  val card = CyberHomeColors.card

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 8.dp, bottom = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier.size(250.dp),
      contentAlignment = Alignment.Center,
    ) {
      // Soft radial glow bleeding out past the ring.
      Box(
        modifier = Modifier
          .size(250.dp)
          .background(
            Brush.radialGradient(listOf(primary.copy(alpha = 0.14f), Color.Transparent)),
            CircleShape,
          ),
      )
      Canvas(modifier = Modifier.size(206.dp)) {
        val stroke = 13.dp.toPx()
        val inset = stroke / 2f + 2.dp.toPx()
        val diameter = size.minDimension - inset * 2f
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)
        val radius = diameter / 2f

        drawArc(
          color = track,
          startAngle = 0f,
          sweepAngle = 360f,
          useCenter = false,
          topLeft = topLeft,
          size = arcSize,
          style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        val sweepDeg = sweep.value * 360f
        if (sweepDeg > 0.5f) {
          drawArc(
            brush = Brush.sweepGradient(
              colors = listOf(primary, sky, primary),
              center = center,
            ),
            startAngle = -90f,
            sweepAngle = sweepDeg,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
          )
          val tipRad = Math.toRadians((-90.0 + sweepDeg))
          val tip = Offset(
            x = center.x + radius * cos(tipRad).toFloat(),
            y = center.y + radius * sin(tipRad).toFloat(),
          )
          drawCircle(color = accent, radius = stroke * 0.46f, center = tip)
          drawCircle(color = card, radius = stroke * 0.20f, center = tip)
        }
      }
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = period.mileageTitle,
          maxLines = 1,
          style = TextStyle(
            fontSize = 13.sp,
            fontWeight = FontWeight.W700,
            letterSpacing = 0.6.sp,
            color = CyberHomeColors.inkMuted,
          ),
        )
        Spacer(Modifier.height(8.dp))
        ScaleToFit(
          modifier = Modifier
            .width(160.dp)
            .height(56.dp),
          contentAlignment = Alignment.Center,
        ) {
          AnimatedValueText(
            value = periodValue,
            unit = " $unit",
            style = TextStyle(fontSize = 50.sp, fontWeight = FontWeight.W800, color = CyberHomeColors.ink),
            unitStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.inkMuted),
            maxLines = 1,
          )
        }
        Spacer(Modifier.height(8.dp))
        Text(
          text = "${stringResource(R.string.ride_stats_total_distance)} $totalValue $unit",
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          style = cyberCaptionStyle,
        )
      }
    }
  }
}

// ── Period selector (sliding pill) ────────────────────────────────────────

@Composable
private fun PeriodSelector(
  selected: OfficialRidePeriod,
  onSelected: (OfficialRidePeriod) -> Unit,
) {
  val periods = OfficialRidePeriod.values()
  val selectedIndex = periods.indexOf(selected).coerceAtLeast(0)
  val reduceMotion = MotionPolicy.reduceMotion()
  BoxWithConstraints(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(AppRadii.pill))
      .background(CyberHomeColors.control)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.pill))
      .padding(4.dp),
  ) {
    val tabWidth = maxWidth / periods.size
    val indicatorOffset by animateDpAsState(
      targetValue = tabWidth * selectedIndex,
      animationSpec = if (reduceMotion) snap() else tween(AppMotion.tabSwitch, easing = AppMotion.pressCurve),
      label = "periodIndicator",
    )
    Box(
      modifier = Modifier
        .offset(x = indicatorOffset)
        .width(tabWidth)
        .height(44.dp)
        .clip(RoundedCornerShape(AppRadii.pill))
        .background(CyberHomeColors.card)
        .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.pill)),
    )
    Row {
      periods.forEachIndexed { index, item ->
        val isSelected = index == selectedIndex
        AppPressable(
          onClick = { onSelected(item) },
          semanticsLabel = stringResource(R.string.ride_stats_tab_format, item.tabLabel),
          semanticsSelected = isSelected,
          shape = RoundedCornerShape(AppRadii.pill),
          modifier = Modifier
            .width(tabWidth)
            .height(44.dp),
        ) {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = item.tabLabel,
              maxLines = 1,
              style = TextStyle(
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.W800 else FontWeight.W600,
                color = if (isSelected) CyberHomeColors.primary else CyberHomeColors.inkMuted,
              ),
            )
          }
        }
      }
    }
  }
}

// ── Mileage breakdown bars (day / week / month) ───────────────────────────

@Composable
private fun MileageBreakdown(
  statistics: OfficialRideStatistics?,
  selected: OfficialRidePeriod,
) {
  val distanceUnit = LocalDistanceUnitPreference.current
  val day = statistics?.dayMileage ?: ""
  val week = statistics?.weekMileage ?: ""
  val month = statistics?.monthsMileage ?: ""
  val maxM = maxOf(parseMeters(day), parseMeters(week), parseMeters(month)).takeIf { it > 0f } ?: 1f

  Column(modifier = Modifier.fillMaxWidth()) {
    BreakdownBar(OfficialRidePeriod.DAY, day, parseMeters(day) / maxM, selected == OfficialRidePeriod.DAY, distanceUnit)
    Spacer(Modifier.height(16.dp))
    BreakdownBar(OfficialRidePeriod.WEEK, week, parseMeters(week) / maxM, selected == OfficialRidePeriod.WEEK, distanceUnit)
    Spacer(Modifier.height(16.dp))
    BreakdownBar(OfficialRidePeriod.MONTH, month, parseMeters(month) / maxM, selected == OfficialRidePeriod.MONTH, distanceUnit)
  }
}

@Composable
private fun BreakdownBar(
  period: OfficialRidePeriod,
  rawValue: String,
  fraction: Float,
  highlighted: Boolean,
  distanceUnit: DistanceUnitPreference,
) {
  val reduceMotion = MotionPolicy.reduceMotion()
  val animatedFraction by animateFloatAsState(
    targetValue = if (fraction <= 0f) 0f else fraction.coerceIn(0.04f, 1f),
    animationSpec = if (reduceMotion) snap() else tween(AppMotion.reveal, easing = AppMotion.entranceCurve),
    label = "breakdownBar",
  )
  val value = OfficialRideStatistics.formatMileage(rawValue, distanceUnit)
  val barBrush = if (highlighted) {
    Brush.horizontalGradient(listOf(CyberHomeColors.primary, Color(0xFF4FC3FF)))
  } else {
    Brush.horizontalGradient(listOf(CyberHomeColors.controlStrong, CyberHomeColors.controlStrong))
  }

  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = period.mileageTitle,
      modifier = Modifier.width(64.dp),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = TextStyle(
        fontSize = 12.sp,
        fontWeight = if (highlighted) FontWeight.W700 else FontWeight.W600,
        color = if (highlighted) CyberHomeColors.ink else CyberHomeColors.inkMuted,
      ),
    )
    Spacer(Modifier.width(12.dp))
    Box(
      modifier = Modifier
        .weight(1f)
        .height(12.dp)
        .clip(RoundedCornerShape(AppRadii.pill))
        .background(CyberHomeColors.control),
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth(animatedFraction.coerceIn(0f, 1f))
          .fillMaxHeight()
          .clip(RoundedCornerShape(AppRadii.pill))
          .background(barBrush),
      )
    }
    Spacer(Modifier.width(12.dp))
    Text(
      text = value,
      modifier = Modifier.width(64.dp),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.End,
      style = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.W700,
        color = if (highlighted) CyberHomeColors.primary else CyberHomeColors.ink,
      ),
    )
  }
}

// ── Eco impact ────────────────────────────────────────────────────────────

@Composable
private fun EcoStats(
  period: OfficialRidePeriod,
  statistics: OfficialRideStatistics?,
  onCarbonHelp: () -> Unit,
  onTreeHelp: () -> Unit,
) {
  Row(modifier = Modifier.fillMaxWidth()) {
    EcoCard(
      modifier = Modifier.weight(1f),
      icon = Lucide.leaf,
      accent = CyberHomeColors.success,
      title = period.carbonTitle,
      value = OfficialRideStatistics.displayValue(statistics?.carbonSaving ?: ""),
      unit = "kg",
      helpLabel = stringResource(R.string.ride_stats_carbon_help),
      onHelp = onCarbonHelp,
    )
    Spacer(Modifier.width(12.dp))
    EcoCard(
      modifier = Modifier.weight(1f),
      icon = Lucide.tree,
      accent = CyberHomeColors.warning,
      title = stringResource(R.string.ride_stats_tree),
      value = OfficialRideStatistics.displayValue(statistics?.carbonAbsorption ?: ""),
      unit = stringResource(R.string.ride_stats_tree_unit),
      helpLabel = stringResource(R.string.ride_stats_tree_help),
      onHelp = onTreeHelp,
    )
  }
}

@Composable
private fun EcoCard(
  icon: ImageVector,
  accent: Color,
  title: String,
  value: String,
  unit: String,
  helpLabel: String,
  onHelp: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(AppRadii.lg))
      .background(CyberHomeColors.cardMuted)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.lg))
      .padding(horizontal = 14.dp, vertical = 12.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .size(30.dp)
          .clip(RoundedCornerShape(AppRadii.tile))
          .background(accent.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
      ) {
        LucideIcon(icon = icon, size = 16.dp, color = accent)
      }
      Spacer(Modifier.width(8.dp))
      Text(
        text = title,
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.inkMuted),
      )
      AppPressable(
        onClick = onHelp,
        semanticsLabel = helpLabel,
      ) {
        Box(
          modifier = Modifier.size(28.dp),
          contentAlignment = Alignment.Center,
        ) {
          LucideIcon(icon = Lucide.help, size = 16.dp, color = CyberHomeColors.inkFaint)
        }
      }
    }
    Spacer(Modifier.height(12.dp))
    ScaleToFit(
      modifier = Modifier
        .fillMaxWidth()
        .height(38.dp),
      contentAlignment = Alignment.CenterStart,
    ) {
      AnimatedValueText(
        value = value,
        unit = " $unit",
        style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.W800, color = CyberHomeColors.ink),
        unitStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.inkMuted),
        maxLines = 1,
      )
    }
  }
}

// ── Metrics grid ──────────────────────────────────────────────────────────

@Composable
private fun MetricsGrid(statistics: OfficialRideStatistics?) {
  val distanceUnit = LocalDistanceUnitPreference.current
  fun value(raw: String?): String = OfficialRideStatistics.displayValue(raw ?: "")
  fun speedValue(raw: String?): String = formatSpeedKilometersPerHourValue(
    raw = raw,
    unit = distanceUnit,
  )
  val speedUnit = speedUnitSuffix(distanceUnit)
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(AppRadii.lg))
      .background(CyberHomeColors.cardMuted)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.lg)),
  ) {
    Row {
      MetricCell(
        modifier = Modifier.weight(1f),
        label = stringResource(R.string.ride_stats_max_speed),
        value = speedValue(statistics?.maxSpeed),
        unit = speedUnit,
        icon = Lucide.gauge,
        accent = CyberHomeColors.primary,
      )
      VerticalDividerLine()
      MetricCell(
        modifier = Modifier.weight(1f),
        label = stringResource(R.string.ride_stats_total_duration),
        value = value(statistics?.ridingTime),
        unit = stringResource(R.string.ride_stats_minutes),
        icon = Lucide.history,
        accent = CyberHomeColors.warning,
      )
    }
    HorizontalDividerLine()
    Row {
      MetricCell(
        modifier = Modifier.weight(1f),
        label = stringResource(R.string.ride_stats_total_trips),
        value = value(statistics?.ridingCount),
        unit = stringResource(R.string.ride_stats_trip_unit),
        icon = Lucide.route,
        accent = CyberHomeColors.rideAccent,
      )
      VerticalDividerLine()
      MetricCell(
        modifier = Modifier.weight(1f),
        label = stringResource(R.string.ride_stats_avg_speed),
        value = speedValue(statistics?.avgSpeed),
        unit = speedUnit,
        icon = Lucide.activity,
        accent = CyberHomeColors.success,
      )
    }
  }
}

@Composable
private fun MetricCell(
  label: String,
  value: String,
  unit: String,
  icon: ImageVector,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .height(118.dp)
      .padding(horizontal = 16.dp, vertical = 14.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .size(28.dp)
          .clip(RoundedCornerShape(AppRadii.tile))
          .background(accent.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
      ) {
        LucideIcon(icon = icon, size = 15.dp, color = accent)
      }
      Spacer(Modifier.width(8.dp))
      Text(
        text = label,
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.inkMuted),
      )
    }
    Spacer(Modifier.weight(1f))
    ScaleToFit(
      modifier = Modifier
        .fillMaxWidth()
        .height(34.dp),
      contentAlignment = Alignment.CenterStart,
    ) {
      AnimatedValueText(
        value = value,
        unit = " $unit",
        style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.W800, color = CyberHomeColors.ink),
        unitStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.inkMuted),
        maxLines = 1,
      )
    }
  }
}

// ── Dividers ──────────────────────────────────────────────────────────────

@Composable
private fun VerticalDividerLine() {
  Box(
    modifier = Modifier
      .width(1.dp)
      .height(118.dp)
      .background(CyberHomeColors.line),
  )
}

@Composable
private fun HorizontalDividerLine() {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(1.dp)
      .background(CyberHomeColors.line),
  )
}

// ── Mileage notice ────────────────────────────────────────────────────────

@Composable
private fun MileageNotice() {
  Text(
    text = "* ${rideNotice()}",
    style = TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint, lineHeight = 12.sp * 1.45f),
  )
}

// ── Gate / error states ───────────────────────────────────────────────────

@Composable
private fun GateState(
  title: String,
  actionLabel: String,
  onAction: () -> Unit,
) {
  Box(
    modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = title,
        textAlign = TextAlign.Center,
        style = TextStyle(fontSize = 13.sp, color = CyberHomeColors.inkMuted, lineHeight = 13.sp * 1.45f),
      )
      Spacer(Modifier.height(14.dp))
      Button(
        onClick = onAction,
        modifier = Modifier.width(120.dp).height(48.dp),
        colors = cyberFilledButtonColors(),
        shape = cyberButtonShape,
      ) {
        Text(actionLabel)
      }
    }
  }
}

@Composable
private fun ErrorState(
  message: String,
  onRetry: () -> Unit,
) {
  Box(
    modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = message,
        textAlign = TextAlign.Center,
        style = TextStyle(fontSize = 13.sp, color = CyberHomeColors.inkMuted, lineHeight = 13.sp * 1.45f),
      )
      Spacer(Modifier.height(12.dp))
      TextButton(onClick = onRetry) {
        Text(stringResource(R.string.ride_stats_retry), color = CyberHomeColors.primary)
      }
    }
  }
}

// ── Info sheet ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(
  title: String,
  content: String,
  onDismiss: () -> Unit,
) {
  androidx.compose.material3.ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = CyberHomeColors.card,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 20.dp, top = 18.dp, end = 12.dp, bottom = 24.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = title,
        modifier = Modifier.weight(1f),
        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      )
      AppPressable(
        onClick = onDismiss,
        semanticsLabel = stringResource(R.string.ride_stats_close),
      ) {
        LucideIcon(icon = Lucide.x, size = 20.dp, color = CyberHomeColors.inkMuted)
      }
    }
    Spacer(Modifier.height(8.dp))
    Text(
      text = content,
      modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
      style = TextStyle(fontSize = 13.sp, color = CyberHomeColors.inkMuted, lineHeight = 13.sp * 1.45f),
    )
  }
}
