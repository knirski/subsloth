package net.subsloth.database

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import net.subsloth.core.domain.port.LibraryPort
import net.subsloth.core.domain.port.Session
import net.subsloth.core.domain.port.SessionPort
import net.subsloth.core.model.error.LibraryError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.library.LibraryCollection
import net.subsloth.core.model.library.LibraryItem
import net.subsloth.core.model.media.Media
import net.subsloth.database.entity.FavoriteEntity
import net.subsloth.database.entity.LocalLibraryRecordEntity
import kotlin.time.Instant

/**
 * Production implementation of [LibraryPort].
 *
 * Persists favorites to the [FavoriteDao] table and custom-library items
 * to the [LocalLibraryRecordDao] table, both scoped by the active
 * session's user profile key.
 */
class LibraryPortAdapter(
    private val favoriteDao: net.subsloth.database.dao.FavoriteDao,
    private val localLibraryDao: net.subsloth.database.dao.LocalLibraryRecordDao,
    private val sessionPort: SessionPort,
) : LibraryPort {

    private val log = Logger.withTag("LibraryPortAdapter")

    companion object {
        private const val DEFAULT_PROFILE_KEY = "default"
    }

    override suspend fun listLibrary(): Outcome<List<LibraryItem>> = try {
        val key = profileKey()
        val favorites = favoriteDao.getAllForProfile(key).first().map { it.toLibraryItem() }
        val custom = localLibraryDao.getAllForProfile(key).first().map { it.toLibraryItem() }
        Outcome.Success(favorites + custom)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.e(e) { "listLibrary failed" }
        Outcome.Failure(LibraryError.NotSupported)
    }

    override suspend fun addToLibrary(item: LibraryItem): Outcome<Unit> = try {
        val key = profileKey()
        val contentId = item.mediaId.toContentId()
        val contentType = item.mediaId.toContentType()
        when (item.collection) {
            LibraryCollection.FAVORITES -> favoriteDao.upsert(
                FavoriteEntity(profileKey = key, contentId = contentId, contentType = contentType),
            )

            LibraryCollection.CUSTOM -> localLibraryDao.upsert(
                LocalLibraryRecordEntity(
                    profileKey = key,
                    contentId = contentId,
                    contentType = contentType,
                    addedAtEpochSeconds = item.addedAtEpochSeconds.epochSeconds,
                ),
            )

            LibraryCollection.HISTORY -> {
                // HISTORY collection is managed by playback progress tracking
            }
        }
        Outcome.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.e(e) { "addToLibrary failed" }
        Outcome.Failure(LibraryError.NotSupported)
    }

    override suspend fun removeFromLibrary(mediaId: Media.MediaId): Outcome<Unit> = try {
        val key = profileKey()
        val contentId = mediaId.toContentId()

        val favorite = favoriteDao.getByProfileAndContentId(key, contentId)
        if (favorite != null) favoriteDao.delete(favorite)

        val localRecord = localLibraryDao.getByProfileAndContentId(key, contentId)
        if (localRecord != null) localLibraryDao.delete(localRecord)

        Outcome.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.e(e) { "removeFromLibrary failed" }
        Outcome.Failure(LibraryError.NotSupported)
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun profileKey(): String = when (val session = sessionPort.current()) {
        is Session.Anonymous -> DEFAULT_PROFILE_KEY
        is Session.Authenticated -> session.userId
    }

    private fun Media.MediaId.toContentId(): String = when (this) {
        is Media.MediaId.Movie -> value.value.toString()
        is Media.MediaId.Show -> value.value.toString()
        is Media.MediaId.Episode -> value.value.toString()
    }

    private fun Media.MediaId.toContentType(): String = when (this) {
        is Media.MediaId.Movie -> "movie"
        is Media.MediaId.Show -> "show"
        is Media.MediaId.Episode -> "episode"
    }

    private fun FavoriteEntity.toLibraryItem(): LibraryItem = LibraryItem(
        mediaId = parseMediaId(contentId, contentType),
        collection = LibraryCollection.FAVORITES,
        addedAtEpochSeconds = Instant.fromEpochSeconds(0),
        sortOrder = id.toInt(),
    )

    private fun LocalLibraryRecordEntity.toLibraryItem(): LibraryItem = LibraryItem(
        mediaId = parseMediaId(contentId, contentType),
        collection = LibraryCollection.CUSTOM,
        addedAtEpochSeconds = Instant.fromEpochSeconds(addedAtEpochSeconds),
        sortOrder = id.toInt(),
    )

    private fun parseMediaId(contentId: String, contentType: String): Media.MediaId = when (contentType) {
        "movie" -> Media.MediaId.Movie(MovieId(contentId.toInt()))
        "show" -> Media.MediaId.Show(ShowId(contentId.toInt()))
        "episode" -> Media.MediaId.Episode(EpisodeId(contentId.toInt()))
        else -> error("Unknown content type: $contentType")
    }
}
