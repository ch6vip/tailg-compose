package com.tailg.plus.data.cloud

import com.tailg.plus.log.LogLevel
import kotlinx.coroutines.CancellationException

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
 * apiCall(service, session = state.sessionIdentity, silent = false) { token ->
 *   service.apiClient.request("app/endpoint", method = "POST", token = token)
 * }
 * ```
 */
internal suspend fun <T : Any> apiCall(
    service: OfficialCloudService,
    session: OfficialCloudSession = service.state.sessionIdentity,
    silent: Boolean = false,
    loading: Boolean = !silent,
    tokenRequired: Boolean = true,
    failureMessage: String = "请求失败",
    block: suspend (token: String) -> T,
): T? {
    val effectiveToken = session.token
    fun isCurrent() = !service.disposed && service.state.sessionIdentity == session
    fun setLoading(value: Boolean) = service.updateState { current ->
        if (current.sessionIdentity == session) current.copyWith(loading = value) else current
    }
    if (tokenRequired && effectiveToken.isEmpty()) {
        if (!silent) throw OfficialCloudApiException(OfficialCloudMessages.SIGN_IN_REQUIRED)
        return null
    }
    if (!isCurrent()) return null
    if (loading) setLoading(true)
    return try {
        val result = block(effectiveToken)
        service.ensureSuccess(
            getBody(result),
            fallback = failureMessage,
        )
        if (!isCurrent()) return null
        service.log.operation(
            failureMessage.replace("失败", "成功"),
            level = LogLevel.INFO,
        )
        result
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        if (!isCurrent()) return null
        service.handleAuthFailureIfNeeded(e, session)
        if (!silent) throw e
        service.log.operation(
            failureMessage,
            detail = OfficialCloudRedactor.errorMessage(e),
            level = LogLevel.WARNING,
        )
        null
    } finally {
        if (loading) setLoading(false)
    }
}

/** Extract body map from a response or other result type. */
private fun getBody(result: Any): Map<String, Any?> = when (result) {
    is OfficialCloudApiResponse -> result.body
    is Map<*, *> -> @Suppress("UNCHECKED_CAST") result as Map<String, Any?>
    else -> emptyMap()
}
