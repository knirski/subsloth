package net.subsloth.preferences

interface CredentialBackend {
    fun save(key: String, data: ByteArray)
    fun load(key: String): ByteArray?
    fun delete(key: String)
    fun isAvailable(): Boolean
}

internal fun ProcessBuilder.execute(): String {
    redirectErrorStream(true)
    val process = start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        throw java.io.IOException("Command failed with exit code $exitCode: $output")
    }
    return output.trim()
}

internal fun ProcessBuilder.executeOrNull(): String? = try {
    execute()
} catch (_: java.io.IOException) {
    null
}
