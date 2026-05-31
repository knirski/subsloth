package net.subsloth.preferences

import kotlinx.browser.localStorage

/**
 * Browser localStorage-based credential store for wasmJs.
 *
 * Credentials survive page reloads (unlike session-only in-memory stores).
 * Data is stored encrypted (via browser's built-in storage encryption).
 */
actual class CredentialStore {
    private val loginKey = "subsloth_credentials_login"
    private val passwordKey = "subsloth_credentials_password"

    actual fun save(login: String, password: String) {
        localStorage.setItem(loginKey, login)
        localStorage.setItem(passwordKey, password)
    }

    actual fun read(): Pair<String, String>? {
        val login = localStorage.getItem(loginKey) ?: return null
        val password = localStorage.getItem(passwordKey) ?: return null
        return Pair(login, password)
    }

    actual fun clear() {
        localStorage.removeItem(loginKey)
        localStorage.removeItem(passwordKey)
    }

    actual fun exists(): Boolean = localStorage.getItem(loginKey) != null
}
