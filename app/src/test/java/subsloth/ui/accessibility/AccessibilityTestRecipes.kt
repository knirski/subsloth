package subsloth.ui.accessibility

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AccessibilityTestRecipes {

    @Test
    fun largeTextContrastState() {
        assertTrue(ContrastState.LargeText != ContrastState.Disabled)
    }

    @Test
    fun ratingAccessibilityLabel() {
        val label = AccessibilityLabels.rating(value = 7.5, max = 10.0)
        assertEquals("Rating 7 out of 10", label)
    }

    @Test
    fun progressAccessibilityLabel() {
        val label = AccessibilityLabels.progress(current = 3600, total = 7200)
        assertEquals("Progress 1h 0m of 2h 0m", label)
    }

    @Test
    fun accessibilityLabelsAreDefined() {
        assertTrue(AccessibilityLabels.Play.isNotEmpty())
        assertTrue(AccessibilityLabels.Pause.isNotEmpty())
        assertTrue(AccessibilityLabels.Search.isNotEmpty())
        assertTrue(AccessibilityLabels.Back.isNotEmpty())
    }
}
