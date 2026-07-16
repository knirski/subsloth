package net.subsloth.core.model.download

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import net.subsloth.core.model.Availability
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Instant

class DownloadStateInvariantsTest {
    private val localId = LocalMediaIdentifier("movie_100")
    private val mediaId = Media.MediaId.Movie(MovieId(100))
    private val quality = QualityDescriptor(Resolution(1920, 1080), "1080p", null, null)

    @Test
    fun `Active requires progressPercent between 0 and 100`() {
        DownloadState.Active(localId, mediaId, quality, progressPercent = 0)
        DownloadState.Active(localId, mediaId, quality, progressPercent = 50)
        DownloadState.Active(localId, mediaId, quality, progressPercent = 100)
    }

    @Test
    fun `Active rejects progressPercent below 0`() {
        assertThrows<IllegalArgumentException> {
            DownloadState.Active(localId, mediaId, quality, progressPercent = -1)
        }
    }

    @Test
    fun `Active rejects progressPercent above 100`() {
        assertThrows<IllegalArgumentException> {
            DownloadState.Active(localId, mediaId, quality, progressPercent = 101)
        }
    }

    @Test
    fun `Completed requires non-negative sizeBytes when provided`() {
        DownloadState.Completed(
            localId,
            mediaId,
            quality,
            downloadedAtEpochSeconds = Instant.DISTANT_PAST,
            sizeBytes = 0L,
            videoPath = OfflineRelativePath("videos/100.mp4"),
        )
        DownloadState.Completed(
            localId,
            mediaId,
            quality,
            downloadedAtEpochSeconds = Instant.DISTANT_PAST,
            sizeBytes = 1024L,
            videoPath = OfflineRelativePath("videos/100.mp4"),
        )
        DownloadState.Completed(
            localId,
            mediaId,
            quality,
            downloadedAtEpochSeconds = Instant.DISTANT_PAST,
            sizeBytes = null,
            videoPath = OfflineRelativePath("videos/100.mp4"),
        )
    }

    @Test
    fun `Completed rejects negative sizeBytes`() {
        assertThrows<IllegalArgumentException> {
            DownloadState.Completed(
                localId,
                mediaId,
                quality,
                downloadedAtEpochSeconds = Instant.DISTANT_PAST,
                sizeBytes = -1L,
                videoPath = OfflineRelativePath("videos/100.mp4"),
            )
        }
    }

    @Test
    fun `Queued provides defaults`() {
        val q = DownloadState.Queued(localId, mediaId, quality)
        assertThat(q.subtitleLanguages).isEmpty()
        assertThat(q.queueId).isNull()
    }

    @Test
    fun `Failed carries reason`() {
        val f = DownloadState.Failed(localId, mediaId, quality, reason = DownloadFailureReason.NeedsWifi)
        assertThat(f.reason).isEqualTo(DownloadFailureReason.NeedsWifi)
    }

    @Test
    fun `Paused carries reason`() {
        val p = DownloadState.Paused(localId, mediaId, quality, reason = DownloadFailureReason.InsufficientStorage)
        assertThat(p.reason).isEqualTo(DownloadFailureReason.InsufficientStorage)
    }

    @Test
    fun `Removed carries no extra fields`() {
        val r = DownloadState.Removed(localId, mediaId, quality)
        assertThat(r.subtitleLanguages).isEmpty()
    }

    @Test
    fun `Unavailable carries reason`() {
        val u = DownloadState.Unavailable(localId, mediaId, quality, reason = DownloadFailureReason.MissingLocalFile)
        assertThat(u.reason).isEqualTo(DownloadFailureReason.MissingLocalFile)
    }

    @Test
    fun `Partial carries staged path`() {
        val path = OfflineRelativePath("videos/staged/100.part")
        val p = DownloadState.Partial(localId, mediaId, quality, stagedPath = path)
        assertThat(p.stagedPath.value).isEqualTo("videos/staged/100.part")
    }
}
