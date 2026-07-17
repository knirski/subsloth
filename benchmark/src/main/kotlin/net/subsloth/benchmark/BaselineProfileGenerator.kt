package net.subsloth.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a baseline profile for the SubSloth app by exercising startup,
 * catalog scrolling, detail navigation, and playback initiation.
 *
 * Run on a device/emulator via:
 * ```
 * ./gradlew :benchmark:connectedCheck
 * ```
 * Generated profile output is written to
 * `benchmark/build/outputs/connected_android_test_additional_output/`.
 * Copy to `androidApp/src/main/baselineProfiles/` for app consumption.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        baselineProfileRule.collect("net.subsloth", 3) {
            // 1. Startup — cold launch
            startActivityAndWait()

            // 2. Wait for home screen to render
            device.waitForIdle()

            // 3. Movie detail — navigate into a movie
            val movieCard = device.findObject(
                androidx.test.uiautomator.By.descContains("The Grand Adventure")
            ) ?: device.findObject(
                androidx.test.uiautomator.By.descContains("Stellar Origins")
            )
            movieCard?.click()
            device.waitForIdle()

            // 4. Series detail — navigate into a series
            device.pressBack()
            device.waitForIdle()
            val seriesCard = device.findObject(
                androidx.test.uiautomator.By.descContains("The Last Kingdom")
            ) ?: device.findObject(
                androidx.test.uiautomator.By.descContains("Quantum Break")
            )
            seriesCard?.click()
            device.waitForIdle()

            // 5. Playback start — initiate playback from detail
            val playButton = device.findObject(
                androidx.test.uiautomator.By.text("Play")
            )
            playButton?.click()
            device.waitForIdle()
            device.waitForWindowUpdate("net.subsloth", 3000)

            device.waitForIdle()
        }
    }
}
