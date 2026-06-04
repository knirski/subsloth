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
    if (!crypto || !crypto.subtle) {
        return Promise.reject(new Error('WebCrypto requires HTTPS or localhost'));
    }
    try {
        const encoder = new TextEncoder();
        const data = encoder.encode(plaintext);
        const iv = crypto.getRandomValues(new Uint8Array(12));
        const stored = localStorage.getItem('subsloth_credentials_key');
        let keyPromise;
        if (stored) {
            try {
                const jwk = JSON.parse(stored);
                keyPromise = crypto.subtle.importKey(
                    'jwk', jwk, {name:'AES-GCM'}, false, ['encrypt', 'decrypt']);
            } catch (e) {
                localStorage.removeItem('subsloth_credentials_key');
            }
        }
        if (!keyPromise) {
            keyPromise = crypto.subtle.generateKey(
                {name:'AES-GCM', length:256}, true, ['encrypt','decrypt'])
                .then(key => crypto.subtle.exportKey('jwk', key).then(jwk => {
                    localStorage.setItem('subsloth_credentials_key', JSON.stringify(jwk));
                    return key;
                }));
        }
        return keyPromise
            .then(key => crypto.subtle.encrypt({name:'AES-GCM', iv:iv}, key, data))
            .then(enc => {
                const combined = new Uint8Array(12 + enc.byteLength);
                combined.set(iv);
                combined.set(new Uint8Array(enc), 12);
                let binString = '';
                for (let i = 0; i < combined.length; i++) {
                    binString += String.fromCharCode(combined[i]);
                }
                return btoa(binString);
            });
    } catch (err) {
        return Promise.reject(err);
    }
}""",
)
private external fun webCryptoEncrypt(plaintext: String): Promise<JsString>

@JsFun(
    """(base64Data) => {
    if (!crypto || !crypto.subtle) {
        return Promise.resolve('');
    }
    try {
        const binary = atob(base64Data);
        const combined = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) combined[i] = binary.charCodeAt(i);
        const iv = combined.slice(0, 12);
        const ct = combined.slice(12);
        const stored = localStorage.getItem('subsloth_credentials_key');
        if (!stored) return Promise.reject(new Error('Missing crypto key for stored credentials'));
        const jwk = JSON.parse(stored);
        return crypto.subtle.importKey(
            'jwk', jwk, {name:'AES-GCM'}, false, ['encrypt', 'decrypt'])
            .then(key => crypto.subtle.decrypt({name:'AES-GCM', iv:iv}, key, ct))
            .then(decrypted => new TextDecoder().decode(decrypted))
            .catch(() => '');
    } catch (err) {
        return Promise.resolve('');
    }
}""",
)
private external fun webCryptoDecrypt(base64Data: String): Promise<JsString>
