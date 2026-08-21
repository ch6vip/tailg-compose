package com.tailg.plus.data.cloud

import com.tailg.plus.log.LogLevel

/**
 * High-order function that encapsulates the common API call pattern:
 * token check → loading state → try/catch → ensureSuccess → isCurrentSession guard
 * → handleAuthFailureIfNeeded → logging.
 *
 * Reduces ~40% boilerplate in [OfficialCloudServiceOperations] and
 * [OfficialCloudServiceRefresh].
 *
 * Usage:
 * ```kotlin
 * apiCall(service, token = state.token, silent = false) {
 *   service.apiClient.request("app/endpoint", method = "POST", token = token)
 * }
 * ```
 */
internal suspend fun <T : Any> apiCall(
    service: OfficialCloudService,
    token: String? = service.state.token,
    silent: Boolean = false,
    loading: Boolean = !silent,
    tokenRequired: Boolean = true,
    failureMessage: String = "请求失败",
    block: suspend (token: String) -> T,
): T? {
    val effectiveToken = (token ?: service.state.token).takeIf { it.isNotEmpty() }
    if (tokenRequired && effectiveToken == null) {
        if (!silent) throw OfficialCloudApiException(OfficialCloudMessages.SIGN_IN_REQUIRED)
        return null
    }
    if (loading) service.setLoading(true)
    return try {
        val result = block(effectiveToken!!)
        service.ensureSuccess(
            getBody(result),
            fallback = failureMessage,
        )
        if (!service.isCurrentSession(effectiveToken)) return null
        service.log.operation(
            failureMessage.replace("失败", "成功"),
            level = LogLevel.INFO,
        )
        result
    } catch (e: Exception) {
        if (effectiveToken != null && !service.isCurrentSession(effectiveToken)) return null
        service.handleAuthFailureIfNeeded(e)
        if (!silent) throw e
        service.log.operation(
            failureMessage,
            detail = OfficialCloudRedactor.errorMessage(e),
            level = LogLevel.WARNING,
        )
        null
    } finally {
        if (loading) service.setLoading(false)
    }
}

/** Extract body map from a response or other result type. */
private fun getBody(result: Any): Map<String, Any?> = when (result) {
    is OfficialCloudApiResponse -> result.body
    is Map<*, *> -> @Suppress("UNCHECKED_CAST") result as Map<String, Any?>
    else -> emptyMap()
}