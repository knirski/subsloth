package net.subsloth.preferences

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.precomposedStringWithCanonicalMapping
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.darwin.CCHmacFinal
import platform.darwin.CCHmacInit
import platform.darwin.CCHmacUpdate
import platform.darwin.CC_SHA256_DIGEST_LENGTH

@OptIn(ExperimentalForeignApi::class)
actual fun generateSalt(): String {
    val bytes = ByteArray(32)
    bytes.usePinned { pinned ->
        SecRandomCopyBytes(kSecRandomDefault, 32, pinned.addressOf(0))
    }
    return bytes.joinToString("") { "%02x".format(it) }
}

@OptIn(ExperimentalForeignApi::class)
actual fun hmacSha256(
    key: ByteArray,
    data: ByteArray,
): ByteArray {
    val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
    memScoped {
        val ctx = allocArrayOf<Byte>(CC_SHA256_DIGEST_LENGTH) // scratch space
        CCHmacInit(ctx, platform.darwin.kCCHmacAlgSHA256, key.toCValues(), key.size.toULong())
        CCHmacUpdate(ctx, data.toCValues(), data.size.toULong())
        CCHmacFinal(ctx, digest.toCValues())
    }
    return digest
}

private fun ByteArray.toCValues() = this.usePinned { it.addressOf(0) }

actual fun normalizeLogin(login: String): String {
    val trimmed = login.trim()
    val nfc =
        NSString
            .create(string = trimmed)
            .precomposedStringWithCanonicalMapping
    return nfc.lowercase()
}
