package net.subsloth.core.domain.policy

import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.OfflineRelativePath
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class StorageCleanupPolicyTest {
    @Test
    fun `candidates sorted by downloaded time ascending`() {
        val old = completed(epoch = 1000, size = 500)
        val mid = completed(epoch = 2000, size = 300)
        val recent = completed(epoch = 3000, size = 700)
        val candidates = StorageCleanupPolicy.cleanupCandidates(listOf(recent, old, mid))
        assertThat(candidates).isEqualTo(listOf(old, mid, recent))
    }

    @Test
    fun `candidates filters out non completed downloads`() {
        val completedDownload = completed(epoch = 1000, size = 500)
        val queued = DownloadState.Queued(localId = id("q/1"), mediaId = movieId, quality = quality)
        val active = DownloadState.Active(
            localId = id("a/1"),
            mediaId = movieId,
            quality = quality,
            progressPercent = 50,
        )
        val candidates = StorageCleanupPolicy.cleanupCandidates(listOf(completedDownload, queued, active))
        assertThat(candidates).hasSize(1)
        assertThat(candidates[0]).isEqualTo(completedDownload)
    }

    @Test
    fun `empty list returns no candidates`() {
        assertThat(StorageCleanupPolicy.cleanupCandidates(emptyList())).isEmpty()
    }

    @Test
    fun `estimated reclaimable bytes sums completed sizes`() {
        val candidates = listOf(
            completed(epoch = 1000, size = 500),
            completed(epoch = 2000, size = 300),
        )
        val reclaimable = StorageCleanupPolicy.estimatedReclaimableBytes(candidates)
        assertThat(reclaimable).isEqualTo(800)
    }

    @Test
    fun `null size bytes contributes zero to reclaimable`() {
        val candidates = listOf(
            completed(epoch = 1000, size = null),
        )
        assertThat(StorageCleanupPolicy.estimatedReclaimableBytes(candidates)).isEqualTo(0)
    }

    private fun completed(epoch: Long, size: Long?): DownloadState.Completed = DownloadState.Completed(
        localId = id("test/$epoch"),
        mediaId = movieId,
        quality = quality,
        downloadedAtEpochSeconds = Instant.fromEpochSeconds(epoch),
        sizeBytes = size,
        videoPath = OfflineRelativePath.safe("test.mp4"),
    )

    private val movieId = Media.MediaId.Movie(MovieId(1))
    private val quality =
        QualityDescriptor(
            resolution = net.subsloth.core.model.identifier.Resolution.HD_720,
            label = "720p",
            bitrate = null,
            mimeType = null,
        )

    private fun id(value: String): LocalMediaIdentifier = LocalMediaIdentifier(value)
}
