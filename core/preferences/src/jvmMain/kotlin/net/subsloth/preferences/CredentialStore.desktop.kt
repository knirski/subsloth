package net.subsloth.preferences

import java.io.File
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

actual class CredentialStore {
    private val dir = File(System.getProperty("user.home"), ".subsloth")
    private val keystoreFile = File(dir, "credentials.ks")
    private val dataFile = File(dir, "credentials.dat")
    private val storePass = "subsloth"
    private val keyAlias = "credentials_key"

    init {
        dir.mkdirs()
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("PKCS12")
        try {
            if (keystoreFile.exists()) {
                keystoreFile.inputStream().use { stream ->
                    ks.load(stream, storePass.toCharArray())
                }
            } else {
                ks.load(null, storePass.toCharArray())
            }
        } catch (_: IOException) {
            // Keystore corrupted — regenerate
            keystoreFile.delete()
            ks.load(null, storePass.toCharArray())
        }
        if (ks.containsAlias(keyAlias)) {
            return (
                ks.getEntry(keyAlias, KeyStore.PasswordProtection(storePass.toCharArray()))
                    as KeyStore.SecretKeyEntry
                ).secretKey
        }
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val key = keyGen.generateKey()
        ks.setEntry(keyAlias, KeyStore.SecretKeyEntry(key), KeyStore.PasswordProtection(storePass.toCharArray()))
        keystoreFile.outputStream().use { stream ->
            ks.store(stream, storePass.toCharArray())
        }
        return key
    }

    actual fun save(login: String, password: String) {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ct = cipher.doFinal("$login\u0000$password".toByteArray(Charsets.UTF_8))
        dataFile.writeBytes(cipher.iv + ct)
    }

    actual fun read(): Pair<String, String>? {
        if (!dataFile.exists()) return null
        return try {
            val data = dataFile.readBytes()
            val iv = data.copyOfRange(0, 12)
            val ct = data.copyOfRange(12, data.size)
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val parts = String(cipher.doFinal(ct), Charsets.UTF_8).split("\u0000", limit = 2)
            if (parts.size != 2) null else Pair(parts[0], parts[1])
        } catch (_: Exception) {
            null
        }
    }

    actual fun clear() {
        dataFile.delete()
        keystoreFile.delete()
    }

    actual fun exists(): Boolean = dataFile.exists()
}
