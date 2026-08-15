package com.tailg.plus.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisualStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tailg.plus.ui.theme.AppColorsDark
import com.tailg.plus.ui.theme.AppColorsLight
import com.tailg.plus.ui.theme.AppRadii
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Port of `lib/widgets/app_snack.dart`.
 *
 * Compose has no ambient `ScaffoldMessenger`; call sites pass a
 * [SnackbarHostState] instead of a `BuildContext`. Both suspend and
 * fire-and-forget (`CoroutineScope`) variants are provided.
 *
 * Token mapping (Dart → Compose):
 * - `VoidColors.energyRed` → [AppColorsDark.energyRed]; Dart `Colors.white` → [AppColorsDark.textPrimary].
 * - `VoidColors.energy` → [AppColorsDark.energyGreen]; Dart `Colors.black` → [AppColorsLight.textPrimary].
 * - `VoidColors.voidPanelHi` → [AppColorsDark.surfaceContainerHigh]; `VoidColors.ink` → [AppColorsDark.textPrimary].
 * - `AppRadii.sm` → [AppRadii.sm].
 *
 * Icons: `Lucide.alert-circle` → Material `Icons.Filled.ErrorOutline`;
 * `Lucide.check-circle` → `Icons.Filled.CheckCircle`; `Lucide.info` → `Icons.Filled.Info`.
 */
object AppSnack {
  // Dart _errorDuration = 3s, _infoDuration = 2s.
  private const val errorDurationMillis = 3_000L
  private const val infoDurationMillis = 2_000L

  /** 错误提示：红色背景，长时间停留 3s。 */
  suspend fun error(
    hostState: SnackbarHostState,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
  ) {
    show(
      hostState,
      message = message,
      background = AppColorsDark.energyRed,
      foreground = AppColorsDark.textPrimary,
      icon = Lucide.alertCircle,
      durationMillis = errorDurationMillis,
      actionLabel = actionLabel,
      onAction = onAction,
    )
  }

  fun error(
    scope: CoroutineScope,
    hostState: SnackbarHostState,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
  ) {
    scope.launch { error(hostState, message, actionLabel, onAction) }
  }

  /** 成功提示：energy 背景，2s 停留。 */
  suspend fun success(hostState: SnackbarHostState, message: String) {
    show(
      hostState,
      message = message,
      background = AppColorsDark.energyGreen,
      foreground = AppColorsLight.textPrimary, // Dart Colors.black on energy green
      icon = Lucide.checkCircle,
      durationMillis = infoDurationMillis,
    )
  }

  fun success(scope: CoroutineScope, hostState: SnackbarHostState, message: String) {
    scope.launch { success(hostState, message) }
  }

  /** 普通提示：void panel 背景，2s 停留。 */
  suspend fun info(hostState: SnackbarHostState, message: String) {
    show(
      hostState,
      message = message,
      background = AppColorsDark.surfaceContainerHigh,
      foreground = AppColorsDark.textPrimary,
      icon = Lucide.info,
      durationMillis = infoDurationMillis,
    )
  }

  fun info(scope: CoroutineScope, hostState: SnackbarHostState, message: String) {
    scope.launch { info(hostState, message) }
  }

  /** Placeholder / not-yet-open feature entry (cloud-only product boundary). */
  suspend fun featureUnavailable(hostState: SnackbarHostState, label: String) {
    info(hostState, "$label暂未开放，可先使用官方云端控车")
  }

  fun featureUnavailable(scope: CoroutineScope, hostState: SnackbarHostState, label: String) {
    scope.launch { featureUnavailable(hostState, label) }
  }

  /** Out-of-scope feature (L3 / non-replica) — never implies official support. */
  suspend fun outOfReplicaScope(hostState: SnackbarHostState, label: String) {
    info(hostState, "$label不在复刻范围内")
  }

  fun outOfReplicaScope(scope: CoroutineScope, hostState: SnackbarHostState, label: String) {
    scope.launch { outOfReplicaScope(hostState, label) }
  }

  /** Short not-yet-open notice for legal/support entries without a cloud fallback. */
  suspend fun notYetOpen(hostState: SnackbarHostState, label: String) {
    info(hostState, "$label暂未开放")
  }

  fun notYetOpen(scope: CoroutineScope, hostState: SnackbarHostState, label: String) {
    scope.launch { notYetOpen(hostState, label) }
  }

  private suspend fun show(
    hostState: SnackbarHostState,
    message: String,
    background: androidx.compose.ui.graphics.Color,
    foreground: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    durationMillis: Long,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
  ) {
    hostState.currentSnackbarData?.dismiss()
    val result = hostState.showSnackbar(
      message = message,
      actionLabel = actionLabel,
      withDismissAction = false,
      duration = if (durationMillis >= 3_000L) SnackbarDuration.Long else SnackbarDuration.Short,
      visualStyle = SnackbarVisualStyle(
        icon = icon,
        backgroundColor = background,
        contentColor = foreground,
        shape = RoundedCornerShape(AppRadii.sm),
      ),
    )
    if (result == SnackbarResult.ActionPerformed) {
      onAction?.invoke()
    }
  }
}

/**
 * Snackbar host that renders the VOID snackbar (Dart `SnackBarBehavior.floating`
 * with `margin: 16`). Place `Scaffold(snackbarHost = { AppSnackbarHost(state) })`
 * or overlay it in the app root.
 */
@Composable
fun AppSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
  SnackbarHost(
    hostState = hostState,
    modifier = modifier.padding(16.dp),
  )
}
