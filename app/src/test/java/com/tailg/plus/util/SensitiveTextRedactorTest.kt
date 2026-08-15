package com.tailg.plus.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SensitiveTextRedactorTest {

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
    fun redact_authorizationWithoutBearer() {
        // "Basic" is masked as a short value; the trailing base64 run is
        // untouched because it has no sensitive key — same as the Dart source.
        assertEquals(
            "authorization: *** dXNlcjpwYXNz",
            SensitiveTextRedactor.redact("authorization: Basic dXNlcjpwYXNz"),
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
