package net.subsloth.core.domain.policy

object OfflineHomePolicy {
    fun shouldSurfaceOfflineLibrary(
        isOnline: Boolean,
        playableDownloads: Int,
    ): Boolean = !isOnline && playableDownloads > 0
}
