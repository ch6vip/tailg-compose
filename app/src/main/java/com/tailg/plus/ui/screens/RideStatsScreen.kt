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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.data.cloud.OfficialCloudRedactor
import com.tailg.plus.data.model.OfficialRidePeriod
import com.tailg.plus.data.model.OfficialRideStatistics
import com.tailg.plus.ui.components.AnimatedValueText
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.components.cyberButtonShape
import com.tailg.plus.ui.components.cyberFilledButtonColors
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppTouchTargets
import com.tailg.plus.ui.theme.CyberHomeColors
import com.tailg.plus.ui.navigation.Routes
import kotlinx.coroutines.launch

private const val RIDE_NOTICE =
  "为剔除车辆静止时的卫星信号飘移、短距离骑行、原地推车或短暂挪动等无效干扰，" +
    "系统设定单次持续移动距离小于50米时，不纳入总里程累计。" +
    "这确保您仪表盘上的每一公里，都真实反映您的实际骑行足迹。"

/**
 * Port of `lib/pages/ride_stats_page.dart` — ride statistics with period
 * selector (day / week / month), environmental summary (carbon saving +
 * tree absorption), mileage summary, and a metrics grid.
 *
 * Navigation: [onBack] pops; gates route to login / add-vehicle via [Routes].
 */
@Composable
fun RideStatsScreen(
  vehicleId: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val cloudService = rememberOfficialCloudService()
  val scope = rememberCoroutineScope()
  val cloudState by cloudService.stateFlow.collectAsState()

  var period by remember { mutableStateOf(OfficialRidePeriod.DAY) }
  var statistics by remember { mutableStateOf<OfficialRideStatistics?>(null) }
  var loading by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var gate by remember { mutableStateOf(RideStatsGate.READY) }
  var showInfoSheet by remember { mutableStateOf<InfoSheetContent?>(null) }

  // Initial load.
  LaunchedEffect(Unit) {
    val cloud = cloudService.currentState
    if (cloud.ridePeriod == period) {
      statistics = cloud.rideStatistics
    }
    loadStatistics(
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
      RideStatsHeader(
        onBack = onBack,
        onHelp = { showInfoSheet = InfoSheetContent("轨迹记录、统计说明", RIDE_NOTICE) },
      )
      Box(modifier = Modifier.fillMaxSize()) {
        when {
          gate == RideStatsGate.NEED_LOGIN -> GateState(
            title = "请先登录官方账号",
            actionLabel = "去登录",
            onAction = { onNavigate(Routes.LOGIN) },
          )
          gate == RideStatsGate.NEED_VEHICLE -> GateState(
            title = "暂无车辆，请先同步官方车辆",
            actionLabel = "添加车辆",
            onAction = { onNavigate(Routes.ADD_VEHICLE) },
          )
          error != null && statistics == null -> ErrorState(
            message = error!!,
            onRetry = {
              loadStatistics(
                cloudService = cloudService,
                period = period,
                onGate = { gate = it },
                onLoading = { loading = it },
                onError = { error = it },
                onStatistics = { statistics = it },
              )
            },
          )
          else -> {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
            ) {
              EnvironmentalSummary(
                period = period,
                statistics = statistics,
                onCarbonHelp = {
                  showInfoSheet = InfoSheetContent("节碳量说明", "每行驶1公里，相当于\n减排二氧化碳0.171kg")
                },
                onTreeHelp = {
                  showInfoSheet = InfoSheetContent("树木吸碳说明", "每棵树平均每天吸收\n二氧化碳5.023kg")
                },
              )
              Column(
                modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp),
              ) {
                PeriodSelector(
                  selected = period,
                  onSelected = { next ->
                    if (period == next) return@PeriodSelector
                    period = next
                    statistics = null
                    error = null
                    loadStatistics(
                      cloudService = cloudService,
                      period = next,
                      onGate = { gate = it },
                      onLoading = { loading = it },
                      onError = { error = it },
                      onStatistics = { statistics = it },
                    )
                  },
                )
                Spacer(Modifier.height(16.dp))
                MileageNotice()
                Spacer(Modifier.height(12.dp))
                MileageSummary(period = period, statistics = statistics)
                Spacer(Modifier.height(14.dp))
                MetricsGrid(statistics = statistics)
              }
            }
            if (loading) {
              LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = CyberHomeColors.primary,
                trackColor = CyberHomeColors.primarySoft,
              )
            }
          }
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

private fun onNavigate(route: String) {
  // TODO: the NavHost owns navigation; RideStatsScreen currently has no onNavigate
  // callback. Wire one through the constructor when the NavHost is updated to pass it.
}

// ── Gate ──────────────────────────────────────────────────────────────────

enum class RideStatsGate { READY, NEED_LOGIN, NEED_VEHICLE }

private data class InfoSheetContent(val title: String, val text: String)

// ── Load helper ───────────────────────────────────────────────────────────

private fun loadStatistics(
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
  kotlinx.coroutines.GlobalScope.launch {
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

// ── Header ────────────────────────────────────────────────────────────────

@Composable
private fun RideStatsHeader(
  onBack: () -> Unit,
  onHelp: () -> Unit,
) {
  Row(
    modifier = Modifier
      .height(64.dp)
      .padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(modifier = Modifier.width(92.dp)) {
      AppPressable(
        onClick = onBack,
        semanticsLabel = "返回",
      ) {
        Box(
          modifier = Modifier.size(AppTouchTargets.min),
          contentAlignment = Alignment.Center,
        ) {
          LucideIcon(icon = Lucide.arrowLeft, size = 20.dp, color = CyberHomeColors.inkSecondary)
        }
      }
    }
    Text(
      text = "骑行统计",
      modifier = Modifier.weight(1f),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
      style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
    )
    AppPressable(
      onClick = onHelp,
      semanticsLabel = "查看统计说明",
      modifier = Modifier.width(92.dp),
    ) {
      Box(
        modifier = Modifier
          .height(AppTouchTargets.min)
          .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterEnd,
      ) {
        Text(
          text = "统计说明",
          maxLines = 1,
          style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.primary),
        )
      }
    }
  }
}

// ── Environmental summary ──────────────────────────────────────────────────

@Composable
private fun EnvironmentalSummary(
  period: OfficialRidePeriod,
  statistics: OfficialRideStatistics?,
  onCarbonHelp: () -> Unit,
  onTreeHelp: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp)
      .background(CyberHomeColors.card),
  ) {
    EcoMetric(
      modifier = Modifier.weight(1f),
      title = period.carbonTitle,
      value = OfficialRideStatistics.displayValue(statistics?.carbonSaving ?: ""),
      unit = "kg",
      icon = Lucide.leaf,
      accent = CyberHomeColors.success,
      tooltip = "节碳量说明",
      onHelp = onCarbonHelp,
    )
    Spacer(Modifier.width(12.dp))
    EcoMetric(
      modifier = Modifier.weight(1f),
      title = "树木吸碳",
      value = OfficialRideStatistics.displayValue(statistics?.carbonAbsorption ?: ""),
      unit = "棵",
      icon = Lucide.activity,
      accent = CyberHomeColors.warning,
      tooltip = "树木吸碳说明",
      onHelp = onTreeHelp,
    )
  }
}

@Composable
private fun EcoMetric(
  title: String,
  value: String,
  unit: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  accent: Color,
  tooltip: String,
  onHelp: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      LucideIcon(icon = icon, size = 18.dp, color = accent)
      Spacer(Modifier.width(6.dp))
      Text(
        text = title,
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.inkMuted),
      )
      AppPressable(
        onClick = onHelp,
        semanticsLabel = tooltip,
      ) {
        Box(
          modifier = Modifier.size(AppTouchTargets.min),
          contentAlignment = Alignment.Center,
        ) {
          LucideIcon(icon = Lucide.help, size = 17.dp, color = CyberHomeColors.inkFaint)
        }
      }
    }
    Spacer(Modifier.height(10.dp))
    Box(modifier = Modifier.height(36.dp), contentAlignment = Alignment.CenterStart) {
      AnimatedValueText(
        value = value,
        style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
        unit = " $unit",
        unitStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.inkMuted),
      )
    }
  }
}

// ── Period selector ───────────────────────────────────────────────────────

@Composable
private fun PeriodSelector(
  selected: OfficialRidePeriod,
  onSelected: (OfficialRidePeriod) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(50.dp)
      .padding(2.dp)
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.control)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile)),
  ) {
    OfficialRidePeriod.values().forEach { period ->
      AppPressable(
        onClick = { onSelected(period) },
        semanticsLabel = "按${period.tabLabel}查看骑行统计",
        modifier = Modifier.weight(1f),
      ) {
        Box(
          modifier = Modifier
            .height(AppTouchTargets.min)
            .clip(RoundedCornerShape(AppRadii.xs))
            .background(if (selected == period) CyberHomeColors.card else Color.Transparent),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = period.tabLabel,
            style = TextStyle(
              fontSize = 14.sp,
              fontWeight = FontWeight.W700,
              color = if (selected == period) CyberHomeColors.primary else CyberHomeColors.inkMuted,
            ),
          )
        }
      }
    }
  }
}

// ── Mileage notice ────────────────────────────────────────────────────────

@Composable
private fun MileageNotice() {
  Text(
    text = "* $RIDE_NOTICE",
    style = TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint, lineHeight = 12.sp * 1.45f),
  )
}

// ── Mileage summary ───────────────────────────────────────────────────────

@Composable
private fun MileageSummary(
  period: OfficialRidePeriod,
  statistics: OfficialRideStatistics?,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(102.dp)
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile)),
  ) {
    MileageValue(
      modifier = Modifier.weight(1f),
      label = period.mileageTitle,
      value = OfficialRideStatistics.formatMileageKm(statistics?.mileageFor(period) ?: ""),
    )
    Box(
      modifier = Modifier
        .width(1.dp)
        .height(50.dp)
        .align(Alignment.CenterVertically)
        .background(CyberHomeColors.line),
    )
    MileageValue(
      modifier = Modifier.weight(1f),
      label = "累计里程",
      value = OfficialRideStatistics.formatMileageKm(statistics?.totalMileage ?: ""),
    )
  }
}

@Composable
private fun MileageValue(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxSize(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = label,
      style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.inkMuted),
    )
    Spacer(Modifier.height(8.dp))
    AnimatedValueText(
      value = value,
      style = TextStyle(fontSize = 23.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
      unit = " km",
      unitStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.inkMuted),
    )
  }
}

// ── Metrics grid ──────────────────────────────────────────────────────────

@Composable
private fun MetricsGrid(statistics: OfficialRideStatistics?) {
  fun value(raw: String?): String = OfficialRideStatistics.displayValue(raw ?: "")
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile)),
  ) {
    Row {
      MetricCell(
        modifier = Modifier.weight(1f),
        label = "最快时速",
        value = value(statistics?.maxSpeed),
        unit = "km/h",
        icon = Lucide.gauge,
        accent = CyberHomeColors.primary,
      )
      Box(
        modifier = Modifier
          .width(1.dp)
          .height(92.dp)
          .background(CyberHomeColors.line),
      )
      MetricCell(
        modifier = Modifier.weight(1f),
        label = "总时长",
        value = value(statistics?.ridingTime),
        unit = "分钟",
        icon = Lucide.history,
        accent = CyberHomeColors.warning,
      )
    }
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(1.dp)
        .background(CyberHomeColors.line),
    )
    Row {
      MetricCell(
        modifier = Modifier.weight(1f),
        label = "骑行次数",
        value = value(statistics?.ridingCount),
        unit = "次",
        icon = Lucide.route,
        accent = CyberHomeColors.rideAccent,
      )
      Box(
        modifier = Modifier
          .width(1.dp)
          .height(92.dp)
          .background(CyberHomeColors.line),
      )
      MetricCell(
        modifier = Modifier.weight(1f),
        label = "平均时速",
        value = value(statistics?.avgSpeed),
        unit = "km/h",
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
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .height(116.dp)
      .padding(start = 16.dp, top = 14.dp, end = 12.dp, bottom = 12.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      LucideIcon(icon = icon, size = 17.dp, color = accent)
      Spacer(Modifier.width(7.dp))
      Text(
        text = label,
        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.inkMuted),
      )
    }
    Spacer(Modifier.weight(1f))
    Box(
      modifier = Modifier.fillMaxWidth().height(31.dp),
      contentAlignment = Alignment.CenterStart,
    ) {
      AnimatedValueText(
        value = value,
        style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
        unit = " $unit",
        unitStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.inkMuted),
      )
    }
  }
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
      FilledButton(
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
        Text("重试", color = CyberHomeColors.primary)
      }
    }
  }
}

// ── Info sheet ────────────────────────────────────────────────────────────

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
        semanticsLabel = "关闭",
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
