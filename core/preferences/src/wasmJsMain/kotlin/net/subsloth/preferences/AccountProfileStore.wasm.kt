@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package net.subsloth.preferences

import kotlinx.coroutines.await
import kotlin.js.JsString
import kotlin.js.Promise

/**
 * Cryptographically secure salt generation using browser Web Crypto API.
 * Uses crypto.getRandomValues() instead of kotlin.random.Random (PRNG).
 */
actual fun generateSalt(): String = webCryptoRandomHex()

@JsFun(
    """() => {
    const array = new Uint8Array(32);
    crypto.getRandomValues(array);
    return Array.from(array).map(b => b.toString(16).padStart(2, '0')).join('');
}""",
)
private external fun webCryptoRandomHex(): String

actual suspend fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val keyHex = key.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    val dataHex = data.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    val resultHex = webCryptoHmacHex(keyHex, dataHex).await().toString()
    return hexToBytes(resultHex)
}

actual fun normalizeLogin(login: String): String = jsStringNormalizeNfc(login.trim()).lowercase()

@JsFun(
    """(k, d) => {
    const key = new Uint8Array(k.match(/.{2}/g).map(b => parseInt(b, 16)));
    const data = new Uint8Array(d.match(/.{2}/g).map(b => parseInt(b, 16)));
    return crypto.subtle.importKey('raw', key, {name:'HMAC', hash:'SHA-256'}, false, ['sign'])
        .then(cryptoKey => crypto.subtle.sign('HMAC', cryptoKey, data))
        .then(sig => Array.from(new Uint8Array(sig)).map(b => b.toString(16).padStart(2, '0')).join(''));
}""",
)
private external fun webCryptoHmacHex(keyHex: String, dataHex: String): Promise<JsString>

@JsFun("(s) => s.normalize('NFC')")
private external fun jsStringNormalizeNfc(s: String): String

private fun hexToBytes(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "Hex string must have even length" }
    return ByteArray(hex.length / 2) { i ->
        val hi = hexDigit(hex[i * 2])
        val lo = hexDigit(hex[i * 2 + 1])
        ((hi shl 4) or lo).toByte()
    }
}

private fun hexDigit(c: Char): Int = when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> error("Invalid hex digit: $c")
}
