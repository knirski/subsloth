package net.subsloth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import net.subsloth.core.data.media.CatalogRepository
import net.subsloth.core.domain.port.Session
import net.subsloth.core.domain.port.SessionPort
import net.subsloth.core.network.media.api.Api
import net.subsloth.core.network.media.client.ClientFactory
import net.subsloth.database.SubSlothDatabase
import net.subsloth.database.createSubSlothDatabase
import net.subsloth.preferences.CredentialStore
import net.subsloth.preferences.CredentialsStoreAdapter
import net.subsloth.preferences.UserPreferences
import net.subsloth.catalog.HomeViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.io.File
import kotlin.time.Clock

/**
 * Application-level dependency container.
 *
 * Initialised once in [SubSlothApplication.onCreate] and exposed via
 * the Application instance. Survives configuration changes and
 * Activity recreation. This is a manual-service-locator pattern
 * (no DI framework) — only dependencies that need to outlive a
 * screen or Activity live here.
 */
class AppContainer(context: Context) {
    /** System clock implementation. */
    val clock: Clock = Clock.System

    /**
     * Process-lifetime coroutine scope. Never cancelled — [AppContainer]
     * lives as long as [SubSlothApplication], so a [SupervisorJob]-backed
     * scope that outlives every launch is the accepted pattern here (see
     * [dataStore]'s own scope below), not a leak.
     */
    private val containerScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** DataStore for user preferences. */
    val dataStore: DataStore<Preferences> by lazy {
        val appContext = context.applicationContext
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { File(appContext.filesDir, "subsloth.preferences_pb") },
        )
    }

    /** User preferences backed by DataStore. */
    val userPreferences: UserPreferences by lazy {
        UserPreferences(dataStore)
    }

    /** Room database for cached catalog, library, and playback state. */
    val database: SubSlothDatabase by lazy {
        createSubSlothDatabase("subsloth_db")
    }

    /** Cached catalog DAO. */
    val cachedCatalogDao by lazy { database.cachedCatalogDao() }

    /**
     * Production session port. Persists credentials via the Keystore-backed
     * [CredentialsStoreAdapter]/[CredentialStore] and validates them against
     * the same API base URL resolution [resolveApiBaseUrl] uses.
     *
     * Kept as the concrete [AndroidSessionState] type privately so [init]
     * below can call its [AndroidSessionState.recover] — not part of the
     * [SessionPort] interface — exactly once at cold start; [sessionPort]
     * exposes it to the rest of the app as the plain [SessionPort] contract.
     */
    private val androidSessionState = AndroidSessionState(
        credentialsPort = CredentialsStoreAdapter(CredentialStore()),
        baseUrlProvider = { resolveApiBaseUrl() },
        clock = clock,
    )

    /** [SessionPort] state starts as `Anonymous` until cold-start recovery (see [init]) completes. */
    val sessionPort: SessionPort = androidSessionState

    /**
     * Media API client reflecting the current [sessionPort] session:
     * authenticated (BasicAuth) when [Session.Authenticated], anonymous
     * otherwise. Starts as an anonymous client built against
     * [ClientFactory]'s default base URL and is replaced — together with
     * [catalogRepository], which wraps it — every time [sessionPort]'s
     * state actually changes (see the `collect` launch in `init` below).
     */
    @Volatile
    private var currentApi: Api = Api(ClientFactory.create())

    /** Catalog repository combining API sync with Room caching. */
    @Volatile
    private var currentCatalogRepository: CatalogRepository = buildCatalogRepository(currentApi)

    val api: Api get() = currentApi

    val catalogRepository: CatalogRepository get() = currentCatalogRepository

    init {
        // Cold-start session recovery — invoked exactly once, unconditionally,
        // as part of container construction.
        containerScope.launch { androidSessionState.recover() }

        // Rebuild the authenticated client (and the repository wrapping it)
        // whenever the session's credentials actually change. StateFlow only
        // emits on a structural change, so this does not rebuild on every
        // access or poll for changes.
        containerScope.launch {
            sessionPort.state.collect { session ->
                val previousApi = currentApi
                val newApi = buildApi(session)
                currentApi = newApi
                currentCatalogRepository = buildCatalogRepository(newApi)
                // Close the superseded client only after the new one is fully
                // swapped in, so nothing still references it as "current".
                previousApi.close()
            }
        }
    }

    /**
     * Resolves the API base URL with the same precedence
     * `MainActivity.kt`'s `readApiBaseUrl` uses for [net.subsloth.auth.LoginViewModel]:
     * the persisted [UserPreferences.apiBaseUrl] value, unless it is still
     * the default and a non-empty [BuildConfig.SUBSLOTH_API_BASE_URL] override
     * is configured, in which case the build-config value wins.
     */
    private suspend fun resolveApiBaseUrl(): String {
        val stored = userPreferences.apiBaseUrl().first()
        return if (stored == UserPreferences.DEFAULT_API_BASE_URL && BuildConfig.SUBSLOTH_API_BASE_URL.isNotEmpty()) {
            BuildConfig.SUBSLOTH_API_BASE_URL
        } else {
            stored
        }
    }

    private suspend fun buildApi(session: Session): Api {
        val baseUrl = resolveApiBaseUrl()
        val client = when (session) {
            is Session.Authenticated -> ClientFactory.create(
                login = session.credentials.login,
                password = session.credentials.password,
                baseUrl = baseUrl,
            )

            Session.Anonymous -> ClientFactory.create(baseUrl = baseUrl)
        }
        return Api(client)
    }

    private fun buildCatalogRepository(api: Api): CatalogRepository = CatalogRepository(
        api = api,
        catalogDao = cachedCatalogDao,
        userPreferences = userPreferences,
        clock = clock,
    )
}

/**
 * [ViewModelProvider.Factory] for [HomeViewModel] that receives its
 * [CatalogRepository] dependency explicitly rather than capturing it
 * from the composable scope.
 */
internal class HomeViewModelFactory(
    private val catalogRepository: CatalogRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        requireNotNull(
            modelClass.cast(
                HomeViewModel(
                    catalogItems = { contentType -> catalogRepository.catalogItems(contentType) },
                    syncCatalog = { catalogRepository.sync() },
                    isCatalogStale = { catalogRepository.isStale() },
                ),
            ),
        )
}
