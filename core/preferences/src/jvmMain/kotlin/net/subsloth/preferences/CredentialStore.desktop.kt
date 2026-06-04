package net.subsloth.preferences

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

actual class CredentialStore {
    actual fun save(login: String, password: String) {
        val data = "$login\u0000$password".toByteArray(Charsets.UTF_8)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ct = cipher.doFinal(data)
        val blob = Base64.getEncoder().encodeToString(salt + iv + ct)
        runBlocking {
            dataStore.edit { prefs ->
                prefs[keyCredentials] = blob
            }
        }
    }

    actual fun read(): Pair<String, String>? = runBlocking {
        val prefs = dataStore.data.first()
        val blob = prefs[keyCredentials] ?: return@runBlocking null
        try {
            val raw = Base64.getDecoder().decode(blob)
            val salt = raw.copyOfRange(0, 16)
            val iv = raw.copyOfRange(16, 28)
            val ct = raw.copyOfRange(28, raw.size)
            val key = deriveKey(salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val parts = String(cipher.doFinal(ct), Charsets.UTF_8).split("\u0000", limit = 2)
            if (parts.size == 2) Pair(parts[0], parts[1]) else null
        } catch (_: Exception) {
            null
        }
    }

    actual fun clear() {
        runBlocking {
            dataStore.edit { prefs ->
                prefs.remove(keyCredentials)
            }
        }
    }

    actual fun exists(): Boolean = runBlocking {
        dataStore.data.first().contains(keyCredentials)
    }

    private fun deriveKey(salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(machineId.toCharArray(), salt, 100_000, 256)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    private val keyCredentials = stringPreferencesKey("encrypted_credentials")

    private companion object {
        val machineId by lazy { resolveMachineId().getOrThrow() }

        val dataStore by lazy {
            createDataStorePreferences(
                name = "credential_store",
                scope = CoroutineScope(Dispatchers.IO),
            )
        }

        fun resolveMachineId(): Result<String> = runCatching {
            when {
                System.getProperty("os.name")?.lowercase()?.contains("linux") == true ->
                    File("/etc/machine-id").readText().trim().ifEmpty {
                        File("/var/lib/dbus/machine-id").readText().trim()
                    }

                System.getProperty("os.name")?.lowercase()?.contains("mac") == true ->
                    ProcessBuilder("ioreg", "-rd1", "-c", "IOPlatformExpertDevice")
                        .redirectErrorStream(true)
                        .start()
                        .inputStream.bufferedReader().readText()
                        .lines()
                        .first { it.contains("IOPlatformUUID") }
                        .substringAfter("= \"")
                        .substringBeforeLast("\"")

                else ->
                    ProcessBuilder(
                        "reg",
                        "query",
                        "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Cryptography",
                        "/v",
                        "MachineGuid",
                    )
                        .redirectErrorStream(true)
                        .start()
                        .inputStream.bufferedReader().readText()
                        .lines()
                        .first { it.contains("MachineGuid") }
                        .substringAfter("REG_SZ")
                        .trim()
            }
        }
    }
}
