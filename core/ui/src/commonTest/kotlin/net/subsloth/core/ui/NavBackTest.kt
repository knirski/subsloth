package net.subsloth.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavBackTest {

    @Test
    fun `mid-stack back pops the last destination`() {
        val backStack = mutableListOf<AppNavKey>(CatalogKey, LibraryKey, MovieDetailKey("1"))
        var exited = false

        navigateBack(backStack) { exited = true }

        assertEquals(listOf<AppNavKey>(CatalogKey, LibraryKey), backStack)
        assertFalse(exited)
    }

    @Test
    fun `root offline library back exits to login`() {
        val backStack = mutableListOf<AppNavKey>(OfflineLibraryKey)
        var exited = false

        navigateBack(backStack) { exited = true }

        assertEquals(listOf<AppNavKey>(OfflineLibraryKey), backStack)
        assertTrue(exited)
    }

    @Test
    fun `root catalog back is a no-op`() {
        val backStack = mutableListOf<AppNavKey>(CatalogKey)
        var exited = false

        navigateBack(backStack) { exited = true }

        assertEquals(listOf<AppNavKey>(CatalogKey), backStack)
        assertFalse(exited)
    }
}
