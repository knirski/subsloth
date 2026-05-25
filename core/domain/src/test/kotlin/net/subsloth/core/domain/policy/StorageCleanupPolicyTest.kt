package net.subsloth.core.domain.policy

import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.DownloadStatus
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class StorageCleanupPolicyTest {
    @Test
    fun `cleanupCandidates returns only completed downloads`() {
        val downloads = listOf(
            download(DownloadStatus.COMPLETED, downloadedAt = 1000L),
            download(DownloadStatus.DOWNLOADING, downloadedAt = 2000L),
            download(DownloadStatus.FAILED, downloadedAt = 3000L),
            download(DownloadStatus.COMPLETED, downloadedAt = 500L),
        )
        val candidates = StorageCleanupPolicy.cleanupCandidates(downloads)
        assertThat(candidates).hasSize(2)
        assertThat(candidates.all { it.status == DownloadStatus.COMPLETED }).isTrue()
    }

    @Test
    fun `cleanupCandidates sorts completed by oldest first`() {
        val downloads = listOf(
            download(DownloadStatus.COMPLETED, downloadedAt = 3000L),
            download(DownloadStatus.COMPLETED, downloadedAt = 1000L),
            download(DownloadStatus.COMPLETED, downloadedAt = 2000L),
        )
        val candidates = StorageCleanupPolicy.cleanupCandidates(downloads)
        assertThat(candidates[0].downloadedAtEpochSeconds).isEqualTo(Instant.fromEpochSeconds(1000L))
        assertThat(candidates[1].downloadedAtEpochSeconds).isEqualTo(Instant.fromEpochSeconds(2000L))
        assertThat(candidates[2].downloadedAtEpochSeconds).isEqualTo(Instant.fromEpochSeconds(3000L))
    }

    @Test
    fun `estimatedReclaimableBytes sums known sizes`() {
        val candidates = listOf(
            download(DownloadStatus.COMPLETED, sizeBytes = 500L),
            download(DownloadStatus.COMPLETED, sizeBytes = 300L),
            download(DownloadStatus.COMPLETED, sizeBytes = null),
        )
        val bytes = StorageCleanupPolicy.estimatedReclaimableBytes(candidates)
        assertThat(bytes).isEqualTo(800L)
    }

    @Test
    fun `estimatedReclaimableBytes returns zero for all null sizes`() {
        val candidates = listOf(
            download(DownloadStatus.COMPLETED, sizeBytes = null),
            download(DownloadStatus.COMPLETED, sizeBytes = null),
        )
        val bytes = StorageCleanupPolicy.estimatedReclaimableBytes(candidates)
        assertThat(bytes).isEqualTo(0L)
    }

    private fun download(
        status: DownloadStatus,
        downloadedAt: Long = 1_800_000_000L,
        sizeBytes: Long? = null,
    ): DownloadState =
        DownloadState(
            localId = LocalMediaIdentifier("local_$status"),
            mediaId = Media.MediaId.Movie(MovieId(42)),
            status = status,
            quality = QualityDescriptor(Resolution.FULL_HD, "1080p", null, null),
            downloadedAtEpochSeconds = Instant.fromEpochSeconds(downloadedAt),
            sizeBytes = sizeBytes,
            relativePath = null,
        )
}
