package subsloth.preferences

import kotlinx.cinterop.CFTypeRefVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.Foundation.NSDictionary
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Security.CFDictionaryRef
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

@OptIn(ExperimentalForeignApi::class)
actual class CredentialStore {
    private val serviceName = "subsloth.credentials"

    actual fun save(login: String, password: String) {
        val data = "$login\u0000$password".encodeToByteArray().toNSData()
        val query =
            NSDictionary.dictionaryWithObjects(
                objects = listOf(kSecClassGenericPassword, serviceName, "credentials", data),
                forKeys = listOf(kSecClass, kSecAttrService, kSecAttrAccount, kSecValueData),
            )

        // Try update first, fall back to add
        val updateStatus = SecItemUpdate(query as CFDictionaryRef, query as CFDictionaryRef)
        if (updateStatus == errSecItemNotFound) {
            val status = SecItemAdd(query as CFDictionaryRef, null)
            check(status == errSecSuccess) {
                "Failed to save credentials: error $status"
            }
        } else {
            check(updateStatus == errSecSuccess) {
                "Failed to update credentials: error $updateStatus"
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun read(): Pair<String, String>? {
        val query =
            NSDictionary.dictionaryWithObjects(
                objects = listOf(kSecClassGenericPassword, serviceName, "credentials", true, kSecMatchLimitOne),
                forKeys = listOf(kSecClass, kSecAttrService, kSecAttrAccount, kSecReturnData, kSecMatchLimit),
            )

        return memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)
            if (status != errSecSuccess) return@memScoped null
            val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
            val str = NSString.create(data = data, encoding = NSUTF8StringEncoding) ?: return@memScoped null
            val parts = str.toString().split("\u0000", limit = 2)
            if (parts.size != 2) null else Pair(parts[0], parts[1])
        }
    }

    actual fun clear() {
        val query =
            NSDictionary.dictionaryWithObjects(
                objects = listOf(kSecClassGenericPassword, serviceName, "credentials"),
                forKeys = listOf(kSecClass, kSecAttrService, kSecAttrAccount),
            )
        SecItemDelete(query as CFDictionaryRef)
    }

    actual fun exists(): Boolean = read() != null
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}
