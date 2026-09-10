package net.subsloth.desktop

import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * Regression test for the Kotlin property-initialization-order crash that
 * broke app startup: `currentPlaybackPort`'s eager initializer forces the
 * `offlineSourceResolver` lazy, which reads other lazy delegates. If any
 * dependency is declared after its reader in the class, construction fails
 * with "Cannot invoke Lazy.getValue() because <delegate> is null" (see
 * #239's OfflineFirstPlaybackPort wiring). Constructing the container here
 * keeps that ordering enforced by the compiler+runtime themselves.
 *
 * `user.home` is pointed at a temp dir because the JVM DataStore factory
 * resolves the app-data dir from it (independent of [DesktopContainer]'s
 * `dataDirOverride`, which only redirects Room/downloads) — the test must
 * not touch the developer's real preferences.
 */
class DesktopContainerConstructionTest {
    @Test
    fun `container constructs without initialization-order failures`() {
        val fakeHome = Files.createTempDirectory("subsloth-desktop-test-home").toFile()
        val originalHome = System.getProperty("user.home")
        System.setProperty("user.home", fakeHome.path)
        try {
            DesktopContainer(
                dataDirOverride = Files.createTempDirectory("subsloth-desktop-test-data").toFile(),
            )
        } finally {
            System.setProperty("user.home", originalHome)
            fakeHome.deleteRecursively()
        }
    }
}
