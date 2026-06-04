package net.subsloth.preferences

import java.util.Base64

internal class WindowsKeychainBackend : CredentialBackend {

    override fun isAvailable(): Boolean = System.getProperty("os.name")?.lowercase()?.contains("win") == true

    override fun save(key: String, data: ByteArray) {
        val encoded = Base64.getEncoder().encodeToString(data)
        ProcessBuilder(
            "powershell",
            "-Command",
            "\$vault = New-Object Windows.Security.Credentials.PasswordVault; " +
                "\$cred = New-Object " +
                "Windows.Security.Credentials.PasswordCredential(" +
                "'net.subsloth.credentials', '$key', '$encoded'); " +
                "\$vault.Add(\$cred)",
        )
            .execute()
    }

    override fun load(key: String): ByteArray? {
        val stdout = ProcessBuilder(
            "powershell",
            "-Command",
            "try { " +
                "\$vault = New-Object Windows.Security.Credentials.PasswordVault; " +
                "\$cred = \$vault.Retrieve('net.subsloth.credentials', '$key'); " +
                "\$cred.Password " +
                "} catch {}",
        )
            .executeOrNull() ?: return null
        return if (stdout.isEmpty()) null else Base64.getDecoder().decode(stdout)
    }

    override fun delete(key: String) {
        ProcessBuilder(
            "powershell",
            "-Command",
            "try { " +
                "\$vault = New-Object Windows.Security.Credentials.PasswordVault; " +
                "\$cred = \$vault.Retrieve('net.subsloth.credentials', '$key'); " +
                "\$vault.Remove(\$cred) " +
                "} catch {}",
        )
            .executeOrNull()
    }
}
