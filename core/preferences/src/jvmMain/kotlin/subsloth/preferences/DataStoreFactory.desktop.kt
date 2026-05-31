package subsloth.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

actual fun createDataStorePreferences(
    name: String,
    corruptionHandler: ReplaceFileCorruptionHandler<Preferences>?,
    scope: CoroutineScope,
): DataStore<Preferences> {
    val dataDir: Path = resolveAppDataDir().toPath()
    FileSystem.SYSTEM.createDirectories(dataDir)
    return PreferenceDataStoreFactory.createWithPath(
        corruptionHandler = corruptionHandler,
        scope = scope,
        produceFile = { dataDir.resolve("$name.preferences_pb") },
    )
}

private fun resolveAppDataDir(): String {
    val osName = System.getProperty("os.name").lowercase()
    val userHome = System.getProperty("user.home")
    return when {
        osName.contains("linux") || osName.contains("mac") ->
            "$userHome/.local/share/subsloth"

        osName.contains("windows") ->
            "${System.getenv("APPDATA") ?: "${System.getProperty("user.home")}\\AppData\\Roaming"}\\subsloth"

        else ->
            "$userHome/.subsloth"
    }
}
