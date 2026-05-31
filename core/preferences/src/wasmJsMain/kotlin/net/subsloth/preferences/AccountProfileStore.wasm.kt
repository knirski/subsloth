@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package net.subsloth.preferences

import kotlin.random.Random

actual fun generateSalt(): String {
    val bytes = ByteArray(32)
    Random.nextBytes(bytes)
    return bytes.joinToString("") { b ->
        (b.toInt() and 0xff).let { if (it < 16) "0${it.toString(16)}" else it.toString(16) }
    }
}

actual suspend fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val keyHex = key.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    val dataHex = data.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    val resultHex = webCryptoHmacHex(keyHex, dataHex)
    return hexToBytes(resultHex)
}

actual fun normalizeLogin(login: String): String = jsStringNormalizeNfc(login.trim().lowercase())

// Uses browser Web Crypto API via @JsFun interop for HMAC-SHA256.
// The hex-string encoding avoids ByteArray interop limitations on wasmJs.
@JsFun(
    """(k, d) => {
    const key = new Uint8Array(k.match(/.{2}/g).map(b => parseInt(b, 16)));
    const data = new Uint8Array(d.match(/.{2}/g).map(b => parseInt(b, 16)));
    return crypto.subtle.importKey('raw', key, {name:'HMAC', hash:'SHA-256'}, false, ['sign'])
        .then(cryptoKey => crypto.subtle.sign('HMAC', cryptoKey, data))
        .then(sig => Array.from(new Uint8Array(sig)).map(b => b.toString(16).padStart(2, '0')).join(''));
}""",
)
private external fun webCryptoHmacHex(keyHex: String, dataHex: String): String

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
