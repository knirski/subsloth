package net.subsloth.core.model

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import net.subsloth.core.model.download.DownloadFailureReason
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.OfflineAsset
import net.subsloth.core.model.download.OfflineRelativePath
import net.subsloth.core.model.download.SeasonDownloadConfirmation
import net.subsloth.core.model.download.SizeEstimate
import net.subsloth.core.model.download.TransferPreference
import net.subsloth.core.model.error.AuthError
import net.subsloth.core.model.error.DecodeError
import net.subsloth.core.model.error.DomainError
import net.subsloth.core.model.error.DownloadError
import net.subsloth.core.model.error.LibraryError
import net.subsloth.core.model.error.MediaError
import net.subsloth.core.model.error.NetworkError
import net.subsloth.core.model.error.PaymentLimitError
import net.subsloth.core.model.error.QualityError
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
import net.subsloth.core.model.media.ShowStatus
import net.subsloth.core.model.progress.PlaybackProgress
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Instant

class CoreModelTest {
    // ── Identifiers ──────────────────────────────────────────────────────

    @Test
    fun `resolution compares by pixel count`() {
        val sd = Resolution.SD
        val hd = Resolution.HD_720
        val fhd = Resolution.FULL_HD
        assertThat(sd).isLessThan(hd)
        assertThat(hd).isLessThan(fhd)
        assertThat(fhd).isGreaterThan(sd)
    }

    @Test
    fun `resolution label is human-readable`() {
        assertThat(Resolution.SD.label).isEqualTo("SD")
        assertThat(Resolution.HD_720.label).isEqualTo("720p")
        assertThat(Resolution.FULL_HD.label).isEqualTo("1080p")
        assertThat(Resolution.UHD_4K.label).isEqualTo("4K")
    }

    @Test
    fun `show status enum covers all states`() {
        val all = ShowStatus.entries.toSet()
        assertThat(all).containsExactly(
            ShowStatus.ONGOING,
            ShowStatus.ENDED,
            ShowStatus.UPCOMING,
            ShowStatus.UNKNOWN,
        )
    }

    // ── Upcoming Episode Scenario (spec requirement) ───────────────────────

    @Test
    fun `upcoming episode prevents playable intent`() {
        val episode =
            Episode(
                id = EpisodeId(1),
                showId = ShowId(10),
                seasonNumber = 1,
                episodeNumber = 3,
                title = "Future Episode",
                plot = "Not yet released",
                durationSeconds = 1800L,
                availability =
                    Availability.Upcoming.At(
                        availableAtEpochSeconds = Instant.fromEpochSeconds(1_900_000_000L),
                    ),
                imdbId = null,
                qualities = persistentListOf(),
                subtitles = persistentListOf(),
                airDateEpochSeconds = null,
                premiereDateEpochSeconds = Instant.fromEpochSeconds(1_900_000_000L),
            )
        assertThat(episode.isUpcoming).isTrue()
    }

    @Test
    fun `available episode is playable`() {
        val episode =
            Episode(
                id = EpisodeId(2),
                showId = ShowId(10),
                seasonNumber = 1,
                episodeNumber = 1,
                title = "First Episode",
                plot = "The beginning",
                durationSeconds = 1800L,
                availability = Availability.Available,
                imdbId = null,
                qualities = persistentListOf(),
                subtitles = persistentListOf(),
                airDateEpochSeconds = Instant.fromEpochSeconds(1_700_000_000L),
                premiereDateEpochSeconds = null,
            )
        assertThat(episode.isUpcoming).isFalse()
    }

    // ── Quality ────────────────────────────────────────────────────────────

    @Test
    fun `quality carries resolution and ephemeral URLs`() {
        val q =
            Quality(
                info =
                    QualityDescriptor(
                        resolution = Resolution.FULL_HD,
                        label = "1080p",
                        bitrate = 5_000_000,
                        mimeType = "video/mp4",
                    ),
                url = "https://example.com/stream",
                downloadUrl = null,
            )
        assertThat(q.info.resolution.label).isEqualTo("1080p")
        assertThat(q.info.bitrate).isEqualTo(5_000_000)
    }

    // ── Progress ───────────────────────────────────────────────────────────

    @Test
    fun `playback progress computes fraction`() {
        val progress =
            PlaybackProgress(
                mediaId = Media.MediaId.Movie(MovieId(1)),
                positionSeconds = 500,
                durationSeconds = 1000,
                lastUpdatedEpochSeconds = Instant.fromEpochSeconds(1_800_000_000L),
                isWatched = false,
            )
        assertThat(progress.fraction).isWithin(0.001).of(0.5)
    }

    @Test
    fun `fully watched progress marks as complete`() {
        val completed =
            PlaybackProgress(
                mediaId = Media.MediaId.Movie(MovieId(1)),
                positionSeconds = 1000,
                durationSeconds = 1000,
                lastUpdatedEpochSeconds = Instant.fromEpochSeconds(1_800_000_000L),
                isWatched = true,
            )
        assertThat(completed.isWatched).isTrue()
    }

    // ── Download ───────────────────────────────────────────────────────────

    @Test
    fun `download state exposes offline lifecycle variants without nullable baggage`() {
        val completed: DownloadState =
            DownloadState.Completed(
                localId = LocalMediaIdentifier("movie-7"),
                mediaId = Media.MediaId.Movie(MovieId(7)),
                quality = qualityDescriptor(Resolution.FULL_HD, "1080p"),
                downloadedAtEpochSeconds = Instant.fromEpochSeconds(10),
                sizeBytes = 1024L,
                videoPath = OfflineRelativePath("downloads/video/7/main.mp4"),
                subtitleLanguages = persistentSetOf<LanguageCode>(),
            )
        val unavailable: DownloadState =
            DownloadState.Unavailable(
                localId = LocalMediaIdentifier("movie-7"),
                mediaId = Media.MediaId.Movie(MovieId(7)),
                quality = qualityDescriptor(Resolution.HD_720, "720p"),
                reason = DownloadFailureReason.MissingLocalFile,
            )
        assertThat(completed).isInstanceOf(DownloadState.Completed::class.java)
        assertThat(unavailable).isInstanceOf(DownloadState.Unavailable::class.java)
        val completedTyped = completed as DownloadState.Completed
        assertThat(completedTyped.videoPath).isEqualTo(OfflineRelativePath("downloads/video/7/main.mp4"))
        assertThat(completedTyped.sizeBytes).isEqualTo(1024L)
        val unavailableTyped = unavailable as DownloadState.Unavailable
        assertThat(unavailableTyped.reason).isEqualTo(DownloadFailureReason.MissingLocalFile)
    }

    @Test
    fun `season queue summary rejects negative counts`() {
        assertThrows<IllegalArgumentException> {
            SeasonDownloadConfirmation(
                episodeCount = -1,
                alreadyAvailableCount = 0,
                fallbackQualityCount = 0,
                fallbackSubtitleToEnglishCount = 0,
                noSubtitleCount = 0,
                unavailableCount = 0,
                sizeEstimate = SizeEstimate.Unknown,
                transferPreference = TransferPreference.WifiOnly,
            )
        }
    }

    @Test
    fun `offline asset keeps subtitle sidecars separate from video asset`() {
        val asset =
            OfflineAsset(
                mediaId = Media.MediaId.Movie(MovieId(7)),
                localId = LocalMediaIdentifier("movie-7"),
                videoRelativePath = OfflineRelativePath("downloads/video/7/main.mp4"),
                subtitleLanguages = persistentSetOf(LanguageCode("en"), LanguageCode("pl")),
                effectiveQuality = qualityDescriptor(Resolution.FULL_HD, "1080p"),
                displayTitle = "Movie",
                isPlayable = true,
            )
        assertThat(asset.subtitleLanguages).contains(LanguageCode("en"))
        assertThat(asset.subtitleLanguages).contains(LanguageCode("pl"))
    }

    @Test
    fun `season queue summary tracks fallback and blocked counts`() {
        val summary =
            SeasonDownloadConfirmation(
                episodeCount = 8,
                alreadyAvailableCount = 2,
                fallbackQualityCount = 1,
                fallbackSubtitleToEnglishCount = 3,
                noSubtitleCount = 1,
                unavailableCount = 1,
                sizeEstimate = SizeEstimate.Unknown,
                transferPreference = TransferPreference.WifiOnly,
            )
        assertThat(summary.fallbackSubtitleToEnglishCount).isEqualTo(3)
        assertThat(summary.noSubtitleCount).isEqualTo(1)
    }

    // ── Domain Error Hierarchy ────────────────────────────────────────────

    @Test
    fun `all error types are DomainError`() {
        val errors: List<DomainError> =
            listOf(
                AuthError.InvalidCredentials,
                AuthError.SessionExpired,
                AuthError.AccountSuspended,
                PaymentLimitError.ConcurrentStreamLimit,
                PaymentLimitError.SubscriptionRequired,
                MediaError.Unavailable,
                MediaError.NotFound,
                MediaError.GeoRestricted,
                MediaError.Expired,
                MediaError.Upcoming,
                DownloadError.InsufficientStorage,
                DownloadError.MissingSubtitle,
                DownloadError.QueueFull,
                DownloadError.NeedsWifi,
                DownloadError.MissingLocalFile,
                DownloadError.AmbiguousQuality,
                QualityError.Unsupported,
                QualityError.NoFallback,
                QualityError.BelowMinimum,
                DecodeError.InvalidResponseFormat,
                DecodeError.SerializationFailed,
                DecodeError.MissingFields(listOf("title")),
                NetworkError.Timeout,
                NetworkError.NoConnectivity,
                NetworkError.HttpError(500, "Internal Server Error"),
                NetworkError.UnexpectedResponse,
                NetworkError.RateLimited(retryAfterSeconds = 30),
                LibraryError.NotSupported,
                LibraryError.AlreadyExists,
                LibraryError.NotFound,
            )
        assertThat(errors).hasSize(30)
        errors.forEach { assertThat(it).isInstanceOf(DomainError::class.java) }
    }

    private fun qualityDescriptor(
        resolution: Resolution,
        label: String,
    ): QualityDescriptor = QualityDescriptor(resolution = resolution, label = label, bitrate = null, mimeType = null)
}
