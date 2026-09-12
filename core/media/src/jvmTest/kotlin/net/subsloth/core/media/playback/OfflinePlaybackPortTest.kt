package net.subsloth.core.media.playback

import kotlinx.coroutines.test.runTest
import net.subsloth.core.model.download.OfflineAsset
import net.subsloth.core.model.download.OfflineRelativePath
import net.subsloth.core.model.download.OfflineSubtitle
import net.subsloth.core.model.error.MediaError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.core.model.media.SubtitleFormat
import net.subsloth.core.model.playback.PlaybackMode
import net.subsloth.core.model.playback.VideoSource
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test

private val movieId = Media.MediaId.Movie(MovieId(1))
private val otherMovieId = Media.MediaId.Movie(MovieId(2))

private fun offlineAsset(mediaId: Media.MediaId = movieId, isPlayable: Boolean = true) = OfflineAsset(
    mediaId = mediaId,
    localId = LocalMediaIdentifier("1/1"),
    videoRelativePath = OfflineRelativePath.safe("1/abc.mp4"),
    subtitleLanguages = kotlinx.collections.immutable.persistentSetOf(),
    effectiveQuality = QualityDescriptor(
        resolution = Resolution.HD_720,
        label = "720p",
        bitrate = null,
        mimeType = null,
    ),
    displayTitle = "Movie 1",
    isPlayable = isPlayable,
)

/** Files fake: verify only paths containing "abc", uri echoes the path. */
private val files = object : OfflineAssetFiles {
    override fun fileUri(localPath: OfflineRelativePath): String = "file:///downloads/${localPath.value}"
    override fun verifyFile(localPath: OfflineRelativePath): Boolean = localPath.value.contains("abc")
}

class OfflineSourceResolverTest {

    @Test
    fun `resolves a verified playable asset to an offline source`() = runTest {
        val resolver = OfflineSourceResolver(
            offlineAssets = { Result.success(listOf(offlineAsset())) },
            files = files,
        )
        val source = resolver.resolve(movieId)
        assertThat(source).isNotNull()
        val s = requireNotNull(source)
        assertThat(s.playbackMode).isEqualTo(PlaybackMode.OFFLINE)
        assertThat(s.streamUrl).isEqualTo("file:///downloads/1/abc.mp4")
        assertThat(s.localId?.value).isEqualTo("1/1")
        assertThat(s.durationSeconds).isEqualTo(0L)
        assertThat(s.selectedQuality.info.resolution).isEqualTo(Resolution.HD_720)
    }

    @Test
    fun `exposes verified local subtitle files on the offline source`() = runTest {
        val asset =
            offlineAsset().copy(
                subtitles =
                kotlinx.collections.immutable.persistentListOf(
                    OfflineSubtitle(
                        language = LanguageCode("en"),
                        format = SubtitleFormat.SRT,
                        relativePath = OfflineRelativePath.safe("1/abc.en.srt"),
                    ),
                    OfflineSubtitle(
                        language = LanguageCode("pl"),
                        format = SubtitleFormat.SRT,
                        relativePath = OfflineRelativePath.safe("1/gone.pl.srt"),
                    ),
                ),
            )
        val resolver = OfflineSourceResolver(
            offlineAssets = { Result.success(listOf(asset)) },
            files = files,
        )

        val source = requireNotNull(resolver.resolve(movieId))

        assertThat(source.availableSubtitles.map { it.language.value }).containsExactly("en")
        assertThat(source.availableSubtitles.first().url).isEqualTo("file:///downloads/1/abc.en.srt")
        assertThat(source.availableSubtitles.first().format).isEqualTo(SubtitleFormat.SRT)
    }

    @Test
    fun `returns null for a media with no download`() = runTest {
        val resolver = OfflineSourceResolver(
            offlineAssets = { Result.success(listOf(offlineAsset())) },
            files = files,
        )
        assertThat(resolver.resolve(otherMovieId)).isNull()
    }

    @Test
    fun `returns null when the asset is flagged not playable`() = runTest {
        val resolver = OfflineSourceResolver(
            offlineAssets = { Result.success(listOf(offlineAsset(isPlayable = false))) },
            files = files,
        )
        assertThat(resolver.resolve(movieId)).isNull()
    }

    @Test
    fun `returns null when the file no longer verifies on disk`() = runTest {
        val missingFileAsset = offlineAsset().copy(videoRelativePath = OfflineRelativePath.safe("1/gone.mp4"))
        val resolver = OfflineSourceResolver(
            offlineAssets = { Result.success(listOf(missingFileAsset)) },
            files = files,
        )
        assertThat(resolver.resolve(movieId)).isNull()
    }

    @Test
    fun `returns null when listing offline assets fails`() = runTest {
        val resolver = OfflineSourceResolver(
            offlineAssets = { Result.failure(IllegalStateException("db error")) },
            files = files,
        )
        assertThat(resolver.resolve(movieId)).isNull()
    }
}

class OfflineFirstPlaybackPortTest {

    private class FakeOnlinePort : net.subsloth.core.domain.port.PlaybackPort {
        var prepareCalls = 0
        var refreshCalls = 0
        var playCalls = 0
        var pauseCalls = 0
        var seekCalls = 0

        private val onlineSource = VideoSource(
            mediaId = movieId,
            streamUrl = "https://example.com/stream.m3u8",
            selectedQuality = net.subsloth.core.model.media.Quality(
                info = QualityDescriptor(Resolution.FULL_HD, "1080p", null, null),
                url = "https://example.com/stream.m3u8",
                downloadUrl = null,
            ),
            availableQualities = kotlinx.collections.immutable.persistentListOf(),
            availableSubtitles = kotlinx.collections.immutable.persistentListOf(),
            durationSeconds = 60L,
        )

        override suspend fun prepareSource(mediaId: Media.MediaId): Outcome<VideoSource> {
            prepareCalls++
            return Outcome.Success(onlineSource)
        }

        override suspend fun refreshStreamUrl(mediaId: Media.MediaId): Outcome<VideoSource> {
            refreshCalls++
            return Outcome.Success(onlineSource)
        }

        override suspend fun play(source: VideoSource, positionSeconds: Long): Outcome<Unit> {
            playCalls++
            return Outcome.Success(Unit)
        }

        override suspend fun pause(): Outcome<Unit> {
            pauseCalls++
            return Outcome.Success(Unit)
        }

        override suspend fun seek(positionSeconds: Long): Outcome<Unit> {
            seekCalls++
            return Outcome.Success(Unit)
        }
    }

    private fun portWith(assets: List<OfflineAsset>): Pair<OfflineFirstPlaybackPort, FakeOnlinePort> {
        val online = FakeOnlinePort()
        val resolver = OfflineSourceResolver(
            offlineAssets = { Result.success(assets) },
            files = files,
        )
        return OfflineFirstPlaybackPort(resolver, online) to online
    }

    @Test
    fun `prepareSource prefers the offline asset and never touches the online port`() = runTest {
        val (port, online) = portWith(listOf(offlineAsset()))
        val outcome = port.prepareSource(movieId)
        assertThat(outcome).isInstanceOf(Outcome.Success::class.java)
        assertThat((outcome as Outcome.Success).value.playbackMode).isEqualTo(PlaybackMode.OFFLINE)
        assertThat(online.prepareCalls).isEqualTo(0)
    }

    @Test
    fun `prepareSource falls back to the online port without offline asset`() = runTest {
        val (port, online) = portWith(emptyList())
        val outcome = port.prepareSource(movieId)
        assertThat((outcome as Outcome.Success).value.streamUrl).isEqualTo("https://example.com/stream.m3u8")
        assertThat(online.prepareCalls).isEqualTo(1)
    }

    @Test
    fun `refreshStreamUrl over an offline asset returns the offline source without network`() = runTest {
        val (port, online) = portWith(listOf(offlineAsset()))
        val outcome = port.refreshStreamUrl(movieId)
        assertThat((outcome as Outcome.Success).value.playbackMode).isEqualTo(PlaybackMode.OFFLINE)
        assertThat(online.refreshCalls).isEqualTo(0)
    }

    @Test
    fun `refreshStreamUrl delegates to the online port when offline is absent`() = runTest {
        val (port, online) = portWith(emptyList())
        port.refreshStreamUrl(movieId)
        assertThat(online.refreshCalls).isEqualTo(1)
    }

    @Test
    fun `renderer-control operations always delegate to the online port`() = runTest {
        val (port, online) = portWith(listOf(offlineAsset()))
        val source = (port.prepareSource(movieId) as Outcome.Success).value
        port.play(source, 0L)
        port.pause()
        port.seek(10L)
        assertThat(online.playCalls).isEqualTo(1)
        assertThat(online.pauseCalls).isEqualTo(1)
        assertThat(online.seekCalls).isEqualTo(1)
    }

    @Test
    fun `prepareSource propagates the online failure when offline is absent`() = runTest {
        val online = FakeOnlinePort()
        val resolver = OfflineSourceResolver(
            offlineAssets = { Result.success(emptyList()) },
            files = files,
        )
        val failingOnline = object : net.subsloth.core.domain.port.PlaybackPort {
            override suspend fun prepareSource(mediaId: Media.MediaId) = Outcome.Failure(MediaError.Unavailable)
            override suspend fun refreshStreamUrl(mediaId: Media.MediaId) = Outcome.Failure(MediaError.Unavailable)
            override suspend fun play(source: VideoSource, positionSeconds: Long) = Outcome.Success(Unit)
            override suspend fun pause() = Outcome.Success(Unit)
            override suspend fun seek(positionSeconds: Long) = Outcome.Success(Unit)
        }
        val port = OfflineFirstPlaybackPort(resolver, failingOnline)
        val outcome = port.prepareSource(movieId)
        assertThat(outcome).isEqualTo(Outcome.Failure(MediaError.Unavailable))
        assertThat(online.prepareCalls).isEqualTo(0)
    }
}
