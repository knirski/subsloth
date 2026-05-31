package net.subsloth.preferences

import java.security.SecureRandom
import java.text.Normalizer
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

actual fun generateSalt(): String {
    val random = SecureRandom()
    val bytes = ByteArray(32)
    random.nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

actual suspend fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    val keySpec = SecretKeySpec(key, "HmacSHA256")
    mac.init(keySpec)
    return mac.doFinal(data)
}

actual fun normalizeLogin(login: String): String {
    val trimmed = login.trim()
    val nfc = Normalizer.normalize(trimmed, Normalizer.Form.NFC)
    return nfc.lowercase(Locale.ROOT)
}
