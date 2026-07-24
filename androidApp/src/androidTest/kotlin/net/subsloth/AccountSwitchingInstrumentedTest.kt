package net.subsloth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.subsloth.core.domain.port.Credentials
import net.subsloth.core.domain.port.Session
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.library.LibraryCollection
import net.subsloth.core.model.library.LibraryItem
import net.subsloth.core.model.media.Media
import net.subsloth.database.LibraryPortAdapter
import net.subsloth.database.SubSlothDatabase
import net.subsloth.database.createSubSlothDatabase
import net.subsloth.preferences.AccountProfileStore
import net.subsloth.preferences.CredentialStore
import net.subsloth.preferences.CredentialsStoreAdapter
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import net.subsloth.database.AndroidContext as DatabaseAndroidContext

/**
 * Instrumented test for this change's "Account Switching" `auth-security`
 * spec scenario: logging in as a second account must not see the first
 * account's library data, without deleting it (see "Persistence Scope
 * Separation").
 *
 * Uses the same real [AndroidSessionState]/[CredentialsStoreAdapter] chain
 * as `AndroidSessionStateInstrumentedTest.kt`, network-mocked via
 * [MockEngine], paired with a real [LibraryPortAdapter] over a dedicated
 * Room database file (`account_switch_test_db`, distinct from the app's
 * production `subsloth_db`) so favorites persist and partition exactly as
 * they do in production, without touching [AppContainer]'s shared
 * production database/DataStore instances (see
 * [LogoutCleanupInstrumentedTest]'s doc for why a *second* full
 * `AppContainer`/DataStore isn't constructed in this suite either).
 *
 * The [AndroidSessionState] under test derives `userId` via a real
 * [AccountProfileStore], wrapping the process-wide [AppContainer.dataStore]
 * instance (through [SubSlothApplication]'s already-constructed container)
 * rather than opening a second `DataStore` against the same
 * `subsloth.preferences_pb` file, which throws at runtime.
 */
@RunWith(AndroidJUnit4::class)
class AccountSwitchingInstrumentedTest {
    private val dbName = "account_switch_test_db"
    private lateinit var database: SubSlothDatabase
    private lateinit var credentialsAdapter: CredentialsStoreAdapter
    private lateinit var sessionState: AndroidSessionState
    private lateinit var libraryPortAdapter: LibraryPortAdapter

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // AppContainer/SubSlothApplication.onCreate already initialises this
        // process-wide singleton with the same context; re-init is a no-op
        // in production but keeps this test self-sufficient if run in
        // isolation.
        DatabaseAndroidContext.init(context)

        database = createSubSlothDatabase(dbName)
        credentialsAdapter = CredentialsStoreAdapter(CredentialStore())
        val accountProfileStore =
            AccountProfileStore(
                ApplicationProvider.getApplicationContext<SubSlothApplication>().container.dataStore,
            )
        sessionState =
            AndroidSessionState(
                credentialsPort = credentialsAdapter,
                baseUrlProvider = { "http://localhost/" },
                accountProfileStore = accountProfileStore,
                engineOverride = validLoginEngine(),
            )
        libraryPortAdapter =
            LibraryPortAdapter(
                favoriteDao = database.favoriteDao(),
                localLibraryDao = database.localLibraryRecordDao(),
                sessionPort = sessionState,
            )
    }

    @After
    fun tearDown() =
        runTest {
            credentialsAdapter.clear()
            database.close()
            ApplicationProvider.getApplicationContext<Context>().deleteDatabase(dbName)
        }

    private fun validLoginEngine(): MockEngine =
        MockEngine { _ ->
            respond(
                content = """{"movies":[]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

    @Test
    fun switchingAccounts_isolatesLibraryDataByProfileKey() =
        runTest {
            val aliceFavorite =
                LibraryItem(
                    mediaId = Media.MediaId.Movie(MovieId(1)),
                    collection = LibraryCollection.FAVORITES,
                    addedAtEpochSeconds = Instant.fromEpochSeconds(0),
                    sortOrder = 0,
                )

            // Log in as account A and add a favorite scoped to A's profile key.
            val aliceOpen = sessionState.open(Credentials("alice@test.com", "pw"))
            assertIs<Outcome.Success<Unit>>(aliceOpen)
            val aliceSession = sessionState.state.value
            assertIs<Session.Authenticated>(aliceSession)
            val aliceUserId = aliceSession.userId

            val addOutcome = libraryPortAdapter.addToLibrary(aliceFavorite)
            assertIs<Outcome.Success<Unit>>(addOutcome)

            val aliceLibrary = libraryPortAdapter.listLibrary()
            assertIs<Outcome.Success<List<LibraryItem>>>(aliceLibrary)
            assertTrue(aliceLibrary.value.any { it.mediaId == aliceFavorite.mediaId })

            // Log out, then log in as account B with different credentials.
            sessionState.close()
            assertEquals(Session.Anonymous, sessionState.state.value)

            val bobOpen = sessionState.open(Credentials("bob@test.com", "different-pw"))
            assertIs<Outcome.Success<Unit>>(bobOpen)
            val bobSession = sessionState.state.value
            assertIs<Session.Authenticated>(bobSession)
            val bobUserId = bobSession.userId

            // B's derived profile key differs from A's.
            assertNotEquals(aliceUserId, bobUserId)

            // B does not see A's favorite.
            val bobLibrary = libraryPortAdapter.listLibrary()
            assertIs<Outcome.Success<List<LibraryItem>>>(bobLibrary)
            assertTrue(bobLibrary.value.none { it.mediaId == aliceFavorite.mediaId })

            // A's favorite still exists in storage under A's key — switching
            // accounts scopes data away, it does not delete it (Persistence
            // Scope Separation).
            val aliceFavoritesAfterSwitch = database.favoriteDao().getAllForProfile(aliceUserId).first()
            assertTrue(aliceFavoritesAfterSwitch.any { it.contentId == "1" })
        }
}
