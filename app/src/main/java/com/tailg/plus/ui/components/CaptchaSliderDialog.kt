package com.tailg.plus.ui.components

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tailg.plus.ui.theme.AppIconSizes
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppTouchTargets
import com.tailg.plus.ui.theme.CyberHomeColors
import org.json.JSONObject

/**
 * 官方滑块验证对话框 —— 对齐官方 3.6.0 的登录验证码流程。
 *
 * 加载官方托管的 `appCode.html`（AppID 199180182，内含腾讯防水墙/253 滑块）。
 * 用户完成滑块后，页面 JS 通过 `tailgAppJsInterface.setSmsInfo(json)` 回传
 * `ticket` / `randstr`，经 [onResult] 交回调用方；调用方再走
 * `sliderVerify` → `loginCode` 发验证码（旧 `app/getCode` 已被后端 UA 拦截）。
 *
 * JS 接口方法由 WebView 的 JavaBridge 线程回调，必须 post 回主线程才能更新
 * Compose 状态；[onResult] 也因此在主线程被调用。
 */
@Composable
fun CaptchaSliderDialog(
  onResult: (ticket: String, randstr: String) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  var failed by remember { mutableStateOf(false) }

  val webView = remember(context) {
    WebView(context).apply {
      setBackgroundColor(android.graphics.Color.WHITE)
      @SuppressLint("SetJavaScriptEnabled")
      settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        cacheMode = WebSettings.LOAD_DEFAULT
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
      }
      addJavascriptInterface(
        CaptchaJsInterface(
          onResult = { ticket, randstr ->
            Handler(Looper.getMainLooper()).post { onResult(ticket, randstr) }
          },
          onError = {
            Handler(Looper.getMainLooper()).post { failed = true }
          },
        ),
        "tailgAppJsInterface",
      )
      webViewClient = WebViewClient()
      loadUrl(CAPTCHA_URL)
    }
  }
  DisposableEffect(webView) {
    onDispose { webView.destroy() }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .clip(RoundedCornerShape(AppRadii.sheet))
        .background(CyberHomeColors.card)
        .padding(horizontal = 20.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        LucideIcon(
          icon = Lucide.shield,
          size = AppIconSizes.md,
          color = CyberHomeColors.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
          text = "安全验证",
          style = TextStyle(
            fontSize = 16.sp,
            fontWeight = FontWeight.W700,
            color = CyberHomeColors.ink,
          ),
          modifier = Modifier.weight(1f),
        )
        AppPressable(
          onClick = onDismiss,
          shape = CircleShape,
          semanticsLabel = "关闭",
        ) {
          Box(
            modifier = Modifier.size(AppTouchTargets.min),
            contentAlignment = Alignment.Center,
          ) {
            LucideIcon(icon = Lucide.x, size = AppIconSizes.md, color = CyberHomeColors.inkMuted)
          }
        }
      }
      Spacer(Modifier.height(6.dp))
      Text(
        text = "请完成滑块验证后获取验证码",
        textAlign = TextAlign.Center,
        style = TextStyle(
          fontSize = 13.sp,
          color = CyberHomeColors.inkMuted,
        ),
      )
      Spacer(Modifier.height(14.dp))
      AndroidView(
        factory = { webView },
        modifier = Modifier
          .fillMaxWidth()
          .height(400.dp)
          .clip(RoundedCornerShape(AppRadii.tile)),
      )
      if (failed) {
        Spacer(Modifier.height(12.dp))
        Text(
          text = "验证码加载失败，请关闭后重试",
          style = TextStyle(
            fontSize = 12.sp,
            color = CyberHomeColors.danger,
          ),
        )
      }
    }
  }
}

/**
 * `tailgAppJsInterface` —— appCode.html 通过 `navigator.userAgent` 判断平台后，
 * Android 分支调用 `window.tailgAppJsInterface.setSmsInfo(data)`。
 */
private class CaptchaJsInterface(
  private val onResult: (String, String) -> Unit,
  private val onError: (String) -> Unit,
) {
  @JavascriptInterface
  fun setSmsInfo(data: String) {
    try {
      val json = JSONObject(data)
      val ticket = json.optString("ticket")
      val randstr = json.optString("randstr")
      if (ticket.isNotBlank()) {
        onResult(ticket, randstr)
      }
    } catch (_: Exception) {
      // 解析失败按无结果处理，页面会通过 setError 上报。
    }
  }

  @JavascriptInterface
  fun setError(errorCode: String) {
    onError(errorCode)
  }
}

private const val CAPTCHA_URL = "https://www.tailgdd.com/document/appCode.html"
