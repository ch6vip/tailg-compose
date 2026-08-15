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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.log.LogEntry
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService
import com.tailg.plus.service.DiagnosticExportService
import com.tailg.plus.data.store.VehicleStore
import com.tailg.plus.ui.components.AppSnack
import com.tailg.plus.ui.components.CyberEmptyState
import com.tailg.plus.ui.components.CyberHeaderAction
import com.tailg.plus.ui.components.CyberPageHeader
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.cyberButtonShape
import com.tailg.plus.ui.components.cyberFilledButtonColors
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.CyberHomeColors
import com.tailg.plus.util.ClipboardText
import com.tailg.plus.util.formatLogClockTime
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Port of `lib/pages/log_page.dart` → `LogScreen.kt`.
 *
 * The Dart `StatefulWidget` subscribes to `LogService.changes` (a broadcast
 * `Stream<void>`) and bumps a `_listGeneration` counter to force a rebuild.
 * Here we collect the `SharedFlow<Unit>` in a `LaunchedEffect` and bump a
 * `mutableIntStateOf` generation; the `LazyColumn` reads `logService.all` on
 * every recomposition (the snapshot is cheap and the buffer is capped at 2000).
 *
 * The diagnostic-report copy path uses the Kotlin [DiagnosticExportService],
 * which needs an [OfficialCloudService] + [VehicleStore]; those are
 * constructed once via `remember` from the current [android.content.Context].
 */
@Composable
fun LogScreen(
  onBack: () -> Unit,
  logService: LogService? = null,
) {
  val context = LocalContext.current
  val log = remember(logService) { logService ?: LogService() }
  val clipboard = remember { ClipboardText(context) }
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  val cloud = rememberOfficialCloudService()
  val vehicleStore = remember { VehicleStore(context) }
  val exportService = remember {
    DiagnosticExportService(
      logService = log,
      vehicleStore = vehicleStore,
      officialCloudService = cloud,
    )
  }

  var listGeneration by remember { mutableIntStateOf(0) }
  var showClearDialog by remember { mutableStateOf(false) }

  // Subscribe to LogService.changes so the list refreshes when new entries arrive.
  LaunchedEffect(log) {
    log.changes.collectLatest { listGeneration++ }
  }

  val copyAll: () -> Unit = {
    scope.launch {
      val entries = log.all
      if (entries.isEmpty()) {
        AppSnack.info(snackbarHostState, "当前没有可复制的日志")
        return@launch
      }
      val report = exportService.buildReport(entries)
      clipboard.writeClipboardText(report)
      AppSnack.success(snackbarHostState, "已复制诊断报告（${entries.size} 条日志）")
    }
  }

  val confirmClear: () -> Unit = { showClearDialog = true }

  if (showClearDialog) {
    AlertDialog(
      onDismissRequest = { showClearDialog = false },
      containerColor = CyberHomeColors.card,
      title = { Text("清空日志") },
      text = { Text("清空后无法恢复。") },
      confirmButton = {
        Button(
          onClick = {
            showClearDialog = false
            log.clear()
          },
          shape = cyberButtonShape,
          colors = cyberFilledButtonColors().copy(
            containerColor = CyberHomeColors.danger,
            contentColor = CyberHomeColors.white,
          ),
        ) { Text("清空") }
      },
      dismissButton = {
        TextButton(onClick = { showClearDialog = false }) { Text("取消") }
      },
    )
  }

  Scaffold(
    containerColor = CyberHomeColors.pageBg,
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
    ) {
      CyberPageHeader(
        title = "日志",
        onBack = onBack,
        actions = {
          CyberHeaderAction(icon = Lucide.copy, label = "复制全部", onTap = copyAll)
          CyberHeaderAction(icon = Lucide.refresh, label = "刷新", onTap = { listGeneration++ })
          CyberHeaderAction(icon = Lucide.trash, label = "清空", onTap = confirmClear)
        },
      )
      val entries = log.all
      if (entries.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          CyberEmptyState(
            icon = Lucide.receipt,
            title = "暂无日志",
            subtitle = "云端控车与诊断操作的运行日志会显示在这里。",
          )
        }
      } else {
        // listGeneration is read so a bump forces recomposition.
        @Suppress("UNUSED_EXPRESSION") listGeneration
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          reverseLayout = true,
          verticalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
          items(entries.asReversed(), key = { it.time.toString() + it.message.hashCode() }) { entry ->
            LogTile(entry = entry)
          }
        }
      }
    }
  }
}

/** Dart `_LogTile`: time + level dot + message/detail. */
@Composable
private fun LogTile(entry: LogEntry) {
  val timeStr = formatLogClockTime(entry.time)
  val detail = entry.detail
  val levelColor = when (entry.level) {
    LogLevel.DEBUG -> CyberHomeColors.inkFaint
    LogLevel.INFO -> CyberHomeColors.primary
    LogLevel.WARNING -> CyberHomeColors.warning
    LogLevel.ERROR -> CyberHomeColors.danger
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 5.dp)
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(14.dp),
  ) {
    Text(
      text = timeStr,
      style = TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkFaint, fontFamily = FontFamily.Monospace),
    )
    Spacer(Modifier.width(8.dp))
    Box(
      modifier = Modifier
        .padding(top = 6.dp)
        .size(6.dp)
        .clip(CircleShape)
        .background(levelColor),
    )
    Spacer(Modifier.width(8.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = entry.message,
        style = TextStyle(fontSize = 13.sp, color = CyberHomeColors.ink, lineHeight = 13.sp * 1.4f),
      )
      if (detail != null) {
        Spacer(Modifier.height(2.dp))
        Text(
          text = detail,
          style = TextStyle(fontSize = 11.sp, color = CyberHomeColors.inkMuted, fontFamily = FontFamily.Monospace),
        )
      }
    }
  }
}
