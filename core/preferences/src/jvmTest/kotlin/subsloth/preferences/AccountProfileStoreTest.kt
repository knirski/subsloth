package subsloth.preferences

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import subsloth.testing.assertions.assertThat
import java.util.regex.Pattern

class AccountProfileStoreTest {
    private lateinit var store: AccountProfileStore

    @BeforeEach
    fun setUp() {
        val dataStore = createTempFileDataStore()
        store = AccountProfileStore(dataStore)
    }

    @Test
    fun `deriveProfileKey returns same key for same normalized login`() = runTest {
        val key1 = store.deriveProfileKey("user@example.com")
        val key2 = store.deriveProfileKey("user@example.com")
        assertThat(key1).isEqualTo(key2)
    }

    @Test
    fun `deriveProfileKey returns different keys for different logins`() = runTest {
        val key1 = store.deriveProfileKey("user1@example.com")
        val key2 = store.deriveProfileKey("user2@example.com")
        assertThat(key1).isNotEqualTo(key2)
    }

    @Test
    fun `deriveProfileKey normalizes email case`() = runTest {
        val keyMixed = store.deriveProfileKey("User@Example.Com")
        val keyLower = store.deriveProfileKey("user@example.com")
        assertThat(keyMixed).isEqualTo(keyLower)
    }

    @Test
    fun `deriveProfileKey normalizes leading and trailing whitespace`() = runTest {
        val keyTrimmed = store.deriveProfileKey("user@example.com")
        val keyUntrimmed = store.deriveProfileKey("  user@example.com  ")
        assertThat(keyTrimmed).isEqualTo(keyUntrimmed)
    }

    @Test
    fun `deriveProfileKey normalizes Unicode NFC`() = runTest {
        // "é" can be encoded as U+00E9 (NFC) or U+0065 U+0301 (NFD)
        // NFC normalization should make them the same
        val keyNfc = store.deriveProfileKey("\u00E9xample@test.com") // NFC
        val keyNfd = store.deriveProfileKey("e\u0301xample@test.com") // NFD
        assertThat(keyNfc).isEqualTo(keyNfd)
    }

    @Test
    fun `raw login is not stored as account identifier`() = runTest {
        // The returned key should be a hex string (64 chars for SHA-256),
        // not the raw login value
        val key = store.deriveProfileKey("user@example.com")
        assertThat(key.value).doesNotContain("user")
        assertThat(key.value).doesNotContain("@")
        assertThat(key.value).doesNotContain("example")
        assertThat(key.value).matches(Pattern.compile("^[0-9a-f]{64}$"))
    }

    @Test
    fun `same salt produces same key for same login`() = runTest {
        val salt1 = store.getOrCreateSalt()
        val key1 = store.deriveProfileKey("user@example.com")

        // Simulate re-creating store with same data (in-memory persists)
        val key2 = store.deriveProfileKey("user@example.com")
        val salt2 = store.getOrCreateSalt()

        assertThat(salt1).isEqualTo(salt2)
        assertThat(key1).isEqualTo(key2)
    }

    @Test
    fun `getOrCreateSalt generates a salt on first call`() = runTest {
        assertThat(store.hasSalt()).isFalse()
        val salt = store.getOrCreateSalt()
        assertThat(salt).isNotEmpty()
        assertThat(salt).matches(Pattern.compile("^[0-9a-f]{64}$"))
        assertThat(store.hasSalt()).isTrue()
    }

    @Test
    fun `deriveProfileKey produces HMAC-SHA256 length output`() = runTest {
        // HMAC-SHA256 produces 32 bytes = 64 hex chars
        val key = store.deriveProfileKey("any-login")
        assertThat(key.value.length).isEqualTo(64)
    }

    @Test
    fun `salt is not cleared by repeated calls`() = runTest {
        val salt1 = store.getOrCreateSalt()
        val salt2 = store.getOrCreateSalt()
        assertThat(salt1).isEqualTo(salt2)
    }
}
