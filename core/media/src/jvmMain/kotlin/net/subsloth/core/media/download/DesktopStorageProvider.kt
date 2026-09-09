package net.subsloth.core.media.download

import net.subsloth.core.domain.policy.DownloadPolicy
import net.subsloth.core.domain.port.StoragePort
import java.io.File

/**
 * Desktop (JVM) [StoragePort] over the app-private downloads directory —
 * the counterpart of Android's `StorageProvider` (androidMain), which
 * reads the same space numbers from a `Context`-rooted directory.
 */
class DesktopStorageProvider(storageDir: File) : StoragePort {
    private val storageDir: File = storageDir.also { it.mkdirs() }

    override fun availableBytes(): Long = storageDir.usableSpace

    override fun totalBytes(): Long = storageDir.totalSpace

    override fun reserveBytes(): Long = DownloadPolicy.requiredReserveBytes(totalBytes())
}
