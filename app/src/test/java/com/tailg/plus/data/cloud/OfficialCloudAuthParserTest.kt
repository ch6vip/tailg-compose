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
}
