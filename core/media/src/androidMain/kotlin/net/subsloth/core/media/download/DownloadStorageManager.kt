package net.subsloth.core.media.download

import android.content.Context
import android.net.Uri
import net.subsloth.core.media.playback.OfflineAssetFiles
import net.subsloth.core.model.download.OfflineRelativePath
import net.subsloth.core.model.media.Media
import java.io.File
import java.io.InputStream

class DownloadStorageManager(private val context: Context) :
    DownloadFileStore,
    OfflineAssetFiles,
    DownloadTransferStore {
    private val storageDir: File
        get() = context.noBackupFilesDir.resolve("downloads").also { it.mkdirs() }

    override fun allocatePath(contentId: String, extension: String): OfflineRelativePath =
        allocatePath(contentId, extension, java.util.UUID.randomUUID().toString())

    private fun allocatePath(contentId: String, extension: String, fileName: String): OfflineRelativePath {
        val dir = storageDir.resolve(contentId).also { it.mkdirs() }
        val relative = "$contentId/$fileName$extension"
        return OfflineRelativePath.safe(relative)
    }

    override fun stageFile(relativePath: OfflineRelativePath): File = File(storageDir, "${relativePath.value}.part")

    override fun finalFile(relativePath: OfflineRelativePath): File = File(storageDir, relativePath.value)

    fun storeStream(inputStream: InputStream, targetFile: File): Long {
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

    fun getContentUri(localPath: OfflineRelativePath): Uri = Uri.fromFile(finalFile(localPath))

    override fun fileUri(localPath: OfflineRelativePath): String = getContentUri(localPath).toString()
}
