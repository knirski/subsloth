@file:Suppress("DEPRECATION")

package net.subsloth.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android actual of [CredentialStore].
 *
 * Backed by `androidx.security.crypto`'s [EncryptedSharedPreferences], which
 * generates and stores an AES256-GCM master key in the Android Keystore
 * ([MasterKey.KeyScheme.AES256_GCM]) and uses it to encrypt both the
 * preference keys (AES256-SIV) and values (AES256-GCM) of the underlying
 * SharedPreferences file.
 *
 * The SharedPreferences file is named [PREFS_FILE_NAME] (on disk:
 * `shared_prefs/subsloth_encrypted_credentials.xml`) to match the exclusion
 * rules already declared in `androidApp/src/main/res/xml/backup_rules.xml`
 * and `data_extraction_rules.xml`, which keep it out of Auto Backup and
 * device-to-device transfer.
 *
 * `MasterKey`/`EncryptedSharedPreferences` are annotated `@Deprecated` as of
 * `androidx.security:security-crypto:1.1.0` (AndroidX has not published a
 * stable replacement); the `@file:Suppress("DEPRECATION")` above silences
 * that under this module's `allWarningsAsErrors` build. They remain the
 * standard, Keystore-backed way to encrypt a SharedPreferences file.
 *
 * Requires [AndroidContext] to have been initialised in
 * [android.app.Application.onCreate].
 */
actual class CredentialStore {
    private val context: Context = AndroidContext.requireApplicationContext()

    private val prefs: SharedPreferences by lazy { createEncryptedPrefs(context) }

    actual suspend fun save(login: String, password: String) {
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(KEY_LOGIN, login)
                .putString(KEY_PASSWORD, password)
                .commit()
        }
    }

    actual suspend fun read(): Pair<String, String>? = withContext(Dispatchers.IO) {
        val login = prefs.getString(KEY_LOGIN, null)
        val password = prefs.getString(KEY_PASSWORD, null)
        if (login != null && password != null) login to password else null
    }

    actual suspend fun clear() {
        withContext(Dispatchers.IO) {
            prefs.edit()
                .remove(KEY_LOGIN)
                .remove(KEY_PASSWORD)
                .commit()
        }
    }

    actual suspend fun exists(): Boolean = withContext(Dispatchers.IO) {
        prefs.contains(KEY_LOGIN) && prefs.contains(KEY_PASSWORD)
    }

    private companion object {
        const val PREFS_FILE_NAME = "subsloth_encrypted_credentials"
        const val KEY_LOGIN = "login"
        const val KEY_PASSWORD = "password"

        fun createEncryptedPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            return EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
