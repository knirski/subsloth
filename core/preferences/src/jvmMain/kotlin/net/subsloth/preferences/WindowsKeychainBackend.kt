package net.subsloth.preferences

import java.util.Base64

internal class WindowsKeychainBackend : CredentialBackend {

    override fun isAvailable(): Boolean = System.getProperty("os.name")?.lowercase()?.contains("win") == true

    override fun save(key: String, data: ByteArray) {
        val encoded = Base64.getEncoder().encodeToString(data)
        ProcessBuilder(
            "cmdkey",
            "/add:net.subsloth.credentials",
            "/user:$key",
            "/pass:$encoded",
        )
            .execute()
    }

    override fun load(key: String): ByteArray? {
        val stdout = ProcessBuilder(
            "powershell",
            "-Command",
            "\$cred = Get-StoredCredential -Target 'net.subsloth.credentials' -Type Generic; " +
                "if (\$cred) { [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String(\$cred.Password)) }",
        )
            .executeOrNull() ?: return null
        return if (stdout.isEmpty()) null else Base64.getDecoder().decode(stdout)
    }

    override fun delete(key: String) {
        ProcessBuilder("cmdkey", "/delete:net.subsloth.credentials")
            .executeOrNull()
    }
}
