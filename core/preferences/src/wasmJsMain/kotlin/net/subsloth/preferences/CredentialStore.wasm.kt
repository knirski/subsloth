@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package net.subsloth.preferences

import kotlinx.browser.localStorage
import kotlinx.coroutines.await
import kotlin.js.JsString
import kotlin.js.Promise

actual class CredentialStore {
    private val dataKey = "subsloth_credentials_data"

    actual suspend fun save(login: String, password: String) {
        val data = "$login\u0000$password"
        val encrypted = webCryptoEncrypt(data).await().toString()
        localStorage.setItem(dataKey, encrypted)
    }

    actual suspend fun read(): Pair<String, String>? {
        val encrypted = localStorage.getItem(dataKey) ?: return null
        return try {
            val decrypted = webCryptoDecrypt(encrypted).await().toString()
            val parts = decrypted.split("\u0000", limit = 2)
            if (parts.size == 2) Pair(parts[0], parts[1]) else null
        } catch (_: Exception) {
            null
        }
    }

    actual suspend fun clear() {
        localStorage.removeItem(dataKey)
    }

    actual suspend fun exists(): Boolean = localStorage.getItem(dataKey) != null
}

@JsFun(
    """(plaintext) => {
    const encoder = new TextEncoder();
    const data = encoder.encode(plaintext);
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const stored = localStorage.getItem('subsloth_credentials_key');
    return (stored
        ? crypto.subtle.importKey('jwk', JSON.parse(stored), {name:'AES-GCM'}, false, ['encrypt'])
        : crypto.subtle.generateKey({name:'AES-GCM', length:256}, true, ['encrypt','decrypt'])
            .then(key => {
                return crypto.subtle.exportKey('jwk', key).then(jwk => {
                    localStorage.setItem('subsloth_credentials_key', JSON.stringify(jwk));
                    return key;
                });
            })
    ).then(key => crypto.subtle.encrypt({name:'AES-GCM', iv:iv}, key, data))
     .then(enc => {
        const combined = new Uint8Array(12 + enc.byteLength);
        combined.set(iv);
        combined.set(new Uint8Array(enc), 12);
        return btoa(String.fromCharCode(...combined));
     });
}""",
)
private external fun webCryptoEncrypt(plaintext: String): Promise<JsString>

@JsFun(
    """(base64Data) => {
    const binary = atob(base64Data);
    const combined = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) combined[i] = binary.charCodeAt(i);
    const iv = combined.slice(0, 12);
    const ct = combined.slice(12);
    const stored = localStorage.getItem('subsloth_credentials_key');
    if (!stored) return Promise.reject(new Error('Missing crypto key for stored credentials'));
    return crypto.subtle.importKey('jwk', JSON.parse(stored), {name:'AES-GCM'}, false, ['decrypt'])
        .then(key => crypto.subtle.decrypt({name:'AES-GCM', iv:iv}, key, ct))
        .then(decrypted => new TextDecoder().decode(decrypted));
}""",
)
private external fun webCryptoDecrypt(base64Data: String): Promise<JsString>
