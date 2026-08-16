package com.tailg.plus.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Port of the Dart `official_cloud_auth_parser` tests — auth-error detection
 * and user-id extraction from login payloads.
 */
class OfficialCloudAuthParserTest {

  @Test
  fun `detects http 401 and 403 statuses`() {
    assertTrue(OfficialCloudAuthParser.looksLikeAuthError(OfficialCloudApiException("未授权", statusCode = 401)))
    assertTrue(OfficialCloudAuthParser.looksLikeAuthError(OfficialCloudApiException("forbidden", statusCode = 403)))
    assertFalse(OfficialCloudAuthParser.looksLikeAuthError(OfficialCloudApiException("not found", statusCode = 404)))
    assertFalse(OfficialCloudAuthParser.looksLikeAuthError(OfficialCloudApiException("服务器开小差了")))
  }

  @Test
  fun `detects auth keywords in message`() {
    assertTrue(OfficialCloudAuthParser.looksLikeAuthError(Exception("unauthorized request")))
    assertTrue(OfficialCloudAuthParser.looksLikeAuthError(Exception("token expired")))
    assertTrue(OfficialCloudAuthParser.looksLikeAuthError(Exception("认证失败，请重新登录")))
    assertTrue(OfficialCloudAuthParser.looksLikeAuthError(Exception("登录已过期")))
    assertTrue(OfficialCloudAuthParser.looksLikeAuthError(Exception("授权已失效")))
  }

  @Test
  fun `compound token plus expiry keyword is an auth error`() {
    assertTrue(OfficialCloudAuthParser.looksLikeAuthError(Exception("token 已过期")))
    assertTrue(OfficialCloudAuthParser.looksLikeAuthError(Exception("TOKEN 失效了")))
    assertFalse(OfficialCloudAuthParser.looksLikeAuthError(Exception("token")))
    assertFalse(OfficialCloudAuthParser.looksLikeAuthError(Exception("网络连接已失效，请重试")))
  }

  @Test
  fun `extracts uid or userId top-level and nested`() {
    assertEquals("42", OfficialCloudAuthParser.extractUserId(mapOf("uid" to "42")))
    assertEquals("u1", OfficialCloudAuthParser.extractUserId(mapOf("data" to mapOf("userId" to "u1"))))
    assertEquals(
      "u2",
      OfficialCloudAuthParser.extractUserId(
        mapOf("list" to listOf(mapOf("name" to "x"), mapOf("uid" to "u2"))),
      ),
    )
    assertEquals("", OfficialCloudAuthParser.extractUserId(mapOf("data" to emptyMap<String, Any?>())))
  }

  @Test
  fun `does not extract from id-like keys`() {
    // 'id' / 'carId' / 'deviceTravelId' must never be mistaken for the user id.
    assertEquals(
      "",
      OfficialCloudAuthParser.extractUserId(mapOf("id" to "1", "carId" to "2", "deviceTravelId" to "3")),
    )
    assertEquals("", OfficialCloudAuthParser.extractUserId(emptyMap()))
    assertEquals("", OfficialCloudAuthParser.extractUserId(mapOf("uid" to "  ")))
  }

  // -- normalizeAuthorizationToken -------------------------------------------

  @Test
  fun `bare token gets Bearer prefix`() {
    assertEquals("Bearer abc123", OfficialCloudAuthParser.normalizeAuthorizationToken("abc123"))
  }

  @Test
  fun `Bearer token is preserved`() {
    assertEquals("Bearer abc123", OfficialCloudAuthParser.normalizeAuthorizationToken("Bearer abc123"))
    assertEquals("Bearer abc123", OfficialCloudAuthParser.normalizeAuthorizationToken("bearer abc123"))
  }

  @Test
  fun `Authorization header line is extracted`() {
    assertEquals(
      "Bearer abc123",
      OfficialCloudAuthParser.normalizeAuthorizationToken("Authorization: Bearer abc123"),
    )
  }

  @Test
  fun `URL-encoded bare token is decoded and gets Bearer prefix`() {
    // %2F=/, %2B=+, %3D==
    assertEquals(
      "Bearer a/b+c=",
      OfficialCloudAuthParser.normalizeAuthorizationToken("a%2Fb%2Bc%3D"),
    )
  }

  @Test
  fun `URL-encoded Bearer token is decoded`() {
    assertEquals(
      "Bearer a/b+c=",
      OfficialCloudAuthParser.normalizeAuthorizationToken("Bearer a%2Fb%2Bc%3D"),
    )
  }

  @Test
  fun `empty input returns empty`() {
    assertEquals("", OfficialCloudAuthParser.normalizeAuthorizationToken(""))
    assertEquals("", OfficialCloudAuthParser.normalizeAuthorizationToken("   "))
  }

  @Test
  fun `real-world URL-encoded token is decoded`() {
    val encoded = "c3fwod5KRO6B%2FPX7o6YOu81xVzPu24uGlaH5jEOudIG2d%2FKZ6i51depGp9NkWDN"
    val expected = "Bearer c3fwod5KRO6B/PX7o6YOu81xVzPu24uGlaH5jEOudIG2d/KZ6i51depGp9NkWDN"
    assertEquals(expected, OfficialCloudAuthParser.normalizeAuthorizationToken(encoded))
  }

  @Test
  fun `literal plus survives mixed URL-encoded bare token`() {
    // Copied from a URL query string: '+' kept literal, '/'/'=' percent-encoded.
    // URLDecoder would turn '+' into a space and silently corrupt the token.
    assertEquals(
      "Bearer a+b/c=",
      OfficialCloudAuthParser.normalizeAuthorizationToken("a+b%2Fc%3D"),
    )
  }

  @Test
  fun `literal plus survives mixed URL-encoded Bearer token`() {
    assertEquals(
      "Bearer a+b/c=",
      OfficialCloudAuthParser.normalizeAuthorizationToken("Bearer a+b%2Fc%3D"),
    )
  }

  @Test
  fun `malformed percent sequence falls back to raw token`() {
    // Trailing lone '%' cannot be decoded; keep the paste as-is instead of
    // failing the login outright (Dart throws here — we degrade gracefully).
    assertEquals("Bearer abc%", OfficialCloudAuthParser.normalizeAuthorizationToken("abc%"))
  }
}
