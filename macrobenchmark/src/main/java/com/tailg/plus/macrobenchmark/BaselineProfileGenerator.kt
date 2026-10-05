package com.tailg.plus.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates Baseline Profile rules for tailg-compose.
 *
 * Covers:
 * 1. Cold startup bootstrapping (App/Activity/Compose/Hilt/Theme)
 * 2. ControlScreen first interaction and smooth vertical scrolling
 * 3. Channel selection and Vehicle switch sheet interaction paths
 *
 * Generate with:
 *   .\gradlew.bat :macrobenchmark:connectedBenchmarkAndroidTest -P android.testInstrumentationRunnerArguments.class=com.tailg.plus.macrobenchmark.BaselineProfileGenerator
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineRule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() = baselineRule.collect(
        packageName = APP_PACKAGE,
        includeInStartupProfile = true,
        profileBlock = {
            // 1. Launch activity and wait for the root compose tree to settle
            startActivityAndWait()
            device.wait(Until.hasObject(By.pkg(APP_PACKAGE).depth(0)), 5_000)

            // 2. Perform smooth scroll over the control home screen
            val scrollContainer = device.findObject(By.scrollable(true))
            scrollContainer?.let { container ->
                container.setGestureMargin(device.displayWidth / 5)
                container.fling(Direction.DOWN)
                device.waitForIdle()
                container.fling(Direction.UP)
                device.waitForIdle()
            }

            // 3. Trigger channel selection sheet if available to warm up sheet components
            val channelBtn = device.findObject(By.descContains("控制通道"))
            channelBtn?.click()
            device.waitForIdle()
            device.pressBack()
        },
    )
}
