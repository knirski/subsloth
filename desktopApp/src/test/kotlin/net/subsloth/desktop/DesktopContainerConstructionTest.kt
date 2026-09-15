package net.subsloth.desktop

import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * Regression test for the Kotlin property-initialization-order crash that
 * broke app startup: `currentPlaybackPort`'s eager initializer forces the
 * `offlineSourceResolver` lazy, which reads other lazy delegates. If any
 * dependency is declared after its reader in the class, construction fails
 * with "Cannot invoke Lazy.getValue() because <delegate> is null" (see
 * #239's OfflineFirstPlaybackPort wiring). Constructing the container here
 * keeps that ordering enforced by the compiler+runtime themselves.
 *
 * It uses a temp data directory and closes the container, so the test
 * neither touches the developer's real preferences nor mutates global JVM
 * state (`user.home`) that other tests read while constructing their own
 * containers.
 */
class DesktopContainerConstructionTest {
    @Test
    fun `container constructs without initialization-order failures`() {
        val dataDir = Files.createTempDirectory("subsloth-desktop-test-data").toFile()
        try {
            DesktopContainer(dataDirOverride = dataDir).close()
        } finally {
            dataDir.deleteRecursively()
        }
    }

    @Test
    fun `close cancels background work and closes the database`() {
        val dataDir = Files.createTempDirectory("subsloth-desktop-close-test").toFile()
        try {
            val container = DesktopContainer(dataDirOverride = dataDir)
            val countBefore = runBlocking { container.database.cachedCatalogDao().count() }
            assertEquals(0, countBefore)

            container.close()

            assertFalse(container.externalScope.isActive, "close() must cancel the container scope")
            assertFailsWith<IllegalStateException> {
                runBlocking { container.database.cachedCatalogDao().count() }
            }
        } finally {
            dataDir.deleteRecursively()
        }
    }
}
