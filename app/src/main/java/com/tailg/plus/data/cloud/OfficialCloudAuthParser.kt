package com.tailg.plus.data.cloud

/**
 * Port of `OfficialCloudAuthParser` from `lib/services/official_cloud_auth_parser.dart`.
 *
 * Detects auth failures (HTTP 401/403 or token/认证 keywords) and extracts the
 * user id from a login payload (`uid` / `userId` keys only, recursively —
 * deliberately NOT `id`, which would match `carId` / `deviceTravelId` /
 * `extendId` and return the wrong user id).
 */
object OfficialCloudAuthParser {

    private val http401Pattern = Regex("""\b401\b""")
    private val http403Pattern = Regex("""\b403\b""")

    fun looksLikeAuthError(error: Throwable): Boolean {
        // Check HTTP status code first.
        if (error is OfficialCloudApiException) {
            if (error.statusCode == 401 || error.statusCode == 403) return true
        }
        val message = error.toString().trim().lowercase()
        if (message.contains("unauthorized") ||
            message.contains("token expired") ||
            message.contains("token invalid") ||
            message.contains("认证失败") ||
            message.contains("登录已过期") ||
            message.contains("授权已失效") ||
            http401Pattern.containsMatchIn(message) ||
            http403Pattern.containsMatchIn(message)
        ) {
            return true
        }
        // Compound: 'token' paired with expiry keyword catches 'token 已过期' etc.
        if (message.contains("token") &&
            (message.contains("过期") || message.contains("失效"))
        ) {
            return true
        }
        return false
    }

    fun extractUserId(body: Map<String, Any?>): String = findUserId(body) ?: ""

    private fun findUserId(value: Any?): String? {
        if (value is Map<*, *>) {
            for (key in listOf("uid", "userId")) {
                val candidate = value[key]
                val text = candidate?.toString()?.trim()
                if (!text.isNullOrEmpty()) return text
            }
            for (child in value.values) {
                val found = findUserId(child)
                if (found != null) return found
            }
        } else if (value is List<*>) {
            for (child in value) {
                val found = findUserId(child)
                if (found != null) return found
            }
        }
        return null
    }
}
