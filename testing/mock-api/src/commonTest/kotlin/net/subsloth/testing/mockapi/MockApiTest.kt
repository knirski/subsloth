package net.subsloth.testing.mockapi

import kotlinx.collections.immutable.persistentListOf
import net.subsloth.core.model.error.AuthError
import net.subsloth.core.model.error.NetworkError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.library.LibraryCollection
import net.subsloth.core.model.library.LibraryItem
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieDetails
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.ShowDetails
import net.subsloth.core.model.media.ShowSummary
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MockApiTest {
    @BeforeTest
    fun setUp() {
        MockApi.reset()
    }

    @AfterTest
    fun tearDown() {
        MockApi.reset()
    }

    @Test
    fun listCatalog_returns_non_empty_seed() {
        val result = MockApi.listCatalog()
        val items = result.getOrNull() ?: error("expected success, got $result")
        assertTrue(items.isNotEmpty(), "seed catalog should be non-empty")
        assertTrue(items.any { it is MovieSummary })
        assertTrue(items.any { it is ShowSummary })
    }

    @Test
    fun getDetails_known_movie_returns_movie_details() {
        val result = MockApi.getDetails(Media.MediaId.Movie(MovieId(1)))
        val details = result.getOrNull() ?: error("expected success, got $result")
        assertIs<MovieDetails>(details)
        assertEquals(MovieId(1), details.id.value)
        assertTrue(details.qualities.isNotEmpty())
        assertTrue(details.subtitles.isNotEmpty())
    }

    @Test
    fun getDetails_unknown_id_returns_UnexpectedResponse() {
        val result = MockApi.getDetails(Media.MediaId.Movie(MovieId(999)))
        assertEquals(Outcome.Failure(NetworkError.UnexpectedResponse), result)
    }

    @Test
    fun library_mutations_are_observable() {
        val initial = MockApi.listLibrary().getOrNull() ?: error("expected success")
        val beforeCount = initial.count { it.collection == LibraryCollection.HISTORY }

        MockApi.addToLibrary(Media.MediaId.Movie(MovieId(7)), LibraryCollection.HISTORY)
            .getOrNull() ?: error("expected success")

        val after = MockApi.listLibrary().getOrNull() ?: error("expected success")
        val afterCount = after.count { it.collection == LibraryCollection.HISTORY }
        assertEquals(beforeCount + 1, afterCount)
        assertTrue(
            after.any {
                it.mediaId == Media.MediaId.Movie(MovieId(7)) &&
                    it.collection == LibraryCollection.HISTORY
            },
        )
    }

    @Test
    fun removeFromLibrary_removes_the_item() {
        val target = Media.MediaId.Movie(MovieId(1))
        val before = MockApi.listLibrary().getOrNull() ?: error("expected success")
        assertTrue(before.any { it.mediaId == target })

        MockApi.removeFromLibrary(target).getOrNull() ?: error("expected success")

        val after = MockApi.listLibrary().getOrNull() ?: error("expected success")
        assertTrue(after.none { it.mediaId == target })
    }

    @Test
    fun login_then_expire_then_call_returns_SessionExpired() {
        val loginResult = MockApi.login(email = "user@example.com", password = "secret")
        assertEquals(Outcome.Success(Unit), loginResult)

        // Calls work while session is active.
        assertIs<Outcome.Success<*>>(MockApi.listCatalog())

        // Expire the session.
        MockApi.expireSession()

        // Next call fails with SessionExpired.
        val failedResult = MockApi.listCatalog()
        val failure = failedResult as? Outcome.Failure ?: error("expected Failure, got $failedResult")
        assertEquals(AuthError.SessionExpired, failure.error)
    }

    @Test
    fun login_after_expiry_reinstates_the_session() {
        MockApi.login("a@b.c", "pw")
        MockApi.expireSession()
        assertIs<Outcome.Failure>(MockApi.listCatalog())

        MockApi.login("a@b.c", "pw2")
        assertIs<Outcome.Success<*>>(MockApi.listCatalog())
    }

    @Test
    fun login_rejects_blank_credentials() {
        val result = MockApi.login(email = "", password = "pw")
        assertEquals(Outcome.Failure(AuthError.InvalidCredentials), result)
    }

    @Test
    fun downloads_returns_seed() {
        val list = MockApi.listDownloads().getOrNull() ?: error("expected success")
        assertTrue(list.isNotEmpty())
    }

    @Test
    fun sync_returns_success_when_session_active() {
        MockApi.login("u@e.c", "pw")
        val sync = MockApi.sync()
        assertEquals(Outcome.Success(Unit), sync)
    }

    @Test
    fun reset_restores_initial_state() {
        val movieId = Media.MediaId.Movie(MovieId(7))
        MockApi.addToLibrary(movieId, LibraryCollection.FAVORITES)
        MockApi.login("u@e.c", "pw")
        MockApi.expireSession()

        MockApi.reset()

        val library = MockApi.listLibrary().getOrNull() ?: error("expected success")
        assertTrue(library.none { it.mediaId == movieId }, "library should not contain the added item after reset")
        assertIs<Outcome.Success<*>>(MockApi.listCatalog())
    }

    @Test
    fun listProgress_returns_seed() {
        val progress = MockApi.listProgress().getOrNull() ?: error("expected success")
        assertTrue(progress.isNotEmpty())
    }
}

private fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.value
