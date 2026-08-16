package com.tailg.plus.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Port of the Dart `OfficialCloudLoginValidator` tests — phone / SMS code
 * validation for the login form.
 */
class OfficialCloudLoginValidatorTest {

  @Test
  fun `phone must be exactly 11 digits`() {
    assertTrue(OfficialCloudLoginValidator.isValidPhone("13812345678"))
    assertFalse(OfficialCloudLoginValidator.isValidPhone("1381234567"))
    assertFalse(OfficialCloudLoginValidator.isValidPhone("138123456789"))
    assertFalse(OfficialCloudLoginValidator.isValidPhone("1381234567a"))
    assertFalse(OfficialCloudLoginValidator.isValidPhone(""))
  }

  @Test
  fun `sms code is 4 to 8 digits`() {
    assertTrue(OfficialCloudLoginValidator.isValidSmsCode("1234"))
    assertTrue(OfficialCloudLoginValidator.isValidSmsCode("12345678"))
    assertFalse(OfficialCloudLoginValidator.isValidSmsCode("123"))
    assertFalse(OfficialCloudLoginValidator.isValidSmsCode("123456789"))
    assertFalse(OfficialCloudLoginValidator.isValidSmsCode("12a4"))
  }

  @Test
  fun `compactPhone strips all whitespace`() {
    assertEquals("13812345678", OfficialCloudLoginValidator.compactPhone(" 138 1234 5678 "))
    assertEquals("13812345678", OfficialCloudLoginValidator.compactPhone("13812345678"))
    // Whitespace-stripped input still has to pass the digit rule itself.
    assertFalse(OfficialCloudLoginValidator.isValidPhone(OfficialCloudLoginValidator.compactPhone("138 12x4 5678")))
  }
}
