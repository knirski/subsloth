package net.subsloth.core.domain.port

/**
 * Port for reading and writing encrypted credentials.
 *
 * Implementations are provided by the Android shell using
 * Android Keystore-backed encryption.
 */
interface CredentialsPort {
    /** Saves encrypted login credentials. */
    suspend fun save(
        login: String,
        password: String,
    ): Result<Unit>

    /** Reads the saved login credentials, or returns `null` if none exist. */
    suspend fun read(): Result<Credentials?>

    /** Clears all saved credentials. */
    suspend fun clear(): Result<Unit>
}

/** Login credentials for the Media service. */
data class Credentials(
    val login: String,
    val password: String,
)
