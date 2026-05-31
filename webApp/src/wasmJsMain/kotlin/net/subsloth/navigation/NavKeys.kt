@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package net.subsloth.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppNavKey : NavKey

@Serializable
data object LoginKey : AppNavKey

@Serializable
data object CatalogKey : AppNavKey

@Serializable
data class MovieDetailKey(val movieId: String) : AppNavKey

@Serializable
data class ShowDetailKey(val showId: String) : AppNavKey

@Serializable
data class PlayerKey(val contentId: String, val contentType: String) : AppNavKey

@Serializable
data object LibraryKey : AppNavKey

@Serializable
data object DownloadsKey : AppNavKey

@Serializable
data object SettingsKey : AppNavKey

@Serializable
data object DiagnosticsKey : AppNavKey

@Serializable
data object AuthRepairKey : AppNavKey

@Serializable
data object OfflineLibraryKey : AppNavKey
