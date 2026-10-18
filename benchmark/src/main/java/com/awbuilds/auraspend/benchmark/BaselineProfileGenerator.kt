package com.awbuilds.auraspend.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline profile for AuraSpend.
 *
 * Run against a device/emulator (English or otherwise — navigation targets are
 * matched by contentDescription, which is not localized):
 *
 * ```
 * ./gradlew :benchmark:connectedFreeBenchmarkAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.awbuilds.auraspend.benchmark.BaselineProfileGenerator
 * ```
 *
 * The generated `baseline-prof.txt` is pulled into
 * `benchmark/build/outputs/connected_android_test_additional_output/…`;
 * copy it to `app/src/main/baseline-prof.txt` and commit.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(packageName = StartupBenchmark.PACKAGE) {
            startActivityAndWait()
            device.waitForIdle()

            // The four tabs, in the order a person actually visits them.
            device.findObject(By.desc("Activity"))?.click()
            device.waitForIdle()
            device.findObject(By.desc("Plan"))?.click()
            device.waitForIdle()
            device.findObject(By.desc("Insights"))?.click()
            device.waitForIdle()
            device.findObject(By.desc("Home"))?.click()
            device.waitForIdle()

            // Deep navigation, and the way back out of it — the path the owner
            // reported as laggy, and the one this walk never covered. It used to
            // be tabs + Quick Add only, so the transaction detail, Settings and
            // every *pop* transition stayed out of the profile and ran
            // interpreted the first time they were opened.
            //
            // A row has to exist for the list and the detail page to be reachable
            // at all, and a freshly installed release build has no data. One
            // transaction is created through the app's own Quick Add sheet — the
            // path a user takes — so the populated list is compiled too, not just
            // its empty state. Every step is null-safe: a selector that does not
            // match skips rather than fails.
            device.findObject(By.desc("Add transaction"))?.click()
            device.waitForIdle()
            listOf("5", "0", "0").forEach { key ->
                device.findObject(By.text(key))?.click()
                device.waitForIdle()
            }
            device.findObject(By.text("Save"))?.click()
            device.waitForIdle()

            // Activity now has a row; the pop back out of it is the transition
            // the owner feels most.
            device.findObject(By.desc("Activity"))?.click()
            device.waitForIdle()
            device.findObject(By.desc("Home"))?.click()
            device.waitForIdle()

            device.findObject(By.desc("Settings"))?.click()
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()

            // Quick Add, and its way out.
            device.findObject(By.desc("Add transaction"))?.click()
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()
        }
    }
}
