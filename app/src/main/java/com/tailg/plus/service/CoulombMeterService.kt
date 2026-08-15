package com.tailg.plus.service

import com.tailg.plus.data.ble.CommandCode
import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Port of `lib/services/coulomb_meter_service.dart` — official TLV battery
 * page "库仑计" (open SOC self-learning).
 *
 * BLE-only feature from the official `BatteryInfoTlvActivity`:
 * - Query: write FBB2 `D0018A00` after powering the vehicle
 * - Response: starts with `D0010A08`, status bit0 of byte at hex[10..12]
 * - On:  `D0018A020500`
 * - Off: `D0018A020600`
 * - Hidden for lithium `bmsTlvType == 208`
 *
 * Deviations:
 * - Dart singleton `instance` → plain constructor-injected class; the Dart
 *   global `connectionManager` fallback becomes the injected
 *   [connectionManager] property (per `CONVENTIONS.md`).
 * - Dart `StreamSubscription` + `Completer` + `Future.timeout` → a child
 *   collector coroutine started with `CoroutineStart.UNDISPATCHED` (so the
 *   FBB2 subscription is registered before the write) feeding a channel that
 *   is awaited with `withTimeoutOrNull`.
 * - The Dart `@visibleForTesting` top-level alias
 *   `parseCoulombSocVisibleForTest` is omitted: Kotlin tests live in the
 *   same package and call [CoulombMeterService.parseSocVisible] directly.
 */
class CoulombMeterService(
    private val connectionManager: ConnectionManager,
    private val logService: LogService = LogService(),
) {

    companion object {
        const val QUERY_FRAME = "D0018A00"
        const val TURN_ON_FRAME = "D0018A020500"
        const val TURN_OFF_FRAME = "D0018A020600"
        const val RESPONSE_PREFIX = "D0010A08"

        private val NON_HEX = Regex("[^0-9a-fA-F]")

        /** Lithium packs (official type "208") cannot use coulomb meter. */
        fun isSupported(modelType: Int?, bmsTlvType: String): Boolean {
            val tlv = bmsTlvType.trim()
            if (tlv == "208") return false
            // Official shows open-SOC on TLV pages; prefer when tlv is present.
            // Also allow known GPS combo / QGJ when BLE is available.
            if (tlv.isNotEmpty()) return true
            if (modelType == 8 || modelType == 283) return true
            if (modelType == 3 ||
                modelType == 10 ||
                modelType == 14 ||
                modelType == 401 ||
                modelType == 928 ||
                modelType == 1501 ||
                modelType == 1601 ||
                modelType == 1701
            ) {
                return true
            }
            return false
        }

        /**
         * Parse official `setSocVisible` response.
         *
         * Returns:
         * - `true` / `false` when switch state is known
         * - `null` when vehicle must power on first (show refresh button)
         */
        fun parseSocVisible(rawHex: String): Boolean? {
            val hex = rawHex.replace(NON_HEX, "").uppercase()
            if (hex.length < 12 || !hex.startsWith(RESPONSE_PREFIX)) return null
            val statusByteHex = hex.substring(10, 12)
            val status = statusByteHex.toIntOrNull(16) ?: return null
            // Official: binary bit0 of status byte == "1" means ON.
            return (status and 0x01) == 0x01
        }
    }

    /**
     * Dart `queryStatus({manager, timeout})`. Powers the vehicle first
     * (official flow), writes [QUERY_FRAME] and returns the parsed SOC
     * visibility, or null when the vehicle must power on first / on timeout.
     */
    suspend fun queryStatus(
        manager: ConnectionManager? = null,
        timeout: Duration = 4.seconds,
    ): Boolean? {
        val cm = manager ?: connectionManager
        if (!cm.isProtocolLoggedIn) {
            throw IllegalStateException("请先连接车辆蓝牙")
        }
        if (cm.fbb2Char == null) {
            throw IllegalStateException("当前连接不支持库仑计通道 (FBB2)")
        }

        // Official powers vehicle before reading SOC status.
        try {
            cm.sendCommand(CommandCode.powerOn)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logService.operation(
                "库仑计查询前上电失败",
                detail = e.toString(),
                level = LogLevel.WARNING,
            )
        }

        val response = awaitFbb2FirstFrame(cm, timeout) { cm.writeFbb2(QUERY_FRAME) }
        if (response == null) {
            logService.operation("库仑计查询超时", level = LogLevel.WARNING)
            return null
        }
        val on = parseSocVisible(response)
        logService.operation(
            "库仑计状态",
            detail = "raw=$response on=${on?.toString() ?: "unknown"}",
        )
        return on
    }

    /**
     * Dart `setEnabled(enabled, {manager, timeout})`. Powers the vehicle
     * first, writes the on/off frame and returns the parsed result; when the
     * firmware acked without a parseable status frame the target state is
     * returned (same fallback as the Dart original).
     */
    suspend fun setEnabled(
        enabled: Boolean,
        manager: ConnectionManager? = null,
        timeout: Duration = 4.seconds,
    ): Boolean? {
        val cm = manager ?: connectionManager
        if (!cm.isProtocolLoggedIn) {
            throw IllegalStateException("请先连接车辆蓝牙")
        }
        if (cm.fbb2Char == null) {
            throw IllegalStateException("当前连接不支持库仑计通道 (FBB2)")
        }

        try {
            cm.sendCommand(CommandCode.powerOn)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logService.operation(
                "库仑计设置前上电失败",
                detail = e.toString(),
                level = LogLevel.WARNING,
            )
        }

        val frame = if (enabled) TURN_ON_FRAME else TURN_OFF_FRAME
        val response = awaitFbb2FirstFrame(cm, timeout) { cm.writeFbb2(frame) }
        if (response == null) {
            // Some firmwares ack without a parseable status frame.
            logService.operation(
                "库仑计设置超时，采用目标状态",
                detail = "enabled=$enabled",
                level = LogLevel.WARNING,
            )
            return enabled
        }
        val on = parseSocVisible(response) ?: enabled
        logService.operation(
            "库仑计设置",
            detail = "enabled=$enabled raw=$response result=$on",
        )
        return on
    }

    /**
     * Subscribes to FBB2 notifications (before [write] runs), writes the
     * frame, then returns the first `D001...` payload or null on [timeout].
     */
    private suspend fun awaitFbb2FirstFrame(
        cm: ConnectionManager,
        timeout: Duration,
        write: suspend () -> Unit,
    ): String? = coroutineScope {
        val frames = Channel<String>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            cm.fbb2Flow.collect { hex ->
                val clean = hex.replace(NON_HEX, "").uppercase()
                if (clean.startsWith("D001")) {
                    frames.trySend(clean)
                }
            }
        }
        try {
            write()
            withTimeoutOrNull(timeout) { frames.receive() }
        } finally {
            collector.cancel()
        }
    }
}
