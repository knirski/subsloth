package net.subsloth

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import net.subsloth.core.network.media.client.ClientFactory
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
        // Poster artwork is fetched through Coil with the same Kodi-identity,
        // signed-URL client as downloads (wildcard Accept, no JSON response
        // validation, no auth) instead of Coil's anonymous default client.
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components { add(KtorNetworkFetcherFactory(httpClient = { ClientFactory.createForDownloads() })) }
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
