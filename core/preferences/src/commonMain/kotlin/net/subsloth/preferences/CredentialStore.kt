package net.subsloth.preferences

expect class CredentialStore {
    suspend fun save(login: String, password: String)

    suspend fun read(): Pair<String, String>?

    suspend fun clear()

    suspend fun exists(): Boolean
}
