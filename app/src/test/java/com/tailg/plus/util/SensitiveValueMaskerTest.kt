package com.tailg.plus.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SensitiveValueMaskerTest {

    @Test
    fun compact_shortValuesAreFullyMasked() {
        assertEquals("***", SensitiveValueMasker.compact("abc"))
        assertEquals("***", SensitiveValueMasker.compact("123456"))
        assertEquals("***", SensitiveValueMasker.compact(""))
        assertEquals("fallback", SensitiveValueMasker.compact("", emptyValue = "fallback"))
    }

    @Test
    fun compact_keepsHeadAndTail() {
        assertEquals("abc***hij", SensitiveValueMasker.compact("abcdefghij"))
        assertEquals("138***678", SensitiveValueMasker.compact("13812345678"))
    }

    @Test
    fun compact_trimsByDefault() {
        assertEquals("abc***hij", SensitiveValueMasker.compact("  abcdefghij  "))
        assertEquals("abc***hij", SensitiveValueMasker.compact("abcdefghij", trim = false))
    }

    @Test
    fun phone_masksMiddleFour() {
        assertEquals("138****5678", SensitiveValueMasker.phone("13812345678"))
    }

    @Test
    fun phone_shortValuesReturnShortValueOrOriginal() {
        assertEquals("123456", SensitiveValueMasker.phone("123456"))
        assertEquals("short", SensitiveValueMasker.phone("123456", shortValue = "short"))
        assertEquals("1234567", SensitiveValueMasker.phone("1234567", minMaskLength = 10))
    }
}
