package com.tailg.plus.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.ui.theme.AppColorsDark
import com.tailg.plus.ui.theme.AppColorsLight
import com.tailg.plus.ui.theme.AppRadii
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Port of `lib/widgets/app_toast.dart` — global unified Toast sliding down
 * from the top of the screen.
 *
 * Compose has no global `Overlay`; the singleton [AppToast] drives a
 * [ToastHostState] that [AppToastHost] observes. Place `AppToastHost()` once
 * at the app root (inside the theme, above the nav host). The Dart
 * `navigatorKey`-based overlay is replaced by the host composable; the
 * no-`BuildContext` call API (`AppToast.show("…")`) is preserved.
 *
 * Token mapping:
 * - error bg `AppColors.energyRed` → [AppColorsDark.energyRed], fg `Colors.white` → [AppColorsDark.textPrimary].
 * - success bg `AppColors.energyGreen` → [AppColorsDark.energyGreen], fg `Colors.black` → [AppColorsLight.textPrimary].
 * - radius `AppRadii.md` → [AppRadii.md].
 *
 * Icons: `Lucide.x` → `Icons.Filled.Close`; `Lucide.check-circle` → `Icons.Filled.CheckCircle`.
 */
data class ToastData(
  val message: String,
  val isError: Boolean,
  val id: Long,
)

/** Observable toast queue for [AppToastHost]. */
class ToastHostState {
  var toast by mutableStateOf<ToastData?>(null)
    private set

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var dismissJob: Job? = null

  fun show(message: String, isError: Boolean) {
    dismissJob?.cancel()
    toast = ToastData(message = message, isError = isError, id = System.nanoTime())
    dismissJob = scope.launch {
      delay(AppMotion.toastVisible)
      toast = null
    }
  }

  fun dismiss() {
    dismissJob?.cancel()
    dismissJob = null
    toast = null
  }
}

/** Global unified Toast — mirrors `AppToast`'s static API. */
object AppToast {
  val hostState = ToastHostState()

  fun show(message: String, isError: Boolean = false) {
    hostState.show(message, isError)
  }

  fun dismiss() {
    hostState.dismiss()
  }
}

/**
 * Root overlay host for [AppToast]. Non-interactive except for the pill's
 * dismiss button (Dart `OverlayEntry` behaves the same).
 */
@Composable
fun AppToastHost(modifier: Modifier = Modifier) {
  val toast = AppToast.hostState.toast
  Box(modifier = modifier.fillMaxSize()) {
    AnimatedVisibility(
      visible = toast != null,
      modifier = Modifier
        .align(Alignment.TopCenter)
        .statusBarsPadding()
        .padding(start = 20.dp, end = 20.dp, top = 12.dp),
      enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(AppMotion.toastEntrance)) + fadeIn(tween(AppMotion.toastEntrance)),
      exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(AppMotion.toastEntrance)) + fadeOut(tween(AppMotion.toastEntrance)),
    ) {
      if (toast != null) {
        ToastPill(toast = toast, onDismiss = { AppToast.dismiss() })
      }
    }
  }
}

@Composable
private fun ToastPill(toast: ToastData, onDismiss: () -> Unit) {
  val bg = if (toast.isError) AppColorsDark.energyRed else AppColorsDark.energyGreen
  val fg = if (toast.isError) AppColorsDark.textPrimary else AppColorsLight.textPrimary
  val icon = if (toast.isError) Lucide.x else Lucide.checkCircle

  Row(
    modifier = Modifier
      .shadow(
        elevation = 8.dp,
        shape = RoundedCornerShape(AppRadii.md),
        clip = false,
        ambientColor = Color.Transparent,
        spotColor = bg.copy(alpha = 0.35f),
      )
      .clip(RoundedCornerShape(AppRadii.md))
      .background(bg)
      .padding(start = 18.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    LucideIcon(icon = icon, size = 18.dp, color = fg)
    Text(
      text = toast.message,
      style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W600, color = fg),
      modifier = Modifier.weight(1f),
    )
    AppPressable(
      onClick = onDismiss,
      haptic = false,
      shape = RoundedCornerShape(AppRadii.md),
    ) {
      Box(
        modifier = Modifier
          .size(width = 44.dp, height = 44.dp),
        contentAlignment = Alignment.Center,
      ) {
        LucideIcon(icon = Lucide.x, size = 16.dp, color = fg.copy(alpha = 0.7f))
      }
    }
  }
}
