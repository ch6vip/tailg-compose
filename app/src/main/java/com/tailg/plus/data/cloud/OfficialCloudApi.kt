package com.tailg.plus.data.cloud

import com.tailg.plus.util.SensitiveTextRedactor
import com.tailg.plus.util.SensitiveValueMasker
import okhttp3.HttpUrl

/**
 * Port of `lib/services/official_cloud_api_client.dart` foundation types
 * (exceptions, redactor, retry policy, API config). The Retrofit endpoint
 * contract lives in [OfficialCloudApiService] and the OkHttp transport in
 * [OfficialCloudApiClient].
 */
class OfficialCloudApiException(
    val message: String,
    val statusCode: Int? = null,
) : Exception(message) {
    override fun toString(): String = message
}

/** Redacts sensitive values from request paths, texts and error messages. */
object OfficialCloudRedactor {
    /**
     * `phone|token|authorization|imei|carId|uid|userId|password|frame|btmac|mac=<value>`
     * in a query string. The Dart original used a variable-length lookbehind
     * (unsupported by the JVM regex engine); the port captures the `key=` prefix
     * in group 1 and masks only group 2 — same observable behavior.
     */
    private val sensitiveQueryPattern = Regex(
        """(\b(?:phone|token|authorization|imei|carId|uid|userId|password|frame|btmac|mac)=)([^&\s]+)""",
        RegexOption.IGNORE_CASE,
    )

    fun requestPath(path: String): String =
        sensitiveQueryPattern.replace(path) { match ->
            match.groupValues[1] + mask(match.groupValues[2])
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
        /** Retry transport failures only (timeouts / socket errors). */
        val TRANSPORT_ONLY = OfficialCloudRetryPolicy()

        /** Also retry HTTP 5xx responses and decode failures. */
        val READ_REQUEST = OfficialCloudRetryPolicy(retryServerErrors = true)
    }
}

/** Port of `OfficialCloudApiConfig` (official defaults). */
class OfficialCloudApiConfig(
    val apiBase: String = DEFAULT_API_BASE,
    val loginMacCode: String = DEFAULT_LOGIN_MAC_CODE,
    val phoneMode: String = DEFAULT_PHONE_MODE,
    // Empty by default: callers that genuinely need IP forwarding must set it
    // explicitly. The previous 'localhost' default leaked into production
    // requests and could confuse upstream routing/gateway logic.
    val forwardServiceIp: String = DEFAULT_FORWARD_SERVICE_IP,
    val language: String = DEFAULT_LANGUAGE,
    val zoneId: String = DEFAULT_ZONE_ID,
    val apiVersion: String = DEFAULT_API_VERSION,
    val userAgent: String = DEFAULT_USER_AGENT,
    val connectTimeout: kotlin.time.Duration = DEFAULT_CONNECT_TIMEOUT,
    val responseTimeout: kotlin.time.Duration = DEFAULT_RESPONSE_TIMEOUT,
    val retryBaseDelay: kotlin.time.Duration = DEFAULT_RETRY_BASE_DELAY,
) {
    /** Resolve a relative API path against [apiBase] (Dart `Uri.resolve`). */
    fun resolve(path: String): HttpUrl {
        val base = apiBase.trimEnd('/') + "/"
        return base.toHttpUrl().resolve(path)
            ?: throw IllegalArgumentException("无法解析官方接口路径: $path")
    }

    /** Exponential-ish backoff: base * (attempt + 1). */
    fun retryDelayForAttempt(attempt: Int): kotlin.time.Duration {
        val normalizedAttempt = if (attempt < 0) 0 else attempt
        return retryBaseDelay * (normalizedAttempt + 1)
    }

    /** Default request headers (Dart `defaultHeaders`). */
    val defaultHeaders: Map<String, String>
        get() = buildMap {
            put("Content-Type", "application/json")
            // Only emit Forward-Service-Ip when actually configured. The
            // duplicate 'Forward-ServiceIp' (missing hyphen) typo is not
            // reproduced.
            if (forwardServiceIp.isNotEmpty()) put("Forward-Service-Ip", forwardServiceIp)
            put("language", language)
            put("Accept-Language", language)
            put("Zone-id", zoneId)
            put("Api-Version", apiVersion)
            put("User-Agent", userAgent)
        }

    companion object {
        const val DEFAULT_API_BASE = "https://www.tailgdd.com/v1/api/"
        const val DEFAULT_LOGIN_MAC_CODE = "000000000000"
        const val DEFAULT_PHONE_MODE = "SM-G998B"
        const val DEFAULT_FORWARD_SERVICE_IP = ""
        const val DEFAULT_LANGUAGE = "zh_CN"
        const val DEFAULT_ZONE_ID = "UTC+08:00"
        const val DEFAULT_API_VERSION = "3.0.0"
        const val DEFAULT_USER_AGENT = "okhttp/4.9.3"
        val DEFAULT_CONNECT_TIMEOUT: kotlin.time.Duration = kotlin.time.Duration.parse("15s")
        val DEFAULT_RESPONSE_TIMEOUT: kotlin.time.Duration = kotlin.time.Duration.parse("15s")
        val DEFAULT_RETRY_BASE_DELAY: kotlin.time.Duration = kotlin.time.Duration.parse("500ms")
    }
}
