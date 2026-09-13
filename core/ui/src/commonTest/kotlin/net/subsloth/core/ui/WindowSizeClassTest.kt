package net.subsloth.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowSizeClassTest {

    @Test
    fun `classifies widths at the material breakpoints`() {
        assertEquals(WindowWidthClass.COMPACT, windowWidthClassOf(0f))
        assertEquals(WindowWidthClass.COMPACT, windowWidthClassOf(599f))
        assertEquals(WindowWidthClass.MEDIUM, windowWidthClassOf(600f))
        assertEquals(WindowWidthClass.MEDIUM, windowWidthClassOf(839f))
        assertEquals(WindowWidthClass.EXPANDED, windowWidthClassOf(840f))
        assertEquals(WindowWidthClass.EXPANDED, windowWidthClassOf(2560f))
    }
}
