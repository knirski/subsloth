package net.subsloth

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.subsloth.core.domain.port.InMemorySessionState
import net.subsloth.core.domain.port.SessionPort
import net.subsloth.core.model.identifier.AccountProfileKey
import net.subsloth.database.entity.FavoriteEntity
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Instrumented tests exercising [AppContainer]'s real, production-wired
 * `SettingsViewModel` cleanup lambdas ([AppContainer.clearPreferences],
 * [AppContainer.clearLibrary]) against real storage (Room, DataStore), and
 * a "no no-op production binding" check on [AppContainer.sessionPort].
 *
 * Reuses the single [AppContainer] instance [SubSlothApplication.onCreate]
 * already constructed for this process — rather than constructing a second
 * `AppContainer(context)` — because `PreferenceDataStoreFactory`'s backing
 * `DataStore` throws `IllegalStateException: There are multiple DataStores
 * active for this file` if two `DataStore` instances are opened against the
 * same file (`subsloth.preferences_pb`) within one process, and the real
 * `SubSlothApplication.onCreate` already pre-warms that DataStore
 * eagerly. Reusing the app's own singleton container is at least as
 * faithful to "the real production wiring" as constructing a redundant
 * second instance would be.
 *
 * Uses arbitrary, test-only [AccountProfileKey] values (never `"default"`,
 * the production anonymous-session fallback) wherever the cleanup lambda
 * accepts an explicit key, so this can't collide with real app data on the
 * shared device/emulator. [AppContainer.clearLibrary] is the one exception:
 * it derives its scope from [AppContainer.currentProfileKey] (the *current*
 * session's key) rather than taking a parameter, so that test reads
 * whatever key is actually active (this container's `sessionPort` is never
 * authenticated by any test in this suite, so it is consistently
 * `"default"`) instead of hard-coding it.
 */
@RunWith(AndroidJUnit4::class)
class LogoutCleanupInstrumentedTest {

    private val container: AppContainer
        get() = (ApplicationProvider.getApplicationContext<SubSlothApplication>()).container

    private val prefsTestProfileKey = AccountProfileKey("logout-cleanup-test-prefs")

    @After
    fun tearDown() = runBlocking {
        // Best-effort cleanup regardless of where an assertion failure left
        // things, so this test can't bleed into any other test sharing the
        // same production AppContainer/storage.
        container.userPreferences.clearProfilePreferences(prefsTestProfileKey)
        val libraryKey = container.currentProfileKey().value
        container.database.favoriteDao().getAllForProfile(libraryKey).first().forEach {
            container.database.favoriteDao().delete(it)
        }
    }

    private suspend fun awaitUntil(timeoutMs: Long = 5_000, intervalMs: Long = 50, condition: suspend () -> Boolean) {
        val deadlineMillis = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadlineMillis) {
            if (condition()) return
            delay(intervalMs)
        }
        assertTrue(condition(), "Condition not met within ${timeoutMs}ms")
    }

    @Test
    fun clearPreferences_onlyClearsPreferencesScope_leavesLibraryUntouched() = runBlocking {
        // Seed a preference value under the test profile key.
        container.userPreferences.setQuality(prefsTestProfileKey, "1080p")
        assertEquals("1080p", container.userPreferences.quality(prefsTestProfileKey).first())

        // Seed a cross-scope control row under the SAME profile key, in the
        // library scope, that clearPreferences must NOT touch.
        val controlFavorite = FavoriteEntity(
            profileKey = prefsTestProfileKey.value,
            contentId = "9001",
            contentType = "movie",
        )
        container.database.favoriteDao().upsert(controlFavorite)

        container.clearPreferences(prefsTestProfileKey)

        awaitUntil { container.userPreferences.quality(prefsTestProfileKey).first() == null }
        assertNull(container.userPreferences.quality(prefsTestProfileKey).first())

        // Library scope for the same profile key is untouched.
        val remainingFavorites = container.database.favoriteDao().getAllForProfile(prefsTestProfileKey.value).first()
        assertTrue(remainingFavorites.any { it.contentId == "9001" }, "clearPreferences must not touch library scope")

        // Cleanup the control row this test seeded.
        container.database.favoriteDao().delete(controlFavorite)
    }

    @Test
    fun clearLibrary_onlyClearsLibraryScope_leavesPreferencesUntouched() = runBlocking {
        // clearLibrary() has no profileKey parameter — it derives the active
        // profile from the container's current session, so this test reads
        // that same key rather than assuming "default".
        val libraryKey = AccountProfileKey(container.currentProfileKey().value)

        val favorite = FavoriteEntity(
            profileKey = libraryKey.value,
            contentId = "9002",
            contentType = "movie",
        )
        container.database.favoriteDao().upsert(favorite)
        assertTrue(
            container.database.favoriteDao().getAllForProfile(libraryKey.value).first().any { it.contentId == "9002" },
        )

        // Seed a cross-scope control preference under the SAME profile key
        // that clearLibrary must NOT touch.
        container.userPreferences.setQuality(libraryKey, "720p")
        assertEquals("720p", container.userPreferences.quality(libraryKey).first())

        container.clearLibrary()

        awaitUntil {
            container.database.favoriteDao().getAllForProfile(libraryKey.value).first().none { it.contentId == "9002" }
        }
        assertFalse(
            container.database.favoriteDao().getAllForProfile(libraryKey.value).first().any { it.contentId == "9002" },
        )

        // Preferences scope for the same profile key is untouched.
        assertEquals("720p", container.userPreferences.quality(libraryKey).first())

        // Cleanup the control preference this test seeded.
        container.userPreferences.setQuality(libraryKey, null)
    }

    /**
     * Task 9.2: proves production Android startup constructs a real
     * [AndroidSessionState] — not [InMemorySessionState] (the in-memory
     * default `RootContainerViewModel` falls back to when no `sessionPort`
     * is supplied, per its doc).
     *
     * [AppContainer.libraryPortAdapter]/[AppContainer.downloadController]
     * are deliberately not asserted here: both are exposed as concrete
     * types ([net.subsloth.database.LibraryPortAdapter] /
     * [net.subsloth.core.media.download.DownloadController]), not as an
     * interface a no-op stub could instead satisfy, so asserting their type
     * would be tautological — there is no in-memory/no-op alternative they
     * could have been bound to instead. Only [AppContainer.sessionPort] is
     * exposed as its interface type ([SessionPort]), where a no-op
     * substitution is actually possible and worth ruling out.
     */
    @Test
    fun productionContainer_bindsRealSessionPort_notInMemoryStub() {
        val sessionPort: SessionPort = container.sessionPort
        assertNotNull(sessionPort)
        assertFalse(sessionPort is InMemorySessionState)
        assertIs<AndroidSessionState>(sessionPort)
    }
}
