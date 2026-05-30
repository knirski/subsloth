package net.subsloth.preferences

import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CredentialStoreJvmTest {
    private lateinit var credentialStore: CredentialStore

    @BeforeEach
    fun setUp() {
        credentialStore = CredentialStore()
        credentialStore.clear()
    }

    @Test
    fun `save and read credentials`() {
        credentialStore.save("user@example.com", "securePassword123")
        val result = credentialStore.read()
        assertThat(result).isNotNull()
        assertThat(result!!.first).isEqualTo("user@example.com")
        assertThat(result.second).isEqualTo("securePassword123")
    }

    @Test
    fun `read returns null when no credentials stored`() {
        assertThat(credentialStore.read()).isNull()
    }

    @Test
    fun `exists returns false when no credentials`() {
        assertThat(credentialStore.exists()).isFalse()
    }

    @Test
    fun `exists returns true when credentials are stored`() {
        credentialStore.save("user@example.com", "password")
        assertThat(credentialStore.exists()).isTrue()
    }

    @Test
    fun `clear removes credentials`() {
        credentialStore.save("user@example.com", "password")
        assertThat(credentialStore.exists()).isTrue()
        credentialStore.clear()
        assertThat(credentialStore.exists()).isFalse()
        assertThat(credentialStore.read()).isNull()
    }

    @Test
    fun `save overwrites existing credentials`() {
        credentialStore.save("user1@example.com", "pass1")
        credentialStore.save("user2@example.com", "pass2")
        val result = credentialStore.read()
        assertThat(result!!.first).isEqualTo("user2@example.com")
        assertThat(result.second).isEqualTo("pass2")
    }

    @Test
    fun `credentials handle special characters`() {
        val login = "test+special@example.com"
        val password = "p@ssw0rd!\"\"#$%&'()*+,-./:;<=>?@[]^_`{|}~"
        credentialStore.save(login, password)
        val result = credentialStore.read()
        assertThat(result!!.first).isEqualTo(login)
        assertThat(result.second).isEqualTo(password)
    }

    @Test
    fun `clear is idempotent`() {
        credentialStore.clear()
        credentialStore.clear()
        assertThat(credentialStore.exists()).isFalse()
    }
}
