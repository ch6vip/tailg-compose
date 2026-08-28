package com.tailg.plus.domain.control

import androidx.compose.runtime.Immutable
import com.tailg.plus.data.cloud.OfficialCloudMessages
import com.tailg.plus.data.model.OfficialVehicle

/**
 * Port of `lib/services/control_channel_resolver.dart`.
 *
 * Preferred control channel preference (user/app policy).
 *
 * When [AUTOMATIC], transport is decided by [OfficialControlRoute] — the pure
 * decision table extracted from official ControlFragment / ControlTypeUtil.
 */
enum class OfficialControlChannel(val label: String, val description: String) {
  AUTOMATIC("自动", "完全按官方 modelType + isGps + BLE LOGIN 分流"),
  BLE("BLE", "只使用本地蓝牙直连"),
  OFFICIAL_CLOUD("官方云端", "强制使用官方账号远程控车"),
}

/**
 * Minimal view of the official cloud session that control routing reads.
 *
 * The parallel port of `lib/services/official_cloud_service.dart`
 * (`com.tailg.plus.data.cloud.OfficialCloudState`) implements this interface;
 * the domain layer depends on this narrow contract, not the full state class.
 */
interface ControlCloudState {
  val signedIn: Boolean

  /** Dart `selectedVehicle` semantics: first vehicle when no key, fallback first. */
  val selectedVehicle: OfficialVehicle?

  /**
   * Dart `OfficialCloudState.linkedLocalVehicleId(key)`:
   * `OfficialCloudVehicleLinks.normalize(localVehicleLinks)[officialVehicleKey.trim()]`
   * — trims both sides, drops empty entries, returns the linked local device id.
   */
  fun linkedLocalVehicleId(officialVehicleKey: String): String?
}

@Immutable
data class ControlChannelAvailability(
  val channel: OfficialControlChannel,
  val officialDecision: OfficialControlRouteDecision?,
  val canUseBle: Boolean,
  val canUseCloud: Boolean,
  val enabled: Boolean,
  val willUseBle: Boolean,
  val vehicleAllowsCloudFallback: Boolean,
  val effectiveChannelLabel: String,
  val bleUnavailableReason: String,
  val cloudUnavailableReason: String,
  val disabledReason: String,
) {
  fun copyWith(
    channel: OfficialControlChannel? = null,
    officialDecision: OfficialControlRouteDecision? = null,
    canUseBle: Boolean? = null,
    canUseCloud: Boolean? = null,
    enabled: Boolean? = null,
    willUseBle: Boolean? = null,
    vehicleAllowsCloudFallback: Boolean? = null,
    effectiveChannelLabel: String? = null,
    bleUnavailableReason: String? = null,
    cloudUnavailableReason: String? = null,
    disabledReason: String? = null,
  ): ControlChannelAvailability = ControlChannelAvailability(
    channel = channel ?: this.channel,
    officialDecision = officialDecision ?: this.officialDecision,
    canUseBle = canUseBle ?: this.canUseBle,
    canUseCloud = canUseCloud ?: this.canUseCloud,
    enabled = enabled ?: this.enabled,
    willUseBle = willUseBle ?: this.willUseBle,
    vehicleAllowsCloudFallback = vehicleAllowsCloudFallback ?: this.vehicleAllowsCloudFallback,
    effectiveChannelLabel = effectiveChannelLabel ?: this.effectiveChannelLabel,
    bleUnavailableReason = bleUnavailableReason ?: this.bleUnavailableReason,
    cloudUnavailableReason = cloudUnavailableReason ?: this.cloudUnavailableReason,
    disabledReason = disabledReason ?: this.disabledReason,
  )
}

/**
 * Combines the official vehicle route with user channel preference and
 * linked-local-vehicle guard into one availability snapshot for the UI.
 */
object ControlChannelResolver {
  fun resolve(
    cloudState: ControlCloudState,

    /// Official LoginStatus.LOGIN equivalent (use ConnectionManager.isProtocolLoggedIn).
    bleReady: Boolean = false,

    /// Optional detail when [bleReady] is false (e.g. connecting / not LOGIN).
    bleNotReadyReason: String? = null,
    defaultVehicleId: String? = null,
    channel: OfficialControlChannel = OfficialControlChannel.AUTOMATIC,
    busy: Boolean = false,
    networkReady: Boolean = true,
  ): ControlChannelAvailability {
    val selected = cloudState.selectedVehicle
    val cloudSessionReady =
      cloudState.signedIn && cloudState.selectedVehicle != null

    // Linked-local-vehicle guard (ours): even if BLE LOGIN, refuse if the
    // selected official car is hard-linked to another local device id.
    val bleLinkedOk = canUseLinkedBle(
      cloudState = cloudState,
      bleReady = bleReady,
      defaultVehicleId = defaultVehicleId,
    )
    val effectiveBleReady = bleReady && bleLinkedOk

    val officialDecision = OfficialControlRoute.resolve(
      bindingCar = selected != null,
      modelType = selected?.modelType,
      isGps = selected?.isGps,
      bleReady = effectiveBleReady,
      networkReady = networkReady,
      cloudSessionReady = cloudSessionReady,
    )

    val canUseBle = when (channel) {
      OfficialControlChannel.BLE ->
        officialDecision.usesBle && effectiveBleReady
      OfficialControlChannel.OFFICIAL_CLOUD -> false
      OfficialControlChannel.AUTOMATIC ->
        officialDecision.usesBle && effectiveBleReady
    }

    val vehicleAllowsCloudFallback = officialAllowsCloudFallback(
      selected = selected,
      decision = officialDecision,
    )

    val canUseCloud = when (channel) {
      OfficialControlChannel.OFFICIAL_CLOUD ->
        vehicleAllowsCloudFallback && cloudSessionReady && networkReady
      OfficialControlChannel.BLE -> false
      OfficialControlChannel.AUTOMATIC ->
        officialDecision.usesCloud && cloudSessionReady && networkReady
    }

    val bleUnavailableReason = if (canUseBle) {
      ""
    } else {
      bleUnavailableReason(
        cloudState = cloudState,
        bleReady = bleReady,
        bleNotReadyReason = bleNotReadyReason,
        defaultVehicleId = defaultVehicleId,
        officialReason = if (officialDecision.usesBle) "" else officialDecision.reason,
      )
    }

    val cloudUnavailableReason = if (canUseCloud) {
      ""
    } else {
      cloudUnavailableReason(
        cloudState = cloudState,
        channel = channel,
        networkReady = networkReady,
        officialReason = officialDecision.reason,
      )
    }

    val enabled = !busy &&
      when (channel) {
        OfficialControlChannel.BLE -> canUseBle
        OfficialControlChannel.OFFICIAL_CLOUD -> canUseCloud
        OfficialControlChannel.AUTOMATIC ->
          !officialDecision.isUnavailable && (canUseBle || canUseCloud)
      }

    val willUseBle = !busy &&
      when (channel) {
        OfficialControlChannel.BLE -> canUseBle
        OfficialControlChannel.OFFICIAL_CLOUD -> false
        OfficialControlChannel.AUTOMATIC ->
          officialDecision.usesBle && canUseBle
      }

    val otherwiseAvailable = canUseBle || canUseCloud

    return ControlChannelAvailability(
      channel = channel,
      officialDecision = officialDecision,
      canUseBle = canUseBle,
      canUseCloud = canUseCloud,
      enabled = enabled,
      willUseBle = willUseBle,
      vehicleAllowsCloudFallback = if (channel == OfficialControlChannel.BLE) {
        false
      } else {
        vehicleAllowsCloudFallback
      },
      effectiveChannelLabel = effectiveChannelLabel(
        enabled = enabled,
        willUseBle = willUseBle,
        canUseCloud = canUseCloud,
      ),
      bleUnavailableReason = bleUnavailableReason,
      cloudUnavailableReason = cloudUnavailableReason,
      disabledReason = if (busy && otherwiseAvailable) {
        "正在执行控车指令，请稍候"
      } else {
        disabledReason(
          channel = channel,
          bleUnavailableReason = bleUnavailableReason,
          cloudUnavailableReason = cloudUnavailableReason,
          officialDecision = officialDecision,
        )
      },
    )
  }

  private fun officialAllowsCloudFallback(
    selected: OfficialVehicle?,
    decision: OfficialControlRouteDecision,
  ): Boolean {
    if (selected == null) return false
    if (decision.usesCloud) return true
    // Probe: if BLE were not ready, would official choose cloud?
    val withoutBle = OfficialControlRoute.resolve(
      bindingCar = true,
      modelType = selected.modelType,
      isGps = selected.isGps,
      bleReady = false,
      networkReady = true,
      cloudSessionReady = true,
    )
    return withoutBle.usesCloud
  }

  private fun canUseLinkedBle(
    cloudState: ControlCloudState,
    bleReady: Boolean,
    defaultVehicleId: String?,
  ): Boolean {
    if (!bleReady) return false
    val selected = cloudState.selectedVehicle
    if (selected == null) return true
    val linkedId = cloudState.linkedLocalVehicleId(selected.key)
    if (linkedId == null || linkedId.isEmpty()) return true
    return defaultVehicleId == linkedId
  }

  private fun effectiveChannelLabel(
    enabled: Boolean,
    willUseBle: Boolean,
    canUseCloud: Boolean,
  ): String {
    if (!enabled) return "不可用"
    if (willUseBle) return "BLE"
    if (canUseCloud) return "官方云端"
    return "不可用"
  }

  private fun bleUnavailableReason(
    cloudState: ControlCloudState,
    bleReady: Boolean,
    bleNotReadyReason: String?,
    defaultVehicleId: String?,
    officialReason: String,
  ): String {
    if (!bleReady) {
      // Prefer explicit non-LOGIN detail over generic official "蓝牙未连接".
      if (bleNotReadyReason != null && bleNotReadyReason.isNotEmpty()) {
        return bleNotReadyReason
      }
      return if (officialReason.isNotEmpty()) officialReason else "蓝牙未连接"
    }
    val selected = cloudState.selectedVehicle
    if (selected == null) return ""
    val linkedId = cloudState.linkedLocalVehicleId(selected.key)
    if (linkedId == null || linkedId.isEmpty()) return ""
    if (defaultVehicleId == null || defaultVehicleId.isEmpty()) {
      return "没有默认本地车辆"
    }
    return "默认本地车辆与官方车辆关联不一致"
  }

  private fun cloudUnavailableReason(
    cloudState: ControlCloudState,
    channel: OfficialControlChannel,
    networkReady: Boolean,
    officialReason: String,
  ): String {
    if (!networkReady) return "手机网络未连接"
    if (!cloudState.signedIn) return OfficialCloudMessages.SIGN_IN_REQUIRED
    if (cloudState.selectedVehicle == null) return "官方账号未选择车辆"
    if (channel == OfficialControlChannel.AUTOMATIC && officialReason.isNotEmpty()) {
      return officialReason
    }
    return ""
  }

  private fun disabledReason(
    channel: OfficialControlChannel,
    bleUnavailableReason: String,
    cloudUnavailableReason: String,
    officialDecision: OfficialControlRouteDecision,
  ): String {
    return when (channel) {
      OfficialControlChannel.BLE ->
        if (bleUnavailableReason.isEmpty()) {
          "当前官方车辆未关联这台本地 BLE 车辆"
        } else {
          bleUnavailableReason
        }
      OfficialControlChannel.OFFICIAL_CLOUD ->
        if (cloudUnavailableReason.isEmpty()) "官方云端不可用" else cloudUnavailableReason
      OfficialControlChannel.AUTOMATIC -> {
        // Keep non-BLE official reasons (未绑定/未登录/无网) first.
        // For the generic "蓝牙未连接" branch, prefer the more specific
        // non-LOGIN detail (连接中 / 未完成协议登录) when available.
        if (officialDecision.isUnavailable &&
          officialDecision.reason.isNotEmpty() &&
          officialDecision.reason != "蓝牙未连接"
        ) {
          return officialDecision.reason
        }
        if (bleUnavailableReason.isNotEmpty()) {
          return bleUnavailableReason
        }
        if (officialDecision.isUnavailable && officialDecision.reason.isNotEmpty()) {
          return officialDecision.reason
        }
        val reasons = buildList {
          if (bleUnavailableReason.isNotEmpty()) add("BLE：$bleUnavailableReason")
          if (cloudUnavailableReason.isNotEmpty()) add("云端：$cloudUnavailableReason")
        }
        if (reasons.isEmpty()) "请连接蓝牙或登录官方账号后再控车" else reasons.joinToString("；")
      }
    }
  }
}