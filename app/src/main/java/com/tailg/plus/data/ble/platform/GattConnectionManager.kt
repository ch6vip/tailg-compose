package com.tailg.plus.data.ble.platform

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import com.tailg.plus.data.ble.BleTimings
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

/**
 * Core GATT connection lifecycle manager — extracted from [ConnectionManager].
 * Handles BluetoothGatt lifecycle, characteristic discovery, and MTU negotiation.
 */
class GattConnectionManager(
    private val context: Context,
    private val log: LogService,
) {
    private var _gatt: BluetoothGatt? = null
    private var _device: BluetoothDevice? = null
    private val writeDeferreds = ConcurrentHashMap<UUID, CompletableDeferred<Unit>>()
    private val _connectDeferred = AtomicDeferred<Unit>()
    private val _discoveryDeferred = AtomicDeferred<Unit>()
    private val _mtuDeferred = AtomicDeferred<Int>()

    val gatt: BluetoothGatt? get() = _gatt
    val device: BluetoothDevice? get() = _device

    // GATT callback — delegates to the provided handler
    val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            onConnectionStateChanged?.invoke(status, newState)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            onServicesDiscovered?.invoke(status)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            onCharacteristicRead?.invoke(characteristic, value, status)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            onCharacteristicWrite?.invoke(characteristic, status)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            onDescriptorWrite?.invoke(descriptor, status)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            onCharacteristicChanged?.invoke(characteristic, value)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            onMtuChanged?.invoke(mtu, status)
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            onReadRemoteRssi?.invoke(rssi, status)
        }
    }

    // Callback delegates
    var onConnectionStateChanged: ((status: Int, newState: Int) -> Unit)? = null
    var onServicesDiscovered: ((status: Int) -> Unit)? = null
    var onCharacteristicRead: ((characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) -> Unit)? = null
    var onCharacteristicWrite: ((characteristic: BluetoothGattCharacteristic, status: Int) -> Unit)? = null
    var onDescriptorWrite: ((descriptor: BluetoothGattDescriptor, status: Int) -> Unit)? = null
    var onCharacteristicChanged: ((characteristic: BluetoothGattCharacteristic?, value: ByteArray) -> Unit)? = null
    var onMtuChanged: ((mtu: Int, status: Int) -> Unit)? = null
    var onReadRemoteRssi: ((rssi: Int, status: Int) -> Unit)? = null

    /**
     * Connect to the given device with TRANSPORT_LE. Returns when the connection
     * is established or the timeout expires.
     */
    suspend fun connectGatt(device: BluetoothDevice, timeout: Duration): BluetoothGatt {
        val previous = _connectDeferred.getAndSet(null)
        if (previous != null && !previous.isCompleted) {
            previous.complete(Unit)
        }
        val deferred = CompletableDeferred<Unit>()
        _connectDeferred.getAndSet(deferred)
        _device = device
        val gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            ?: throw IllegalStateException("connectGatt returned null")
        _gatt = gatt
        try {
            withTimeout(timeout) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            closeGatt()
            throw e
        } catch (e: CancellationException) {
            closeGatt()
            throw e
        }
        return gatt
    }

    fun onConnected(status: Int, newState: Int) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            _connectDeferred.complete(Unit)
        }
    }

    /** Discover services and await completion. */
    suspend fun discoverServices(): Boolean {
        val gatt = _gatt ?: throw IllegalStateException("GATT is null")
        val deferred = CompletableDeferred<Unit>()
        _discoveryDeferred.getAndSet(deferred)
        if (!gatt.discoverServices()) {
            _discoveryDeferred.compareAndSet(deferred, null)
            return false
        }
        try {
            withTimeout(BleTimings.discoveryTimeout) { deferred.await() }
            return true
        } catch (e: TimeoutCancellationException) {
            log.ble("服务发现超时", level = LogLevel.WARNING)
            return false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.ble("服务发现失败", detail = e.toString(), level = LogLevel.WARNING)
            return false
        }
    }

    fun onServicesDiscoveredResult(status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            _discoveryDeferred.complete(Unit)
        } else {
            _discoveryDeferred.completeExceptionally(
                GattException(status, "Service discovery failed with status $status")
            )
        }
    }

    /** Request MTU and await the result. */
    suspend fun requestMtu(mtu: Int): Int? {
        val gatt = _gatt ?: return null
        val deferred = CompletableDeferred<Int>()
        _mtuDeferred.getAndSet(deferred)
        if (!gatt.requestMtu(mtu)) {
            _mtuDeferred.compareAndSet(deferred, null)
            return null
        }
        return try {
            withTimeout(BleTimings.mtuTimeout) { deferred.await() }
        } catch (e: Exception) {
            null
        }
    }

    fun onMtuChangedResult(mtu: Int, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            _mtuDeferred.complete(mtu)
        } else {
            _mtuDeferred.completeExceptionally(
                GattException(status, "MTU request failed with status $status")
            )
        }
    }

    /**
     * Write a characteristic value. Uses the modern API on API 33+.
     */
    suspend fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int,
    ) {
        val gatt = _gatt ?: throw IllegalStateException("GATT is null")
        val deferred = CompletableDeferred<Unit>()
        writeDeferreds[characteristic.uuid] = deferred
        val started = if (Build.VERSION.SDK_INT >= 33) {
            @Suppress("NewApi")
            gatt.writeCharacteristic(characteristic, value, writeType) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = value
            @Suppress("DEPRECATION")
            characteristic.writeType = writeType
            gatt.writeCharacteristic(characteristic)
        }
        if (!started) {
            writeDeferreds.remove(characteristic.uuid)
            throw IllegalStateException("writeCharacteristic failed: ${characteristic.uuid}")
        }
        try {
            deferred.await()
        } finally {
            writeDeferreds.remove(characteristic.uuid)
        }
    }

    fun onCharacteristicWriteResult(characteristic: BluetoothGattCharacteristic, status: Int) {
        val deferred = writeDeferreds.remove(characteristic.uuid)
        if (deferred != null && !deferred.isCompleted) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                deferred.complete(Unit)
            } else {
                deferred.completeExceptionally(
                    GattException(status, "Write failed: ${characteristic.uuid}")
                )
            }
        }
    }

    fun closeGatt() {
        try {
            _gatt?.close()
        } catch (e: Exception) {
            log.ble("关闭 GATT 失败", detail = e.toString(), level = LogLevel.DEBUG)
        }
        _gatt = null
        _device = null
        _connectDeferred.getAndSet(null)
        _discoveryDeferred.getAndSet(null)
        _mtuDeferred.getAndSet(null)
        writeDeferreds.clear()
    }

    fun dispose() {
        closeGatt()
    }
}