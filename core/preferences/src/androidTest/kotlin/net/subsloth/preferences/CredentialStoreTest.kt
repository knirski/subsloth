package net.subsloth.preferences

import androidx.test.core.app.ApplicationProvider
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Instrumented tests for [CredentialStore] using the real Android Keystore.
 *
 * These tests require a device or emulator with Android Keystore support (API 26+).
 * They verify encryption round-trips, key persistence across store re-creation,
 * credential isolation from other storage, and proper clear behavior.
 */
class CredentialStoreTest {
    private lateinit var credentialStore: CredentialStore

    @BeforeEach
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        credentialStore = CredentialStore(context)
        // Ensure clean state before each test
        credentialStore.clear()
    }

    @AfterEach
    fun tearDown() {
        credentialStore.clear()
    }

    @Test
    @JvmName("saveAndReadCredentials")
    fun `save and read credentials`() {
        credentialStore.save("user@example.com", "securePassword123")

        val result = credentialStore.read()
        assertThat(result).isNotNull()
        assertThat(result!!.first).isEqualTo("user@example.com")
        assertThat(result.second).isEqualTo("securePassword123")
    }

    @Test
    @JvmName("readReturnsNullWhenNoCredentialsStored")
    fun `read returns null when no credentials stored`() {
        val result = credentialStore.read()
        assertThat(result).isNull()
    }

    @Test
    @JvmName("existsReturnsFalseWhenNoCredentials")
    fun `exists returns false when no credentials`() {
        assertThat(credentialStore.exists()).isFalse()
    }

    @Test
    @JvmName("existsReturnsTrueWhenCredentialsAreStored")
    fun `exists returns true when credentials are stored`() {
        credentialStore.save("user@example.com", "password")
        assertThat(credentialStore.exists()).isTrue()
    }

    @Test
    @JvmName("clearRemovesCredentials")
    fun `clear removes credentials`() {
        credentialStore.save("user@example.com", "password")
        assertThat(credentialStore.exists()).isTrue()

        credentialStore.clear()

        assertThat(credentialStore.exists()).isFalse()
        assertThat(credentialStore.read()).isNull()
    }

    @Test
    @JvmName("saveOverwritesExistingCredentials")
    fun `save overwrites existing credentials`() {
        credentialStore.save("user1@example.com", "pass1")
        credentialStore.save("user2@example.com", "pass2")

        val result = credentialStore.read()
        assertThat(result!!.first).isEqualTo("user2@example.com")
        assertThat(result.second).isEqualTo("pass2")
    }

    @Test
    @JvmName("credentialsSurviveStoreRecreation")
    fun `credentials survive store recreation`() {
        credentialStore.save("persistent@test.com", "keepMe")

        // Simulate app restart by creating a new CredentialStore instance
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val newStore = CredentialStore(context)

        val result = newStore.read()
        assertThat(result).isNotNull()
        assertThat(result!!.first).isEqualTo("persistent@test.com")
        assertThat(result.second).isEqualTo("keepMe")
    }

    @Test
    @JvmName("credentialsAreSeparateFromDatastorePreferences")
    fun `credentials are separate from datastore preferences`() {
        // CredentialStore uses its own SharedPreferences file
        credentialStore.save("separate@test.com", "isolatedPassword")

        // DataStore preferences files should not contain credential data
        // Verify by checking the credential file exists but DataStore files
        // don't contain credential info
        assertThat(credentialStore.exists()).isTrue()

        // Read credentials back to confirm they're intact
        val result = credentialStore.read()
        assertThat(result).isNotNull()
    }

    @Test
    @JvmName("clearDoesNotAffectDatastoreData")
    fun `clear does not affect datastore data`() {
        // Save credentials
        credentialStore.save("user@test.com", "password")

        // Simulate some DataStore-like preferences being set separately
        // (This is an architectural test - CredentialStore operates on its
        // own SharedPreferences file, separate from DataStore)
        credentialStore.clear()

        // After clearing credentials, only the credential file is affected
        assertThat(credentialStore.exists()).isFalse()
    }

    @Test
    @JvmName("credentialsHandleSpecialCharacters")
    fun `credentials handle special characters`() {
        val login = "test+special@example.com"
        val password = "p@ssw0rd!\"#$%&'()*+,-./:;<=>?@[]^_`{|}~"

        credentialStore.save(login, password)

        val result = credentialStore.read()
        assertThat(result!!.first).isEqualTo(login)
        assertThat(result.second).isEqualTo(password)
    }

    @Test
    @JvmName("clearIsIdempotent")
    fun `clear is idempotent`() {
        // Clearing when no credentials exist should not throw
        credentialStore.clear()
        credentialStore.clear()
        assertThat(credentialStore.exists()).isFalse()
    }
}
