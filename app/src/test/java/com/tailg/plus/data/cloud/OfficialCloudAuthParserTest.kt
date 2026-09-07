package com.tailg.plus.data.cloud

import com.tailg.plus.data.model.parsePersistedString
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
  // The official v1/api gateway wants the token URL-encoded and WITHOUT a
  // `Bearer ` prefix (decompiled ResPlatfromTailgRetrofit sends the raw
  // stored value; verified against the production server 2026-08).

  @Test
  fun `bare token is kept as-is`() {
    assertEquals("abc123", OfficialCloudAuthParser.normalizeAuthorizationToken("abc123"))
  }

  @Test
  fun `Bearer prefix is stripped`() {
    assertEquals("abc123", OfficialCloudAuthParser.normalizeAuthorizationToken("Bearer abc123"))
    assertEquals("abc123", OfficialCloudAuthParser.normalizeAuthorizationToken("bearer abc123"))
  }

  @Test
  fun `Authorization header line is extracted and Bearer stripped`() {
    assertEquals(
      "abc123",
      OfficialCloudAuthParser.normalizeAuthorizationToken("Authorization: Bearer abc123"),
    )
    assertEquals(
      "a%2Fb",
      OfficialCloudAuthParser.normalizeAuthorizationToken("Authorization: a%2Fb"),
    )
  }

  @Test
  fun `percent-encoded token is sent verbatim without decoding`() {
    // The server matches the encoded form exactly — decoding it produces
    // {"code":401,"msg":"认证失败"}.
    assertEquals(
      "a%2Fb%2Bc%3D",
      OfficialCloudAuthParser.normalizeAuthorizationToken("a%2Fb%2Bc%3D"),
    )
  }

  @Test
  fun `decoded bare token is re-encoded`() {
    assertEquals(
      "a%2Fb%2Bc%3D",
      OfficialCloudAuthParser.normalizeAuthorizationToken("a/b+c="),
    )
  }

  @Test
  fun `empty input returns empty`() {
    assertEquals("", OfficialCloudAuthParser.normalizeAuthorizationToken(""))
    assertEquals("", OfficialCloudAuthParser.normalizeAuthorizationToken("   "))
  }

  @Test
  fun `unicode paste is encoded as UTF-8 without crashing`() {
    assertEquals("%E4%B8%AD%E6%96%87%E2%80%94%F0%9F%94%91",
      OfficialCloudAuthParser.normalizeAuthorizationToken("中文\u2014\uD83D\uDD11"))
    assertEquals("AZaz09-._~%C3%A9", OfficialCloudAuthParser.normalizeAuthorizationToken("AZaz09-._~é"))
  }

  @Test
  fun `real-world URL-encoded token passes through unchanged`() {
    val encoded = "c3fwod5KRO6B%2FPX7o6YOu81xVzPu24uGlaH5jEOudIG2d%2FKZ6i51depGp9NkWDN"
    assertEquals(encoded, OfficialCloudAuthParser.normalizeAuthorizationToken(encoded))
  }

  @Test
  fun `mixed paste with percent sequences is not re-encoded`() {
    // Contains valid %XX → treated as already encoded and sent verbatim.
    assertEquals(
      "a+b%2Fc%3D",
      OfficialCloudAuthParser.normalizeAuthorizationToken("a+b%2Fc%3D"),
    )
  }

  @Test
  fun `trailing lone percent is encoded as literal`() {
    // No valid %XX sequence → encode path: literal '%' becomes %25.
    assertEquals("abc%25", OfficialCloudAuthParser.normalizeAuthorizationToken("abc%"))
  }

  @Test
  fun `real-world decoded token is fully re-encoded`() {
    // The decoded form of a captured official token; must round-trip back to
    // the encoded form the server accepts.
    val decoded = "c3fwod5KRO6B/PX7o6YOu81xVzPu24uGlaH5jEOudIG2d/KZ6i51depGp9NkWDN"
    val expected = "c3fwod5KRO6B%2FPX7o6YOu81xVzPu24uGlaH5jEOudIG2d%2FKZ6i51depGp9NkWDN"
    assertEquals(expected, OfficialCloudAuthParser.normalizeAuthorizationToken(decoded))
  }
}

class NumericIdParsingTest {
  @Test
  fun `integral doubles render as plain integers (carId-slash-uid corruption regression)`() {
    // A JSON number like 171234567895 parsed by Moshi's Any adapter arrives
    // as Double 1.71234567895E11; Java toString() renders scientific
    // notation which the official endpoints reject with 400.
    assertEquals("171234567895", parsePersistedString(1.71234567895E11))
    assertEquals("5", parsePersistedString(5.0))
    assertEquals("171234567895", com.tailg.plus.data.model.renderScalar(1.71234567895E11))
  }

  @Test
  fun `doubles at or above 1e7 render as plain decimal like Dart`() {
    // Dart double.toString() keeps plain decimal notation up to 1e21;
    // Java switches to scientific at 1e7. The device diagnostic showed a
    // carId of "1.7***5E7" — exactly this corruption.
    assertEquals("17123456.7895", parsePersistedString(1.71234567895E7))
    assertEquals("17000000", parsePersistedString(1.7E7))
  }

  @Test
  fun `fractional doubles below 1e7 keep their normal rendering`() {
    assertEquals("82.5", parsePersistedString(82.5))
    assertEquals("0.5", parsePersistedString(0.5))
  }

  @Test
  fun `extractUserId reads numeric uid as integer string`() {
    val body = mapOf<String, Any?>("data" to mapOf("uid" to 1.71234567895E11))
    assertEquals("171234567895", OfficialCloudAuthParser.extractUserId(body))
  }
}
