package com.tailg.plus.data.cloud

import com.tailg.plus.data.model.parsePersistedMap
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Port of `OfficialCloudApiClient` from `lib/services/official_cloud_api_client.dart`.
 *
 * Generic OkHttp transport that reproduces the Dart semantics exactly:
 * - shared OkHttpClient (keep-alive / connection pool, connect timeout)
 * - per-request retry loop ([OfficialCloudRetryPolicy]) with backoff
 * - default headers from [OfficialCloudApiConfig], `Authorization` when a token
 *   is supplied
 * - JSON body encode / response decode via Moshi; lenient envelope maps
 * - non-2xx → redacted `msg` or `官方接口返回 <status>`
 * - timeout → `请求超时，请检查网络`; socket failure → `网络不可用，请检查连接`
 * - last-request diagnostic summary + redacted operation logs
 *
 * Deviations from Dart (documented): the >32KiB body "background isolate" split
 * is unnecessary because every call already runs on [Dispatchers.IO]; the
 * client uses the same [LogService] instance the service holds.
 */
class OfficialCloudApiClient(
    val config: OfficialCloudApiConfig = OfficialCloudApiConfig(),
    val log: LogService = LogService(),
    private val okHttpClient: OkHttpClient? = null,
    clock: () -> LocalDateTime = { LocalDateTime.now() },
) {
    private val httpClient: OkHttpClient = okHttpClient ?: OkHttpClient.Builder()
        .connectTimeout(config.connectTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .build()

    private val moshi = com.squareup.moshi.Moshi.Builder().build()

    /** Typed Retrofit contract for the same endpoints (see [OfficialCloudApiService]). */
    val api: OfficialCloudApiService = Retrofit.Builder()
        .baseUrl(config.apiBase)
        .client(httpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(OfficialCloudApiService::class.java)

    private var clock: () -> LocalDateTime = clock

    private var lastRequestValue: OfficialCloudRequestSummary? = null

    val lastRequest: OfficialCloudRequestSummary? get() = lastRequestValue

    fun dispose() {
        httpClient.dispatcher.cancelAll()
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    /**
     * Generic request with Dart retry semantics.
     *
     * @param path relative API path (optionally with a query string)
     * @param method HTTP method, e.g. "POST"
     * @param token authorization header value when signed in
     * @param body JSON request body (Moshi-encoded)
     * @param retryPolicy per-call retry policy
     */
    suspend fun request(
        path: String,
        method: String,
        token: String? = null,
        body: Map<String, Any?>? = null,
        retryPolicy: OfficialCloudRetryPolicy = OfficialCloudRetryPolicy.TRANSPORT_ONLY,
    ): OfficialCloudApiResponse {
        for (attempt in 0..retryPolicy.maxRetries) {
            val startedAt = clock()
            try {
                val requestBuilder = Request.Builder()
                    .url(config.resolve(path))
                    .method(method, body?.let { CloudJson.encode(it).toRequestBody(JSON_MEDIA_TYPE) })
                config.defaultHeaders.forEach { (name, value) -> requestBuilder.header(name, value) }
                if (!token.isNullOrEmpty()) {
                    requestBuilder.header("Authorization", token)
                }
                val call = httpClient.newCall(requestBuilder.build())
                call.timeout().timeout(config.responseTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)

                val response = withContext(Dispatchers.IO) { call.execute() }
                try {
                    val text = withContext(Dispatchers.IO) { response.body?.string() ?: "" }
                    val decoded = decodeBodyForStatus(
                        text = text,
                        path = path,
                        method = method,
                        startedAt = startedAt,
                        statusCode = response.code,
                        attempt = attempt,
                        retryPolicy = retryPolicy,
                    )
                    if (decoded == null) continue

                    recordRequest(
                        path = path,
                        method = method,
                        startedAt = startedAt,
                        statusCode = response.code,
                        body = decoded,
                    )
                    val headers = mutableMapOf<String, String>()
                    response.headers.forEach { (name, value) ->
                        headers.putIfAbsent(name.lowercase(), value)
                    }
                    if (response.code !in 200..299) {
                        val message = OfficialCloudRedactor.text(
                            decoded["msg"]?.toString() ?: "官方接口返回 ${response.code}",
                        )
                        if (retryPolicy.shouldRetryStatusCode(response.code) &&
                            retryPolicy.canRetryAttempt(attempt)
                        ) {
                            delayBeforeRetry(
                                path = path,
                                method = method,
                                attempt = attempt,
                                retryPolicy = retryPolicy,
                                message = message,
                                statusCode = response.code,
                            )
                            continue
                        }
                        throw OfficialCloudApiException(message, statusCode = response.code)
                    }
                    return OfficialCloudApiResponse(
                        statusCode = response.code,
                        headers = headers,
                        body = decoded,
                    )
                } finally {
                    response.close()
                }
            } catch (e: SocketTimeoutException) {
                if (retryPolicy.canRetryAttempt(attempt)) {
                    delayBeforeRetry(
                        path = path,
                        method = method,
                        attempt = attempt,
                        retryPolicy = retryPolicy,
                        message = "请求超时，请检查网络",
                    )
                    continue
                }
                recordRequestFailure(
                    path = path,
                    method = method,
                    startedAt = startedAt,
                    message = "请求超时，请检查网络",
                )
                throw OfficialCloudApiException("请求超时，请检查网络")
            } catch (e: IOException) {
                if (retryPolicy.canRetryAttempt(attempt)) {
                    delayBeforeRetry(
                        path = path,
                        method = method,
                        attempt = attempt,
                        retryPolicy = retryPolicy,
                        message = "网络不可用，请检查连接",
                    )
                    continue
                }
                recordRequestFailure(
                    path = path,
                    method = method,
                    startedAt = startedAt,
                    message = "网络不可用，请检查连接",
                )
                throw OfficialCloudApiException("网络不可用，请检查连接")
            } catch (e: OfficialCloudApiException) {
                recordRequestFailure(
                    path = path,
                    method = method,
                    startedAt = startedAt,
                    message = e.message,
                    statusCode = e.statusCode,
                )
                throw e
            }
        }
        // Unreachable — the loop always returns or throws.
        throw IllegalStateException("Unreachable")
    }

    private suspend fun decodeBodyForStatus(
        text: String,
        path: String,
        method: String,
        startedAt: LocalDateTime,
        statusCode: Int,
        attempt: Int,
        retryPolicy: OfficialCloudRetryPolicy,
    ): Map<String, Any?>? {
        return try {
            decodeBody(text)
        } catch (e: OfficialCloudApiException) {
            if (retryPolicy.shouldRetryStatusCode(statusCode) &&
                retryPolicy.canRetryAttempt(attempt)
            ) {
                recordRequestFailure(
                    path = path,
                    method = method,
                    startedAt = startedAt,
                    message = e.message,
                    statusCode = statusCode,
                )
                delayBeforeRetry(
                    path = path,
                    method = method,
                    attempt = attempt,
                    retryPolicy = retryPolicy,
                    message = e.message,
                    statusCode = statusCode,
                )
                null
            } else {
                throw e
            }
        }
    }

    private suspend fun delayBeforeRetry(
        path: String,
        method: String,
        attempt: Int,
        retryPolicy: OfficialCloudRetryPolicy,
        message: String,
        statusCode: Int? = null,
    ) {
        val safePath = OfficialCloudRedactor.requestPath(path)
        log.operation(
            "官方云接口重试",
            detail = "$method $safePath attempt=${attempt + 1}/${retryPolicy.maxRetries} " +
                "status=${statusCode?.toString() ?: "none"} msg=${shortMessage(message) ?: "none"}",
            level = LogLevel.WARNING,
        )
        delay(config.retryDelayForAttempt(attempt).inWholeMilliseconds)
    }

    private fun recordRequest(
        path: String,
        method: String,
        startedAt: LocalDateTime,
        statusCode: Int,
        body: Map<String, Any?>,
    ) {
        val completedAt = clock()
        val elapsed = Duration.between(startedAt, completedAt)
        val safePath = OfficialCloudRedactor.requestPath(path)
        val code = body["code"]?.toString()
        val msg = shortMessage(body["msg"]?.toString())
        lastRequestValue = OfficialCloudRequestSummary(
            path = safePath,
            method = method,
            statusCode = statusCode,
            code = code,
            message = msg,
            elapsed = elapsed,
            success = statusCode in 200..299,
            at = completedAt,
        )
        log.operation(
            "官方云接口返回",
            detail = "$method $safePath status=$statusCode code=${code ?: "none"} " +
                "elapsed=${elapsed.toMillis()}ms msg=${msg ?: "none"}",
            level = LogLevel.DEBUG,
        )
    }

    private fun recordRequestFailure(
        path: String,
        method: String,
        startedAt: LocalDateTime,
        message: String,
        statusCode: Int? = null,
    ) {
        val completedAt = clock()
        val elapsed = Duration.between(startedAt, completedAt)
        val safePath = OfficialCloudRedactor.requestPath(path)
        lastRequestValue = OfficialCloudRequestSummary(
            path = safePath,
            method = method,
            statusCode = statusCode,
            code = null,
            message = shortMessage(message),
            elapsed = elapsed,
            success = false,
            at = completedAt,
        )
        log.operation(
            "官方云接口失败",
            detail = "$method $safePath status=${statusCode?.toString() ?: "none"} " +
                "elapsed=${elapsed.toMillis()}ms msg=${shortMessage(message)}",
            level = LogLevel.WARNING,
        )
    }

    private fun shortMessage(message: String?): String? {
        val normalized = message?.trim()
        if (normalized.isNullOrEmpty()) return null
        val redacted = OfficialCloudRedactor.text(normalized)
        return if (redacted.length <= 80) redacted else redacted.substring(0, 80)
    }

    private fun decodeBody(text: String): Map<String, Any?> {
        if (text.trim().isEmpty()) return emptyMap()
        val decoded = try {
            CloudJson.decode(text)
        } catch (e: Exception) {
            throw OfficialCloudApiException("服务器返回非 JSON 数据: ${responseBodyExcerpt(text)}")
        }
        if (decoded is Map<*, *>) {
            return parsePersistedMap(decoded) ?: throw OfficialCloudApiException("服务器返回数据格式不正确")
        }
        throw OfficialCloudApiException("服务器返回数据格式不正确")
    }

    private fun responseBodyExcerpt(text: String): String {
        val redacted = OfficialCloudRedactor.text(text)
        return if (redacted.length < 80) redacted else redacted.substring(0, 80)
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
