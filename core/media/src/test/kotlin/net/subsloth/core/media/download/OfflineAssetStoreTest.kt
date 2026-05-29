package net.subsloth.core.media.download

import net.subsloth.core.model.download.OfflineRelativePath
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempDirectory

class OfflineAssetStoreTest {
    @Test
    fun `verifyPlayable detects existing and missing files`() {
        val tempDir = createTempDirectory("offline-test").toFile()
        try {
            val store = OfflineAssetStore(tempDir)
            val videoFile = store.finalVideo(OfflineRelativePath("downloads/video/7/main.mp4"))
            videoFile.parentFile!!.mkdirs()
            videoFile.writeBytes(byteArrayOf(0x01))

            assertThat(store.verifyPlayable(videoFile)).isTrue()

            videoFile.delete()
            assertThat(store.verifyPlayable(videoFile)).isFalse()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `deletePartial removes only staged files`() {
        val tempDir = createTempDirectory("offline-test").toFile()
        try {
            val store = OfflineAssetStore(tempDir)
            val relativePath = OfflineRelativePath("downloads/video/7/main.mp4")
            val staged = store.stageVideo(relativePath)
            staged.parentFile!!.mkdirs()
            staged.createNewFile()

            assertThat(staged.exists()).isTrue()

            store.deletePartial(relativePath)
            assertThat(staged.exists()).isFalse()
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
