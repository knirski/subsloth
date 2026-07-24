package net.subsloth.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope

/**
 * Android actual of [createDataStorePreferences].
 *
 * Uses [androidx.datastore.preferences.preferencesDataStoreFile], which
 * resolves the backing file under `filesDir/datastore/$name.preferences_pb`
 * — the same `datastore/` location already excluded from Auto Backup and
 * device-to-device transfer in `backup_rules.xml` / `data_extraction_rules.xml`.
 *
 * Requires [AndroidContext] to have been initialised in
 * [android.app.Application.onCreate].
 */
actual fun createDataStorePreferences(
    name: String,
    corruptionHandler: ReplaceFileCorruptionHandler<Preferences>?,
    scope: CoroutineScope,
): DataStore<Preferences> {
    val context = AndroidContext.requireApplicationContext()
    return PreferenceDataStoreFactory.create(
        corruptionHandler = corruptionHandler,
        scope = scope,
        produceFile = { context.preferencesDataStoreFile(name) },
    )
}
