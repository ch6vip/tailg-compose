/**
 * Port of `lib/services/diagnostic_export_service.dart` (tailg-ble-app) →
 * package `com.tailg.plus.service`.
 *
 * Builds the plain-text diagnostic report (header, local vehicle, official
 * cloud, MQTT, logs). All values are derived from injected services, never
 * from the device, so the report logic is fully unit-testable.
 *
 * Mapping notes vs the Dart original:
 * - `officialCloudService.state` → `currentState` (the Kotlin port exposes the
 *   snapshot as `OfficialCloudService.currentState`).
 * - `defaultTargetPlatform.name` (Flutter) → literal `android` (this target is
 *   Android-only); `kReleaseMode` → `BuildConfig.DEBUG`.
 * - `DateTime.toIso8601String()` → `ISO_LOCAL_DATE_TIME` formatting (no
 *   `.000` millis suffix); `Instant` values keep `toString()` (UTC `Z`).
 * - Dart enum `.name` is lowercase (`tlink`, `disconnected`, `mqtt`) while
 *   Kotlin enum `.name` is uppercase, so enum-name lines are lower-cased to
 *   keep the report byte-identical to the Dart one.
 * - [ControlChannelResolver.resolve] consumes the narrow `ControlCloudState`
 *   contract; [OfficialCloudState] is adapted via [asControlCloudState] (the
 *   Dart original passed the full `OfficialCloudState` directly).
 */
package com.tailg.plus.service

import com.tailg.plus.BuildConfig
import com.tailg.plus.data.ble.platform.OfficialBleConnectionContext
import com.tailg.plus.data.ble.platform.OfficialBleStack
import com.tailg.plus.data.cloud.OfficialCloudRedactor
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.data.cloud.OfficialCloudState
import com.tailg.plus.data.model.OfficialBatteryInfo
import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.data.model.parsePersistedInt
import com.tailg.plus.data.model.parsePersistedMap
import com.tailg.plus.data.model.parsePersistedString
import com.tailg.plus.data.mqtt.OfficialMqttConfig
import com.tailg.plus.data.mqtt.OfficialMqttService
import com.tailg.plus.data.store.VehicleStore
import com.tailg.plus.domain.control.ControlChannelResolver
import com.tailg.plus.domain.control.ControlCloudState
import com.tailg.plus.log.LogEntry
import com.tailg.plus.log.LogService
import com.tailg.plus.util.SensitiveValueMasker
import com.tailg.plus.util.formatLogClockTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Port of `lib/services/diagnostic_export_service.dart`. */
class DiagnosticExportService(
  val logService: LogService,
  val vehicleStore: VehicleStore,
  val officialCloudService: OfficialCloudService,
  officialMqttService: OfficialMqttService? = null,
  clock: (() -> LocalDateTime)? = null,
) {
  /** Dart `officialMqttService ?? OfficialMqttService()`. */
  val officialMqttService: OfficialMqttService = officialMqttService ?: OfficialMqttService()

  /** Dart `_clock` — injectable report time for deterministic tests. */
  private val clock: (() -> LocalDateTime)? = clock

  /** Dart `buildReport(entries)`: join all sections with blank separators. */
  fun buildReport(entries: List<LogEntry>): String {
    return listOf(
      buildHeader(),
      "",
      buildVehicleSection(),
      "",
      buildOfficialCloudSection(),
      "",
      buildMqttSection(),
      "",
      buildLogSectionHeading(entries.size),
      *entries.map(::formatEntry).toTypedArray(),
    ).joinToString("\n")
  }

  private fun buildMqttSection(): String {
    val mqtt = officialMqttService
    val vehicle = officialCloudService.currentState.selectedVehicle
    val broker = if (vehicle == null) "none" else OfficialMqttConfig.brokerUriFor(vehicle)
    val configuredTransport = if (vehicle == null) {
      "none"
    } else {
      runCatching { OfficialMqttConfig.parseBrokerUri(broker).diagnosticLabel }
        .getOrElse { "invalid" }
    }
    val mqUser = if (vehicle == null) {
      "none"
    } else if (vehicle.mqUsername.trim().isEmpty()) {
      "missing"
    } else {
      SensitiveValueMasker.compact(vehicle.mqUsername)
    }
    val mqPass = if (vehicle == null) {
      "none"
    } else if (vehicle.mqPassword.trim().isEmpty()) {
      "missing"
    } else {
      "present"
    }
    return listOf(
      "## Official MQTT",
      "Link state: ${mqtt.linkState.value.name.lowercase()}",
      "Link label: ${mqtt.linkStateLabel}",
      "Connected: ${mqtt.isConnected}",
      "Preconnect in flight: ${mqtt.preconnectInFlight}",
      "Broker: $broker",
      "Configured transport: $configuredTransport",
      "Connected transport: ${mqtt.connectedTransportSecurity?.diagnosticLabel ?: "none"}",
      "Vehicle mqUsername: $mqUser",
      "Vehicle mqPassword: $mqPass",
      "Last user error: ${mqtt.lastPreconnectError ?: "none"}",
      "Last raw error: ${mqtt.lastPreconnectRawError ?: "none"}",
      "Last send path: ${mqtt.lastSendPath?.name?.lowercase() ?: "none"}",
    ).joinToString("\n")
  }

  private fun buildLogSectionHeading(entryCount: Int): String {
    val evictedCount = logService.evictedCount
    val evictedSuffix = if (evictedCount > 0) " [$evictedCount older entries evicted]" else ""
    return "## Logs ($entryCount)$evictedSuffix"
  }

  private fun buildOfficialCloudSection(): String {
    val state = officialCloudService.currentState
    val vehicle = state.selectedVehicle
    val availability = ControlChannelResolver.resolve(cloudState = state.asControlCloudState())
    val lines = mutableListOf(
      "## Official Cloud",
      "Initialized: ${state.initialized}",
      "Signed in: ${state.signedIn}",
      "Phone: ${if (state.phone.isEmpty()) "none" else SensitiveValueMasker.phone(state.phone, shortValue = "present")}",
      "Token: ${if (state.token.isEmpty()) "none" else "present"}",
      "Vehicles: ${state.vehicles.size}",
      "Control channel: 官方云端",
      "Effective control channel: ${availability.effectiveChannelLabel}",
      "Cloud control available: ${availability.canUseCloud}",
      "Cloud unavailable reason: ${if (availability.cloudUnavailableReason.isEmpty()) "none" else availability.cloudUnavailableReason}",
      "Selected vehicle: ${vehicle?.displayName ?: "none"}",
    )

    lines.addAll(buildSelectedVehicleLines(state))
    lines.addAll(buildOfficialBatteryLines(state.batteryInfo))
    lines.addAll(buildOfficialBmsLines(state))
    if (vehicle != null) {
      lines.add(
        "Battery spec label: ${if (vehicle.batterySpecLabel.isEmpty()) "none" else vehicle.batterySpecLabel}",
      )
      lines.add(
        "Battery bind date: ${if (vehicle.batteryBindDate.isEmpty()) "none" else vehicle.batteryBindDate}",
      )
      lines.add(
        "Battery type id: ${if (vehicle.batteryTypeId.isEmpty()) "none" else vehicle.batteryTypeId}",
      )
      lines.add(
        "BMS TLV type: ${if (vehicle.bmsTlvType.isEmpty()) "none" else vehicle.bmsTlvType}",
      )
    }

    if (state.error != null) {
      lines.add("Error: ${OfficialCloudRedactor.text(state.error!!)}")
    }
    val lastRequest = officialCloudService.lastRequest
    if (lastRequest != null) {
      lines.add("Last request: ${lastRequest.method} ${lastRequest.path}")
      lines.add(
        "Last request status: ${lastRequest.statusCode?.toString() ?: "none"}",
      )
      lines.add("Last request code: ${lastRequest.code ?: "none"}")
      lines.add(
        "Last request elapsed: ${lastRequest.elapsed.toMillis()}ms",
      )
      lines.add("Last request success: ${lastRequest.success}")
      lines.add("Last request message: ${lastRequest.message ?: "none"}")
      lines.add("Last request time: ${lastRequest.at.toString()}")
    }
    return lines.joinToString("\n")
  }

  private fun buildSelectedVehicleLines(state: OfficialCloudState): List<String> {
    val vehicle = state.selectedVehicle
    if (vehicle == null) return emptyList()

    val linkedId = state.linkedLocalVehicleId(vehicle.key)
    val rawMac = parsePersistedString(vehicle.raw["mac"])
    val bleContext = OfficialBleConnectionContext.fromVehicle(
      vehicle,
      userId = state.userId,
    )
    val passwordInfo = parsePersistedMap(vehicle.raw["passwordInfo"])
    val passwordMap = parsePersistedMap(vehicle.raw["password"])
    val mainFromInfo = passwordInfo?.get("main")?.let { parsePersistedInt(it) }
    val mainFromPassword = passwordMap?.get("main")?.let { parsePersistedInt(it) }
    val childrenSource = passwordInfo?.get("children")
      ?: passwordMap?.get("children")
      ?: vehicle.raw["childrenPassword"]
      ?: vehicle.raw["children"]
    val childrenCount = if (childrenSource is Iterable<*>) childrenSource.count() else 0
    val hasPasswordInfoKey = vehicle.raw.containsKey("passwordInfo")
    val hasPasswordKey = vehicle.raw.containsKey("password")
    val hasMacKey = vehicle.raw.containsKey("mac")
    val hasBtmacKey = vehicle.raw.containsKey("btmac")
    val bleCredentialsReady = when (bleContext.stack) {
      OfficialBleStack.TLINK -> bleContext.hasTLinkCredentials
      OfficialBleStack.QGJ -> bleContext.hasQgjCredentials
      OfficialBleStack.KKS -> bleContext.targetMacCompact.isNotEmpty()
      OfficialBleStack.UNSUPPORTED -> false
    }

    return listOf(
      "Selected key: ${SensitiveValueMasker.compact(vehicle.key, emptyValue = "none", trim = false)}",
      "Linked local vehicle: ${if (linkedId == null) "none" else SensitiveValueMasker.compact(linkedId, emptyValue = "none", trim = false)}",
      "Online: ${vehicle.online}",
      "Defence: ${vehicle.defenceLabel}",
      "ACC: ${vehicle.powerLabel}",
      "Official vehicle battery: ${vehicle.electricQuantity?.toString() ?: "--"}%",
      "Official vehicle voltage: ${vehicle.voltage?.toString() ?: "--"}V",
      "ModelType: ${vehicle.modelType?.toString() ?: "none"}",
      "Command IMEI: ${SensitiveValueMasker.compact(vehicle.commandImei, emptyValue = "none", trim = false)}",
      "IMEI: ${SensitiveValueMasker.compact(vehicle.imei, emptyValue = "none", trim = false)}",
      "GPS IMEI: ${SensitiveValueMasker.compact(vehicle.imeiGps, emptyValue = "none", trim = false)}",
      "BT name: ${if (vehicle.btname.isEmpty()) "none" else vehicle.btname}",
      "BT MAC: ${SensitiveValueMasker.compact(vehicle.btmac, emptyValue = "none", trim = false)}",
      // Official ControlFragment QGJ uses CarControlInfoBean.mac as identity.
      "Raw mac field: ${if (rawMac.isEmpty()) (if (hasMacKey) "empty" else "missing") else SensitiveValueMasker.compact(rawMac, emptyValue = "none", trim = false)}",
      "Raw btmac field: ${if (vehicle.btmac.isEmpty()) (if (hasBtmacKey) "empty" else "missing") else SensitiveValueMasker.compact(vehicle.btmac, emptyValue = "none", trim = false)}",
      "BLE identity MAC: ${SensitiveValueMasker.compact(vehicle.bleIdentityMac, emptyValue = "none", trim = false)}",
      "BLE stack: ${bleContext.stack.name.lowercase()}",
      "BLE target MAC compact: ${SensitiveValueMasker.compact(bleContext.targetMacCompact, emptyValue = "none", trim = false)}",
      "passwordInfo key: ${if (hasPasswordInfoKey) "present" else "missing"}",
      "password key: ${if (hasPasswordKey) "present" else "missing"}",
      "passwordInfo.main: ${if (mainFromInfo == null) "missing" else "present"}",
      "password.main: ${if (mainFromPassword == null) "missing" else "present"}",
      "mainBlePassword: ${if (vehicle.mainBlePassword == null) "missing" else "present"}",
      "childBlePasswords: $childrenCount",
      "shareCarFlag: ${vehicle.shareCarFlag}",
      "BLE uid present: ${bleContext.userId.isNotEmpty()}",
      "BLE credentials ready: $bleCredentialsReady",
      "Location: ${if (vehicle.latitude.isEmpty() || vehicle.longitude.isEmpty()) "none" else "present (hidden)"}",
    )
  }

  private fun buildOfficialBatteryLines(batteryInfo: OfficialBatteryInfo?): List<String> {
    if (batteryInfo == null) return listOf("Official battery detail: none")

    fun metric(value: String, unit: String = ""): String {
      val text = value.trim()
      if (text.isEmpty()) return "missing"
      if (unit.isEmpty()) return text
      return if (text.endsWith(unit)) text else "$text$unit"
    }

    return listOf(
      "Official battery detail: ${if (batteryInfo.dumpEnergyPercentLabel.isEmpty()) "none" else batteryInfo.dumpEnergyPercentLabel}",
      "Official battery detail voltage: ${metric(batteryInfo.voltage, unit = "V")}",
      "Official battery detail temperature: ${metric(batteryInfo.temperature, unit = "C")}",
      "Official battery consumePowerPercent: ${metric(batteryInfo.consumePowerPercent, unit = "%")}",
      "Official battery loopCount: ${metric(batteryInfo.loopCount)}",
      "Official battery capacitance: ${metric(batteryInfo.capacitance)}",
      "Official battery score: ${metric(batteryInfo.batteryScore)}",
      "Official battery raw keys: ${batteryInfo.raw.keys.take(20).joinToString(",")}",
    )
  }

  private fun buildOfficialBmsLines(state: OfficialCloudState): List<String> {
    val bms = state.bmsInfo
    if (bms == null) {
      return listOf(
        "Official BMS detail: none",
        "Official BMS loading: ${state.bmsInfoLoading}",
        "Official BMS error: ${state.bmsInfoError ?: "none"}",
      )
    }
    val detail = bms.primaryDetail
    return listOf(
      "Official BMS detail: present",
      "Official BMS soc: ${if (bms.soc.isEmpty()) "missing" else bms.soc}",
      "Official BMS details count: ${bms.details.size}",
      "Official BMS primary temp: ${detail?.batteryTemperature?.takeIf { it.isNotEmpty() } ?: "missing"}",
      "Official BMS primary cycles: ${detail?.batteryCyclesNum?.takeIf { it.isNotEmpty() } ?: "missing"}",
      "Official BMS loading: ${state.bmsInfoLoading}",
      "Official BMS error: ${state.bmsInfoError ?: "none"}",
    )
  }

  private fun buildHeader(): String {
    return listOf(
      "# Tailg Diagnostic Report",
      "Generated: ${isoLocalTime(now())}",
      "Platform: android",
      "Mode: ${if (BuildConfig.DEBUG) "debug/profile" else "release"}",
    ).joinToString("\n")
  }

  /** Dart `_now()`: injected clock or the system wall clock. */
  private fun now(): LocalDateTime = (clock ?: { LocalDateTime.now() })()

  /**
   * `DateTime.toIso8601String()` equivalent for local wall-clock values.
   * `ISO_LOCAL_DATE_TIME` omits the Dart `.000` millis suffix.
   */
  private fun isoLocalTime(time: LocalDateTime): String =
    time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

  private fun buildVehicleSection(): String {
    val vehicle = vehicleStore.defaultVehicle
    if (vehicle == null) return "## Vehicle\nDefault: none"

    val location = vehicle.lastLocation
    val lines = mutableListOf(
      "## Vehicle",
      "Default ID: ${SensitiveValueMasker.compact(vehicle.id, emptyValue = "none", trim = false)}",
      "Name: ${vehicle.displayName}",
      "Protocol: ${vehicle.protocol.label}",
      "Last connected: ${vehicle.lastConnectedAt?.toString() ?: "none"}",
    )
    if (location != null) {
      lines.add("Last location: present (hidden)")
    }
    return lines.joinToString("\n")
  }

  private fun formatEntry(entry: LogEntry): String {
    val t = formatLogClockTime(entry.time)
    val level = entry.level.name.uppercase()
    val detail = entry.detail
    return "$t [OP] [$level] ${OfficialCloudRedactor.text(entry.message)}" +
      (if (detail == null) "" else " | ${OfficialCloudRedactor.text(detail)}")
  }
}

/**
 * Adapt the concrete cloud snapshot to the narrow [ControlCloudState] contract
 * consumed by [ControlChannelResolver]. The Dart original passed the full
 * `OfficialCloudState` directly; the Kotlin domain/data layers stay decoupled,
 * so the adapter only exposes the three members the resolver reads.
 */
private fun OfficialCloudState.asControlCloudState(): ControlCloudState = object : ControlCloudState {
  override val signedIn: Boolean get() = this@asControlCloudState.signedIn
  override val selectedVehicle: OfficialVehicle? get() = this@asControlCloudState.selectedVehicle
  override fun linkedLocalVehicleId(officialVehicleKey: String): String? =
    this@asControlCloudState.linkedLocalVehicleId(officialVehicleKey)
}
