package net.subsloth.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope

/**
 * DataStore for wasmJs using browser localStorage.
 *
 * Okio 3.17 does not provide `FileSystem.SYSTEM` for browser wasmJs
 * (browsers have no synchronous filesystem API), so
 * [PreferenceDataStoreFactory.createWithPath] cannot be used.
 *
 * Instead, [LocalStorageDataStore] persists preferences as a single JSON
 * blob in `localStorage`, which survives page reloads.
 *
 * @see <a href="https://github.com/knirski/subsloth/issues/71">Issue #71</a>
 */
actual fun createDataStorePreferences(
    name: String,
    corruptionHandler: ReplaceFileCorruptionHandler<Preferences>?,
    scope: CoroutineScope,
): DataStore<Preferences> = LocalStorageDataStore(storageKey = "subsloth_preferences_$name")
