package com.tailg.plus.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

/**
 * Lottie power-feedback animation helper.
 *
 * **Origin note**: the Flutter app ships `assets/official_tailg/lottie/`
 * (anmim + startanmim + stopanmim) but **no Dart widget consumes them yet**
 * (grep across `lib/` finds no `lottie` reference) — the assets are orphaned
 * in the Flutter repo as well. Per the port task, the JSON + embedded images
 * were copied into `app/src/main/assets/official_tailg/lottie/` preserving the
 * folder structure, and this composable is the reference point for the
 * original paths (see the constants below) so a future screen can drop it in.
 *
 * Backed by `com.airbnb.android:lottie-compose` 6.6.4 (declared in
 * `gradle/libs.versions.toml`).
 */
enum class ControlPowerLottieKind { Start, Stop, Loading }

/** Original Flutter asset paths, preserved 1:1 under `assets/official_tailg/lottie/`. */
object ControlPowerLottieAssets {
  // assets/official_tailg/lottie/anmim/control_daw_start_stop_load.json
  const val LOADING = "official_tailg/lottie/anmim/control_daw_start_stop_load.json"
  // assets/official_tailg/lottie/startanmim/control_daw_start.json + images/img_0.png
  const val START = "official_tailg/lottie/startanmim/control_daw_start.json"
  // assets/official_tailg/lottie/stopanmim/control_daw_stop.json + images/img_0.png
  const val STOP = "official_tailg/lottie/stopanmim/control_daw_stop.json"
}

@Composable
fun ControlPowerLottie(
  kind: ControlPowerLottieKind,
  modifier: Modifier = Modifier,
  size: Dp = 120.dp,
) {
  val asset = when (kind) {
    ControlPowerLottieKind.Loading -> ControlPowerLottieAssets.LOADING
    ControlPowerLottieKind.Start -> ControlPowerLottieAssets.START
    ControlPowerLottieKind.Stop -> ControlPowerLottieAssets.STOP
  }
  val composition by rememberLottieComposition(LottieCompositionSpec.Asset(asset))
  val progress by animateLottieCompositionAsState(
    composition = composition,
    iterations = LottieConstants.IterateForever,
  )
  LottieAnimation(
    composition = composition,
    progress = { progress },
    modifier = modifier.size(size),
  )
}
