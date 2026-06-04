package net.subsloth.preferences

import java.io.File

class FileBasedBackendTest : CredentialBackendContractTest() {
    override fun createBackend(tempDir: File): CredentialBackend = FileBasedBackend(tempDir)
}
