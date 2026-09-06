package com.tailg.plus.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SensitiveTextRedactorTest {

    @Test
    fun captchaAndMqttCredentialsAreRedactedInLogsAndRequestPaths() {
        val secret = "sensitive-secret-value"
        for (key in listOf("captchaPassToken", "smsCode", "mqPassword", "mainPassword", "childrenPassword")) {
            org.junit.Assert.assertFalse(SensitiveTextRedactor.redact("$key=$secret").contains(secret))
            org.junit.Assert.assertFalse(com.tailg.plus.data.cloud.OfficialCloudRedactor.requestPath("app/test?$key=$secret").contains(secret))
        }
    }

    @Test
    fun redact_bearerToken() {
        assertEquals(
            "Bearer eyJ***xYw",
            SensitiveTextRedactor.redact("Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOjEyMzQ1Njc4OX0.xYw"),
        )
    }

    @Test
    fun redact_phoneNumber() {
        assertEquals(
            "手机号 138***678 已绑定",
            SensitiveTextRedactor.redact("手机号 13812345678 已绑定"),
        )
    }

    @Test
    fun redact_imeiViaKeyValue() {
        assertEquals("imei=860***012", SensitiveTextRedactor.redact("imei=860123456789012"))
    }

    @Test
    fun redact_standaloneImei() {
        assertEquals("序列号 123***234 正常", SensitiveTextRedactor.redact("序列号 12345678901234 正常"))
    }

    @Test
    fun redact_macAddress() {
        assertEquals("AA:***:FF offline", SensitiveTextRedactor.redact("AA:BB:CC:DD:EE:FF offline"))
    }

    @Test
    fun redact_compactMacAddress() {
        assertEquals("AAB***EFF", SensitiveTextRedactor.redact("AABBCCDDEEFF"))
    }

    @Test
    fun redact_keyValuePair() {
        assertEquals("token=abc***456", SensitiveTextRedactor.redact("token=abc123def456"))
    }

    @Test
    fun redact_quotedPasswordPair() {
        assertEquals(
            "'password': 'hun***ret'",
            SensitiveTextRedactor.redact("'password': 'hunter2secret'"),
        )
    }

    @Test
    fun redact_authorizationBasicCredentials() {
        assertEquals(
            "authorization: Basic ***",
            SensitiveTextRedactor.redact("authorization: Basic dXNlcjpwYXNz"),
        )
        assertEquals(
            "\"Authorization\": \"Basic ***\"",
            SensitiveTextRedactor.redact("\"Authorization\": \"Basic dXNlcjpwYXNz\""),
        )
    }

    @Test
    fun redact_authorizationBearerNotDoubleMasked() {
        assertEquals(
            "authorization: Bearer abc***ijk",
            SensitiveTextRedactor.redact("authorization: Bearer abcdefghijk"),
        )
    }

    @Test
    fun redact_plainTextUnchanged() {
        assertEquals("hello world", SensitiveTextRedactor.redact("hello world"))
    }
}
