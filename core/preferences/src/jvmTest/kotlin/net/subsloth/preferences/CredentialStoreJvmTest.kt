package net.subsloth.preferences

import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CredentialStoreJvmTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var credentialStore: CredentialStore

    @BeforeEach
    fun setUp() {
        credentialStore = CredentialStore.createForTesting(tempDir)
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
