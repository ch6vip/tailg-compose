package com.tailg.plus.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.data.cloud.OfficialCloudRedactor
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.service.FirmwareOtaPhase
import com.tailg.plus.service.FirmwareOtaProgress
import com.tailg.plus.service.FirmwareOtaService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [FirmwareOtaScreen].
 *
 * Owns the OTA [FirmwareOtaService] flow collection and the running/progress
 * UI state so the screen survives configuration changes and the composable
 * holds no business logic. The `ConnectionManager` and `OfficialCloudService`
 * come from the Hilt graph as singletons.
 */
@HiltViewModel
class FirmwareOtaViewModel @Inject constructor(
  cloud: OfficialCloudService,
  connectionManager: ConnectionManager,
) : ViewModel() {

  private val ota = FirmwareOtaService(cloud = cloud, connectionManager = connectionManager)

  private val _running = MutableStateFlow(false)
  val running: StateFlow<Boolean> = _running.asStateFlow()

  /** Progress state; the [FirmwareOtaPhase] + message drive the UI. */
  private val _progress = MutableStateFlow(FirmwareOtaProgress(FirmwareOtaPhase.IDLE, 0.0, ""))
  val progress: StateFlow<FirmwareOtaProgress> = _progress.asStateFlow()

  /** Start the OTA flow. No-op while already running. */
  fun start() {
    if (_running.value) return
    viewModelScope.launch {
      _running.value = true
      _progress.value = FirmwareOtaProgress(FirmwareOtaPhase.QUERYING, 0.0, "")
      try {
        ota.run().collectLatest { p ->
          _progress.value = p
          if (p.phase == FirmwareOtaPhase.COMPLETED || p.phase == FirmwareOtaPhase.FAILED) {
            _running.value = false
          }
        }
      } catch (e: Exception) {
        _running.value = false
        _progress.value = FirmwareOtaProgress(FirmwareOtaPhase.FAILED, _progress.value.fraction, OfficialCloudRedactor.errorMessage(e))
      }
    }
  }
}