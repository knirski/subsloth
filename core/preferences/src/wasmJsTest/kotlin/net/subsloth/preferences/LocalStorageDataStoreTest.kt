package net.subsloth.preferences

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LocalStorageDataStoreTest {
    private val storageKey = "subsloth_preferences_test"
    private val apiKey = stringPreferencesKey("api_base_url")

    @AfterTest
    fun cleanUp() {
        localStorage.removeItem(storageKey)
    }

    @Test
    fun `updateData writes a json blob to localStorage`() = runTest {
        val store = LocalStorageDataStore(storageKey)

        store.edit { prefs -> prefs[apiKey] = "https://example.test/api/v2/" }

        assertNotNull(localStorage.getItem(storageKey), "expected a persisted JSON blob")
    }

    @Test
    fun `persisted value is read back by a fresh instance`() = runTest {
        val store = LocalStorageDataStore(storageKey)
        store.edit { prefs -> prefs[apiKey] = "https://example.test/api/v2/" }

        val reloaded = LocalStorageDataStore(storageKey)
        assertEquals("https://example.test/api/v2/", reloaded.data.first()[apiKey])
    }
}
