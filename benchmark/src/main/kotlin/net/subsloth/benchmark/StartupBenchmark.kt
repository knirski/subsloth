package net.subsloth.benchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark measuring cold and warm startup time for the SubSloth app.
 *
 * Run on a device/emulator via:
 * ```
 * ./gradlew :benchmark:connectedBenchmarkAndroidTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupCold() {
        benchmarkRule.measureRepeated(
            packageName = "net.subsloth",
            metrics = listOf(StartupTimingMetric()),
            iterations = 10,
            startupMode = StartupMode.COLD,
            setupBlock = {
                // No setup — measure from a true cold start
            },
        ) {
            pressHome()
            startActivityAndWait()
        }
    }

    @Test
    fun startupWarm() {
        benchmarkRule.measureRepeated(
            packageName = "net.subsloth",
            metrics = listOf(StartupTimingMetric()),
            iterations = 10,
            startupMode = StartupMode.WARM,
            setupBlock = {
                pressHome()
            },
        ) {
            startActivityAndWait()
        }
    }
}
