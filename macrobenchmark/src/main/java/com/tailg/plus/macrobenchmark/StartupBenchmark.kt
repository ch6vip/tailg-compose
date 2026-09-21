package com.tailg.plus.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-start timing for the real (R8-minified, non-debuggable) `benchmark`
 * build of :app.
 *
 * Run on a physical device or an emulator with a release-signed-capable image:
 *   .\gradlew.bat :macrobenchmark:connectedBenchmarkAndroidTest
 *
 * The measured numbers feed [app/src/main/baseline-prof.txt] curation: a
 * regression here means either the startup path grew or the baseline profile
 * stopped covering it.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = APP_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        iterations = 5,
        startupMode = StartupMode.COLD,
        setupBlock = {
            // Make sure the next launch really is a cold start: send the app
            // back to the launcher and let the activity be destroyed.
            pressHome()
        },
    ) {
        startActivityAndWait()
    }
}
