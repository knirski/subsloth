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

class DownloadPolicyTest {
    // ── Storage reserve ───────────────────────────────────────────────────

    @Test
    fun `download refused when free space is below minimum reserve`() {
        val result =
            DownloadPolicy.hasSufficientStorage(
                availableBytes = 100L,
                requiredBytes = 50L,
                reserveBytes = 200L,
            )
        assertThat(result).isFalse()
    }

    @Test
    fun `download allowed when free space exceeds reserve plus required`() {
        val result =
            DownloadPolicy.hasSufficientStorage(
                availableBytes = 500L,
                requiredBytes = 100L,
                reserveBytes = 200L,
            )
        assertThat(result).isTrue()
    }

    @Test
    fun `download refused when available meets reserve but not required`() {
        val result =
            DownloadPolicy.hasSufficientStorage(
                availableBytes = 250L,
                requiredBytes = 100L,
                reserveBytes = 200L,
            )
        assertThat(result).isFalse()
    }

    // ── One active video download at a time ───────────────────────────────

    @Test
    fun `new download allowed when no active downloads exist`() {
        val activeDownloads =
            listOf(
                download(DownloadStatus.COMPLETED),
                download(DownloadStatus.PAUSED),
            )
        assertThat(DownloadPolicy.canStartNewDownload(activeDownloads)).isTrue()
    }

    @Test
    fun `new download blocked when an active download exists`() {
        val activeDownloads =
            listOf(
                download(DownloadStatus.DOWNLOADING),
            )
        assertThat(DownloadPolicy.canStartNewDownload(activeDownloads)).isFalse()
    }

    @Test
    fun `queued download counts as active for limiting`() {
        val activeDownloads =
            listOf(
                download(DownloadStatus.QUEUED),
            )
        assertThat(DownloadPolicy.canStartNewDownload(activeDownloads)).isFalse()
    }

    // ── Duplicate asset detection ─────────────────────────────────────────

    @Test
    fun `duplicate media id detected`() {
        val existing =
            listOf(
                download(DownloadStatus.COMPLETED, mediaId = Media.MediaId.Movie(MovieId(1))),
            )
        assertThat(
            DownloadPolicy.isDuplicate(
                existingDownloads = existing,
                candidateMediaId = Media.MediaId.Movie(MovieId(1)),
            ),
        ).isTrue()
    }

    @Test
    fun `different media id is not a duplicate`() {
        val existing =
            listOf(
                download(DownloadStatus.COMPLETED, mediaId = Media.MediaId.Movie(MovieId(1))),
            )
        assertThat(
            DownloadPolicy.isDuplicate(
                existingDownloads = existing,
                candidateMediaId = Media.MediaId.Movie(MovieId(2)),
            ),
        ).isFalse()
    }

    // ── Safe quality replacement ──────────────────────────────────────────

    @Test
    fun `higher quality replacement is allowed`() {
        val existing = qualityDescriptor(Resolution.HD_720, "720p")
        val candidate = qualityDescriptor(Resolution.FULL_HD, "1080p")

        assertThat(DownloadPolicy.canReplaceQuality(existing, candidate)).isTrue()
    }

    @Test
    fun `same quality replacement is not allowed`() {
        val existing = qualityDescriptor(Resolution.FULL_HD, "1080p")
        val candidate = qualityDescriptor(Resolution.FULL_HD, "1080p")

        assertThat(DownloadPolicy.canReplaceQuality(existing, candidate)).isFalse()
    }

    @Test
    fun `lower quality replacement is refused`() {
        val existing = qualityDescriptor(Resolution.FULL_HD, "1080p")
        val candidate = qualityDescriptor(Resolution.HD_720, "720p")

        assertThat(DownloadPolicy.canReplaceQuality(existing, candidate)).isFalse()
    }

    // ── Logout pause and login resume ─────────────────────────────────────

    @Test
    fun `incomplete downloads are paused on logout`() {
        val downloads =
            listOf(
                download(DownloadStatus.DOWNLOADING),
                download(DownloadStatus.QUEUED),
            )
        val paused = DownloadPolicy.pauseOnLogout(downloads)
        assertThat(paused).hasSize(2)
        assertThat(paused.all { it.status == DownloadStatus.PAUSED }).isTrue()
    }

    @Test
    fun `completed downloads are not affected by logout pause`() {
        val downloads =
            listOf(
                download(DownloadStatus.COMPLETED),
                download(DownloadStatus.DOWNLOADING),
            )
        val paused = DownloadPolicy.pauseOnLogout(downloads)
        assertThat(paused).hasSize(2)
        assertThat(paused[0].status).isEqualTo(DownloadStatus.COMPLETED)
        assertThat(paused[1].status).isEqualTo(DownloadStatus.PAUSED)
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun download(
        status: DownloadStatus,
        mediaId: Media.MediaId = Media.MediaId.Movie(MovieId(42)),
    ): DownloadState =
        DownloadState(
            localId = LocalMediaIdentifier("local_$status"),
            mediaId = mediaId,
            status = status,
            quality = qualityDescriptor(Resolution.FULL_HD, "1080p"),
            downloadedAtEpochSeconds = Instant.fromEpochSeconds(1_800_000_000L),
            sizeBytes = null,
            relativePath = null,
        )

    private fun qualityDescriptor(
        resolution: Resolution,
        label: String,
    ): QualityDescriptor =
        QualityDescriptor(
            resolution = resolution,
            label = label,
            bitrate = null,
            mimeType = null,
        )
}
