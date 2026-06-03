package net.subsloth.core.media.download

import android.content.Context
import net.subsloth.core.domain.policy.DownloadPolicy
import net.subsloth.core.domain.port.StoragePort
import java.io.File

class StorageProvider(context: Context) : StoragePort {
    private val storageDir: File =
        context.noBackupFilesDir.resolve("downloads").also { it.mkdirs() }

    override fun availableBytes(): Long = storageDir.usableSpace

    override fun totalBytes(): Long = storageDir.totalSpace

    override fun reserveBytes(): Long = DownloadPolicy.requiredReserveBytes(totalBytes())
}
