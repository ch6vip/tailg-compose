package com.tailg.plus.data.cloud

import com.tailg.plus.data.model.parsePersistedMap
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService
import java.io.IOException
import java.io.InterruptedIOException
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
    override val config: OfficialCloudApiConfig = OfficialCloudApiConfig(),
    val log: LogService = LogService(),
    private val okHttpClient: OkHttpClient? = null,
    clock: () -> LocalDateTime = { LocalDateTime.now() },
) : OfficialCloudApiClientInterface {
    private val httpClient: OkHttpClient = okHttpClient ?: OkHttpClient.Builder()
        .connectTimeout(config.connectTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        // Client-level read/write guards: without these OkHttp defaults to a
        // 10-minute read timeout, so a stalled body could pin the IO thread far
        // beyond the per-call budget below.
        .readTimeout(config.responseTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .writeTimeout(config.responseTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        // Bounded connection pool: a single-user app talks to one official
        // host, so 2 idle keep-alive connections are plenty. The default pool
        // keeps 5 alive for 5 minutes; trimming to 2 cuts idle sockets while
        // still covering concurrent carStatus+battery refreshes.
        .connectionPool(okhttp3.ConnectionPool(2, 2, TimeUnit.MINUTES))
        // Same-host concurrency guard: the official host throttles aggressive
        // clients; 2 parallel requests per host keeps the silent refresh +
        // dependents cascade from self-racing while staying well under the
        // server's abuse threshold.
        .dispatcher(
            okhttp3.Dispatcher().apply {
                maxRequests = 8
                maxRequestsPerHost = 2
            },
        )
        .build()

    // Shared plain-Moshi instance (CloudJson owns it) — a second identical
    // Moshi here used to duplicate built-in adapter setup for no benefit.
    private val moshi: com.squareup.moshi.Moshi = CloudJson.moshi

    private var clock: () -> LocalDateTime = clock

    private var lastRequestValue: OfficialCloudRequestSummary? = null

    override val lastRequest: OfficialCloudRequestSummary? get() = lastRequestValue

    override fun dispose() {
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
    override suspend fun request(
        path: String,
        method: String,
        token: String?,
        body: Map<String, Any?>?,
        retryPolicy: OfficialCloudRetryPolicy,
    ): OfficialCloudApiResponse {
        for (attempt in 0..retryPolicy.maxRetries) {
            val startedAt = clock()
            try {
                val requestBuilder = Request.Builder()
                    .url(config.resolve(path))
                    .method(
                        method,
                        // Dart http.post tolerates a missing body; OkHttp throws
                        // "POST must have a request body". Body-less non-GET
                        // requests (getUserProfile / logout style) get an empty
                        // payload like the Dart original.
                        body?.let { CloudJson.encode(it).toRequestBody(JSON_MEDIA_TYPE) }
                            ?: if (method.equals("GET", ignoreCase = true)) null
                            else ByteArray(0).toRequestBody(),
                    )
                config.defaultHeaders.forEach { (name, value) -> requestBuilder.header(name, value) }
                if (!token.isNullOrEmpty()) {
                    requestBuilder.header("Authorization", token)
                }
                val call = httpClient.newCall(requestBuilder.build())
                call.timeout().timeout(config.responseTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)

                val response = withContext(Dispatchers.IO) { call.execute() }
                try {
                    val text = withContext(Dispatchers.IO) {
                        readBodyLimited(response.body)
                    }
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
            } catch (e: InterruptedIOException) {
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

    /**
     * Read a bounded UTF-8 response body. `ResponseBody.string()` buffers the
     * entire untrusted payload before the caller can enforce a size; a
     * malicious/defective endpoint could otherwise pin memory with a huge
     * body (ComicPlus_Pure applies the same guard via `readStringLimited`).
     * The stream is drained in chunks and aborts as soon as the cap is
     * exceeded, so even a lying `Content-Length` cannot over-allocate.
     */
    private fun readBodyLimited(body: okhttp3.ResponseBody?): String {
        if (body == null) return ""
        val declared = body.contentLength()
        if (declared >= 0 && declared > MAX_RESPONSE_BODY_BYTES) {
            throw OfficialCloudApiException("服务器返回数据过大")
        }
        body.byteStream().use { input ->
            val output = java.io.ByteArrayOutputStream(
                if (declared in 1..MAX_RESPONSE_BODY_BYTES) declared.toInt() else 8192,
            )
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_RESPONSE_BODY_BYTES) {
                    throw OfficialCloudApiException("服务器返回数据过大")
                }
                output.write(buffer, 0, read)
            }
            return output.toString(Charsets.UTF_8.name())
        }
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

        /** Official cloud JSON responses are small; 8 MiB is a generous cap. */
        const val MAX_RESPONSE_BODY_BYTES = 8L * 1024L * 1024L
    }
}
