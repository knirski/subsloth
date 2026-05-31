package net.subsloth.preferences

expect class CredentialStore {
    fun save(login: String, password: String)

    fun read(): Pair<String, String>?

    fun clear()

    fun exists(): Boolean
}
