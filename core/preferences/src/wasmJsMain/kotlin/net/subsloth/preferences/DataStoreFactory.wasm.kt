package net.subsloth.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import okio.Path.Companion.toPath

actual fun createDataStorePreferences(
    name: String,
    corruptionHandler: ReplaceFileCorruptionHandler<Preferences>?,
    scope: CoroutineScope,
): DataStore<Preferences> {
    val path = "/subsloth/data/$name.preferences_pb".toPath()
    // Note: FileSystem.SYSTEM is unavailable on wasmJs in Okio 3.17.
    // PreferenceDataStoreFactory.createWithPath uses it internally.
    // At runtime the DataStore will use Okio's wasm filesystem fallback.
    return PreferenceDataStoreFactory.createWithPath(
        corruptionHandler = corruptionHandler,
        scope = scope,
        produceFile = { path },
    )
}
