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
     * Normalize a user-pasted token into the exact `Authorization` header
     * value the official `v1/api` stack expects.
     *
     * Handles three common paste formats:
     * - `Authorization: Bearer xxx` (full header line) → extract the value
     * - `Bearer xxx` → strip the scheme prefix
     * - bare token (already percent-encoded, or decoded) → keep / restore
     *   percent-encoding
     *
     * Verified against the production server (2026-08): the legacy `v1/api`
     * gateway wants the token **URL-encoded and without a `Bearer ` prefix**
     * — exactly what the decompiled official app sends via
     * `ResPlatfromTailgRetrofit.addHeaders()`:
     * `builder.add("Authorization", PrefsUtil.getToken())`.
     *
     * Sending `Bearer <token>` makes the Spring gateway parse the value as a
     * JWT (`Invalid JWT serialization: Missing dot delimiter(s)` → empty-body
     * 401); sending the decoded base64 form is rejected by the app layer
     * (`{"code":401,"msg":"认证失败"}`). Only the encoded form succeeds —
     * `Bearer <jwt>` belongs to the separate `/v8/` platform stack, not here.
     */
    fun normalizeAuthorizationToken(raw: String): String {
        var token = raw.trim()
        if (token.isEmpty()) return ""
        val authLine = Regex("(?i)authorization\\s*:\\s*(.+)$", RegexOption.MULTILINE).find(token)
        if (authLine != null) {
            token = authLine.groupValues[1].trim()
        }
        token = Regex("\\s+").replace(token, " ").trim()
        // The v1/api stack sends no auth scheme — strip a pasted `Bearer `.
        if (token.lowercase().startsWith("bearer ")) {
            token = token.substring(7).trim()
        }
        if (token.isEmpty()) return ""
        // Already percent-encoded: the server matches this exact form, send verbatim.
        if (percentSequence.containsMatchIn(token)) return token
        // Decoded paste: re-encode the reserved characters the server
        // round-trips (`/`→%2F, `+`→%2B, `=`→%3D, …).
        return percentEncode(token)
    }

    private val percentSequence = Regex("%[0-9A-Fa-f]{2}")

    private const val HEX_DIGITS = "0123456789ABCDEF"

    private fun percentEncode(value: String): String = buildString(value.length) {
        for (char in value) {
            if (char.isLetterOrDigit() || char == '-' || char == '.' || char == '_' || char == '~') {
                append(char)
            } else {
                append('%')
                append(HEX_DIGITS[char.code ushr 4])
                append(HEX_DIGITS[char.code and 0x0F])
            }
        }
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
