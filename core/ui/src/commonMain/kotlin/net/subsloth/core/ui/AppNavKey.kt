package net.subsloth.core.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Typed navigation keys for the app's Navigation3 graph.
 *
 * Each route is a [NavKey] subclass annotated with [Serializable] so
 * the back stack survives process death. Parameters (e.g. movieId,
 * contentId) are constructor properties of the data class.
 */
@Serializable
sealed interface AppNavKey : NavKey

/** Login / auth screen. */
@Serializable
data object LoginKey : AppNavKey

/** Catalog home with tab state. */
@Serializable
data object CatalogKey : AppNavKey

/** Movie detail. */
@Serializable
data class MovieDetailKey(val movieId: String) : AppNavKey

/** Series/Show detail. */
@Serializable
data class ShowDetailKey(val showId: String) : AppNavKey

/** Video player. */
@Serializable
data class PlayerKey(val contentId: String, val contentType: String) : AppNavKey

/** Library screen. */
@Serializable
data object LibraryKey : AppNavKey

/** Downloads screen. */
@Serializable
data object DownloadsKey : AppNavKey

/** Settings screen. */
@Serializable
data object SettingsKey : AppNavKey

/** Diagnostics screen. */
@Serializable
data object DiagnosticsKey : AppNavKey

/** Auth repair screen after a playback auth failure. */
@Serializable
data object AuthRepairKey : AppNavKey

/** Offline library (logged-out state). */
@Serializable
data object OfflineLibraryKey : AppNavKey
