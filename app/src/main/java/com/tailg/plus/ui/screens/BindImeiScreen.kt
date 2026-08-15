package com.tailg.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.tailg.plus.data.cloud.OfficialCloudMessages
import com.tailg.plus.data.cloud.OfficialCloudRedactor
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService
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
import com.tailg.plus.ui.theme.CyberHomeColors
import kotlinx.coroutines.launch

/**
 * Port of `lib/pages/bind_imei_page.dart` — manual IMEI bind
 * (`app/car/bikeBind`).
 *
 * The Dart page talks to the global `officialCloudService` singleton; the
 * Compose port takes the service as a parameter so it is testable and
 * Hilt-free. [onBack] is invoked with `true` when binding succeeded so the
 * caller can refresh the vehicle list (Dart `Navigator.pop(true)`).
 */
@Composable
fun BindImeiScreen(
  cloudService: OfficialCloudService,
  onBack: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  val log = remember { LogService() }

  var imei by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = CyberHomeColors.pageBg,
    snackbarHost = { AppSnackbarHost(snackbarHostState) },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp),
    ) {
      CyberPageHeader(
        title = "IMEI 绑车",
        onBack = { onBack(false) },
      )
      Spacer(Modifier.height(12.dp))
      BindImeiCard(
        imei = imei,
        onImeiChange = { value -> imei = value.filter { it.isDigit() } },
        busy = busy,
        onSubmit = {
          if (busy) return@BindImeiCard
          if (!cloudService.currentState.signedIn) {
            scope.launch { AppSnack.info(snackbarHostState, OfficialCloudMessages.SIGN_IN_REQUIRED) }
            return@BindImeiCard
          }
          busy = true
          scope.launch {
            try {
              cloudService.bindVehicleByImei(imei)
              AppSnack.success(snackbarHostState, "绑车成功，已刷新车辆列表")
              onBack(true)
            } catch (e: Exception) {
              log.operation(
                "IMEI 绑车失败",
                detail = e.toString(),
                level = LogLevel.WARNING,
              )
              AppSnack.error(snackbarHostState, OfficialCloudRedactor.errorMessage(e))
            } finally {
              busy = false
            }
          }
        },
      )
    }
  }
}

@Composable
private fun BindImeiCard(
  imei: String,
  onImeiChange: (String) -> Unit,
  busy: Boolean,
  onSubmit: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(16.dp),
  ) {
    Column {
      Text(
        text = "对照官方手写 IMEI 绑定（bindCar1 / app/car/bikeBind）。坐垫二维码可扫出同一 IMEI 后粘贴至此。",
        style = TextStyle(
          fontSize = 13.sp,
          lineHeight = 13.sp * 1.45f,
          color = CyberHomeColors.inkMuted,
        ),
      )
      Spacer(Modifier.height(16.dp))
      OutlinedTextField(
        value = imei,
        onValueChange = onImeiChange,
        enabled = !busy,
        singleLine = true,
        label = { Text("设备 IMEI") },
        placeholder = { Text("请输入 15 位左右 IMEI") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        leadingIcon = {
          LucideIcon(icon = Lucide.pin, color = CyberHomeColors.primary)
        },
        colors = cyberTextFieldColors(),
        shape = cyberTextFieldShape,
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(16.dp))
      Button(
        onClick = onSubmit,
        enabled = !busy,
        shape = cyberButtonShape,
        colors = cyberFilledButtonColors(),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp),
      ) {
        if (busy) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = CyberHomeColors.white,
          )
        } else {
          Text(
            text = "确认绑定",
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.W700),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}
