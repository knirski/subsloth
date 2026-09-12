package net.subsloth

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.subsloth.desktop.DesktopContainer
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopContainerDataDirTest {
    @Test
    fun `dataDirOverride keeps preferences inside the overridden directory`() {
        val dataDir = Files.createTempDirectory("subsloth-container-test").toFile()
        try {
            val container = DesktopContainer(dataDir)
            runBlocking { container.userPreferences.setApiBaseUrl(API_BASE_URL) }

            val prefsFile = File(dataDir, "subsloth.preferences_pb")
            assertTrue(prefsFile.isFile, "Expected preferences at ${prefsFile.absolutePath}")
            assertEquals(API_BASE_URL, runBlocking { container.userPreferences.storedApiBaseUrl().first() })
        } finally {
            dataDir.deleteRecursively()
        }
    }

    private companion object {
        private const val API_BASE_URL = "https://example.invalid/api/v2/"
    }
}
