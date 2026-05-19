package net.subsloth.preferences

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted credential store backed by Android Keystore.
 *
 * Credentials are encrypted with AES/GCM/NoPadding using a Keystore-held key.
 * They are stored separately from DataStore preferences and account profile data.
 *
 * Compatible with API 26+. Uses direct platform Keystore APIs
 * (no deprecated AndroidX Security encrypted-preferences).
 */
private const val GCM_IV_SIZE = 12
private const val GCM_MIN_ENCODED_SIZE = GCM_IV_SIZE + 1

class CredentialStore(private val context: Context) {
    private val keystoreAlias = "subsloth_credentials_key"
    private val androidKeyStore = "AndroidKeyStore"
    private val prefsName = "subsloth_encrypted_credentials"

    /**
     * Saves encrypted credentials.
     */
    fun save(login: String, password: String) {
        val secretKey = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv

        val plaintext = "$login\u0000$password"
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val combined = iv + ciphertext
        val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)

        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit {
            putString("credentials", encoded)
        }
    }

    /**
     * Reads encrypted credentials, returning null if none exist
     * or if decryption fails (e.g., key changed or data corrupted).
     */
    fun read(): Pair<String, String>? {
        val encoded =
            context
                .getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                .getString("credentials", null) ?: return null

        return try {
            val decoded = Base64.decode(encoded, Base64.NO_WRAP)
            if (decoded.size < GCM_MIN_ENCODED_SIZE) return null

            val iv = decoded.copyOfRange(0, GCM_IV_SIZE)
            val ciphertext = decoded.copyOfRange(GCM_IV_SIZE, decoded.size)

            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val plaintext = cipher.doFinal(ciphertext)
            val parts = String(plaintext, Charsets.UTF_8).split("\u0000", limit = 2)
            if (parts.size != 2) null else Pair(parts[0], parts[1])
        } catch (e: java.security.GeneralSecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Clears all stored credentials.
     */
    fun clear() {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit {
            remove("credentials")
        }
    }

    /**
     * Returns true if credentials are currently stored.
     */
    fun exists(): Boolean = context
        .getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        .contains("credentials")

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(androidKeyStore)
        keyStore.load(null)
        keyStore.getEntry(keystoreAlias, null)?.let { entry ->
            return (entry as KeyStore.SecretKeyEntry).secretKey
        }

        val keyGenerator =
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                androidKeyStore,
            )
        val spec =
            KeyGenParameterSpec
                .Builder(
                    keystoreAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
