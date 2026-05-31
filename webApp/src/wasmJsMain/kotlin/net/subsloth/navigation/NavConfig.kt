package net.subsloth.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

val subslothNavConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(LoginKey::class, LoginKey.serializer())
            subclass(CatalogKey::class, CatalogKey.serializer())
            subclass(MovieDetailKey::class, MovieDetailKey.serializer())
            subclass(ShowDetailKey::class, ShowDetailKey.serializer())
            subclass(PlayerKey::class, PlayerKey.serializer())
            subclass(LibraryKey::class, LibraryKey.serializer())
            subclass(DownloadsKey::class, DownloadsKey.serializer())
            subclass(SettingsKey::class, SettingsKey.serializer())
            subclass(DiagnosticsKey::class, DiagnosticsKey.serializer())
            subclass(AuthRepairKey::class, AuthRepairKey.serializer())
            subclass(OfflineLibraryKey::class, OfflineLibraryKey.serializer())
        }
    }
}
