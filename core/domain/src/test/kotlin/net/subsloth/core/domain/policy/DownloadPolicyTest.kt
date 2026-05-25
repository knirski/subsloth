package net.subsloth.core.domain.policy

import net.subsloth.core.model.Availability
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.DownloadStatus
import net.subsloth.core.model.download.QueueId
import net.subsloth.core.model.download.QueueItem
import net.subsloth.core.model.download.QueueItemStatus
import net.subsloth.core.model.download.QueueStatus
import net.subsloth.core.model.download.SeasonQueue
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.Quality
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.core.model.media.Subtitle
import net.subsloth.core.model.media.SubtitleFormat
import net.subsloth.testing.assertions.assertThat
import kotlinx.collections.immutable.toImmutableList
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

    // ── Quality fallback for downloads ──────────────────────────────────────

    @Test
    fun `selectFallbackQuality returns exact match when available`() {
        val available = listOf(
            qualityDescriptor(Resolution.SD, "SD"),
            qualityDescriptor(Resolution.HD_720, "720p"),
            qualityDescriptor(Resolution.FULL_HD, "1080p"),
        )
        val result = DownloadPolicy.selectFallbackQuality(available, Resolution.HD_720)
        assertThat(result).isNotNull()
        assertThat(result!!.resolution).isEqualTo(Resolution.HD_720)
    }

    @Test
    fun `selectFallbackQuality returns nearest lower when exact not available`() {
        val available = listOf(
            qualityDescriptor(Resolution.SD, "SD"),
            qualityDescriptor(Resolution.FULL_HD, "1080p"),
        )
        val result = DownloadPolicy.selectFallbackQuality(available, Resolution.HD_720)
        assertThat(result).isNotNull()
        assertThat(result!!.resolution).isEqualTo(Resolution.SD)
    }

    @Test
    fun `selectFallbackQuality returns nearest higher when no lower exists`() {
        val available = listOf(
            qualityDescriptor(Resolution.FULL_HD, "1080p"),
            qualityDescriptor(Resolution.UHD_4K, "4K"),
        )
        val result = DownloadPolicy.selectFallbackQuality(available, Resolution.HD_720)
        assertThat(result).isNotNull()
        assertThat(result!!.resolution).isEqualTo(Resolution.FULL_HD)
    }

    @Test
    fun `selectFallbackQuality returns null when list is empty`() {
        val result = DownloadPolicy.selectFallbackQuality(emptyList(), Resolution.FULL_HD)
        assertThat(result).isNull()
    }

    // ── Subtitle fallback for downloads ─────────────────────────────────────

    @Test
    fun `selectFallbackSubtitle returns preferred language when present`() {
        val subtitles = listOf(
            subtitle(LanguageCode("en"), "English"),
            subtitle(LanguageCode("pl"), "Polish"),
        )
        val result = DownloadPolicy.selectFallbackSubtitle(subtitles, LanguageCode("pl"), fallbackToEnglish = true)
        assertThat(result).isNotNull()
        assertThat(result!!.language).isEqualTo(LanguageCode("pl"))
    }

    @Test
    fun `selectFallbackSubtitle falls back to English when preferred missing`() {
        val subtitles = listOf(
            subtitle(LanguageCode("en"), "English"),
            subtitle(LanguageCode("de"), "German"),
        )
        val result = DownloadPolicy.selectFallbackSubtitle(subtitles, LanguageCode("pl"), fallbackToEnglish = true)
        assertThat(result).isNotNull()
        assertThat(result!!.language).isEqualTo(LanguageCode("en"))
    }

    @Test
    fun `selectFallbackSubtitle returns first available when English missing`() {
        val subtitles = listOf(
            subtitle(LanguageCode("de"), "German"),
            subtitle(LanguageCode("fr"), "French"),
        )
        val result = DownloadPolicy.selectFallbackSubtitle(subtitles, LanguageCode("pl"), fallbackToEnglish = true)
        assertThat(result).isNotNull()
        assertThat(result!!.language).isEqualTo(LanguageCode("de"))
    }

    @Test
    fun `selectFallbackSubtitle returns null when subtitles empty`() {
        val result = DownloadPolicy.selectFallbackSubtitle(emptyList(), LanguageCode("en"), fallbackToEnglish = true)
        assertThat(result).isNull()
    }

    // ── Metered network policy ──────────────────────────────────────────────

    @Test
    fun `canDownloadOnMetered returns true on unmetered network`() {
        assertThat(DownloadPolicy.canDownloadOnMetered(isMetered = false, userOptedIn = false)).isTrue()
    }

    @Test
    fun `canDownloadOnMetered returns false on metered without opt-in`() {
        assertThat(DownloadPolicy.canDownloadOnMetered(isMetered = true, userOptedIn = false)).isFalse()
    }

    @Test
    fun `canDownloadOnMetered returns true on metered with opt-in`() {
        assertThat(DownloadPolicy.canDownloadOnMetered(isMetered = true, userOptedIn = true)).isTrue()
    }

    // ── Season preflight ────────────────────────────────────────────────────

    @Test
    fun `prepareSeasonPreflight counts episodes and sizes`() {
        val episodes = listOf(
            episode(seasonNumber = 1, episodeNumber = 1, durationSeconds = 1800L,
                qualities = listOf(quality(Resolution.FULL_HD, "1080p")),
                subtitles = listOf(subtitle(LanguageCode("en"), "English"))),
            episode(seasonNumber = 1, episodeNumber = 2, durationSeconds = 1800L,
                qualities = listOf(quality(Resolution.FULL_HD, "1080p")),
                subtitles = listOf(subtitle(LanguageCode("en"), "English"))),
        )
        val result = DownloadPolicy.prepareSeasonPreflight(episodes, Resolution.FULL_HD, LanguageCode("en"))
        assertThat(result.episodeCount).isEqualTo(2)
        assertThat(result.hasUnknownSizes).isTrue()
        assertThat(result.fallbackQualityCount).isEqualTo(0)
        assertThat(result.unavailableCount).isEqualTo(0)
    }

    @Test
    fun `prepareSeasonPreflight counts fallback quality and unavailable`() {
        val episodes = listOf(
            episode(seasonNumber = 1, episodeNumber = 1, durationSeconds = 1800L,
                qualities = listOf(quality(Resolution.UHD_4K, "4K")),
                subtitles = listOf(subtitle(LanguageCode("en"), "English"))),
            episode(seasonNumber = 1, episodeNumber = 2, durationSeconds = 1800L,
                qualities = listOf(quality(Resolution.FULL_HD, "1080p")),
                subtitles = listOf(subtitle(LanguageCode("en"), "English")),
                availability = Availability.Upcoming.UnknownDate),
        )
        val result = DownloadPolicy.prepareSeasonPreflight(episodes, Resolution.FULL_HD, LanguageCode("pl"))
        assertThat(result.episodeCount).isEqualTo(2)
        assertThat(result.fallbackQualityCount).isEqualTo(1)
        assertThat(result.fallbackSubtitleCount).isEqualTo(1)
        assertThat(result.unavailableCount).isEqualTo(1)
    }

    // ── Queue resume ────────────────────────────────────────────────────────

    @Test
    fun `canResumeQueue returns true when all checks pass`() {
        val queue = SeasonQueue(
            id = QueueId("q1"),
            showId = ShowId(1),
            seasonNumber = 1,
            items = listOf(QueueItem(
                episodeId = EpisodeId(1), episodeTitle = "E1",
                quality = null, subtitleLanguages = null, sizeBytes = null,
                status = QueueItemStatus.PENDING,
            )),
            status = QueueStatus.PAUSED,
            createdAtEpochSeconds = Instant.fromEpochSeconds(1_800_000_000L),
        )
        assertThat(DownloadPolicy.canResumeQueue(queue, isOnline = true, hasStorage = true, isMeteredOk = true, isAuthOk = true)).isTrue()
    }

    @Test
    fun `canResumeQueue returns false when offline`() {
        val queue = SeasonQueue(
            id = QueueId("q1"), showId = ShowId(1), seasonNumber = 1,
            items = emptyList(), status = QueueStatus.PAUSED,
            createdAtEpochSeconds = Instant.fromEpochSeconds(1_800_000_000L),
        )
        assertThat(DownloadPolicy.canResumeQueue(queue, isOnline = false, hasStorage = true, isMeteredOk = true, isAuthOk = true)).isFalse()
    }

    @Test
    fun `canResumeQueue returns false when storage insufficient`() {
        val queue = SeasonQueue(
            id = QueueId("q1"), showId = ShowId(1), seasonNumber = 1,
            items = emptyList(), status = QueueStatus.PAUSED,
            createdAtEpochSeconds = Instant.fromEpochSeconds(1_800_000_000L),
        )
        assertThat(DownloadPolicy.canResumeQueue(queue, isOnline = true, hasStorage = false, isMeteredOk = true, isAuthOk = true)).isFalse()
    }

    @Test
    fun `canResumeQueue returns false when auth expired`() {
        val queue = SeasonQueue(
            id = QueueId("q1"), showId = ShowId(1), seasonNumber = 1,
            items = emptyList(), status = QueueStatus.PAUSED,
            createdAtEpochSeconds = Instant.fromEpochSeconds(1_800_000_000L),
        )
        assertThat(DownloadPolicy.canResumeQueue(queue, isOnline = true, hasStorage = true, isMeteredOk = true, isAuthOk = false)).isFalse()
    }

    // ── Playable downloads ──────────────────────────────────────────────────

    @Test
    fun `hasPlayableDownloads returns true when any download is completed`() {
        val downloads = listOf(
            download(DownloadStatus.DOWNLOADING),
            download(DownloadStatus.COMPLETED),
            download(DownloadStatus.FAILED),
        )
        assertThat(DownloadPolicy.hasPlayableDownloads(downloads)).isTrue()
    }

    @Test
    fun `hasPlayableDownloads returns false when no download is completed`() {
        val downloads = listOf(
            download(DownloadStatus.DOWNLOADING),
            download(DownloadStatus.FAILED),
            download(DownloadStatus.PAUSED),
        )
        assertThat(DownloadPolicy.hasPlayableDownloads(downloads)).isFalse()
    }

    @Test
    fun `hasPlayableDownloads returns false for empty list`() {
        assertThat(DownloadPolicy.hasPlayableDownloads(emptyList())).isFalse()
    }

    // ── Local playability ───────────────────────────────────────────────────

    @Test
    fun `isPlayableLocally returns true when completed file exists and is non-empty`() {
        val download = download(DownloadStatus.COMPLETED)
        assertThat(DownloadPolicy.isPlayableLocally(download, fileExists = true, fileNonEmpty = true)).isTrue()
    }

    @Test
    fun `isPlayableLocally returns false when file is missing`() {
        val download = download(DownloadStatus.COMPLETED)
        assertThat(DownloadPolicy.isPlayableLocally(download, fileExists = false, fileNonEmpty = true)).isFalse()
    }

    @Test
    fun `isPlayableLocally returns false when file is empty`() {
        val download = download(DownloadStatus.COMPLETED)
        assertThat(DownloadPolicy.isPlayableLocally(download, fileExists = true, fileNonEmpty = false)).isFalse()
    }

    @Test
    fun `isPlayableLocally returns false when not completed`() {
        val download = download(DownloadStatus.DOWNLOADING)
        assertThat(DownloadPolicy.isPlayableLocally(download, fileExists = true, fileNonEmpty = true)).isFalse()
    }

    // ── File integrity status ───────────────────────────────────────────────

    @Test
    fun `fileIntegrityStatus returns PLAYABLE when file exists and is non-empty`() {
        val status = DownloadPolicy.fileIntegrityStatus(fileExists = true, fileNonEmpty = true)
        assertThat(status).isEqualTo(DownloadPolicy.FileStatus.PLAYABLE)
    }

    @Test
    fun `fileIntegrityStatus returns MISSING when file does not exist`() {
        val status = DownloadPolicy.fileIntegrityStatus(fileExists = false, fileNonEmpty = true)
        assertThat(status).isEqualTo(DownloadPolicy.FileStatus.MISSING)
    }

    @Test
    fun `fileIntegrityStatus returns CORRUPT when file exists but is empty`() {
        val status = DownloadPolicy.fileIntegrityStatus(fileExists = true, fileNonEmpty = false)
        assertThat(status).isEqualTo(DownloadPolicy.FileStatus.CORRUPT)
    }

    // ── Offline home mode ───────────────────────────────────────────────────

    @Test
    fun `offlineHomeMode returns OFFLINE_WITH_DOWNLOADS when offline with playable downloads`() {
        val mode = DownloadPolicy.offlineHomeMode(
            downloads = listOf(download(DownloadStatus.COMPLETED)),
            connectivityAvailable = false,
            fileVerificationFn = { true },
        )
        assertThat(mode).isEqualTo(DownloadPolicy.OfflineHomeMode.OFFLINE_WITH_DOWNLOADS)
    }

    @Test
    fun `offlineHomeMode returns OFFLINE_NO_DOWNLOADS when offline without playable downloads`() {
        val mode = DownloadPolicy.offlineHomeMode(
            downloads = listOf(download(DownloadStatus.DOWNLOADING)),
            connectivityAvailable = false,
            fileVerificationFn = { true },
        )
        assertThat(mode).isEqualTo(DownloadPolicy.OfflineHomeMode.OFFLINE_NO_DOWNLOADS)
    }

    @Test
    fun `offlineHomeMode returns ONLINE when connectivity is available`() {
        val mode = DownloadPolicy.offlineHomeMode(
            downloads = listOf(download(DownloadStatus.COMPLETED)),
            connectivityAvailable = true,
            fileVerificationFn = { true },
        )
        assertThat(mode).isEqualTo(DownloadPolicy.OfflineHomeMode.ONLINE)
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

    private fun subtitle(
        language: LanguageCode = LanguageCode("en"),
        languageDisplayName: String? = null,
    ): Subtitle =
        Subtitle(
            language = language,
            languageDisplayName = languageDisplayName,
            url = null,
            downloadUrl = null,
            format = SubtitleFormat.SRT,
        )

    private fun quality(
        resolution: Resolution,
        label: String,
    ): Quality =
        Quality(
            info = qualityDescriptor(resolution, label),
            url = null,
            downloadUrl = null,
        )

    private fun episode(
        seasonNumber: Int,
        episodeNumber: Int,
        durationSeconds: Long?,
        qualities: List<Quality>,
        subtitles: List<Subtitle>,
        availability: Availability = Availability.Available,
    ): Episode =
        Episode(
            id = EpisodeId(episodeNumber),
            showId = ShowId(1),
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            title = "Episode $episodeNumber",
            plot = null,
            durationSeconds = durationSeconds,
            availability = availability,
            imdbId = null,
            qualities = qualities.toImmutableList(),
            subtitles = subtitles.toImmutableList(),
            airDateEpochSeconds = null,
            premiereDateEpochSeconds = null,
        )
}
