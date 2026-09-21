package com.tailg.plus.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame-timing (jank) baseline for scroll gestures on the app's main surface.
 *
 * IMPORTANT — this is a COARSE baseline. It deliberately does NOT depend on any
 * specific `testTag` / screen structure: it launches the app and then performs a
 * generic vertical swipe/fling sequence over whatever the start destination
 * renders. Until a concrete screen is wired in (e.g. a tagged list in the garage
 * or diagnostic screen), treat the reported `frameDurationCpuMs` / `frameOverrunMs`
 * as "is this screen jank-free at all", not as a per-screen budget.
 *
 * Once a stable scrollable target exists, replace [genericScroll] with a real
 * `device.findObject(...).scroll()` / `onNodeWithTag(...)` interaction so the
 * metric is attributable to one screen.
 *
 * Run on a physical device:
 *   .\gradlew.bat :macrobenchmark:connectedBenchmarkAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class FrameTimingBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollMainSurface() = benchmarkRule.measureRepeated(
        packageName = APP_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = {
            // Cold start each iteration so the very first frames after launch
            // (the most jank-prone ones) are included.
            pressHome()
        },
    ) {
        startActivityAndWait()
        genericScroll(swipes = SWIPES_PER_ITERATION)
    }

    /**
     * Swipes from ~75% down to ~25% of the screen height on the vertical center
     * axis, i.e. a standard upward fling over an unknown scrollable region.
     * Direction alternates so the measured distance is symmetric and the list
     * does not run off one end.
     */
    private fun MacrobenchmarkScope.genericScroll(swipes: Int) {
        val centerX = device.getDisplayWidth() / 2
        val upperY = device.getDisplayHeight() / 4
        val lowerY = device.getDisplayHeight() * 3 / 4
        val stepDurationMs = 200L

        repeat(swipes) { index ->
            val (fromY, toY) = if (index % 2 == 0) lowerY to upperY else upperY to lowerY
            device.swipe(centerX, fromY, centerX, toY, SWIPE_STEPS)
            // Let the fling settle and a few more frames render before the next
            // gesture, so the metric covers steady-state scrolling too.
            Thread.sleep(stepDurationMs)
        }
    }

    private companion object {
        const val SWIPES_PER_ITERATION = 6
        const val SWIPE_STEPS = 20
    }
}
