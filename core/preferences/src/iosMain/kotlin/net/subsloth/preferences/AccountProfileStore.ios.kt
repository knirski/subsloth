package net.subsloth.preferences

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
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
    val result =
        bytes.usePinned { pinned ->
            SecRandomCopyBytes(kSecRandomDefault, 32u, pinned.addressOf(0))
        }
    check(result == platform.Security.errSecSuccess) {
        "SecRandomCopyBytes failed with error: $result"
    }
    return bytes.joinToString("") { "%02x".format(it) }
}

@OptIn(ExperimentalForeignApi::class)
actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
    memScoped {
        val ctx = alloc<platform.darwin.CCHmacContext>()
        key.usePinned { keyPinned ->
            CCHmacInit(ctx.ptr, platform.darwin.kCCHmacAlgSHA256, keyPinned.addressOf(0), key.size.toULong())
        }
        data.usePinned { dataPinned ->
            CCHmacUpdate(ctx.ptr, dataPinned.addressOf(0), data.size.toULong())
        }
        digest.usePinned { digestPinned ->
            CCHmacFinal(ctx.ptr, digestPinned.addressOf(0))
        }
    }
    return digest
}

actual fun normalizeLogin(login: String): String {
    val trimmed = login.trim()
    val nfc =
        NSString
            .create(string = trimmed)
            .precomposedStringWithCanonicalMapping
    return nfc.toString().lowercase()
}
