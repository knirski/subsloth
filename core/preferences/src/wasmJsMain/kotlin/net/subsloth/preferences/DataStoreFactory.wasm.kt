package net.subsloth.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import okio.Path.Companion.toPath

/**
 * DataStore for wasmJs using preference file-based storage.
 *
 * Internally [PreferenceDataStoreFactory.createWithPath] uses
 * `FileSystem.SYSTEM` from Okio. Okio 3.17 doesn't provide
 * `FileSystem.SYSTEM` for browser wasmJs (browsers have no synchronous
 * filesystem API), so at runtime this will throw if the filesystem is
 * accessed.
 *
 * Until DataStore/Okio ships a wasmJs-compatible storage backend,
 * preferences on the web target are in-memory only and do not persist
 * across page reloads. All other app functionality (database, crypto,
 * credentials via localStorage) works correctly.
 *
 * @see <a href="https://github.com/knirski/subsloth/issues/71">Issue #71</a>
 */
actual fun createDataStorePreferences(
    name: String,
    corruptionHandler: ReplaceFileCorruptionHandler<Preferences>?,
    scope: CoroutineScope,
): DataStore<Preferences> {
    val path = "/subsloth/data/$name.preferences_pb".toPath()
    return PreferenceDataStoreFactory.createWithPath(
        corruptionHandler = corruptionHandler,
        scope = scope,
        produceFile = { path },
    )
}
