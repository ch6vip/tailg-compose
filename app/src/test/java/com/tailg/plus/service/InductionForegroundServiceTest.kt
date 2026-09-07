package com.tailg.plus.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.ResultReceiver
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class InductionForegroundServiceTest {
  @Test
  fun acceptingTheLaunchDoesNotConfirmForegroundStartup() = runTest {
    val intent = slot<Intent>()
    val context = mockk<Context>()
    every { context.packageName } returns "com.tailg.plus"
    every { context.startForegroundService(capture(intent)) } returns
      ComponentName("com.tailg.plus", InductionForegroundService::class.java.name)
    val bridge = AndroidInductionForegroundServiceBridge(context)
    for (result in listOf(0, 1)) {
      val started = async { bridge.start("vehicle") }
      testScheduler.runCurrent()
      assertFalse(started.isCompleted)

      @Suppress("DEPRECATION")
      val receiver = intent.captured.getParcelableExtra<ResultReceiver>(InductionForegroundService.EXTRA_START_RESULT)
      requireNotNull(receiver).send(result, null)

      if (result == 1) assertTrue(started.await()) else assertFalse(started.await())
    }
  }

  @Test
  fun aLaunchWithoutAcknowledgementTimesOut() = runTest {
    val context = mockk<Context>()
    every { context.packageName } returns "com.tailg.plus"
    every { context.startForegroundService(any()) } returns
      ComponentName("com.tailg.plus", InductionForegroundService::class.java.name)

    assertFalse(AndroidInductionForegroundServiceBridge(context).start("vehicle"))
    assertEquals(5_000L, testScheduler.currentTime)
  }

  @Test
  fun serviceDestructionClearsTheRunningSignal() {
    val controller = Robolectric.buildService(InductionForegroundService::class.java).create()
    try {
      controller.startCommand(0, 1)
      assertTrue(InductionForegroundService.isRunning)
    } finally {
      controller.destroy()
    }
    assertFalse(InductionForegroundService.isRunning)
  }
}
