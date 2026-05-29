package net.subsloth.core.media.download

import net.subsloth.core.model.download.OfflineRelativePath
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class OfflineAssetStoreTest {
    @Test
    fun `subtitle deletion does not delete video asset`() {
        val tempDir = createTempDirectory("offline-test").toFile()
        try {
            val videoFile = File(tempDir, "downloads/video/7/main.mp4")
            val subtitleFile = File(tempDir, "downloads/subtitles/7/pl.srt")

            videoFile.parentFile!!.mkdirs()
            subtitleFile.parentFile!!.mkdirs()
            videoFile.createNewFile()
            subtitleFile.createNewFile()

            subtitleFile.delete()

            assertThat(videoFile.exists()).isTrue()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `store creates staged and finalized video paths`() {
        val tempDir = createTempDirectory("offline-test").toFile()
        try {
            val store = OfflineAssetStore(tempDir)

            val staged = store.stageVideo(OfflineRelativePath("downloads/video/7/main.mp4"))
            staged.parentFile!!.mkdirs()
            staged.createNewFile()

            assertThat(staged.name).endsWith(".part")
            assertThat(staged.exists()).isTrue()

            val finalized =
                store.finalVideo(OfflineRelativePath("downloads/video/7/main.mp4"))
            assertThat(finalized.name).isEqualTo("main.mp4")
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
