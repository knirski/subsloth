package net.subsloth.preferences

import java.util.Base64

internal class MacosKeychainBackend : CredentialBackend {

    override fun isAvailable(): Boolean = System.getProperty("os.name")?.lowercase()?.contains("mac") == true &&
        ProcessBuilder("which", "security")
            .executeOrNull() != null

    override fun save(key: String, data: ByteArray) {
        val encoded = Base64.getEncoder().encodeToString(data)
        ProcessBuilder(
            "security", "add-generic-password",
            "-s", "net.subsloth.credentials",
            "-a", key,
            "-w", encoded,
            "-U",
        )
            .execute()
    }

    override fun load(key: String): ByteArray? {
        val stdout = ProcessBuilder(
            "security",
            "find-generic-password",
            "-s",
            "net.subsloth.credentials",
            "-a",
            key,
            "-w",
        )
            .executeOrNull() ?: return null
        return if (stdout.isEmpty()) null else Base64.getDecoder().decode(stdout)
    }

    override fun delete(key: String) {
        ProcessBuilder(
            "security",
            "delete-generic-password",
            "-s",
            "net.subsloth.credentials",
            "-a",
            key,
        )
            .executeOrNull()
    }
}
