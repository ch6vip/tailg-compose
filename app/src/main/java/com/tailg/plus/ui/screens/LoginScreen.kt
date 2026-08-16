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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.data.cloud.OfficialCloudLoginValidator
import com.tailg.plus.data.cloud.OfficialCloudRedactor
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService
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
import com.tailg.plus.ui.theme.AppTouchTargets
import com.tailg.plus.ui.theme.CyberHomeColors
import com.tailg.plus.util.ClipboardText
import com.tailg.plus.util.SmsCountdown
import kotlinx.coroutines.launch

private enum class LoginMode { SMS, TOKEN }

/**
 * Port of `lib/pages/login_page.dart` — combined SMS + Token login.
 *
 * Mirrors the official `LoginOnActivity` / `LoginPhoneCodeActivity` flow in a
 * single page. The Dart page observes `officialCloudService.stateStream` and
 * calls `AppNavigation.returnToVehicleHome` on success; the Compose port
 * invokes [onSignedIn] when the service reports a signed-in state.
 *
 * The Dart page uses a global `SmsCountdown`; the Compose port creates one
 * scoped to the composition via `remember` + `rememberCoroutineScope`.
 */
@Composable
fun LoginScreen(
  cloudService: OfficialCloudService,
  onSignedIn: (successMessage: String?) -> Unit = { _ -> },
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  val log = remember { LogService() }
  val clipboard = remember { ClipboardText(context) }
  val smsCountdown = remember { SmsCountdown(scope = scope) }
  val countdown by smsCountdown.remaining.collectAsState()
  val cloudState by cloudService.stateFlow.collectAsState()

  var phone by remember {
    mutableStateOf(cloudService.currentState.phone)
  }
  var smsCode by remember { mutableStateOf("") }
  var token by remember {
    mutableStateOf(cloudService.currentState.token)
  }
  var agreed by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var mode by remember { mutableStateOf(LoginMode.SMS) }

  // React to signed-in state changes (Dart `_onStateChanged`). This is a
  // safety net: the submit handlers call onSignedIn() directly after success
  // (matching the Dart original), but this catches cases where signedIn
  // changes from outside the handlers (e.g. session restore).
  LaunchedEffect(cloudState.signedIn, busy) {
    if (cloudState.signedIn && !busy) {
      onSignedIn(null)
    }
  }

  val loading = busy || cloudState.loading
  val normalizedPhone = OfficialCloudLoginValidator.compactPhone(phone)
  val validPhone = OfficialCloudLoginValidator.isValidPhone(normalizedPhone)
  val validSms = OfficialCloudLoginValidator.isValidSmsCode(smsCode.trim())

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
        .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      BrandHeader()
      Spacer(Modifier.height(28.dp))
      if (mode == LoginMode.SMS) {
        SmsLoginForm(
          phone = phone,
          onPhoneChange = { value -> phone = value.filter { it.isDigit() }.take(11) },
          smsCode = smsCode,
          onSmsCodeChange = { value -> smsCode = value.filter { it.isDigit() }.take(8) },
          countdown = countdown,
          loading = loading,
          validPhone = validPhone,
          validSms = validSms,
          onRequestCode = {
            if (smsCountdown.isActive || !validPhone) return@SmsLoginForm
            if (!agreed) {
              scope.launch { AppSnack.info(snackbarHostState, "请先阅读并同意用户协议与隐私政策") }
              return@SmsLoginForm
            }
            scope.launch {
              try {
                cloudService.requestSmsCode(normalizedPhone)
                smsCountdown.start()
                AppSnack.success(snackbarHostState, "验证码已发送")
              } catch (e: Exception) {
                log.operation(
                  "官云验证码发送失败",
                  detail = e.toString(),
                  level = LogLevel.WARNING,
                )
                AppSnack.error(snackbarHostState, OfficialCloudRedactor.errorMessage(e))
              }
            }
          },
          onLogin = {
            if (busy) return@SmsLoginForm
            if (!validPhone) {
              scope.launch { AppSnack.info(snackbarHostState, "请输入 11 位手机号") }
              return@SmsLoginForm
            }
            if (!validSms) {
              scope.launch { AppSnack.info(snackbarHostState, "请输入短信验证码") }
              return@SmsLoginForm
            }
            if (!agreed) {
              scope.launch { AppSnack.info(snackbarHostState, "请先阅读并同意用户协议与隐私政策") }
              return@SmsLoginForm
            }
            busy = true
            scope.launch {
              try {
                cloudService.login(normalizedPhone, smsCode.trim())
                // Navigate immediately (Dart AppSnack.success is fire-and-forget;
                // M3 showSnackbar suspends ~4s and would block navigation). The
                // success toast is shown by the host on the destination screen.
                onSignedIn("登录成功")
              } catch (e: Exception) {
                log.operation(
                  "官云登录失败",
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
      } else {
        TokenLoginForm(
          token = token,
          onTokenChange = { token = it },
          loading = loading,
          onPaste = {
            val text = clipboard.readClipboardText()
            if (text == null) {
              scope.launch { AppSnack.info(snackbarHostState, "剪贴板为空") }
              return@TokenLoginForm
            }
            token = text
            scope.launch { AppSnack.success(snackbarHostState, "已从剪贴板粘贴") }
          },
          onLogin = {
            if (busy) return@TokenLoginForm
            val raw = token.trim()
            if (raw.isEmpty()) {
              scope.launch { AppSnack.info(snackbarHostState, "请先粘贴 Token") }
              return@TokenLoginForm
            }
            if (!agreed) {
              scope.launch { AppSnack.info(snackbarHostState, "请先阅读并同意用户协议与隐私政策") }
              return@TokenLoginForm
            }
            busy = true
            scope.launch {
              try {
                cloudService.loginWithToken(
                  raw,
                  phone = cloudService.currentState.phone,
                  userId = cloudService.currentState.userId,
                )
                // Navigate immediately; success toast shown by the host.
                onSignedIn("Token 登录成功")
              } catch (e: Exception) {
                log.operation(
                  "Token 登录失败",
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
      Spacer(Modifier.height(12.dp))
      TextButton(
        onClick = {
          mode = if (mode == LoginMode.SMS) LoginMode.TOKEN else LoginMode.SMS
        },
        enabled = !loading,
      ) {
        LucideIcon(
          icon = if (mode == LoginMode.SMS) Lucide.key else Lucide.phone,
          size = AppIconSizes.sm,
          color = CyberHomeColors.primary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
          text = if (mode == LoginMode.SMS) "使用 Token 登录" else "返回手机号登录",
          color = CyberHomeColors.primary,
        )
      }
      Spacer(Modifier.height(12.dp))
      AgreementRow(
        agreed = agreed,
        onChanged = { agreed = it },
      )
      Spacer(Modifier.height(20.dp))
      if (mode == LoginMode.TOKEN) {
        TokenSafetyNote()
      }
    }
  }
}

@Composable
private fun BrandHeader() {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Spacer(Modifier.height(18.dp))
    Box(
      modifier = Modifier
        .size(72.dp)
        .shadow(
          elevation = 4.dp,
          shape = RoundedCornerShape(AppRadii.tile),
          clip = false,
          ambientColor = Color.Transparent,
          spotColor = CyberHomeColors.actionShadow,
        )
        .clip(RoundedCornerShape(AppRadii.tile))
        .background(CyberHomeColors.primarySoft)
        .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile)),
      contentAlignment = Alignment.Center,
    ) {
      LucideIcon(icon = Lucide.vehicle, size = 34.dp, color = CyberHomeColors.primary)
    }
    Spacer(Modifier.height(18.dp))
    Text(
      text = "TAILG",
      style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.ink),
    )
    Spacer(Modifier.height(6.dp))
    Text(
      text = "台铃智能 · VOID COCKPIT",
      style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.inkFaint),
    )
    Spacer(Modifier.height(10.dp))
    Text(
      text = "登录后同步车辆，享受控车、定位、电池等服务",
      textAlign = TextAlign.Center,
      style = TextStyle(
        fontSize = 13.sp,
        lineHeight = 13.sp * 1.45f,
        color = CyberHomeColors.inkMuted,
      ),
    )
  }
}

@Composable
private fun SmsLoginForm(
  phone: String,
  onPhoneChange: (String) -> Unit,
  smsCode: String,
  onSmsCodeChange: (String) -> Unit,
  countdown: Int,
  loading: Boolean,
  validPhone: Boolean,
  validSms: Boolean,
  onRequestCode: () -> Unit,
  onLogin: () -> Unit,
) {
  val showPhoneError = phone.isNotEmpty() && !validPhone
  val showSmsError = smsCode.isNotEmpty() && !validSms
  val canRequest = !loading && validPhone
  val canLogin = !loading && validPhone && validSms

  Column(modifier = Modifier.fillMaxWidth()) {
    FieldLabel("手机号")
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
      value = phone,
      onValueChange = onPhoneChange,
      singleLine = true,
      isError = showPhoneError,
      supportingText = if (showPhoneError) {
        { Text("请输入 11 位手机号") }
      } else null,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
      placeholder = { Text("请输入手机号") },
      colors = cyberTextFieldColors(),
      shape = cyberTextFieldShape,
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    FieldLabel("验证码")
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.Top) {
      OutlinedTextField(
        value = smsCode,
        onValueChange = onSmsCodeChange,
        singleLine = true,
        isError = showSmsError,
        supportingText = if (showSmsError) {
          { Text("请输入短信验证码") }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        placeholder = { Text("请输入验证码") },
        colors = cyberTextFieldColors(),
        shape = cyberTextFieldShape,
        modifier = Modifier.weight(1f),
      )
      Spacer(Modifier.width(10.dp))
      OutlinedButton(
        onClick = onRequestCode,
        enabled = canRequest && countdown == 0,
        shape = cyberButtonShape,
        colors = cyberOutlinedButtonColors(),
        border = cyberOutlinedButtonBorder,
        modifier = Modifier.height(48.dp),
      ) {
        Text(text = if (countdown > 0) "${countdown}s" else "获取验证码")
      }
    }
    Spacer(Modifier.height(24.dp))
    Button(
      onClick = onLogin,
      enabled = canLogin,
      shape = cyberButtonShape,
      colors = cyberFilledButtonColors(),
      modifier = Modifier
        .fillMaxWidth()
        .height(50.dp),
    ) {
      if (loading) {
        CircularProgressIndicator(
          modifier = Modifier.size(20.dp),
          strokeWidth = 2.dp,
          color = CyberHomeColors.white,
        )
      } else {
        Text(
          text = "登录",
          style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.W700),
        )
      }
    }
  }
}

@Composable
private fun TokenLoginForm(
  token: String,
  onTokenChange: (String) -> Unit,
  loading: Boolean,
  onPaste: () -> Unit,
  onLogin: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    FieldLabel("粘贴 Token")
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
      value = token,
      onValueChange = onTokenChange,
      minLines = 3,
      maxLines = 6,
      textStyle = TextStyle(
        fontSize = 13.sp,
        lineHeight = 13.sp * 1.35f,
        color = CyberHomeColors.ink,
      ),
      placeholder = { Text("粘贴 Token 或 Authorization: Bearer ...") },
      trailingIcon = {
        AppPressable(
          onClick = onPaste,
          shape = CircleShape,
          semanticsLabel = "从剪贴板粘贴",
        ) {
          Box(
            modifier = Modifier.size(AppTouchTargets.min),
            contentAlignment = Alignment.Center,
          ) {
            LucideIcon(icon = Lucide.copy, size = AppIconSizes.sm)
          }
        }
      },
      colors = cyberTextFieldColors(),
      shape = cyberTextFieldShape,
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    Text(
      text = "支持直接粘贴 Authorization 值，或带 Bearer 前缀 / Authorization 头整行。登录后写入安全存储并同步车辆。",
      style = TextStyle(
        fontSize = 12.sp,
        lineHeight = 12.sp * 1.45f,
        color = CyberHomeColors.inkFaint,
      ),
    )
    Spacer(Modifier.height(24.dp))
    Button(
      onClick = onLogin,
      enabled = !loading,
      shape = cyberButtonShape,
      colors = cyberFilledButtonColors(),
      modifier = Modifier
        .fillMaxWidth()
        .height(50.dp),
    ) {
      if (loading) {
        CircularProgressIndicator(
          modifier = Modifier.size(20.dp),
          strokeWidth = 2.dp,
          color = CyberHomeColors.white,
        )
      } else {
        Text(
          text = "用 Token 登录",
          style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.W700),
        )
      }
    }
  }
}

@Composable
private fun AgreementRow(
  agreed: Boolean,
  onChanged: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(AppTouchTargets.min),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(
      checked = agreed,
      onCheckedChange = onChanged,
      colors = androidx.compose.material3.CheckboxDefaults.colors(
        checkedColor = CyberHomeColors.primary,
        uncheckedColor = CyberHomeColors.lineStrong,
        checkmarkColor = CyberHomeColors.white,
      ),
    )
    Spacer(Modifier.width(4.dp))
    Text(
      text = "我已阅读并同意《用户协议》和《隐私政策》",
      style = TextStyle(
        fontSize = 12.sp,
        lineHeight = 12.sp * 1.5f,
        color = CyberHomeColors.inkMuted,
      ),
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun TokenSafetyNote() {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(AppRadii.tile))
      .background(CyberHomeColors.card)
      .border(1.dp, CyberHomeColors.line, RoundedCornerShape(AppRadii.tile))
      .padding(14.dp),
    verticalAlignment = Alignment.Top,
  ) {
    LucideIcon(icon = Lucide.shield, size = 18.dp, color = CyberHomeColors.warning)
    Spacer(Modifier.width(10.dp))
    Text(
      text = "Token 等同于账号登录凭证，请勿分享给不可信的人或页面。复制仅用于你自己的多设备调试与迁移。",
      style = TextStyle(
        fontSize = 12.sp,
        lineHeight = 12.sp * 1.45f,
        color = CyberHomeColors.inkMuted,
      ),
    )
  }
}

@Composable
private fun FieldLabel(text: String) {
  Text(
    text = text,
    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W700, color = CyberHomeColors.inkSecondary),
  )
}
