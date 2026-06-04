package net.subsloth.preferences

import java.io.File
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal class FileBasedBackend(private val dataDir: File) : CredentialBackend {

    private val dataFile = File(dataDir, "credentials.dat")

    override fun isAvailable(): Boolean = true

    override fun save(key: String, data: ByteArray) {
        dataDir.mkdirs()
        val salt = deriveSalt()
        val secretKey = deriveKey(salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ct = cipher.doFinal(data)
        dataFile.writeBytes(byteArrayOf(iv.size.toByte()) + iv + salt + ct)
    }

    override fun load(key: String): ByteArray? {
        if (!dataFile.exists()) return null
        return try {
            val raw = dataFile.readBytes()
            val ivSize = raw[0].toInt()
            val iv = raw.copyOfRange(1, 1 + ivSize)
            val salt = raw.copyOfRange(1 + ivSize, 1 + ivSize + 16)
            val ct = raw.copyOfRange(1 + ivSize + 16, raw.size)
            val secretKey = deriveKey(salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            cipher.doFinal(ct)
        } catch (_: Exception) {
            null
        }
    }

    override fun delete(key: String) {
        dataFile.delete()
    }

    private fun deriveSalt(): ByteArray {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun deriveKey(salt: ByteArray): SecretKeySpec {
        val machineId = getMachineId()
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(machineId.toCharArray(), salt, 100_000, 256)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    private fun getMachineId(): String = try {
        when {
            System.getProperty("os.name")?.lowercase()?.contains("linux") == true ->
                File("/etc/machine-id").readText().trim().ifEmpty {
                    File("/var/lib/dbus/machine-id").readText().trim()
                }

            System.getProperty("os.name")?.lowercase()?.contains("mac") == true ->
                ProcessBuilder("ioreg", "-rd1", "-c", "IOPlatformExpertDevice")
                    .execute()
                    .lines()
                    .firstOrNull { it.contains("IOPlatformUUID") }
                    ?.substringAfter("= \"")
                    ?.substringBeforeLast("\"")
                    ?: throw IOException("Could not find IOPlatformUUID")

            else -> // Windows
                ProcessBuilder(
                    "reg",
                    "query",
                    "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Cryptography",
                    "/v",
                    "MachineGuid",
                )
                    .execute()
                    .lines()
                    .firstOrNull { it.contains("MachineGuid") }
                    ?.substringAfter("REG_SZ")
                    ?.trim()
                    ?: throw IOException("Could not find MachineGuid")
        }
    } catch (e: Exception) {
        throw IOException("Cannot determine machine ID for credential encryption", e)
    }
}
