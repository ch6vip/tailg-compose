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

    /**
     * Normalize a user-pasted token into a valid `Authorization` header value.
     *
     * Handles three common paste formats:
     * - `Authorization: Bearer xxx` (full header line) → extract `Bearer xxx`
     * - `Bearer xxx` → normalize to `Bearer xxx`
     * - bare token (possibly URL-encoded) → decode + prepend `Bearer `
     *
     * The official server expects `Bearer <token>` (see decompiled
     * `PlatfromTailgRetrofit.java`). Tokens copied from URLs / cookies are
     * often percent-encoded (`%2F`, `%2B`, `%3D`); decode them so the server
     * receives the raw base64 value.
     */
    fun normalizeAuthorizationToken(raw: String): String {
        var token = raw.trim()
        if (token.isEmpty()) return ""
        val authLine = Regex("(?i)authorization\\s*:\\s*(.+)$", RegexOption.MULTILINE).find(token)
        if (authLine != null) {
            token = authLine.groupValues[1].trim()
        }
        token = Regex("\\s+").replace(token, " ").trim()
        if (token.lowercase().startsWith("bearer ")) {
            var value = token.substring(7).trim()
            if (value.contains("%")) {
                value = try {
                    java.net.URLDecoder.decode(value, "UTF-8")
                } catch (_: Exception) {
                    value
                }
            }
            return if (value.isEmpty()) "" else "Bearer $value"
        }
        // Bare token: URL-decode if percent-encoded, then prepend Bearer.
        val decoded = if (token.contains("%")) {
            try {
                java.net.URLDecoder.decode(token, "UTF-8")
            } catch (_: Exception) {
                token
            }
        } else {
            token
        }
        return if (decoded.isEmpty()) "" else "Bearer $decoded"
    }

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
