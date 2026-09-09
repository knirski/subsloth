package net.subsloth.core.media.download

import net.subsloth.core.model.download.OfflineRelativePath
import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopDownloadStoreTest {

    private val tempDir = Files.createTempDirectory("subsloth-downloads-test")
    private val store = DesktopDownloadStore(tempDir.toFile())

    @Test
    fun `allocatePath creates content directory under storage root`() {
        val relative = store.allocatePath(contentId = "42", extension = "mp4")
        assertTrue(store.finalFile(relative).parentFile.isDirectory)
    }

    @Test
    fun `stageFile and finalFile live under the storage root`() {
        val relative = OfflineRelativePath.safe("42/abc.mp4")
        assertTrue(store.stageFile(relative).path.startsWith(tempDir.absolutePathString()))
        assertTrue(store.finalFile(relative).path.startsWith(tempDir.absolutePathString()))
        assertTrue(store.stageFile(relative).name.endsWith(".part"))
    }

    @Test
    fun `storeStream writes bytes and finalizeDownload renames the staged file`() {
        val relative = store.allocatePath(contentId = "42", extension = "mp4")
        val staged = store.stageFile(relative)
        val written = store.storeStream("hello".byteInputStream(), staged)
        val target = store.finalFile(relative)
        assertTrue(store.finalizeDownload(staged, target))
        assertEquals(5L, written)
        assertTrue(store.verifyFile(relative))
        assertFalse(staged.exists())
    }

    @Test
    fun `deleteMedia removes both finalized and staged files`() {
        val relative = store.allocatePath(contentId = "7", extension = "mp4")
        val target = store.finalFile(relative)
        target.parentFile?.mkdirs()
        target.writeText("data")
        store.stageFile(relative).writeText("partial")
        assertTrue(store.deleteMedia(relative))
        assertFalse(target.exists())
        assertFalse(store.stageFile(relative).exists())
    }

    @Test
    fun `deleteMedia on missing files reports success`() {
        val relative = OfflineRelativePath.safe("nothing/missing.mp4")
        assertTrue(store.deleteMedia(relative))
    }

    @Test
    fun `verifyFile fails for empty or missing files`() {
        val relative = store.allocatePath(contentId = "8", extension = "mp4")
        store.finalFile(relative).writeText("")
        assertFalse(store.verifyFile(relative))
        assertFalse(store.verifyFile(OfflineRelativePath.safe("8/absent.mp4")))
    }
}
