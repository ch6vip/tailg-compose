package com.tailg.plus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tailg.plus.ui.components.AppSnack
import com.tailg.plus.ui.components.CyberCard
import com.tailg.plus.ui.components.CyberPageHeader
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.components.cyberBodyStyle
import com.tailg.plus.ui.components.cyberButtonShape
import com.tailg.plus.ui.components.cyberCaptionStyle
import com.tailg.plus.ui.components.cyberFilledButtonColors
import com.tailg.plus.ui.components.cyberOutlinedButtonBorder
import com.tailg.plus.ui.components.cyberOutlinedButtonColors
import com.tailg.plus.ui.components.cyberTextFieldColors
import com.tailg.plus.ui.components.cyberTextFieldShape
import com.tailg.plus.ui.theme.AppIconSizes
import com.tailg.plus.ui.theme.CyberHomeColors
import com.tailg.plus.util.SensitiveValueMasker

/**
 * Port of `lib/pages/cloud_token_page.dart` → `CloudTokenScreen.kt`.
 *
 * Copy / paste the official session token to sign in without SMS (device
 * transfer / multi-client sharing). Business logic lives in
 * [CloudTokenViewModel] (Hilt), so the composable stays a thin view and the
 * pasted token survives configuration changes.
 */
@Composable
fun CloudTokenScreen(
  onBack: () -> Unit,
  viewModel: CloudTokenViewModel = hiltViewModel(),
) {
  val snackbarHostState = remember { SnackbarHostState() }
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val messages by viewModel.messages.collectAsStateWithLifecycle()

  // One-shot snackbars: show the newest pending message, then consume it.
  LaunchedEffect(messages) {
    messages.lastOrNull()?.let { msg ->
      if (msg.isError) {
        AppSnack.error(snackbarHostState, msg.text)
      } else {
        AppSnack.info(snackbarHostState, msg.text)
      }
      viewModel.consumeMessage()
    }
  }

  // Seed the field with the current token once.
  LaunchedEffect(uiState.cloudState.token) {
    viewModel.seedTokenIfEmpty()
  }

  val state = uiState.cloudState
  val tokenText = uiState.tokenText
  val busy = uiState.busy
  val signedIn = state.signedIn
  val loading = busy || state.loading

  Scaffold(
    containerColor = CyberHomeColors.pageBg,
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(padding)
        .padding(bottom = 32.dp),
    ) {
      CyberPageHeader(title = "云端 Token", onBack = onBack)
      Spacer(Modifier.height(12.dp))

      CyberCard {
        Column {
          Text(
            text = "当前会话",
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
          )
          Spacer(Modifier.height(8.dp))
          Text(
            text = if (signedIn) "已登录 · ${maskToken(state.token)}" else "未登录 · 可粘贴 Token 直接进入官方会话",
            style = cyberBodyStyle,
          )
          if (state.phone.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
              text = "手机号 ${SensitiveValueMasker.phone(state.phone)}",
              style = cyberCaptionStyle,
            )
          }
          Spacer(Modifier.height(12.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
              onClick = { viewModel.copyCurrentToken() },
              enabled = signedIn && !loading,
              border = cyberOutlinedButtonBorder,
              colors = cyberOutlinedButtonColors(),
              modifier = Modifier.weight(1f),
            ) {
              LucideIcon(icon = Lucide.copy, size = AppIconSizes.sm)
              Spacer(Modifier.width(6.dp))
              Text("复制 Token")
            }
            OutlinedButton(
              onClick = { viewModel.pasteFromClipboard() },
              enabled = !loading,
              border = cyberOutlinedButtonBorder,
              colors = cyberOutlinedButtonColors(),
              modifier = Modifier.weight(1f),
            ) {
              LucideIcon(icon = Lucide.clipboardPaste, size = AppIconSizes.sm)
              Spacer(Modifier.width(6.dp))
              Text("粘贴")
            }
          }
        }
      }

      Spacer(Modifier.height(14.dp))

      CyberCard {
        Column {
          Text(
            text = "粘贴 Token 登录",
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
          )
          Spacer(Modifier.height(4.dp))
          Text(
            text = "支持直接粘贴 Authorization 值,或带 Bearer 前缀 / Authorization 头整行。" +
              "登录后会写入安全存储并同步车辆。",
            style = cyberBodyStyle,
          )
          Spacer(Modifier.height(12.dp))
          OutlinedTextField(
            value = tokenText,
            onValueChange = { viewModel.onTokenTextChange(it) },
            minLines = 3,
            maxLines = 6,
            textStyle = TextStyle(
              fontSize = 13.sp,
              fontFamily = FontFamily.Monospace,
              lineHeight = 13.sp * 1.35f,
              color = CyberHomeColors.ink,
            ),
            placeholder = { Text("粘贴 Token 或 Authorization: Bearer ...") },
            shape = cyberTextFieldShape,
            colors = cyberTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(12.dp))
          Button(
            onClick = { viewModel.loginWithToken() },
            enabled = !loading,
            shape = cyberButtonShape,
            colors = cyberFilledButtonColors(),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp),
          ) {
            if (loading) {
              CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = CyberHomeColors.white,
                modifier = Modifier.height(18.dp),
              )
            } else {
              LucideIcon(icon = Lucide.login)
            }
            Spacer(Modifier.width(8.dp))
            Text(if (signedIn) "用此 Token 重新登录" else "用 Token 登录")
          }
        }
      }

      Spacer(Modifier.height(14.dp))

      CyberCard(contentPadding = PaddingValues(14.dp)) {
        Text(
          text = "Token 等同于账号登录凭证,请勿分享给不可信的人或页面。" +
            "复制仅用于你自己的多设备调试与迁移。",
          style = TextStyle(fontSize = 12.sp, lineHeight = 12.sp * 1.45f, color = CyberHomeColors.inkMuted),
        )
      }
    }
  }
}

private fun maskToken(token: String): String =
  SensitiveValueMasker.compact(token, emptyValue = "未登录")
