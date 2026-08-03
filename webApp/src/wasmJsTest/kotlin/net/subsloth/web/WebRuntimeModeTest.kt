package net.subsloth.web

import kotlinx.browser.localStorage
import kotlinx.coroutines.test.runTest
import net.subsloth.core.model.error.getOrNull
import net.subsloth.core.ui.CatalogKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebRuntimeModeTest {
    @Test
    fun demoIsTheOnlyPagesRuntimeMode() {
        assertEquals(listOf(WebRuntimeMode.Demo), WebRuntimeMode.entries)
    }

    @Test
    fun demoBannerExplainsDataAndCredentialBoundary() {
        assertTrue(DEMO_BANNER_TEXT.contains("Demo mode"))
        assertTrue(DEMO_BANNER_TEXT.contains("sample data"))
        assertTrue(DEMO_BANNER_TEXT.contains("credentials"))
    }

    @Test
    fun credentialKeyConstantsMatchExistingWebStore() {
        assertEquals("subsloth_credentials_data", DEMO_CREDENTIAL_DATA_KEY)
        assertEquals("subsloth_credentials_key", DEMO_CREDENTIAL_KEY_KEY)
    }

    @Test
    fun demoRuntimeUsesMockTransport() = runTest {
        val catalog = createWebDemoRuntime().listCatalog()

        assertEquals(3, catalog.getOrNull()?.size)
    }

    @Test
    fun demoStartupPreservesCredentialStorage() {
        localStorage.setItem(DEMO_CREDENTIAL_DATA_KEY, "seeded-data")
        localStorage.setItem(DEMO_CREDENTIAL_KEY_KEY, "seeded-key")

        try {
            val app = createWebDemoApp()
            try {
                assertEquals(WebRuntimeMode.Demo, app.mode)
                assertEquals(CatalogKey, app.startDestination)
                assertEquals(DEMO_BANNER_TEXT, app.bannerText)
                assertEquals("seeded-data", localStorage.getItem(DEMO_CREDENTIAL_DATA_KEY))
                assertEquals("seeded-key", localStorage.getItem(DEMO_CREDENTIAL_KEY_KEY))
            } finally {
                app.close()
            }
        } finally {
            localStorage.removeItem(DEMO_CREDENTIAL_DATA_KEY)
            localStorage.removeItem(DEMO_CREDENTIAL_KEY_KEY)
        }
    }
}
