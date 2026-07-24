package net.subsloth.preferences

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Instrumented test proving [CredentialStore] round-trips real
 * Keystore-backed [androidx.security.crypto.EncryptedSharedPreferences]
 * encryption on a real Android device/emulator (not a mock/fake).
 *
 * Also verifies that credentials are unrecoverable once [CredentialStore.clear]
 * has removed them, and that the underlying SharedPreferences file's raw
 * on-disk bytes never contain the plaintext login/password — i.e. the data
 * really is encrypted, not merely wrapped by an in-memory fake.
 */
class AndroidCredentialStoreTest {

    private lateinit var credentialStore: CredentialStore

    @Before
    fun setUp() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        AndroidContext.init(appContext)
        credentialStore = CredentialStore()
        runTest { credentialStore.clear() }
    }

    @After
    fun tearDown() {
        runTest { credentialStore.clear() }
        AndroidContext.reset()
    }

    @Test
    fun save_and_read_round_trip_through_real_encrypted_storage() = runTest {
        credentialStore.save("user@example.com", "s3cr3t-P@ssw0rd")

        val result = credentialStore.read()

        assertEquals("user@example.com" to "s3cr3t-P@ssw0rd", result)
    }

    @Test
    fun exists_reflects_presence_of_saved_credentials() = runTest {
        assertFalse(credentialStore.exists())

        credentialStore.save("user@example.com", "password")

        assertTrue(credentialStore.exists())
    }

    @Test
    fun clear_makes_credentials_unrecoverable() = runTest {
        credentialStore.save("user@example.com", "password")
        assertTrue(credentialStore.exists())

        credentialStore.clear()

        assertNull(credentialStore.read())
        assertFalse(credentialStore.exists())
    }

    @Test
    fun on_disk_shared_preferences_file_does_not_contain_plaintext_credentials() = runTest {
        val login = "plaintext-marker@example.com"
        val password = "plaintext-marker-password"
        credentialStore.save(login, password)

        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val prefsFile = java.io.File(
            java.io.File(appContext.applicationInfo.dataDir, "shared_prefs"),
            "subsloth_encrypted_credentials.xml",
        )
        assertTrue("Expected encrypted prefs file to exist at ${prefsFile.path}", prefsFile.exists())

        val rawContents = prefsFile.readText()
        assertFalse(rawContents.contains(login))
        assertFalse(rawContents.contains(password))
    }

    @Test
    fun overwrite_replaces_previous_credentials() = runTest {
        credentialStore.save("user1@example.com", "pass1")
        credentialStore.save("user2@example.com", "pass2")

        assertEquals("user2@example.com" to "pass2", credentialStore.read())
    }
}
