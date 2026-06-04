package net.subsloth.preferences

import java.util.Base64

internal class LinuxKeychainBackend : CredentialBackend {

    override fun isAvailable(): Boolean {
        return System.getProperty("os.name")?.lowercase()?.contains("linux") == true &&
            ProcessBuilder("which", "secret-tool")
                .executeOrNull() != null
    }

    override fun save(key: String, data: ByteArray) {
        val encoded = Base64.getEncoder().encodeToString(data)
        ProcessBuilder("secret-tool", "store",
            "--label", "SubSloth credentials",
            "service", "net.subsloth.credentials",
            "account", key)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .start()
            .also { process ->
                process.outputStream.bufferedWriter().use { it.write(encoded) }
                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    val stderr = process.errorStream.bufferedReader().readText()
                    throw java.io.IOException("secret-tool store failed: $stderr")
                }
            }
    }

    override fun load(key: String): ByteArray? {
        val stdout = ProcessBuilder("secret-tool", "lookup",
            "service", "net.subsloth.credentials",
            "account", key)
            .executeOrNull() ?: return null
        return if (stdout.isEmpty()) null else Base64.getDecoder().decode(stdout)
    }

    override fun delete(key: String) {
        ProcessBuilder("secret-tool", "clear",
            "service", "net.subsloth.credentials",
            "account", key)
            .executeOrNull()
    }
}
