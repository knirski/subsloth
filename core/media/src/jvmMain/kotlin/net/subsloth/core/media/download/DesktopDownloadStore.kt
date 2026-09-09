package net.subsloth.core.media.download

import net.subsloth.core.media.playback.OfflineAssetFiles
import net.subsloth.core.model.download.OfflineRelativePath
import java.io.File
import java.util.UUID

/**
 * Desktop (JVM) download artifact store — the counterpart of Android's
 * `DownloadStorageManager` (androidMain), rooted at an app-private
 * directory (e.g. `<dataDir>/downloads`) instead of Android's
 * `noBackupFilesDir`.
 *
 * Same staging contract: writes land in a `.part` sibling and are
 * renamed into place on finalization.
 */
class DesktopDownloadStore(private val filesDir: File) :
    DownloadFileStore,
    OfflineAssetFiles,
    DownloadTransferStore {

    init {
        filesDir.mkdirs()
    }

    override fun allocatePath(contentId: String, extension: String): OfflineRelativePath =
        allocatePath(contentId, extension, UUID.randomUUID().toString())

    private fun allocatePath(contentId: String, extension: String, fileName: String): OfflineRelativePath {
        val dir = filesDir.resolve(contentId).also { it.mkdirs() }
        val relative = "$contentId/$fileName$extension"
        return OfflineRelativePath.safe(relative)
    }

    override fun stageFile(relativePath: OfflineRelativePath): File = File(filesDir, "${relativePath.value}.part")

    override fun finalFile(relativePath: OfflineRelativePath): File = File(filesDir, relativePath.value)

    fun storeStream(inputStream: java.io.InputStream, targetFile: File): Long {
        targetFile.parentFile?.mkdirs()
        return targetFile.outputStream().use { output ->
            inputStream.copyTo(output)
        }
    }

    override fun finalizeDownload(staged: File, target: File): Boolean {
        target.parentFile?.mkdirs()
        return staged.renameTo(target)
    }

    override fun deleteMedia(localPath: OfflineRelativePath): Boolean {
        val file = finalFile(localPath)
        val staged = stageFile(localPath)
        val deletedFile = if (file.exists()) file.delete() else true
        val deletedStaged = if (staged.exists()) staged.delete() else true
        return deletedFile && deletedStaged
    }

    override fun verifyFile(localPath: OfflineRelativePath): Boolean {
        val file = finalFile(localPath)
        return file.exists() && file.length() > 0L
    }

    override fun fileUri(localPath: OfflineRelativePath): String = finalFile(localPath).toURI().toString()
}
