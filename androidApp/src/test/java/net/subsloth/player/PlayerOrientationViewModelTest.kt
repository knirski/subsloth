package net.subsloth.player

import android.content.pm.ActivityInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayerOrientationViewModelTest {

    @Test
    fun `enterPlayer captures portrait once and locks sensor landscape`() {
        val viewModel = PlayerOrientationViewModel()
        var current = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        viewModel.enterPlayer({ current }) { current = it }
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, current)

        viewModel.enterPlayer({ ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }) { current = it }
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, current)

        viewModel.exitPlayer { current = it }
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, current)
    }

    @Test
    fun `exitPlayer is a no-op when playback never entered`() {
        val viewModel = PlayerOrientationViewModel()
        var writes = 0

        viewModel.exitPlayer { writes++ }

        assertEquals(0, writes)
    }

    @Test
    fun `a second playback session captures the orientation requested before it`() {
        val viewModel = PlayerOrientationViewModel()
        var current = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        viewModel.enterPlayer({ current }) { current = it }
        viewModel.exitPlayer { current = it }
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, current)

        current = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        viewModel.enterPlayer({ current }) { current = it }
        viewModel.exitPlayer { current = it }
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, current)
    }
}
