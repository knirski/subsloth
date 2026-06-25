package net.subsloth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.subsloth.core.domain.port.CurrentTimePort
import net.subsloth.core.model.error.DomainError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.network.media.CatalogRepository
import net.subsloth.core.network.media.api.Api
import net.subsloth.core.network.media.client.ClientFactory
import net.subsloth.database.SubSlothDatabase
import net.subsloth.database.createSubSlothDatabase
import net.subsloth.preferences.UserPreferences
import net.subsloth.catalog.HomeViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.io.File
import kotlin.time.Instant

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
    val clock: CurrentTimePort = object : CurrentTimePort {
        override fun now(): Instant = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        override fun millisNow(): Long = System.currentTimeMillis()
    }

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
     * Media API client.
     *
     * Created without credentials — no BasicAuth is configured. After the
     * user authenticates via [SessionPort], a follow-up change should
     * create a new authenticated client and swap it in so catalog sync
     * succeeds.
     */
    val api: Api by lazy {
        Api(ClientFactory.create())
    }

    /** Catalog repository combining API sync with Room caching. */
    val catalogRepository: CatalogRepository by lazy {
        CatalogRepository(
            api = api,
            catalogDao = cachedCatalogDao,
            userPreferences = userPreferences,
            clock = clock,
        )
    }
}

/**
 * Converts an [Outcome] to a [Result], bridging the domain error type
 * to the [Throwable]-based [Result] shape used by [HomeViewModel].
 *
 * The [DomainError] is preserved on [DomainErrorException.error] so
 * downstream callers can still dispatch on the typed error if needed.
 */
internal fun <T> Outcome<T>.toResult(): Result<T> = when (this) {
    is Outcome.Success -> Result.success(value)
    is Outcome.Failure -> Result.failure(DomainErrorException(error))
}

/**
 * Wraps a typed [DomainError] as a [Throwable] so it can cross the
 * `Result` boundary without losing the domain type.
 */
internal class DomainErrorException(val error: DomainError) :
    RuntimeException(error.toString())

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
                    syncCatalog = {
                        when (val outcome = catalogRepository.sync()) {
                            is Outcome.Success -> Result.success(Unit)
                            is Outcome.Failure -> Result.failure(DomainErrorException(outcome.error))
                        }
                    },
                    isCatalogStale = { catalogRepository.isStale() },
                ),
            ),
        )
}
