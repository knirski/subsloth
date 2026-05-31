package net.subsloth.core.domain.policy

/** Pure policies for deciding when to surface the offline library on the home screen. */
object OfflineHomePolicy {
    fun shouldSurfaceOfflineLibrary(isOnline: Boolean, playableDownloads: Int): Boolean =
        !isOnline && playableDownloads > 0
}
