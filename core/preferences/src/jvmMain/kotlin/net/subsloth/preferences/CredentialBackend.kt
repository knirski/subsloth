package net.subsloth.preferences

interface CredentialBackend {
    fun save(key: String, data: ByteArray)
    fun load(key: String): ByteArray?
    fun delete(key: String)
    fun isAvailable(): Boolean
}

internal fun ProcessBuilder.execute(): String {
    val process = start()
    val stdout = process.inputStream.bufferedReader().readText()
    val stderr = process.errorStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        throw java.io.IOException("Command failed with exit code $exitCode: $stderr")
    }
    return stdout.trim()
}

internal fun ProcessBuilder.executeOrNull(): String? {
    return try {
        execute()
    } catch (_: java.io.IOException) {
        null
    }
}
