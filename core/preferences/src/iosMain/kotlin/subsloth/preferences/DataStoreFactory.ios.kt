package subsloth.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

actual fun createDataStorePreferences(
    name: String,
    corruptionHandler: ReplaceFileCorruptionHandler<Preferences>?,
    scope: CoroutineScope,
): DataStore<Preferences> {
    val documentsDir: String =
        NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true,
        ).first() as String
    val appDir = "$documentsDir/subsloth"
    val path = appDir.toPath()
    FileSystem.SYSTEM.createDirectories(path)
    return PreferenceDataStoreFactory.createWithPath(
        corruptionHandler = corruptionHandler,
        scope = scope,
        produceFile = { path.resolve("$name.preferences_pb") },
    )
}
