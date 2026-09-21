package com.tailg.plus.ui.components

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CaptchaJsInterfaceTest {
  @Test
  fun repeatedJavascriptResultsAreDeliveredOnlyOnce() {
    val results = mutableListOf<String>()
    val bridge = CaptchaJsInterface(onResult = { ticket, _ -> results.add(ticket) }, onError = {})
    bridge.setSmsInfo("""{"ticket":"first","randstr":"random"}""")
    bridge.setSmsInfo("""{"ticket":"second","randstr":"random"}""")
    shadowOf(Looper.getMainLooper()).idle()

    assertEquals(listOf("first"), results)
    bridge.close()
  }

  @Test
  fun queuedAndLateCallbacksCannotOutliveTheDialog() {
    var calls = 0
    val bridge = CaptchaJsInterface(onResult = { _, _ -> calls++ }, onError = { calls++ })
    bridge.setSmsInfo("""{"ticket":"queued"}""")
    bridge.close()
    bridge.setSmsInfo("""{"ticket":"late"}""")
    bridge.setError("late error")
    shadowOf(Looper.getMainLooper()).idle()

    assertEquals(0, calls)
  }
  @Test
  fun captchaNavigationIsRestrictedToOfficialHttpsHost() {
    assertTrue(isAllowedCaptchaUrl("https", "www.tailgdd.com"))
    assertTrue(isAllowedCaptchaUrl("https", "captcha.tailgdd.com"))
    assertTrue(isAllowedCaptchaUrl("https", "captcha.qq.com"))
    assertTrue(isAllowedCaptchaUrl("about", "blank"))
    assertFalse(isAllowedCaptchaUrl("http", "www.tailgdd.com"))
    assertFalse(isAllowedCaptchaUrl("https", "evil.example"))
    assertFalse(isAllowedCaptchaUrl("file", "www.tailgdd.com"))
    assertFalse(isAllowedCaptchaUrl("https", "tailgdd.com.evil"))
    assertFalse(isAllowedCaptchaUrl("https", "evil.qq.com"))
  }
}
