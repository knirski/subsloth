package subsloth.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.UUID

/**
 * Creates an ephemeral [DataStore] for unit testing backed by a unique temp file.
 * Each call produces an isolated DataStore so tests do not share state.
 */
fun createTempFileDataStore(): DataStore<Preferences> {
    val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val tempFile = File.createTempFile("test_datastore_${UUID.randomUUID()}", ".preferences_pb")
    tempFile.deleteOnExit()
    return PreferenceDataStoreFactory.create(
        scope = testScope,
        produceFile = { tempFile },
    )
}
