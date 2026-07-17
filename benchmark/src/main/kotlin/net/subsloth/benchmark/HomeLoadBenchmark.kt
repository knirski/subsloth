package net.subsloth.benchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark measuring home screen load time from cache.
 *
 * The app is launched cold, and the benchmark measures how quickly the
 * home screen renders with cached catalog data.
 *
 * Run on a device/emulator via:
 * ```
 * ./gradlew :benchmark:connectedBenchmarkAndroidTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class HomeLoadBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun homeScreenLoadFromCache() {
        benchmarkRule.measureRepeated(
            packageName = "net.subsloth",
            metrics = listOf(StartupTimingMetric()),
            iterations = 10,
            startupMode = StartupMode.COLD,
            setupBlock = {
                // Pre-condition: app has cached catalog data from a prior session.
                // The benchmark measures the time until the home screen content
                // is fully rendered when using cached data (offline-capable).
                startActivityAndWait()
            },
        ) {
            pressHome()
            startActivityAndWait()
        }
    }
}
