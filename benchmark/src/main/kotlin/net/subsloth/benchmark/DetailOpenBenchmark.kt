package net.subsloth.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmarks measuring frame timing when opening movie and series detail screens.
 *
 * Run on a device/emulator via:
 * ```
 * ./gradlew :benchmark:connectedBenchmarkAndroidTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class DetailOpenBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun openMovieDetail() {
        benchmarkRule.measureRepeated(
            packageName = "net.subsloth",
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD,
            setupBlock = {
                startActivityAndWait()
            },
        ) {
            // Tap the first movie card to open its detail screen
            val movieCard = device.findObject(
                androidx.test.uiautomator.By.descContains("The Grand Adventure")
            ) ?: device.findObject(
                androidx.test.uiautomator.By.descContains("Stellar Origins")
            )
            movieCard?.click()
            device.waitForIdle()
        }
    }

    @Test
    fun openSeriesDetail() {
        benchmarkRule.measureRepeated(
            packageName = "net.subsloth",
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD,
            setupBlock = {
                startActivityAndWait()
            },
        ) {
            // Scroll to shows section
            val homeRecycler = device.findObject(
                androidx.test.uiautomator.By.scrollable(true)
            )
            homeRecycler?.scroll(Direction.DOWN, 0.75f)
            device.waitForIdle()

            // Tap the first series card
            val seriesCard = device.findObject(
                androidx.test.uiautomator.By.descContains("The Last Kingdom")
            ) ?: device.findObject(
                androidx.test.uiautomator.By.descContains("Quantum Break")
            )
            seriesCard?.click()
            device.waitForIdle()
        }
    }
}
