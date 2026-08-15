package com.tailg.plus.domain.control

import com.tailg.plus.data.cloud.OfficialCloudApiException

/**
 * Port of `lib/services/official_remote_error_messages.dart`.
 *
 * Human-facing remote control errors (MQTT ensure/publish + HTTP cmd).
 *
 * P0-B3: token expiry / offline / broker failures must not be swallowed into
 * opaque strings that leave the user without a next step.
 *
 * Placed in `domain.control` (the cloud package was still being ported in
 * parallel when this file was written). [OfficialCloudApiException] is the
 * contract ported from `lib/services/official_cloud_service.dart` into
 * `com.tailg.plus.data.cloud` — see the port report for the exact shape
 * (`message: String`, `statusCode: Int?`).
 */
object OfficialRemoteErrorMessages {
  const val SESSION_EXPIRED = "登录已失效，请重新登录官方账号"
  const val NETWORK_UNAVAILABLE = "手机网络异常，请检查网络后重试"
  const val BROKER_UNREACHABLE = "远程控车服务连接失败，请稍后重试或检查网络"

  fun describe(error: Throwable): String =
    if (error is OfficialCloudApiException) {
      fromApiException(error)
    } else {
      fromText(error.toString()) ?: error.toString()
    }

  private fun fromApiException(error: OfficialCloudApiException): String {
    val status = error.statusCode
    if (status == 401 || status == 403) {
      return SESSION_EXPIRED
    }
    val mapped = fromText(error.message)
    if (mapped != null) return mapped
    val message = error.message.trim()
    return if (message.isEmpty()) BROKER_UNREACHABLE else message
  }

  private fun fromText(raw: String): String? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    val lower = text.lowercase()

    if (text.contains(OfficialCloudMessages.SIGN_IN_REQUIRED) ||
      text.contains(OfficialCloudMessages.SIGN_IN_AND_SELECT_VEHICLE_REQUIRED)
    ) {
      return text
    }
    if (lower.contains("token") ||
      lower.contains("unauthorized") ||
      lower.contains("401") ||
      text.contains("登录失效") ||
      text.contains("未登录") ||
      text.contains("请重新登录")
    ) {
      return SESSION_EXPIRED
    }
    if (lower.contains("socketexception") ||
      lower.contains("failed host lookup") ||
      lower.contains("network is unreachable") ||
      lower.contains("connection refused") ||
      lower.contains("connection reset") ||
      lower.contains("timed out") ||
      lower.contains("timeout") ||
      text.contains("网络失败") ||
      text.contains("手机网络")
    ) {
      return NETWORK_UNAVAILABLE
    }
    if (text.contains("MQTT") &&
      (text.contains("连接失败") ||
        text.contains("未连接") ||
        lower.contains("broker"))
    ) {
      return if (text.contains("网络")) NETWORK_UNAVAILABLE else BROKER_UNREACHABLE
    }
    return null
  }
}
