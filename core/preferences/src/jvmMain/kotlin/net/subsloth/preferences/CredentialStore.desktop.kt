package net.subsloth.preferences

import java.io.File

actual class CredentialStore {
    private var dataDir = File(System.getProperty("user.home"), ".subsloth")
    private var backend = detectBackend(dataDir)

    init {
        dataDir.mkdirs()
        cleanupOldFiles()
    }

    actual fun save(login: String, password: String) {
        val data = "$login\u0000$password".toByteArray(Charsets.UTF_8)
        backend.save("credentials", data)
    }

    actual fun read(): Pair<String, String>? {
        val data = backend.load("credentials") ?: return null
        val parts = String(data, Charsets.UTF_8).split("\u0000", limit = 2)
        return if (parts.size == 2) Pair(parts[0], parts[1]) else null
    }

    actual fun clear() {
        backend.delete("credentials")
        cleanupOldFiles()
    }

    actual fun exists(): Boolean = backend.load("credentials") != null

    private fun cleanupOldFiles() {
        File(dataDir, "credentials.ks").delete()
        File(dataDir, "credentials.dat").delete()
    }

    internal fun reconfigure(baseDir: File) {
        dataDir = baseDir
        backend = FileBasedBackend(baseDir)
        dataDir.mkdirs()
    }

    companion object {
        fun createForTesting(baseDir: File): CredentialStore {
            val store = CredentialStore()
            store.reconfigure(baseDir)
            return store
        }

        private fun detectBackend(dataDir: File): CredentialBackend {
            val candidates = listOf(
                LinuxKeychainBackend(),
                MacosKeychainBackend(),
                WindowsKeychainBackend(),
            )
            return candidates.firstOrNull { it.isAvailable() }
                ?: FileBasedBackend(dataDir)
        }
    }
}
