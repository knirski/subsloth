package net.subsloth.core.media.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.subsloth.core.domain.policy.DownloadPolicy
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.EnqueueOutcome
import net.subsloth.core.model.download.OfflineRelativePath
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/**
 * Integration tests for the refactored [DownloadController.enqueue] logic path.
 *
 * These tests exercise the same data flow — entity → DownloadState → DownloadPolicy
 * — that the refactored enqueue() uses. The DAO layer is exercised separately in
 * [net.subsloth.database.RoomDaoTest].
 */
class DownloadControllerIntegrationTest {

    private val movieId = Media.MediaId.Movie(MovieId(1))

    private fun completedDownload(resolution: Resolution = Resolution.HD_720, label: String = "720p") =
        DownloadState.Completed(
            localId = LocalMediaIdentifier("1/1"),
            mediaId = movieId,
            quality = QualityDescriptor(resolution = resolution, label = label, bitrate = null, mimeType = null),
            downloadedAtEpochSeconds = Instant.fromEpochSeconds(1000),
            sizeBytes = 1024L,
            videoPath = OfflineRelativePath.safe("test.mp4"),
        )

    private fun queuedDownload() = DownloadState.Queued(
        localId = LocalMediaIdentifier("1/1"),
        mediaId = movieId,
        quality = QualityDescriptor(resolution = Resolution.HD_720, label = "720p", bitrate = null, mimeType = null),
    )

    private fun activeDownload() = DownloadState.Active(
        localId = LocalMediaIdentifier("1/1"),
        mediaId = movieId,
        quality = QualityDescriptor(resolution = Resolution.HD_720, label = "720p", bitrate = null, mimeType = null),
        progressPercent = 50,
    )

    // ── Duplicate detection (same flow as enqueue) ──────────────────────

    @Test
    fun `completed download with equal quality is duplicate`() {
        assertThat(DownloadPolicy.isDuplicate(listOf(completedDownload()), movieId, Resolution.HD_720)).isTrue()
    }

    @Test
    fun `completed download with higher quality is duplicate`() {
        assertThat(
            DownloadPolicy.isDuplicate(
                listOf(completedDownload(resolution = Resolution.FULL_HD, label = "1080p")),
                movieId,
                Resolution.HD_720,
            ),
        ).isTrue()
    }

    @Test
    fun `completed download with lower quality allows re-download`() {
        assertThat(
            DownloadPolicy.isDuplicate(
                listOf(completedDownload(resolution = Resolution.HD_720, label = "720p")),
                movieId,
                Resolution.UHD_4K,
            ),
        ).isFalse()
    }

    @Test
    fun `completed download for different media is not duplicate`() {
        val otherId = Media.MediaId.Movie(MovieId(999))
        assertThat(DownloadPolicy.isDuplicate(listOf(completedDownload()), otherId, Resolution.HD_720)).isFalse()
    }

    // ── Active/queued blocking (same flow as enqueue) ────────────────────

    @Test
    fun `queued download blocks new download`() {
        assertThat(DownloadPolicy.canStartNewDownload(listOf(queuedDownload()), movieId)).isFalse()
    }

    @Test
    fun `active download blocks new download`() {
        assertThat(DownloadPolicy.canStartNewDownload(listOf(activeDownload()), movieId)).isFalse()
    }

    @Test
    fun `completed download allows new download`() {
        assertThat(DownloadPolicy.canStartNewDownload(listOf(completedDownload()), movieId)).isTrue()
    }

    // ── Cross-media isolation ───────────────────────────────────────────

    @Test
    fun `queued download for different media does not block`() {
        val otherId = Media.MediaId.Movie(MovieId(999))
        assertThat(DownloadPolicy.canStartNewDownload(listOf(queuedDownload()), otherId)).isTrue()
    }
}
