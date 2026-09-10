package net.subsloth.web

import kotlinx.browser.window
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.Flow
import net.subsloth.catalog.HomeViewModel
import net.subsloth.core.domain.port.DownloadCommandOutcome
import net.subsloth.core.domain.port.DownloadsPort
import net.subsloth.core.domain.port.LibraryPort
import net.subsloth.core.domain.port.PlaybackPort
import net.subsloth.core.domain.port.SessionPort
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.EnqueueOutcome
import net.subsloth.core.model.download.SeasonDownloadQueue
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.identifier.AccountProfileKey
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MediaDetails
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.ShowSummary
import net.subsloth.core.model.playback.PlaybackMode
import net.subsloth.core.model.playback.VideoSource
import net.subsloth.core.model.progress.PlaybackProgress
import kotlin.coroutines.cancellation.CancellationException

/**
 * Union surface consumed by [WebNavHost]: both the demo tier
 * ([WebDemoRuntime]) and the production composition root
 * ([WebProductionContainer]) implement it, so the nav host is shared.
 *
 * Demo implementations are safe no-ops / mock data; production members
 * mirror `DesktopContainer`'s adapter wiring. Download **byte transfer**
 * is jvm-only (no browser equivalent of the staged-file worker), so web
 * download controls manage persisted state only.
 */
interface WebRuntime {
    // ── Catalog / search ────────────────────────────────────────────────

    suspend fun listCatalog(): Outcome<List<Media>>

    suspend fun getDetails(mediaId: Media.MediaId): Outcome<MediaDetails>

    /** Video source for playback (demo: bundled mock asset; production: playbackPort). */
    suspend fun fetchVideoSource(mediaId: Media.MediaId): Outcome<VideoSource>

    fun catalogItems(type: String): Flow<List<Media>>

    fun createHomeViewModel(): HomeViewModel

    suspend fun listAllMedia(): Outcome<List<Media>>

    suspend fun listMovies(): Result<List<MovieSummary>>

    suspend fun listShows(): Result<List<ShowSummary>>

    // ── Player ──────────────────────────────────────────────────────────

    val playbackPort: PlaybackPort

    suspend fun fetchEpisodesForShow(showId: ShowId): Outcome<List<Episode>>

    /** Fetches subtitle document text for the player's Compose subtitle layer. */
    suspend fun fetchSubtitleText(url: String): Outcome<String>

    suspend fun resolveShowIdForEpisode(episodeId: EpisodeId): ShowId?

    suspend fun savePlaybackProgress(
        mediaId: Media.MediaId,
        positionSeconds: Long,
        durationSeconds: Long,
        playbackMode: PlaybackMode,
    )

    suspend fun listAccountPlaybackProgress(): Result<List<PlaybackProgress>>

    suspend fun savePlaybackSpeed(speed: Float)

    suspend fun loadPlaybackSpeed(): Float

    suspend fun loadPreferredLanguage(): LanguageCode

    fun invalidateSession()
    // ── Library / downloads ─────────────────────────────────────────────

    val libraryPort: LibraryPort

    val downloadController: DownloadsPort

    suspend fun listLibrary(): Outcome<List<net.subsloth.core.model.library.LibraryItem>>

    suspend fun listDownloads(): Result<ImmutableList<DownloadState>>

    suspend fun removeDownload(localId: String): Result<DownloadCommandOutcome>

    suspend fun listSeasonQueues(): Result<ImmutableList<SeasonDownloadQueue>>

    suspend fun retryDownload(localId: String): EnqueueOutcome

    suspend fun pauseDownload(localId: String): DownloadCommandOutcome

    suspend fun resumeDownload(localId: String): DownloadCommandOutcome

    suspend fun cancelDownload(localId: String): DownloadCommandOutcome

    suspend fun listWatchedContentIds(): Set<String>

    suspend fun isWatched(mediaId: Media.MediaId): Boolean

    // ── Session / settings ──────────────────────────────────────────────

    val sessionPort: SessionPort

    fun currentProfileKey(): AccountProfileKey

    fun readSubtitleEnabled(profileKey: AccountProfileKey): Flow<Boolean>

    fun readSubtitleLanguage(profileKey: AccountProfileKey): Flow<String?>

    fun readQuality(profileKey: AccountProfileKey): Flow<String?>

    fun readPlaybackSpeed(profileKey: AccountProfileKey): Flow<Float>

    fun readDownloadsWifiOnly(profileKey: AccountProfileKey): Flow<Boolean>

    fun apiBaseUrlFlow(): Flow<String>

    suspend fun saveApiBaseUrl(url: String)

    fun writeSubtitleEnabled(enabled: Boolean)

    fun writeSubtitleLanguage(language: String?)

    fun writeQuality(quality: String?)

    fun writePlaybackSpeed(speed: Float)

    fun writeDownloadsWifiOnly(wifiOnly: Boolean)

    fun deleteAllDownloads()

    fun clearPreferences()

    fun clearLibrary()

    fun clearCredentials()

    fun close()
}

/**
 * Fetches subtitle document text through the browser `fetch` API for the
 * player's Compose subtitle layer. Works for both runtimes: the demo tier
 * resolves bundled compose-resource sidecars relative to the page origin,
 * production resolves absolute subtitle stream URLs.
 */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
internal suspend fun fetchSubtitleTextViaBrowser(url: String): Outcome<String> = try {
    val response = window.fetch(url).await()
    if (response.ok) {
        Outcome.Success(response.text().await().toString())
    } else {
        Outcome.Failure(net.subsloth.core.model.error.NetworkError.UnexpectedResponse)
    }
} catch (exception: CancellationException) {
    throw exception
} catch (exception: Exception) {
    Outcome.Failure(net.subsloth.core.model.error.NetworkError.UnexpectedResponse)
}
