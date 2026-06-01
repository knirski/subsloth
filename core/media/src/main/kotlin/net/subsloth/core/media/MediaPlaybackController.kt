package net.subsloth.core.media

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import co.touchlab.kermit.Logger
import net.subsloth.core.datasource.ktor.KtorDataSource
import net.subsloth.core.model.playback.PlaybackMode
import net.subsloth.core.model.playback.VideoSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@UnstableApi
@Suppress("TooManyFunctions")
class MediaPlaybackController(private val application: Application) {
    private var player: ExoPlayer? = null
    private var errorListener: Player.Listener? = null
    private var errorCallback: ErrorCallback? = null

    /** Callback for player errors. */
    fun interface ErrorCallback {
        /** Called when a playback error occurs. */
        fun onError(error: PlaybackException)
    }

    private val httpDataSourceFactory: KtorDataSource.Factory by lazy {
        KtorDataSource.Factory.create()
    }

    fun buildPlayer(): ExoPlayer {
        Logger.withTag(TAG).d { "Building online player" }
        release()
        val dataSourceFactory = DefaultDataSource.Factory(
            application,
            httpDataSourceFactory,
        )
        val exoPlayer = ExoPlayer.Builder(application)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(dataSourceFactory)
                    .setLiveTargetOffsetMs(DEFAULT_LIVE_OFFSET.inWholeMilliseconds),
            )
            .build()
        player = exoPlayer
        attachErrorListener()
        return exoPlayer
    }

    /**
     * Builds an ExoPlayer configured for local file playback.
     *
     * Local playback uses a direct file URI and does not attempt network
     * refresh or quality fallback.
     */
    fun buildLocalPlayer(): ExoPlayer {
        Logger.withTag(TAG).d { "Building local player" }
        release()
        val exoPlayer = ExoPlayer.Builder(application)
            .setMediaSourceFactory(DefaultMediaSourceFactory(application))
            .build()
        player = exoPlayer
        attachErrorListener()
        return exoPlayer
    }

    /**
     * Prepares and starts playback from a [VideoSource].
     *
     * For [PlaybackMode.OFFLINE], the [VideoSource.streamUrl] must be a
     * local file URI. No network refresh or quality fallback is attempted.
     */
    fun startPlayback(source: VideoSource, positionSeconds: Long = 0L) {
        val p = player ?: run {
            Logger.withTag(TAG).w { "startPlayback: no player built" }
            return
        }
        Logger.withTag(TAG).d {
            "Starting playback at ${positionSeconds}s, subtitles=${source.availableSubtitles.size}"
        }
        val mediaItem = MediaItemFactory.createMediaItem(source)
            .buildUpon()
            .setSubtitleConfigurations(MediaItemFactory.buildSubtitleMediaItem(source))
            .build()
        p.setMediaItem(mediaItem)
        p.prepare()
        if (positionSeconds > 0L) {
            p.seekTo(positionSeconds.seconds.inWholeMilliseconds)
        }
        p.playWhenReady = true
    }

    /**
     * Prepares and starts playback from a local file URI.
     *
     * Use this for offline/downloaded content where the [localFileUri] points
     * to an app-private file on local storage.
     */
    fun startLocalPlayback(localFileUri: String, source: VideoSource, positionSeconds: Long = 0L) {
        val p = player ?: run {
            Logger.withTag(TAG).w { "startLocalPlayback: no player built" }
            return
        }
        Logger.withTag(TAG).d { "Starting local playback from: $localFileUri at ${positionSeconds}s" }
        val mediaItem = MediaItemFactory.createLocalMediaItem(localFileUri, source)
            .buildUpon()
            .setSubtitleConfigurations(MediaItemFactory.buildSubtitleMediaItem(source))
            .build()
        p.setMediaItem(mediaItem)
        p.prepare()
        if (positionSeconds > 0L) {
            p.seekTo(positionSeconds.seconds.inWholeMilliseconds)
        }
        p.playWhenReady = true
    }

    /**
     * Registers a callback for playback errors.
     *
     * Only one callback is active at a time; calling this again replaces the
     * previous callback.
     */
    fun setErrorCallback(callback: ErrorCallback) {
        errorCallback = callback
        attachErrorListener()
    }

    private fun attachErrorListener() {
        val p = player ?: return
        val callback = errorCallback ?: return
        errorListener?.let { p.removeListener(it) }
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Logger.withTag(TAG).e(error) { "Player error: ${error.errorCodeName}" }
                callback.onError(error)
            }
        }
        p.addListener(listener)
        errorListener = listener
    }

    fun setPlaybackSpeed(speed: Float) {
        require(speed > 0f) { "Playback speed must be positive, was $speed" }
        Logger.withTag(TAG).v { "Setting playback speed: $speed" }
        player?.setPlaybackSpeed(speed)
    }

    fun setPreferredTextLanguage(language: String?) {
        val p = player ?: return
        Logger.withTag(TAG).v { "Setting preferred text language: $language" }
        p.trackSelectionParameters = p.trackSelectionParameters
            .buildUpon()
            .setPreferredTextLanguage(language)
            .build()
    }

    fun release() {
        Logger.withTag(TAG).d { "Releasing player" }
        errorListener?.let { player?.removeListener(it) }
        errorListener = null
        player?.release()
        player = null
    }

    fun currentPosition(): Duration = player?.currentPosition?.let { pos ->
        if (pos == C.TIME_UNSET) Duration.ZERO else pos.milliseconds
    } ?: Duration.ZERO

    fun duration(): Duration = player?.duration?.let { dur ->
        if (dur == C.TIME_UNSET) Duration.ZERO else dur.milliseconds
    } ?: Duration.ZERO

    fun isPlaying(): Boolean = player?.isPlaying ?: false

    fun playWhenReady() {
        player?.playWhenReady = true
    }

    fun pause() {
        player?.playWhenReady = false
    }

    fun seekTo(position: Duration) {
        player?.seekTo(position.inWholeMilliseconds)
    }

    companion object {
        private const val TAG = "MediaPlaybackCtrl"
        private val DEFAULT_LIVE_OFFSET: Duration = 5.seconds
    }
}
