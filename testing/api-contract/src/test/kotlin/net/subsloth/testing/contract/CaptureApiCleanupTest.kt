package net.subsloth.testing.contract

import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class CaptureApiCleanupTest {
    @Test
    fun `cleanup native fixture outputs removes known fixtures but keeps unrelated files`() {
        val tempDir = Files.createTempDirectory("capture-api-cleanup-").toFile()
        val knownFixture = File(tempDir, "Movies.json").apply { writeText("{}") }
        val staleDetail = File(tempDir, "EpisodeDetail.json").apply { writeText("{}") }
        val unrelated = File(tempDir, "keep-me.txt").apply { writeText("keep") }

        CaptureApi.cleanupNativeFixtureOutputs(tempDir)

        assertThat(knownFixture.exists()).isFalse()
        assertThat(staleDetail.exists()).isFalse()
        assertThat(unrelated.exists()).isTrue()
    }
}
