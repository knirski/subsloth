package net.subsloth.core.media.download

import android.content.Context
import android.net.Uri
import net.subsloth.core.model.download.OfflineRelativePath
import net.subsloth.core.model.media.Media
import java.io.File
import java.io.InputStream
import java.util.UUID

class DownloadStorageManager(private val context: Context) {
    private val storageDir: File
        get() = context.noBackupFilesDir.resolve("downloads").also { it.mkdirs() }

    fun allocatePath(
        contentId: String,
        extension: String,
        fileName: String = UUID.randomUUID().toString(),
    ): OfflineRelativePath {
        val dir = storageDir.resolve(contentId).also { it.mkdirs() }
        val relative = "$contentId/$fileName$extension"
        return OfflineRelativePath.safe(relative)
    }

    fun stageFile(relativePath: OfflineRelativePath): File = File(storageDir, "${relativePath.value}.part")

    fun finalFile(relativePath: OfflineRelativePath): File = File(storageDir, relativePath.value)

    fun storeStream(inputStream: InputStream, targetFile: File): Long {
        targetFile.parentFile?.mkdirs()
        return targetFile.outputStream().use { output ->
            inputStream.copyTo(output)
        }
    }

    fun finalizeDownload(staged: File, target: File): Boolean {
        target.parentFile?.mkdirs()
        return staged.renameTo(target)
    }

    fun deleteMedia(localPath: OfflineRelativePath): Boolean {
        val file = finalFile(localPath)
        val staged = stageFile(localPath)
        val deletedFile = if (file.exists()) file.delete() else true
        val deletedStaged = if (staged.exists()) staged.delete() else true
        return deletedFile && deletedStaged
    }

    fun verifyFile(localPath: OfflineRelativePath): Boolean {
        val file = finalFile(localPath)
        return file.exists() && file.length() > 0L
    }

    fun getContentUri(localPath: OfflineRelativePath): Uri = Uri.fromFile(finalFile(localPath))
}
