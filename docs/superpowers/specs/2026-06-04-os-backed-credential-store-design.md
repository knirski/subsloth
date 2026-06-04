# OS-Backed Credential Store for Desktop

**Issue:** [#63](https://github.com/nickallendev/nickallendev/issues/63)
**Date:** 2026-06-04

## Problem

The desktop `CredentialStore` stores credentials using a PKCS12 keystore with a hardcoded password (`"subsloth"`) compiled into the binary. This makes the AES/GCM encryption obfuscation rather than real at-rest protection — anyone who can read `~/.subsloth/credentials.ks` can decrypt it.

Additionally, the storage root is hardcoded to `~/.subsloth/`, making it impossible to isolate for testing.

## Goals

1. Replace the hardcoded PKCS12 password with OS keychain storage.
2. Make the storage directory injectable for testability.
3. Maintain backward compatibility with the existing API surface (`save`, `read`, `clear`, `exists`).

## Non-Goals

- Changing the `expect`/`actual` interface for non-desktop platforms (Android, iOS, wasmJs).
- Adding external dependencies (JNA, etc.).
- Encrypting the wasmJs `localStorage`-backed store (already documented as plaintext).

## Design

### Architecture

```
expect class CredentialStore {
    fun save(login: String, password: String)
    fun read(): Pair<String, String>?
    fun clear()
    fun exists(): Boolean
}

actual class CredentialStore private constructor(
    private val dataDir: File,
    private val backend: CredentialBackend,
) {
    // Default constructor: uses ~/.subsloth/, detects best backend
    actual constructor()
    
    // Secondary constructor: injectable baseDir for testing
    constructor(baseDir: File)
}

interface CredentialBackend {
    fun save(key: String, data: ByteArray)
    fun load(key: String): ByteArray?
    fun delete(key: String)
    fun isAvailable(): Boolean
}
```

### Backend Detection

On startup, the `CredentialStore` detects the best available backend by probing the CLI tool once (checking if it exists on PATH via `ProcessBuilder` with `which`/`where` or attempting a no-op call). The result is cached for the lifetime of the `CredentialStore` instance.

1. **OS Keychain** (primary): Try the platform's CLI credential tool.
   - Linux: `secret-tool` (libsecret)
   - macOS: `security` (Keychain)
   - Windows: `cmdkey` (Credential Manager)
2. **Per-user key derivation** (fallback): If no keychain tool is available, derive an AES key from a machine-unique secret and use file-based storage.
   - Linux: `/etc/machine-id` or `/var/lib/dbus/machine-id`
   - macOS: `ioreg -rd1 -c IOPlatformExpertDevice | grep IOPlatformUUID`
   - Windows: `wmic csproduct get UUID`

No migration logic between backends. On first run with the new code, if old `credentials.ks`/`credentials.dat` files are detected alongside a keychain backend, they are deleted and the user must re-enter credentials.

### Platform Backends

**Linux: `LinuxKeychainBackend`**

Uses `secret-tool` (part of libsecret). Data is base64-encoded before storage since `secret-tool` works with strings.

```bash
# Store
secret-tool store --label "SubSloth credentials" service net.subsloth.credentials account credentials <<< "BASE64_DATA"

# Load
secret-tool lookup service net.subsloth.credentials account credentials

# Delete
secret-tool clear service net.subsloth.credentials account credentials
```

**macOS: `MacosKeychainBackend`**

```bash
# Store (base64-encoded to avoid shell escaping issues)
security add-generic-password -s "net.subsloth.credentials" -a "credentials" -w "$(echo -n 'binary_data' | base64)" -U

# Load
security find-generic-password -s "net.subsloth.credentials" -a "credentials" -w | base64 -d

# Delete
security delete-generic-password -s "net.subsloth.credentials" -a "credentials"
```

**Windows: `WindowsKeychainBackend`**

```powershell
# Store
cmdkey /add:net.subsloth.credentials /user:credentials /pass:BASE64_DATA

# Load
# PowerShell approach for clean password retrieval:
powershell -Command "[System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String((New-Object System.Net.NetworkCredential('x', (Get-StoredCredential -Target 'net.subsloth.credentials' -Type Generic).Password)).Password))"

# Delete
cmdkey /delete:net.subsloth.credentials
```

Windows uses base64 encoding to avoid shell escaping issues with binary data.

**Fallback: `FileBasedBackend`**

Uses per-user key derivation (machine-id / platform UUID) to derive an AES-256 key via PBKDF2-SHA256 (100,000 iterations). Stores encrypted credentials in `credentials.dat` with the same IV + ciphertext format as the current implementation. No PKCS12 keystore file — the derived key is used directly.

### Data Format

All backends operate on the same raw bytes: `login + "\u0000" + password` encoded as UTF-8. Keychain backends base64-encode this before passing to CLI tools (since the tools work with strings). The file-based fallback encrypts with AES/GCM/NoPadding using the derived key.

### Error Handling

- If a CLI tool is not found (exit code 127 or `ProcessBuilder` throws), fall back to `FileBasedBackend`.
- If a CLI command fails (non-zero exit), throw `IOException` with stderr content.
- `save()`/`read()`/`clear()` remain synchronous. Subprocess calls are < 50ms typically.
- `FileBasedBackend` catches `NoSuchFileException` on machine-id files and throws `IllegalStateException` with a clear message.

### Injectable baseDir

The secondary constructor accepts a `File` parameter:

```kotlin
constructor(baseDir: File) : this(
    dataDir = baseDir,
    backend = detectBackend(),
)
```

Tests use this to point at a temp directory. The default constructor uses `~/.subsloth/`.

### Testing

1. **Unit test**: Use `constructor(tempDir)` with `FileBasedBackend` (force fallback via mocking or testing on a system without keychain).
2. **Integration test**: Verify round-trip save/read/clear on the current OS keychain.
3. **Contract test**: Abstract test class that all backends must pass (save → read → exists → clear → not exists).

### Migration

- No explicit migration from old PKCS12 scheme.
- On first `read()` after upgrade: if keychain returns data, use it. If not, check for old `credentials.ks`/`credentials.dat`, delete them, return null.
- User must re-enter credentials after upgrade (one-time inconvenience).

## Files to Change

| File | Change |
|------|--------|
| `core/preferences/src/jvmMain/kotlin/.../CredentialStore.desktop.kt` | Rewrite: injectable baseDir, backend detection, delegate to `CredentialBackend` |
| `core/preferences/src/jvmMain/kotlin/.../CredentialBackend.kt` | New: interface definition |
| `core/preferences/src/jvmMain/kotlin/.../LinuxKeychainBackend.kt` | New: `secret-tool` wrapper |
| `core/preferences/src/jvmMain/kotlin/.../MacosKeychainBackend.kt` | New: `security` CLI wrapper |
| `core/preferences/src/jvmMain/kotlin/.../WindowsKeychainBackend.kt` | New: `cmdkey`/PowerShell wrapper |
| `core/preferences/src/jvmMain/kotlin/.../FileBasedBackend.kt` | New: per-user key derivation + AES/GCM |
| `core/preferences/src/jvmTest/kotlin/.../CredentialStoreJvmTest.kt` | Update: test with injectable baseDir |
| `core/preferences/src/jvmTest/kotlin/.../CredentialBackendTest.kt` | New: contract tests for backends |
