package com.tailg.plus.ui.components

import com.tailg.plus.data.cloud.OfficialCloudMessages
import com.tailg.plus.data.cloud.OfficialCloudState

/**
 * Port of `lib/widgets/cloud_vehicle_gate.dart` — gate for vehicle-dependent
 * features (location / battery / settings / …).
 *
 * **Pending references** (later batch, per CONVENTIONS.md):
 * - [OfficialCloudState] (Dart `lib/services/official_cloud_service.dart`,
 *   fields `signedIn`, `selectedVehicle`) → `com.tailg.plus.data.cloud`.
 * - [OfficialCloudMessages] (Dart string constants, e.g. `signInRequired`)
 *   → `com.tailg.plus.data.cloud`.
 *
 * **API adaptation**: Dart takes a `BuildContext` and pushes routes through
 * the Navigator; Compose passes the gate inputs explicitly and the call site
 * owns navigation (NavController / route lambdas). The snackbar is delivered
 * through the suspend [snackbarInfo] hook (callers bridge it to
 * [AppSnack.info]). Returns `true` only when signed in AND a cloud vehicle
 * is selected.
 */
suspend fun requireCloudVehicle(
  state: OfficialCloudState,
  snackbarInfo: suspend (String) -> Unit,
  onNavigateLogin: () -> Unit,
  onNavigateAddVehicle: () -> Unit,
  offerLogin: Boolean = true,
  offerAddVehicle: Boolean = true,
  message: String? = null,
): Boolean {
  if (state.signedIn && state.selectedVehicle != null) {
    return true
  }

  if (!state.signedIn) {
    snackbarInfo(message ?: OfficialCloudMessages.signInRequired)
    if (offerLogin) {
      onNavigateLogin() // Dart: unawaited(Navigator.push(LoginPage()))
    }
    return false
  }

  snackbarInfo(message ?: "暂无车辆，请先同步官方车辆")
  if (offerAddVehicle) {
    onNavigateAddVehicle() // Dart: unawaited(Navigator.push(AddVehiclePage()))
  }
  return false
}

/**
 * Run [open] after an optional cloud-vehicle gate (Dart `openCloudGatedPage`).
 * `gate` is the [requireCloudVehicle] result (or a custom gate).
 */
fun openCloudGatedPage(
  gate: Boolean,
  open: () -> Unit,
  requireVehicle: Boolean = true,
) {
  if (requireVehicle && !gate) return
  open()
}
