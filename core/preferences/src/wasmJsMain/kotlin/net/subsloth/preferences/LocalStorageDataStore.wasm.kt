@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package net.subsloth.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class PrefEntry(val type: String, val value: String)

/**
 * Browser localStorage-backed [DataStore] for wasmJs.
 *
 * Okio 3.17 does not provide `FileSystem.SYSTEM` for browser wasmJs,
 * so [PreferenceDataStoreFactory.createWithPath] cannot be used.
 * This implementation persists preferences as a single JSON blob in
 * `localStorage`, which survives page reloads.
 *
 * If the stored data is corrupt, it is cleared and empty preferences
 * are returned (equivalent to file deletion in the native DataStore).
 */
internal class LocalStorageDataStore(private val storageKey: String) : DataStore<Preferences> {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val mutex = Mutex()
    private val _data = MutableStateFlow(loadFromStorage())

    override val data: Flow<Preferences> = _data.asStateFlow()

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
        val current = _data.value
        val new = transform(current)
        saveToStorage(new)
        _data.value = new
        return new
    }

    private fun loadFromStorage(): Preferences {
        val raw = localStorage.getItem(storageKey) ?: return preferencesOf()
        return try {
            val map = json.decodeFromString<Map<String, PrefEntry>>(raw)
            val pairs = map.mapNotNull { (name, entry) ->
                @Suppress("UNCHECKED_CAST")
                val key: Preferences.Key<Any> = when (entry.type) {
                    "str" -> stringPreferencesKey(name) as Preferences.Key<Any>
                    "bool" -> booleanPreferencesKey(name) as Preferences.Key<Any>
                    "float" -> floatPreferencesKey(name) as Preferences.Key<Any>
                    "long" -> longPreferencesKey(name) as Preferences.Key<Any>
                    "double" -> doublePreferencesKey(name) as Preferences.Key<Any>
                    "int" -> intPreferencesKey(name) as Preferences.Key<Any>
                    "set" -> stringSetPreferencesKey(name) as Preferences.Key<Any>
                    else -> return@mapNotNull null
                }
                val value: Any = when (entry.type) {
                    "str" -> entry.value
                    "bool" -> entry.value.toBoolean()
                    "float" -> entry.value.toFloat()
                    "long" -> entry.value.toLong()
                    "double" -> entry.value.toDouble()
                    "int" -> entry.value.toInt()
                    "set" -> json.decodeFromString<List<String>>(entry.value).toSet()
                    else -> return@mapNotNull null
                }
                key to value
            }
            preferencesOf(*pairs.toTypedArray())
        } catch (_: kotlinx.serialization.SerializationException) {
            localStorage.removeItem(storageKey)
            preferencesOf()
        } catch (_: NumberFormatException) {
            localStorage.removeItem(storageKey)
            preferencesOf()
        } catch (_: IllegalArgumentException) {
            localStorage.removeItem(storageKey)
            preferencesOf()
        }
    }

    private fun saveToStorage(prefs: Preferences) {
        try {
            val map = mutableMapOf<String, PrefEntry>()
            for ((key, value) in prefs.asMap()) {
                @Suppress("UNCHECKED_CAST")
                val entry = when (value) {
                    is String -> PrefEntry("str", value)
                    is Boolean -> PrefEntry("bool", value.toString())
                    is Float -> PrefEntry("float", value.toString())
                    is Long -> PrefEntry("long", value.toString())
                    is Double -> PrefEntry("double", value.toString())
                    is Int -> PrefEntry("int", value.toString())
                    is Set<*> -> PrefEntry("set", json.encodeToString((value as Set<String>).toList()))
                    else -> continue
                }
                map[key.name] = entry
            }
            localStorage.setItem(storageKey, json.encodeToString(map))
        } catch (_: Exception) {
            // Fail silently if localStorage is disabled or full (e.g. QuotaExceededError)
        }
    }
}
