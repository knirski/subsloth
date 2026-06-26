package net.subsloth.preferences

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import net.subsloth.core.domain.port.Credentials
import net.subsloth.core.domain.port.CredentialsPort
import net.subsloth.core.model.error.DecodeError
import net.subsloth.core.model.error.Outcome

/**
 * Production implementation of [CredentialsPort].
 *
 * Delegates to [CredentialStore] for platform-specific encrypted storage
 * (Android Keystore, Java KeyStore, or Web Crypto API).
 */
class CredentialsStoreAdapter(private val store: CredentialStore) : CredentialsPort {

    private val log = Logger.withTag("CredentialsStoreAdapter")

    override suspend fun save(login: String, password: String): Outcome<Unit> = try {
        store.save(login, password)
        Outcome.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.e(e) { "Failed to save credentials" }
        Outcome.Failure(DecodeError.SerializationFailed)
    }

    override suspend fun read(): Outcome<Credentials?> = try {
        val pair = store.read()
        Outcome.Success(pair?.let { (login, password) -> Credentials(login, password) })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.e(e) { "Failed to read credentials" }
        Outcome.Failure(DecodeError.SerializationFailed)
    }

    override suspend fun clear(): Outcome<Unit> = try {
        store.clear()
        Outcome.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.e(e) { "Failed to clear credentials" }
        Outcome.Failure(DecodeError.SerializationFailed)
    }
}
