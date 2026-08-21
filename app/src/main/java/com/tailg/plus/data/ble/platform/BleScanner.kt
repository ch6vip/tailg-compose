package com.tailg.plus.data.ble.platform

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.tailg.plus.log.LogService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * BLE device scanner — extracted from [ConnectionManager] to reduce file size.
 * Port of the scan-related methods from `ConnectionManager`.
 */
class BleScanner(
    private val context: Context,
    private val log: LogService,
) {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var _isScanning = false
    private val _scanResults = Channel<ScanResult>(Channel.CONFLATED)
    val scanResults: Flow<ScanResult> = _scanResults.receiveAsFlow()
    val isScanning: Boolean get() = _isScanning

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            _scanResults.trySend(result)
        }

        override fun onScanFailed(errorCode: Int) {
            log.ble("扫描失败", detail = "errorCode=$errorCode", level = com.tailg.plus.log.LogLevel.WARNING)
            _isScanning = false
        }
    }

    fun init(adapter: BluetoothAdapter?) {
        bluetoothAdapter = adapter
    }

    /**
     * Start BLE scan with the given filters. Returns true if the scan started.
     */
    fun startScan(
        filters: List<ScanFilter> = emptyList(),
        settings: ScanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build(),
    ): Boolean {
        if (_isScanning) return true
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return false
        try {
            scanner.startScan(filters, settings, scanCallback)
            _isScanning = true
            log.ble("扫描已启动", level = com.tailg.plus.log.LogLevel.DEBUG)
            return true
        } catch (e: SecurityException) {
            log.ble("扫描启动失败（权限不足）", detail = e.toString(), level = com.tailg.plus.log.LogLevel.WARNING)
            return false
        } catch (e: Exception) {
            log.ble("扫描启动失败", detail = e.toString(), level = com.tailg.plus.log.LogLevel.WARNING)
            return false
        }
    }

    fun stopScan() {
        if (!_isScanning) return
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            log.ble("停止扫描失败", detail = e.toString(), level = com.tailg.plus.log.LogLevel.DEBUG)
        }
        _isScanning = false
    }

    fun dispose() {
        stopScan()
    }
}