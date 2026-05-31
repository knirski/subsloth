package subsloth.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import subsloth.core.model.identifier.AccountProfileKey

/**
 * Stores an app-local salt and derives account profile keys using HMAC-SHA256.
 *
 * The salt is generated once per app installation and persists across logouts.
 * It is never cleared by logout, reset preferences, or any user action.
 *
 * Profile keys are derived as:
 *   HMAC-SHA256(appLocalProfileSalt, normalizedLogin)
 *
 * where normalizedLogin is the login trimmed, NFC-normalized, and
 * lowercased for email-style logins.
 */
class AccountProfileStore(private val dataStore: DataStore<Preferences>) {
    private val saltKey = stringPreferencesKey("profile_salt")

    /**
     * Returns the current profile salt, generating and persisting a new one
     * if none exists yet.
     */
    suspend fun getOrCreateSalt(): String {
        val result =
            dataStore.edit { prefs ->
                if (prefs[saltKey] == null) {
                    prefs[saltKey] = generateSalt()
                }
            }
        return requireNotNull(result[saltKey]) {
            "Profile salt must exist after getOrCreateSalt"
        }
    }

    /**
     * Derives a non-reversible [AccountProfileKey] from the given login.
     *
     * Normalization steps:
     * 1. Trim leading/trailing whitespace
     * 2. Unicode Normalization Form C (NFC)
     * 3. Locale-independent lowercase for email-style logins
     *
     * The raw login is never stored as an identifier, Room key, DataStore key,
     * download path component, diagnostic field, or log value.
     */
    suspend fun deriveProfileKey(login: String): AccountProfileKey {
        val salt = getOrCreateSalt()
        val normalized = normalizeLogin(login)
        val hash = hmacSha256(salt.toByteArray(Charsets.UTF_8), normalized.toByteArray(Charsets.UTF_8))
        val hex = hash.joinToString("") { "%02x".format(it) }
        return AccountProfileKey(hex)
    }

    /** Indicates whether a profile salt has been generated. */
    suspend fun hasSalt(): Boolean = dataStore.data
        .map { prefs ->
            prefs[saltKey] != null
        }.first()

    /** Exposes the salt as a Flow for observation. */
    fun saltFlow(): Flow<String?> = dataStore.data.map { prefs ->
        prefs[saltKey]
    }
}

expect fun generateSalt(): String

expect fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray

expect fun normalizeLogin(login: String): String
