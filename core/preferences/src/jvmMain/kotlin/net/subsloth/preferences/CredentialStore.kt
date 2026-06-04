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
    private val keyAlias = "credentials_key"

    init {
        dir.mkdirs()
    }

    private fun getStorePassword(): CharArray = resolveMachineId().getOrThrow().toCharArray()

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("PKCS12")
        val password = getStorePassword()
        try {
            if (keystoreFile.exists()) {
                keystoreFile.inputStream().use { stream ->
                    ks.load(stream, password)
                }
            } else {
                ks.load(null, password)
            }
        } catch (_: IOException) {
            keystoreFile.delete()
            ks.load(null, password)
        }
        if (ks.containsAlias(keyAlias)) {
            return (
                ks.getEntry(keyAlias, KeyStore.PasswordProtection(password))
                    as KeyStore.SecretKeyEntry
                ).secretKey
        }
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val key = keyGen.generateKey()
        ks.setEntry(keyAlias, KeyStore.SecretKeyEntry(key), KeyStore.PasswordProtection(password))
        keystoreFile.outputStream().use { stream ->
            ks.store(stream, password)
        }
        return key
    }

    actual suspend fun save(login: String, password: String) {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ct = cipher.doFinal("$login\u0000$password".toByteArray(Charsets.UTF_8))
        dataFile.writeBytes(cipher.iv + ct)
    }

    actual suspend fun read(): Pair<String, String>? {
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

    actual suspend fun clear() {
        dataFile.delete()
        keystoreFile.delete()
    }

    actual suspend fun exists(): Boolean = dataFile.exists()

    private fun resolveMachineId(): Result<String> = runCatching {
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
