package net.subsloth.preferences

import kotlinx.cinterop.CFTypeRefVar
import kotlinx.cinterop.ptr
import platform.CoreFoundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.encodeToByteArray
import platform.Security.CFDictionaryRef
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

actual class CredentialStore {
    private val serviceName = "net.subsloth.credentials"

    actual fun save(
        login: String,
        password: String,
    ) {
        clear()
        val data = "$login\u0000$password".encodeToByteArray()
        val query =
            mapOf<Any?, Any?>(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to serviceName,
                kSecAttrAccount to "credentials",
                kSecValueData to data.toNSData(),
            )
        SecItemAdd(query as CFDictionaryRef, null)
    }

    actual fun read(): Pair<String, String>? {
        val query =
            mapOf<Any?, Any?>(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to serviceName,
                kSecAttrAccount to "credentials",
                kSecReturnData to true,
                kSecMatchLimit to kSecMatchLimitOne,
            )
        val result = CFTypeRefVar()
        val status = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)
        if (status != 0) return null
        val data = CFBridgingRelease(result.value) as? NSData ?: return null
        val str = NSString.create(data = data, encoding = NSUTF8StringEncoding) ?: return null
        val parts = str.toString().split("\u0000", limit = 2)
        return if (parts.size != 2) null else Pair(parts[0], parts[1])
    }

    actual fun clear() {
        val query =
            mapOf<Any?, Any?>(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to serviceName,
                kSecAttrAccount to "credentials",
            )
        SecItemDelete(query as CFDictionaryRef)
    }

    actual fun exists(): Boolean = read() != null
}
