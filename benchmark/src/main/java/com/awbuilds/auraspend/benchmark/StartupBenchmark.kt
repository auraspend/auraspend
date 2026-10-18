package com.awbuilds.auraspend.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-start budget for AuraSpend.
 *
 * Run on a release-like build:
 * `./gradlew :benchmark:connectedDevBenchmarkAndroidTest`
 *
 * Target (Pixel 6a class): time-to-initial-display < 700 ms.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.COLD,
        iterations = 5
    ) {
        startActivityAndWait()
        device.waitForIdle()
    }

    companion object {
        /**
         * The app under test, declared per flavor in `benchmark/build.gradle.kts`.
         *
         * It cannot be read from the instrumentation: this module is
         * *self-instrumenting*, so `targetContext` is the benchmark itself, and
         * asking for it made the macrobenchmark library force-stop its own
         * process. It also must not be a single literal — the old value was the
         * prod id, so the README's dev-flavor command profiled the wrong package.
         */
        val PACKAGE: String = BuildConfig.TARGET_PACKAGE
    }
}
