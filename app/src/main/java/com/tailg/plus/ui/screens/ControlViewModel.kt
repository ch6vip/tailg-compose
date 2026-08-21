package com.tailg.plus.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.model.CommandCode
import com.tailg.plus.data.model.ControlCommandActivityLog
import com.tailg.plus.data.mqtt.OfficialMqttService
import com.tailg.plus.data.network.NetworkAvailabilityService
import com.tailg.plus.data.store.VehicleStore
import com.tailg.plus.domain.control.ControlCommandExecutor
import com.tailg.plus.domain.control.OfficialControlChannel
import com.tailg.plus.log.LogService
import com.tailg.plus.service.LocationService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the vehicle control tab that must survive configuration changes.
 * Transport objects stay injected singletons; this VM owns ephemeral
 * control-session fields previously held in remember {} blocks.
 */
data class ControlUiState(
  val busy: Boolean = false,
  val activeCommand: CommandCode? = null,
  val controlChannel: OfficialControlChannel = OfficialControlChannel.AUTOMATIC,
  val showChannelSheet: Boolean = false,
  val showVehicleSwitchSheet: Boolean = false,
  val lastCommandAtMs: Long = 0L,
  val commandVersion: Int = 0,
  val networkReady: Boolean = true,
)

@HiltViewModel
class ControlViewModel @Inject constructor(
  val cloudService: OfficialCloudService,
  val connectionManager: ConnectionManager,
  val mqttService: OfficialMqttService,
  val vehicleStore: VehicleStore,
  val log: LogService,
  networkAvailability: NetworkAvailabilityService,
  val locationService: LocationService,
) : ViewModel() {

  private val _ui = MutableStateFlow(ControlUiState())
  val uiState: StateFlow<ControlUiState> = _ui.asStateFlow()

  val commandLog = ControlCommandActivityLog()

  val commandExecutor = ControlCommandExecutor(
    sendBleCommand = { command -> connectionManager.sendCommand(command.toBleCommandCode()) },
    sendCloudCommand = { command -> mqttService.sendCommandPreferMqtt(command, cloudService) },
  )

  init {
    viewModelScope.launch {
      networkAvailability.changes.collect { ready ->
        _ui.update { it.copy(networkReady = ready) }
      }
    }
  }

  fun setBusy(busy: Boolean, command: CommandCode? = null) {
    _ui.update {
      it.copy(
        busy = busy,
        activeCommand = if (busy) command else null,
      )
    }
  }

  fun setControlChannel(channel: OfficialControlChannel) {
    _ui.update { it.copy(controlChannel = channel) }
  }

  fun setShowChannelSheet(show: Boolean) {
    _ui.update { it.copy(showChannelSheet = show) }
  }

  fun setShowVehicleSwitchSheet(show: Boolean) {
    _ui.update { it.copy(showVehicleSwitchSheet = show) }
  }

  fun markCommandIssued(nowMs: Long = System.currentTimeMillis()) {
    _ui.update {
      it.copy(
        lastCommandAtMs = nowMs,
        commandVersion = it.commandVersion + 1,
      )
    }
  }

  fun bumpCommandVersion() {
    _ui.update { it.copy(commandVersion = it.commandVersion + 1) }
  }

  fun retryMqttPreconnectIfNeeded() {
    viewModelScope.launch {
      if (!mqttService.isConnected && mqttService.lastPreconnectError != null) {
        mqttService.retryPreconnect(cloudService)
      }
    }
  }
}
