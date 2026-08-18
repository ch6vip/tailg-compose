package com.tailg.plus.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wire-code parsing for the official envelope. Moshi's `Any` adapter decodes
 * every JSON number as a `Double`, so `"code":0` arrives as `0.0`; the parse
 * must tolerate the `.0` suffix.
 */
class OfficialCloudResponseCodeTest {

  @Test
  fun `integer code matches legacy success`() {
    assertEquals(OfficialCloudResponseCode.LEGACY_SUCCESS, OfficialCloudResponseCode.parse(0))
    assertEquals(OfficialCloudResponseCode.SUCCESS, OfficialCloudResponseCode.parse(200))
  }

  @Test
  fun `double code from Moshi matches`() {
    assertEquals(OfficialCloudResponseCode.LEGACY_SUCCESS, OfficialCloudResponseCode.parse(0.0))
    assertEquals(OfficialCloudResponseCode.SUCCESS, OfficialCloudResponseCode.parse(200.0))
  }

  @Test
  fun `string code matches`() {
    assertEquals(OfficialCloudResponseCode.LEGACY_SUCCESS, OfficialCloudResponseCode.parse("0"))
    assertEquals(OfficialCloudResponseCode.SUCCESS, OfficialCloudResponseCode.parse("200"))
    assertEquals(OfficialCloudResponseCode.LEGACY_SUCCESS, OfficialCloudResponseCode.parse("0.0"))
    assertEquals(OfficialCloudResponseCode.SUCCESS, OfficialCloudResponseCode.parse("200.0"))
  }

  @Test
  fun `business failure codes do not match`() {
    assertNull(OfficialCloudResponseCode.parse(401))
    assertNull(OfficialCloudResponseCode.parse("100"))
    assertNull(OfficialCloudResponseCode.parse(null))
  }

  @Test
  fun `success body tolerates double code`() {
    assertTrue(OfficialCloudResponseCode.isSuccessBody(mapOf("code" to 0.0, "msg" to "操作成功")))
    assertFalse(OfficialCloudResponseCode.isSuccessBody(mapOf("code" to 401, "msg" to "认证失败")))
  }
}
