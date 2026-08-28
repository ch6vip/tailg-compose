package com.tailg.plus.ui.screens

import android.content.Context
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService
import com.tailg.plus.ui.components.CyberPageHeader
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.theme.AppIconSizes
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.CyberHomeColors
import com.tailg.plus.util.formatMonthDayMinuteText
import kotlinx.coroutines.flow.first
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R

private val Context.diagnosticDataStore by preferencesDataStore(name = "diagnostic_history")
private val DIAGNOSTIC_HISTORY_KEY = stringSetPreferencesKey("diagnostic_history_entries")

/**
 * Port of `lib/pages/diagnostic_page.dart` → `DiagnosticScreen.kt`.
 *
 * The Dart page persists diagnostic history in `SharedPreferences` under the
 * key `diagnostic_history` as a `List<String>` of JSON records. Here we use a
 * dedicated DataStore (file `diagnostic_history`) with a string-set key, which
 * matches the "set of JSON strings" shape and is the closest DataStore analog.
 *
 * Real-time fault diagnosis is not available in this port (the Dart page also
 * only shows history); the info banner reflects that.
 */
@Composable
fun DiagnosticScreen(
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val log = com.tailg.plus.di.rememberTailgEntryPoint().logService()
  var history by remember { mutableStateOf<List<DiagnosticRecord>>(emptyList()) }
  val strDiagLoadFailed = stringResource(R.string.diag_load_failed)

  LaunchedEffect(Unit) {
    history = loadHistory(context, log, strDiagLoadFailed)
  }

  Scaffold(
    containerColor = CyberHomeColors.pageBg,
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
    ) {
      CyberPageHeader(title = stringResource(R.string.diag_title), onBack = onBack)

      // Info banner: real-time diagnosis unavailable, history only.
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)
          .clip(RoundedCornerShape(AppRadii.tile))
          .background(CyberHomeColors.card)
          .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
          .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        LucideIcon(icon = Lucide.info, color = CyberHomeColors.primary, size = 20.dp)
        Spacer(Modifier.width(10.dp))
        Text(
          text = stringResource(R.string.diag_unavailable_hint),
          style = TextStyle(fontSize = 13.sp, color = CyberHomeColors.inkMuted),
          modifier = Modifier.weight(1f),
        )
      }

      if (history.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .weight(1f),
          contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LucideIcon(
              icon = Lucide.stethoscope,
              size = AppIconSizes.xl,
              color = CyberHomeColors.inkFaint,
            )
            Spacer(Modifier.height(10.dp))
            Text(
              text = stringResource(R.string.diag_no_records),
              style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
            )
            Spacer(Modifier.height(4.dp))
            Text(
              text = stringResource(R.string.diag_no_records_hint),
              style = TextStyle(fontSize = 13.sp, color = CyberHomeColors.inkMuted),
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .weight(1f),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        ) {
          items(history, key = { it.time.toString() + it.rawByte }, contentType = { "diagnostic-record" }) { record ->
            DiagnosticRecordCard(record)
          }
        }
      }
    }
  }
}

/** Dart `DiagnosticRecord` card: status icon + count + faults + time. */
@Composable
private fun DiagnosticRecordCard(record: DiagnosticRecord) {
  val hasFaults = record.faults.isNotEmpty()
  val statusColor = if (hasFaults) CyberHomeColors.danger else CyberHomeColors.success
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(16.dp),
  ) {
    LucideIcon(
      icon = if (hasFaults) Lucide.alert else Lucide.checkCircle,
      color = statusColor,
      size = 18.dp,
    )
    Spacer(Modifier.width(8.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = if (hasFaults) stringResource(R.string.diag_faults_format, record.faults.size) else stringResource(R.string.diag_normal),
        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W600, color = statusColor),
      )
      if (hasFaults) {
        Spacer(Modifier.height(8.dp))
        Text(
          text = record.faults.joinToString("、"),
          style = TextStyle(fontSize = 13.sp, color = CyberHomeColors.inkMuted),
        )
      }
    }
    Text(
      text = formatMonthDayMinuteText(record.time),
      style = TextStyle(fontSize = 12.sp, color = CyberHomeColors.inkFaint),
    )
  }
}

/** Port of Dart `DiagnosticRecord` (subset used by the UI). */
data class DiagnosticRecord(
  val time: java.time.LocalDateTime,
  val rawByte: Int,
  val faults: List<String>,
)

/** Load persisted diagnostic history from DataStore. */
private suspend fun loadHistory(context: Context, log: LogService, strLoadFailed: String): List<DiagnosticRecord> {
  return try {
    val raw = context.diagnosticDataStore.data.first()[DIAGNOSTIC_HISTORY_KEY] ?: emptySet()
    raw.mapNotNull { parseRecord(it) }
      .sortedByDescending { it.time }
  } catch (e: Exception) {
    log.operation(strLoadFailed, detail = e.toString(), level = LogLevel.WARNING)
    emptyList()
  }
}

/** Best-effort parse of a persisted JSON record (Dart `DiagnosticRecord.tryParse`). */
private fun parseRecord(raw: String): DiagnosticRecord? {
  return try {
    // The Dart source uses jsonDecode; Android's org.json is available without
    // an extra dependency, so we parse the record shape directly here.
    val json = org.json.JSONObject(raw)
    val timeStr = json.optString("time")
    if (timeStr.isEmpty()) return null
    val rawByte = json.optInt("raw", 0)
    val faultsArr = json.optJSONArray("faults")
    val faults = if (faultsArr != null) {
      buildList { for (i in 0 until faultsArr.length()) add(faultsArr.getString(i)) }
    } else {
      emptyList()
    }
    DiagnosticRecord(
      time = java.time.LocalDateTime.parse(timeStr, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME),
      rawByte = rawByte,
      faults = faults,
    )
  } catch (e: Exception) {
    null
  }
}
