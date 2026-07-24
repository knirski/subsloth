package net.subsloth

import android.app.Application
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
