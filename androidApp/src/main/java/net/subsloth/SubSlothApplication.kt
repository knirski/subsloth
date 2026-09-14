package net.subsloth

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.subsloth.database.AndroidContext as DatabaseAndroidContext
import net.subsloth.preferences.AndroidContext as PreferencesAndroidContext

class SubSlothApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Poster artwork is fetched through Coil; the Ktor network fetcher is
        // added explicitly so the loader does not depend on JVM service
        // loading. Artwork URLs are signed, so no auth is required.
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components { add(KtorNetworkFetcherFactory()) }
                .build()
        }
        DatabaseAndroidContext.init(this)
        PreferencesAndroidContext.init(this)
        container = AppContainer(this)
        // Pre-warm heavy singletons (Room DB, DataStore) off the main
        // thread so they aren't lazily initialized during composition.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            container.database
            container.dataStore
        }
    }
}
