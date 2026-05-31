package subsloth.core.media.download

import subsloth.core.model.download.OfflineRelativePath
import java.io.File

/**
 * App-private storage for downloaded media assets.
 *
 * Manages staging (`.part` suffix), finalization, and verification of
 * downloaded files within the app's private files directory.
 *
 * @param filesDir The app's private files directory (e.g. `context.filesDir`).
 */
class OfflineAssetStore(private val filesDir: File) {
    /**
     * Returns the staged (partial download) file path.
     * The file may not exist yet; callers should create it after writing.
     */
    fun stageVideo(relativePath: OfflineRelativePath): File = File(filesDir, "${relativePath.value}.part")

    /**
     * Returns the finalized download file path.
     * After a download completes, the staged file should be renamed to this path.
     */
    fun finalVideo(relativePath: OfflineRelativePath): File = File(filesDir, relativePath.value)

    /**
     * Verifies that a file exists and has non-zero size, indicating it is playable.
     */
    fun verifyPlayable(file: File): Boolean = file.exists() && file.length() > 0L

    /**
     * Deletes a partially downloaded file (with `.part` suffix) if it exists.
     */
    fun deletePartial(relativePath: OfflineRelativePath) {
        val file = stageVideo(relativePath)
        if (file.exists() && file.name.endsWith(".part")) {
            file.delete()
        }
    }
}
