package net.subsloth.benchmark

import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark measuring time to start playback from a cold start.
 *
 * The benchmark launches the app, navigates to a movie detail screen,
 * taps Play, and measures the time until the player surface is rendering.
 *
 * Run on a device/emulator via:
 * ```
 * ./gradlew :benchmark:connectedBenchmarkAndroidTest
 * ```
 *
 * Note: Android TV 8 is a required manual/device benchmark target
 * for this scenario due to TV-specific playback pipeline differences.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackStartBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun playbackStart() {
        benchmarkRule.measureRepeated(
            packageName = "net.subsloth",
            metrics = listOf(StartupTimingMetric()),
            iterations = 5,
            setupBlock = {
                startActivityAndWait()
            },
        ) {
            // Navigate to a movie detail
            val movieCard = device.findObject(
                androidx.test.uiautomator.By.descContains("The Grand Adventure")
            ) ?: device.findObject(
                androidx.test.uiautomator.By.descContains("Stellar Origins")
            )
            movieCard?.click()
            device.waitForIdle()

            // Tap Play to start the player
            val playButton = device.findObject(
                androidx.test.uiautomator.By.text("Play")
            )
            playButton?.click()
            device.waitForIdle()

            // Wait for the player surface to render
            device.waitForWindowUpdate("net.subsloth", 3000)
        }
    }
}
