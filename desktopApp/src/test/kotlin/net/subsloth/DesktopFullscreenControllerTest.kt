package net.subsloth

import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import net.subsloth.desktop.DesktopFullscreenController
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopFullscreenControllerTest {

    @Test
    fun toggle_entersFullscreen_andRestoresPreviousPlacement() {
        val windowState = WindowState(placement = WindowPlacement.Maximized)
        val controller = DesktopFullscreenController(windowState)

        assertFalse(controller.isFullscreen.value)

        controller.toggle()

        assertEquals(WindowPlacement.Fullscreen, windowState.placement)
        assertTrue(controller.isFullscreen.value)

        controller.toggle()

        assertEquals(WindowPlacement.Maximized, windowState.placement)
        assertFalse(controller.isFullscreen.value)
    }

    @Test
    fun toggle_fromFloating_restoresFloating() {
        val windowState = WindowState(placement = WindowPlacement.Floating)
        val controller = DesktopFullscreenController(windowState)

        controller.toggle()
        controller.toggle()

        assertEquals(WindowPlacement.Floating, windowState.placement)
        assertFalse(controller.isFullscreen.value)
    }
}
