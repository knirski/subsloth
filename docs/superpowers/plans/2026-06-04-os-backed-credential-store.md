# OS-Backed Credential Store Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hardcoded PKCS12 password in the desktop `CredentialStore` with OS keychain storage, with per-user key derivation as fallback.

**Architecture:** The desktop `CredentialStore` delegates to a `CredentialBackend` interface. Three platform backends wrap CLI tools (`secret-tool`, `security`, `cmdkey`). A fallback `FileBasedBackend` uses machine-id-derived AES keys. Backend is detected once at startup.

**Tech Stack:** Kotlin/JVM, `javax.crypto` (AES/GCM), `java.security.KeyStore` (for fallback only), `ProcessBuilder` (CLI wrappers), JUnit 5 + Truth

---

## File Map

| File | Responsibility |
|------|---------------|
| `core/preferences/src/jvmMain/kotlin/net/subsloth/preferences/CredentialBackend.kt` | Interface: `save`, `load`, `delete`, `isAvailable` |
| `core/preferences/src/jvmMain/kotlin/net/subsloth/preferences/LinuxKeychainBackend.kt` | `secret-tool` wrapper |
| `core/preferences/src/jvmMain/kotlin/net/subsloth/preferences/MacosKeychainBackend.kt` | `security` CLI wrapper |
| `core/preferences/src/jvmMain/kotlin/net/subsloth/preferences/WindowsKeychainBackend.kt` | `cmdkey`/PowerShell wrapper |
| `core/preferences/src/jvmMain/kotlin/net/subsloth/preferences/FileBasedBackend.kt` | Per-user key derivation + AES/GCM |
| `core/preferences/src/jvmMain/kotlin/net/subsloth/preferences/CredentialStore.desktop.kt` | Rewrite: injectable baseDir, backend detection |
| `core/preferences/src/jvmTest/kotlin/net/subsloth/preferences/CredentialStoreJvmTest.kt` | Update: test with injectable baseDir |
| `core/preferences/src/jvmTest/kotlin/net/subsloth/preferences/CredentialBackendContractTest.kt` | New: contract tests for all backends |

---

### Task 1: CredentialBackend Interface

**Files:**
- Create: `core/preferences/src/jvmMain/kotlin/net/subsloth/preferences/CredentialBackend.kt`

- [ ] **Step 1: Create the interface**

```kotlin
package net.subsloth.preferences

import java.io.ByteArrayOutputStream

interface CredentialBackend {
    fun save(key: String, data: ByteArray)
    fun load(key: String): ByteArray?
    fun delete(key: String)
    fun isAvailable(): Boolean
}

internal fun ProcessBuilder.execute(): String {
    val process = start()
    val stdout = process.inputStream.bufferedReader().readText()
    val stderr = process.errorStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        throw java.io.IOException("Command failed with exit code $exitCode: $stderr")
    }
    return stdout.trim()
}

internal fun ProcessBuilder.executeOrNull(): String? {
    return try {
        execute()
    } catch (_: java.io.IOException) {
        null
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :core:preferences:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/preferences/src/jvmMain/kotlin/net/subsloth/preferences/CredentialBackend.kt
git commit -m "feat: add CredentialBackend interface for desktop credential store"
```

---

### Task 2: LinuxKeychainBackend

**Files:**
- Create: `core/preferences/src/jvmMain/kotlin/net/subsloth/preferences/LinuxKeychainBackend.kt`

- [ ] **Step 1: Write the implementation**

```kotlin
package net.subsloth.preferences

import java.util.Base64

internal class LinuxKeychainBackend : CredentialBackend {

    override fun isAvailable(): Boolean {
        return ProcessBuilder("which", "secret-tool")
            .executeOrNull() != null
    }

    override fun save(key: String, data: ByteArray) {
        val encoded = Base64.getEncoder().encodeToString(data)
        ProcessBuilder("secret-tool", "store",
            "--label", "SubSloth credentials",
            "service", "net.subsloth.credentials",
            "account", key)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .start()
            .also { process ->
                process.outputStream.bufferedWriter().use { it.write(encoded) }
                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    val stderr = process.errorStream.bufferedReader().readText()
                    throw java.io.IOException("secret-tool store failed: $stderr")
                }
            }
    }

    override fun load(key: String): ByteArray? {
        val stdout = ProcessBuilder("secret-tool", "lookup",
            "service", "net.subsloth.credentials",
            "account", key)
            .executeOrNull() ?: return null
        return if (stdout.isEmpty()) null else Base64.getDecoder().decode(stdout)
    }

    override fun delete(key: String) {
        ProcessBuilder("secret-tool", "clear",
            "service", "net.subsloth.credentials",
            "account", key)
            .executeOrNull()
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :core:preferences:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/preferences/src/jvmMain/kotlin/net/subsloth/preferences/LinuxKeychainBackend.kt
git commit -m "feat: add LinuxKeychainBackend using secret-tool"
```

---

### Task 3: MacosKeychainBackend

**Files:**
- Create: `core/preferences/src/jvmMain/kotlin/net/subsloth/preferences/MacosKeychainBackend.kt`

- [ ] **Step 1: Write the implementation**

```kotlin
package net.subsloth.preferences

import java.util.Base64

internal class MacosKeychainBackend : CredentialBackend {

    override fun isAvailable(): Boolean {
        return System.getProperty("os.name")?.lowercase()?.contains("mac") == true &&
            ProcessBuilder("which", "security")
                .executeOrNull() != null
    }

    override fun save(key: String, data: ByteArray) {
        val encoded = Base64.getEncoder().encodeToString(data)
        ProcessBuilder("security", "add-generic-password",
            "-s", "net.subsloth.credentials",
            "-a", key,
            "-w", encoded,
            "-U")
            .execute()
    }

    override fun load(key: String): ByteArray? {
        val stdout = ProcessBuilder("security", "find-generic-password",
            "-s", "net.subsloth.credentials",
            "-a", key,
            "-w")
            .executeOrNull() ?: return null
        return if (stdout.isEmpty()) null else Base64.getDecoder().decode(stdout)
    }

    override fun delete(key: String) {
        ProcessBuilder("security", "delete-generic-password",
            "-s", "net.subsloth.credentials",
            "-a", key)
            .executeOrNull()
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :core:preferences:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/preferences/src/jvmMain/kotlin/net/subsloth/preferences/MacosKeychainBackend.kt
git commit -m "feat: add MacosKeychainBackend using security CLI"
```

---

### Task 4: WindowsKeychainBackend

**Files:**
- Create: `core/preferences/src/jvmMain/kotlin/net/subsloth/preferences/WindowsKeychainBackend.kt`

- [ ] **Step 1: Write the implementation**

```kotlin
package net.subsloth.preferences

import java.util.Base64

internal class WindowsKeychainBackend : CredentialBackend {

    override fun isAvailable(): Boolean {
        return System.getProperty("os.name")?.lowercase()?.contains("win") == true
    }

    override fun save(key: String, data: ByteArray) {
        val encoded = Base64.getEncoder().encodeToString(data)
        ProcessBuilder("cmdkey",
            "/add:net.subsloth.credentials",
            "/user:$key",
            "/pass:$encoded")
            .execute()
    }

    override fun load(key: String): ByteArray? {
        val stdout = ProcessBuilder("powershell", "-Command",
            "\$cred = Get-StoredCredential -Target 'net.subsloth.credentials' -Type Generic; " +
            "if (\$cred) { [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String(\$cred.Password)) }")
            .executeOrNull() ?: return null
        return if (stdout.isEmpty()) null else Base64.getDecoder().decode(stdout)
    }

    override fun delete(key: String) {
        ProcessBuilder("cmdkey", "/delete:net.subsloth.credentials")
            .executeOrNull()
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :core:preferences:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/preferences/src/jvmMain/kotlin/net/subsloth/preferences/WindowsKeychainBackend.kt
git commit -m "feat: add WindowsKeychainBackend using cmdkey/PowerShell"
```

---

### Task 5: FileBasedBackend (Fallback)

**Files:**
- Create: `core/preferences/src/jvmMain/kotlin/net/subsloth/preferences/FileBasedBackend.kt`

- [ ] **Step 1: Write the implementation**

```kotlin
package net.subsloth.preferences

import java.io.File
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal class FileBasedBackend(private val dataDir: File) : CredentialBackend {

    private val dataFile = File(dataDir, "credentials.dat")

    override fun isAvailable(): Boolean = true

    override fun save(key: String, data: ByteArray) {
        dataDir.mkdirs()
        val salt = deriveSalt()
        val secretKey = deriveKey(salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ct = cipher.doFinal(data)
        dataFile.writeBytes(byteArrayOf(iv.size.toByte()) + iv + salt + ct)
    }

    override fun load(key: String): ByteArray? {
        if (!dataFile.exists()) return null
        return try {
            val raw = dataFile.readBytes()
            val ivSize = raw[0].toInt()
            val iv = raw.copyOfRange(1, 1 + ivSize)
            val salt = raw.copyOfRange(1 + ivSize, 1 + ivSize + 16)
            val ct = raw.copyOfRange(1 + ivSize + 16, raw.size)
            val secretKey = deriveKey(salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            cipher.doFinal(ct)
        } catch (_: Exception) {
            null
        }
    }

    override fun delete(key: String) {
        dataFile.delete()
    }

    private fun deriveSalt(): ByteArray {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun deriveKey(salt: ByteArray): SecretKeySpec {
        val machineId = getMachineId()
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(machineId.toCharArray(), salt, 100_000, 256)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    private fun getMachineId(): String {
        return try {
            when {
                System.getProperty("os.name")?.lowercase()?.contains("linux") == true ->
                    File("/etc/machine-id").readText().trim().ifEmpty {
                        File("/var/lib/dbus/machine-id").readText().trim()
                    }
                System.getProperty("os.name")?.lowercase()?.contains("mac") == true ->
                    ProcessBuilder("ioreg", "-rd1", "-c", "IOPlatformExpertDevice")
                        .execute()
                        .lines()
                        .first { it.contains("IOPlatformUUID") }
                        .substringAfter("= \"")
                        .substringBeforeLast("\"")
                else -> // Windows
                    ProcessBuilder("wmic", "csproduct", "get", "UUID")
                        .execute()
                        .lines()
                        .first { it.isNotBlank() && !it.startsWith("UUID") }
                        .trim()
            }
        } catch (e: Exception) {
            throw IOException("Cannot determine machine ID for credential encryption", e)
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :core:preferences:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/preferences/src/jvmMain/kotlin/net/subsloth/preferences/FileBasedBackend.kt
git commit -m "feat: add FileBasedBackend with per-user key derivation fallback"
```

---

### Task 6: Rewrite CredentialStore.desktop.kt

**Files:**
- Modify: `core/preferences/src/jvmMain/kotlin/net/subsloth/preferences/CredentialStore.desktop.kt`

- [ ] **Step 1: Rewrite the file**

```kotlin
package net.subsloth.preferences

import java.io.File

actual class CredentialStore private constructor(
    private val dataDir: File,
    private val backend: CredentialBackend,
) {
    actual constructor() : this(
        dataDir = File(System.getProperty("user.home"), ".subsloth"),
        backend = detectBackend(),
    )

    constructor(baseDir: File) : this(
        dataDir = baseDir,
        backend = detectBackend(),
    )

    init {
        dataDir.mkdirs()
        cleanupOldFiles()
    }

    actual fun save(login: String, password: String) {
        val data = "$login\u0000$password".toByteArray(Charsets.UTF_8)
        backend.save("credentials", data)
    }

    actual fun read(): Pair<String, String>? {
        val data = backend.load("credentials") ?: return null
        val parts = String(data, Charsets.UTF_8).split("\u0000", limit = 2)
        return if (parts.size == 2) Pair(parts[0], parts[1]) else null
    }

    actual fun clear() {
        backend.delete("credentials")
        cleanupOldFiles()
    }

    actual fun exists(): Boolean = backend.load("credentials") != null

    private fun cleanupOldFiles() {
        File(dataDir, "credentials.ks").delete()
        File(dataDir, "credentials.dat").delete()
    }

    private companion object {
        fun detectBackend(): CredentialBackend {
            val candidates = listOf(
                LinuxKeychainBackend(),
                MacosKeychainBackend(),
                WindowsKeychainBackend(),
            )
            return candidates.firstOrNull { it.isAvailable() }
                ?: FileBasedBackend(File(System.getProperty("user.home"), ".subsloth"))
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :core:preferences:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/preferences/src/jvmMain/kotlin/net/subsloth/preferences/CredentialStore.desktop.kt
git commit -m "feat: rewrite desktop CredentialStore with OS keychain backends"
```

---

### Task 7: Update Tests

**Files:**
- Modify: `core/preferences/src/jvmTest/kotlin/net/subsloth/preferences/CredentialStoreJvmTest.kt`
- Create: `core/preferences/src/jvmTest/kotlin/net/subsloth/preferences/CredentialBackendContractTest.kt`

- [ ] **Step 1: Write the contract test**

```kotlin
package net.subsloth.preferences

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.io.TempDir
import java.io.File
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

abstract class CredentialBackendContractTest {

    abstract fun createBackend(tempDir: File): CredentialBackend

    @TempDir
    lateinit var tempDir: File

    private lateinit var backend: CredentialBackend

    @BeforeEach
    fun setUp() {
        backend = createBackend(tempDir)
    }

    @Test
    fun `save and load round trip`() {
        val data = "user@example.com\u0000secretPassword".toByteArray(Charsets.UTF_8)
        backend.save("credentials", data)
        val loaded = backend.load("credentials")
        assertThat(loaded).isEqualTo(data)
    }

    @Test
    fun `load returns null when no data`() {
        assertThat(backend.load("nonexistent")).isNull()
    }

    @Test
    fun `delete removes data`() {
        val data = "user@example.com\u0000secretPassword".toByteArray(Charsets.UTF_8)
        backend.save("credentials", data)
        backend.delete("credentials")
        assertThat(backend.load("credentials")).isNull()
    }

    @Test
    fun `overwrite replaces existing data`() {
        val data1 = "user1@example.com\u0000pass1".toByteArray(Charsets.UTF_8)
        val data2 = "user2@example.com\u0000pass2".toByteArray(Charsets.UTF_8)
        backend.save("credentials", data1)
        backend.save("credentials", data2)
        assertThat(backend.load("credentials")).isEqualTo(data2)
    }
}
```

- [ ] **Step 2: Write the FileBasedBackend test**

```kotlin
package net.subsloth.preferences

import java.io.File

class FileBasedBackendTest : CredentialBackendContractTest() {
    override fun createBackend(tempDir: File): CredentialBackend {
        return FileBasedBackend(tempDir)
    }
}
```

- [ ] **Step 3: Update CredentialStoreJvmTest**

```kotlin
package net.subsloth.preferences

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.io.TempDir
import java.io.File
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CredentialStoreJvmTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var credentialStore: CredentialStore

    @BeforeEach
    fun setUp() {
        credentialStore = CredentialStore(tempDir)
    }

    @AfterEach
    fun tearDown() {
        credentialStore.clear()
    }

    @Test
    fun `save and read round trip`() {
        credentialStore.save("user@example.com", "securePassword123")
        val result = credentialStore.read()
        assertThat(result).isEqualTo("user@example.com" to "securePassword123")
    }

    @Test
    fun `read returns null when no credentials`() {
        assertThat(credentialStore.read()).isNull()
    }

    @Test
    fun `exists returns false when no credentials`() {
        assertThat(credentialStore.exists()).isFalse()
    }

    @Test
    fun `exists returns true after save`() {
        credentialStore.save("user@example.com", "password")
        assertThat(credentialStore.exists()).isTrue()
    }

    @Test
    fun `clear removes credentials`() {
        credentialStore.save("user@example.com", "password")
        credentialStore.clear()
        assertThat(credentialStore.exists()).isFalse()
        assertThat(credentialStore.read()).isNull()
    }

    @Test
    fun `overwrite replaces existing credentials`() {
        credentialStore.save("user1@example.com", "pass1")
        credentialStore.save("user2@example.com", "pass2")
        val result = credentialStore.read()
        assertThat(result).isEqualTo("user2@example.com" to "pass2")
    }

    @Test
    fun `handles empty password`() {
        credentialStore.save("user@example.com", "")
        val result = credentialStore.read()
        assertThat(result).isEqualTo("user@example.com" to "")
    }

    @Test
    fun `handles unicode credentials`() {
        val login = "user@example.com"
        val password = "pässwörd123"
        credentialStore.save(login, password)
        val result = credentialStore.read()
        assertThat(result).isEqualTo(login to password)
    }

    @Test
    fun `clear is idempotent`() {
        credentialStore.clear()
        credentialStore.clear()
        assertThat(credentialStore.exists()).isFalse()
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:preferences:jvmTest`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/preferences/src/jvmTest/kotlin/net/subsloth/preferences/
git commit -m "test: add contract tests for credential backends, update desktop tests"
```

---

### Task 8: Final Verification

- [ ] **Step 1: Run full pre-commit checks**

Run: `./gradlew spotlessApply spotlessCheck detekt :core:preferences:compileKotlinJvm :core:preferences:jvmTest`
Expected: All PASS

- [ ] **Step 2: Commit any formatting fixes**

```bash
git add -A
git commit -m "chore: format credential store code"
```

(Only if spotless made changes.)
