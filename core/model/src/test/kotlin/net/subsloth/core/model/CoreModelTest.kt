package net.subsloth.core.model

import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.DownloadStatus
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
                    Availability.Upcoming(
                        availableAtEpochSeconds = Instant.fromEpochSeconds(1_900_000_000L),
                    ),
                imdbId = null,
                qualities = emptyList(),
                subtitles = emptyList(),
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
                qualities = emptyList(),
                subtitles = emptyList(),
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

    @Test
    fun `download state stores quality descriptor without ephemeral URLs`() {
        val item =
            DownloadState(
                localId = LocalMediaIdentifier("file_1"),
                mediaId = Media.MediaId.Movie(MovieId(1)),
                status = DownloadStatus.COMPLETED,
                quality =
                    QualityDescriptor(
                        resolution = Resolution.FULL_HD,
                        label = "1080p",
                        bitrate = 5_000_000,
                        mimeType = "video/mp4",
                    ),
                downloadedAtEpochSeconds = Instant.fromEpochSeconds(1_800_000_000L),
                sizeBytes = 1_500_000_000L,
                relativePath = "movies/1.mp4",
            )
        // Quality info is preserved in the persistent record.
        assertThat(item.quality.resolution).isEqualTo(Resolution.FULL_HD)
        assertThat(item.quality.label).isEqualTo("1080p")
        assertThat(item.quality.bitrate).isEqualTo(5_000_000)
        // QualityDescriptor exposes only stable metadata — no stream or
        // download URL properties exist on it. Ephemeral session data is
        // confined to Quality, which is never stored in DownloadState.
        // This is verified at compile time by the type system.
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
    fun `download state covers all statuses`() {
        val all = DownloadStatus.entries.toSet()
        assertThat(all).containsExactly(
            DownloadStatus.QUEUED,
            DownloadStatus.DOWNLOADING,
            DownloadStatus.COMPLETED,
            DownloadStatus.FAILED,
            DownloadStatus.PAUSED,
            DownloadStatus.REMOVED,
        )
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
        assertThat(errors).hasSize(27)
        errors.forEach { assertThat(it).isInstanceOf(DomainError::class.java) }
    }
}
