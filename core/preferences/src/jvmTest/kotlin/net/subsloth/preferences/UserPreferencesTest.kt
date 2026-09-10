package net.subsloth.preferences

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import net.subsloth.core.model.identifier.AccountProfileKey
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UserPreferencesTest {
    private lateinit var prefs: UserPreferences
    private val profileA = AccountProfileKey("profile_a_key")
    private val profileB = AccountProfileKey("profile_b_key")

    @BeforeEach
    fun setUp() {
        val dataStore = createTempFileDataStore()
        prefs = UserPreferences(dataStore)
    }

    // ── Account isolation ─────────────────────────────────────────────────

    @Test
    fun `subtitle preference is isolated per profile`() = runTest {
        prefs.setSubtitleEnabled(profileA, false)
        prefs.setSubtitleEnabled(profileB, true)

        prefs.subtitleEnabled(profileA).test {
            assertThat(awaitItem()).isFalse()
        }
        prefs.subtitleEnabled(profileB).test {
            assertThat(awaitItem()).isTrue()
        }
    }

    @Test
    fun `quality preference is isolated per profile`() = runTest {
        prefs.setQuality(profileA, "1080p")
        prefs.setQuality(profileB, "720p")

        prefs.quality(profileA).test {
            assertThat(awaitItem()).isEqualTo("1080p")
        }
        prefs.quality(profileB).test {
            assertThat(awaitItem()).isEqualTo("720p")
        }
    }

    // ── Default values ────────────────────────────────────────────────────

    @Test
    fun `subtitle enabled defaults to true`() = runTest {
        prefs.subtitleEnabled(profileA).test {
            assertThat(awaitItem()).isTrue()
        }
    }

    @Test
    fun `playback speed defaults to 1_0`() = runTest {
        prefs.playbackSpeed(profileA).test {
            assertThat(awaitItem()).isEqualTo(1.0f)
        }
    }

    @Test
    fun `downloads wifi only defaults to true`() = runTest {
        prefs.downloadsWifiOnly(profileA).test {
            assertThat(awaitItem()).isTrue()
        }
    }

    @Test
    fun `catalog cache timestamp defaults to null`() = runTest {
        prefs.catalogCacheTimestamp(profileA).test {
            assertThat(awaitItem()).isNull()
        }
    }

    // ── API base URL ──────────────────────────────────────────────────────

    @Test
    fun `stored api base url is null when never saved`() = runTest {
        prefs.storedApiBaseUrl().test {
            assertThat(awaitItem()).isNull()
        }
    }

    @Test
    fun `stored api base url preserves a deliberately saved default`() = runTest {
        prefs.setApiBaseUrl(UserPreferences.DEFAULT_API_BASE_URL)
        prefs.storedApiBaseUrl().test {
            assertThat(awaitItem()).isEqualTo(UserPreferences.DEFAULT_API_BASE_URL)
        }
    }

    @Test
    fun `stored api base url preserves a blank saved value`() = runTest {
        prefs.setApiBaseUrl("")
        prefs.storedApiBaseUrl().test {
            assertThat(awaitItem()).isEqualTo("")
        }
    }

    @Test
    fun `api base url defaults when never saved`() = runTest {
        prefs.apiBaseUrl().test {
            assertThat(awaitItem()).isEqualTo(UserPreferences.DEFAULT_API_BASE_URL)
        }
    }

    @Test
    fun `api base url falls back to default when the saved value is blank`() = runTest {
        prefs.setApiBaseUrl("")
        prefs.apiBaseUrl().test {
            assertThat(awaitItem()).isEqualTo(UserPreferences.DEFAULT_API_BASE_URL)
        }
    }

    @Test
    fun `api base url returns the saved custom value`() = runTest {
        prefs.setApiBaseUrl("https://custom.example.com/api/")
        prefs.apiBaseUrl().test {
            assertThat(awaitItem()).isEqualTo("https://custom.example.com/api/")
        }
    }

    // ── Set and read back ─────────────────────────────────────────────────

    @Test
    fun `set and read playback speed`() = runTest {
        prefs.setPlaybackSpeed(profileA, 1.5f)
        prefs.playbackSpeed(profileA).test {
            assertThat(awaitItem()).isEqualTo(1.5f)
        }
    }

    @Test
    fun `set and read subtitle language`() = runTest {
        prefs.setSubtitleLanguage(profileA, "en")
        prefs.subtitleLanguage(profileA).test {
            assertThat(awaitItem()).isEqualTo("en")
        }
    }

    @Test
    fun `set subtitle language to null removes it`() = runTest {
        prefs.setSubtitleLanguage(profileA, "en")
        prefs.setSubtitleLanguage(profileA, null)
        prefs.subtitleLanguage(profileA).test {
            assertThat(awaitItem()).isNull()
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────

    @Test
    fun `clearProfilePreferences removes only specified profile preferences`() = runTest {
        prefs.setSubtitleEnabled(profileA, false)
        prefs.setSubtitleEnabled(profileB, false)

        prefs.clearProfilePreferences(profileA)

        // Profile A should return to defaults
        prefs.subtitleEnabled(profileA).test {
            assertThat(awaitItem()).isTrue() // default
        }
        // Profile B should remain
        prefs.subtitleEnabled(profileB).test {
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `clearProfilePreferences clears all preference types for a profile`() = runTest {
        prefs.setQuality(profileA, "1080p")
        prefs.setPlaybackSpeed(profileA, 1.5f)
        prefs.setDownloadsWifiOnly(profileA, false)
        prefs.setSubtitleLanguage(profileA, "en")

        prefs.clearProfilePreferences(profileA)

        prefs.quality(profileA).test { assertThat(awaitItem()).isNull() }
        prefs.playbackSpeed(profileA).test { assertThat(awaitItem()).isEqualTo(1.0f) }
        prefs.downloadsWifiOnly(profileA).test { assertThat(awaitItem()).isTrue() }
        prefs.subtitleLanguage(profileA).test { assertThat(awaitItem()).isNull() }
    }

    // ── Cache timestamps ──────────────────────────────────────────────────

    @Test
    fun `set and read catalog cache timestamp`() = runTest {
        val now = 1000L
        prefs.setCatalogCacheTimestamp(profileA, now)
        prefs.catalogCacheTimestamp(profileA).test {
            assertThat(awaitItem()).isEqualTo(now)
        }
    }

    @Test
    fun `cache timestamps are isolated per profile`() = runTest {
        prefs.setCatalogCacheTimestamp(profileA, 100L)
        prefs.setCatalogCacheTimestamp(profileB, 200L)

        prefs.catalogCacheTimestamp(profileA).test { assertThat(awaitItem()).isEqualTo(100L) }
        prefs.catalogCacheTimestamp(profileB).test { assertThat(awaitItem()).isEqualTo(200L) }
    }
}
