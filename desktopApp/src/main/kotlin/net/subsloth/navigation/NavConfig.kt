package net.subsloth.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import net.subsloth.core.ui.AppNavKey
import net.subsloth.core.ui.AuthRepairKey
import net.subsloth.core.ui.CatalogKey
import net.subsloth.core.ui.DiagnosticsKey
import net.subsloth.core.ui.DownloadsKey
import net.subsloth.core.ui.LibraryKey
import net.subsloth.core.ui.LoginKey
import net.subsloth.core.ui.MovieDetailKey
import net.subsloth.core.ui.OfflineLibraryKey
import net.subsloth.core.ui.PlayerKey
import net.subsloth.core.ui.SettingsKey
import net.subsloth.core.ui.ShowDetailKey

/**
 * SavedStateConfiguration for Navigation3 on desktop.
 *
 * Registers every [AppNavKey] subclass as a polymorphic [NavKey] subtype so
 * that [rememberNavBackStack] can serialize/deserialize the back stack.
 * Required on non-Android targets — without it [rememberNavBackStack] throws.
 */
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
