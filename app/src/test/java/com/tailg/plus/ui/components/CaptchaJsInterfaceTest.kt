package com.tailg.plus.ui.components

import android.os.Looper
import org.junit.Assert.assertEquals
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
}
