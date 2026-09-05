package com.tailg.plus.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.mqtt.OfficialMqttService
import com.tailg.plus.data.network.NetworkAvailabilityService
import com.tailg.plus.data.preferences.AppPreferencesService
import com.tailg.plus.data.store.ReplicaFeatureStore
import com.tailg.plus.data.store.VehicleStore
import com.tailg.plus.log.LogService
import com.tailg.plus.service.InductionModeService
import com.tailg.plus.service.ManualModeService
import com.tailg.plus.util.ClipboardText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Application-scoped ViewModel that holds all singleton services for the
 * navigation host. Replaces the [TailgEntryPoint] pattern by providing
 * constructor-injected services through Hilt's ViewModel lifecycle.
 *
 * Screens that need these services receive them as constructor-injected
 * parameters in their own @HiltViewModel, or as Composable parameters
 * passed down from [TailgNavHost].
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    val cloudService: OfficialCloudService,
    val connectionManager: ConnectionManager,
    val mqttService: OfficialMqttService,
    val vehicleStore: VehicleStore,
    val logService: LogService,
    val clipboardText: ClipboardText,
    val appPreferences: AppPreferencesService,
    val networkAvailability: NetworkAvailabilityService,
    val replicaFeatureStore: ReplicaFeatureStore,
    val inductionModeService: InductionModeService,
    val manualModeService: ManualModeService,
) : ViewModel()