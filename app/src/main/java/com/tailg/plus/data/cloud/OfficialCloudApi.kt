package com.tailg.plus.data.cloud

import com.tailg.plus.util.SensitiveTextRedactor
import com.tailg.plus.util.SensitiveValueMasker

/**
 * Port of `lib/services/official_cloud_api_client.dart` foundation types
 * (exceptions, redactor, retry policy, API config). The full Retrofit client
 * lands with the cloud module port.
 */
class OfficialCloudApiException(
    val message: String,
    val statusCode: Int? = null,
) : Exception(message) {
    override fun toString(): String = message
}

/** Redacts sensitive values from request paths, texts and error messages. */
object OfficialCloudRedactor {
    private val sensitiveQueryPattern = Regex(
        "(?<=\\b(?:phone|token|authorization|imei|carId|uid|userId|password|frame|btmac|mac)=)[^&\\s]+",
        RegexOption.IGNORE_CASE,
    )

    fun requestPath(path: String): String =
        sensitiveQueryPattern.replace(path) { match ->
            mask(match.value)
        }

    fun text(value: String): String = SensitiveTextRedactor.redact(value)

    fun errorMessage(error: Any): String {
        val message = if (error is OfficialCloudApiException) error.message else error.toString()
        return text(message)
    }

    private fun mask(value: String): String = SensitiveValueMasker.compact(value)
}

/** Port of `OfficialCloudRetryPolicy` (maxRetries = 2 default). */
class OfficialCloudRetryPolicy(
    val maxRetries: Int = 2,
    val retryServerErrors: Boolean = false,
) {
    init {
        require(maxRetries >= 0)
    }

    fun canRetryAttempt(attempt: Int): Boolean = attempt < maxRetries

    fun shouldRetryStatusCode(statusCode: Int): Boolean =
        retryServerErrors && statusCode in 500..599

    companion object {
        val TRANSPORT_ONLY = OfficialCloudRetryPolicy()
        val READ_REQUEST = OfficialCloudRetryPolicy(retryServerErrors = true)
    }
}

/** Port of `OfficialCloudApiConfig` (official defaults). */
object OfficialCloudApiConfig {
    const val DEFAULT_API_BASE = "https://www.tailgdd.com/v1/api/"
    const val DEFAULT_LOGIN_MAC_CODE = "000000000000"
    const val DEFAULT_PHONE_MODE = "SM-G998B"
    const val DEFAULT_FORWARD_SERVICE_IP = ""
    const val DEFAULT_LANGUAGE = "zh_CN"
    const val DEFAULT_ZONE_ID = "UTC+08:00"
    const val DEFAULT_API_VERSION = "3.0.0"
    const val DEFAULT_USER_AGENT = "okhttp/4.9.3"
    val DEFAULT_CONNECT_TIMEOUT: kotlin.time.Duration = kotlin.time.Duration.parse("15s")
}
