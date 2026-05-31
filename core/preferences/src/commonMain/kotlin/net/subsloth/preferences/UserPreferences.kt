package net.subsloth.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.subsloth.core.model.identifier.AccountProfileKey

/**
 * Account-scoped user preferences backed by DataStore.
 *
 * Each preference key is namespaced under the active [AccountProfileKey]
 * so that different accounts have independent preferences.
 */
@Suppress("TooManyFunctions")
class UserPreferences(private val dataStore: DataStore<Preferences>) {
    // ── Preference keys (namespaced by account profile key) ──────────────

    private fun subtitleEnabledKey(profileKey: AccountProfileKey) =
        booleanPreferencesKey("${profileKey.value}_subtitle_enabled")

    private fun subtitleLanguageKey(profileKey: AccountProfileKey) =
        stringPreferencesKey("${profileKey.value}_subtitle_language")

    private fun qualityKey(profileKey: AccountProfileKey) = stringPreferencesKey("${profileKey.value}_quality")

    private fun playbackSpeedKey(profileKey: AccountProfileKey) =
        floatPreferencesKey("${profileKey.value}_playback_speed")

    private fun downloadsWifiOnlyKey(profileKey: AccountProfileKey) =
        booleanPreferencesKey("${profileKey.value}_downloads_wifi_only")

    private fun catalogCacheTimestampKey(profileKey: AccountProfileKey) =
        longPreferencesKey("${profileKey.value}_catalog_cache_timestamp")

    private fun detailCacheTimestampKey(profileKey: AccountProfileKey) =
        longPreferencesKey("${profileKey.value}_detail_cache_timestamp")

    // ── Subtitle ─────────────────────────────────────────────────────────

    fun subtitleEnabled(profileKey: AccountProfileKey): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[subtitleEnabledKey(profileKey)] ?: true
    }

    suspend fun setSubtitleEnabled(profileKey: AccountProfileKey, enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[subtitleEnabledKey(profileKey)] = enabled
        }
    }

    fun subtitleLanguage(profileKey: AccountProfileKey): Flow<String?> = dataStore.data.map { prefs ->
        prefs[subtitleLanguageKey(profileKey)]
    }

    suspend fun setSubtitleLanguage(profileKey: AccountProfileKey, language: String?) {
        dataStore.edit { prefs ->
            if (language != null) {
                prefs[subtitleLanguageKey(profileKey)] = language
            } else {
                prefs.remove(subtitleLanguageKey(profileKey))
            }
        }
    }

    // ── Quality ──────────────────────────────────────────────────────────

    fun quality(profileKey: AccountProfileKey): Flow<String?> = dataStore.data.map { prefs ->
        prefs[qualityKey(profileKey)]
    }

    suspend fun setQuality(profileKey: AccountProfileKey, quality: String?) {
        dataStore.edit { prefs ->
            if (quality != null) {
                prefs[qualityKey(profileKey)] = quality
            } else {
                prefs.remove(qualityKey(profileKey))
            }
        }
    }

    // ── Playback Speed ───────────────────────────────────────────────────

    fun playbackSpeed(profileKey: AccountProfileKey): Flow<Float> = dataStore.data.map { prefs ->
        prefs[playbackSpeedKey(profileKey)] ?: 1.0f
    }

    suspend fun setPlaybackSpeed(profileKey: AccountProfileKey, speed: Float) {
        dataStore.edit { prefs ->
            prefs[playbackSpeedKey(profileKey)] = speed
        }
    }

    // ── Downloads ────────────────────────────────────────────────────────

    fun downloadsWifiOnly(profileKey: AccountProfileKey): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[downloadsWifiOnlyKey(profileKey)] ?: true
    }

    suspend fun setDownloadsWifiOnly(profileKey: AccountProfileKey, wifiOnly: Boolean) {
        dataStore.edit { prefs ->
            prefs[downloadsWifiOnlyKey(profileKey)] = wifiOnly
        }
    }

    // ── Cache Timestamps ─────────────────────────────────────────────────

    fun catalogCacheTimestamp(profileKey: AccountProfileKey): Flow<Long?> = dataStore.data.map { prefs ->
        prefs[catalogCacheTimestampKey(profileKey)]
    }

    suspend fun setCatalogCacheTimestamp(profileKey: AccountProfileKey, timestamp: Long) {
        dataStore.edit { prefs ->
            prefs[catalogCacheTimestampKey(profileKey)] = timestamp
        }
    }

    fun detailCacheTimestamp(profileKey: AccountProfileKey): Flow<Long?> = dataStore.data.map { prefs ->
        prefs[detailCacheTimestampKey(profileKey)]
    }

    suspend fun setDetailCacheTimestamp(profileKey: AccountProfileKey, timestamp: Long) {
        dataStore.edit { prefs ->
            prefs[detailCacheTimestampKey(profileKey)] = timestamp
        }
    }

    // ── Cleanup ──────────────────────────────────────────────────────────

    /**
     * Clears all preferences for the given profile key.
     * Used during "Reset preferences" logout cleanup.
     */
    suspend fun clearProfilePreferences(profileKey: AccountProfileKey) {
        dataStore.edit { prefs ->
            val keysToRemove =
                prefs.asMap().keys.filter { key ->
                    key.name.startsWith("${profileKey.value}_")
                }
            keysToRemove.forEach { prefs.remove(it) }
        }
    }
}
