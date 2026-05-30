package net.subsloth.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

expect fun createDataStorePreferences(
    name: String,
    corruptionHandler: ReplaceFileCorruptionHandler<Preferences>? = null,
    scope: CoroutineScope = CoroutineScope(SupervisorJob()),
): DataStore<Preferences>
