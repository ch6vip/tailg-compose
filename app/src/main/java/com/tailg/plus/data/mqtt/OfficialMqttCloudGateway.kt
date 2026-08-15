package com.tailg.plus.data.mqtt

import com.tailg.plus.data.model.CommandCode
import com.tailg.plus.data.model.OfficialVehicle
import kotlinx.coroutines.flow.Flow

/**
 * Minimal cloud-session seam consumed by [OfficialMqttService].
 *
 * Dart parity: the Dart service binds the concrete `OfficialCloudService`
 * (`attachToCloud`, `_boundCloud ?? OfficialCloudService()`). The Kotlin port
 * depends on this interface instead, so `data.mqtt` does not reach into the
 * not-yet-ported `data.cloud` module; the cloud port's `OfficialCloudService`
 * must implement this interface (one adapter line: `stateChanges` =
 * `state.map { Unit }`).
 *
 * Contract required from the cloud port (mirrors the Dart API):
 *  - [selectedVehicle] ← `OfficialCloudState.selectedVehicle`
 *  - [signedIn]        ← `OfficialCloudState.signedIn` (token.isNotEmpty)
 *  - [userId]          ← `OfficialCloudState.userId`
 *  - [applyMqttVehicleStatus] ← `OfficialCloudService.applyMqttVehicleStatus`
 *  - [sendCommand]     ← `OfficialCloudService.sendCommand` (HTTP cmd fallback)
 *  - [refreshVehicles] ← `OfficialCloudService.refreshVehicles`
 */
interface OfficialMqttCloudGateway {

    /** Currently selected official vehicle (null when signed out / none selected). */
    val selectedVehicle: OfficialVehicle?

    /** Whether the official account is signed in. */
    val signedIn: Boolean

    /** Current official userId (empty when signed out). */
    val userId: String

    /** Emits whenever [selectedVehicle]/[signedIn]/[userId] may have changed. */
    val stateChanges: Flow<Unit>

    /** Apply MQTT ACC/defence telemetry to the selected vehicle. */
    fun applyMqttVehicleStatus(acc: Int?, defenceStatus: Int?)

    /** Official HTTP cmd fallback; returns the transport message. */
    suspend fun sendCommand(command: CommandCode): String

    /** Lightweight vehicle-list refresh used for secondary consistency. */
    suspend fun refreshVehicles(silent: Boolean, force: Boolean)
}
