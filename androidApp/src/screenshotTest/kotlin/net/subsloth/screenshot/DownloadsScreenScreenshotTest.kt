package net.subsloth.screenshot

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.OfflineRelativePath
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.core.ui.theme.SubSlothTheme
import net.subsloth.library.DownloadGroupItem
import net.subsloth.library.DownloadsContent
import net.subsloth.library.DownloadsUiState
import kotlin.time.Instant

@Suppress("ktlint:standard:max-line-length")
@PreviewTest
@Preview(name = "Phone Light", device = "spec:width=411dp,height=731dp,dpi=420", showBackground = true)
@Preview(name = "Tablet Light", device = "spec:width=800dp,height=1280dp,dpi=320", showBackground = true)
@Preview(name = "TV Light", device = "spec:width=960dp,height=540dp,dpi=320", showBackground = true)
@Composable
fun DownloadsScreenLightScreenshot() {
    SubSlothTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            DownloadsContent(
                state =
                    DownloadsUiState.Content(
                        active =
                            persistentListOf(
                                DownloadGroupItem(
                                    state =
                                        DownloadState.Active(
                                            localId = LocalMediaIdentifier("1"),
                                            mediaId = Media.MediaId.Movie(MovieId(1)),
                                            quality =
                                                QualityDescriptor(
                                                    resolution = Resolution(1920, 1080),
                                                    label = "1080p",
                                                    bitrate = 5000,
                                                    mimeType = "video/mp4",
                                                ),
                                            progressPercent = 65,
                                        ),
                                    progressFraction = 0.65,
                                ),
                            ),
                        queuedOrPaused =
                            persistentListOf(
                                DownloadGroupItem(
                                    state =
                                        DownloadState.Queued(
                                            localId = LocalMediaIdentifier("2"),
                                            mediaId = Media.MediaId.Movie(MovieId(2)),
                                            quality =
                                                QualityDescriptor(
                                                    resolution = Resolution(1280, 720),
                                                    label = "720p",
                                                    bitrate = 3000,
                                                    mimeType = "video/mp4",
                                                ),
                                        ),
                                ),
                            ),
                        failedOrUnavailable = persistentListOf(),
                        completed =
                            persistentListOf(
                                DownloadGroupItem(
                                    state =
                                        DownloadState.Completed(
                                            localId = LocalMediaIdentifier("3"),
                                            mediaId = Media.MediaId.Movie(MovieId(3)),
                                            quality =
                                                QualityDescriptor(
                                                    resolution = Resolution(1920, 1080),
                                                    label = "1080p",
                                                    bitrate = 5000,
                                                    mimeType = "video/mp4",
                                                ),
                                            downloadedAtEpochSeconds = Instant.fromEpochSeconds(1700000000),
                                            sizeBytes = 2_500_000_000L,
                                            videoPath = OfflineRelativePath("movie3.mp4"),
                                        ),
                                ),
                            ),
                        seasonQueues = persistentListOf(),
                    ),
            )
        }
    }
}

@Suppress("ktlint:standard:max-line-length")
@PreviewTest
@Preview(name = "Phone Dark", device = "spec:width=411dp,height=731dp,dpi=420", showBackground = true)
@Preview(name = "Tablet Dark", device = "spec:width=800dp,height=1280dp,dpi=320", showBackground = true)
@Preview(name = "TV Dark", device = "spec:width=960dp,height=540dp,dpi=320", showBackground = true)
@Composable
fun DownloadsScreenDarkScreenshot() {
    SubSlothTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize()) {
            DownloadsContent(
                state =
                    DownloadsUiState.Content(
                        active =
                            persistentListOf(
                                DownloadGroupItem(
                                    state =
                                        DownloadState.Active(
                                            localId = LocalMediaIdentifier("1"),
                                            mediaId = Media.MediaId.Movie(MovieId(1)),
                                            quality =
                                                QualityDescriptor(
                                                    resolution = Resolution(1920, 1080),
                                                    label = "1080p",
                                                    bitrate = 5000,
                                                    mimeType = "video/mp4",
                                                ),
                                            progressPercent = 65,
                                        ),
                                    progressFraction = 0.65,
                                ),
                            ),
                        queuedOrPaused =
                            persistentListOf(
                                DownloadGroupItem(
                                    state =
                                        DownloadState.Queued(
                                            localId = LocalMediaIdentifier("2"),
                                            mediaId = Media.MediaId.Movie(MovieId(2)),
                                            quality =
                                                QualityDescriptor(
                                                    resolution = Resolution(1280, 720),
                                                    label = "720p",
                                                    bitrate = 3000,
                                                    mimeType = "video/mp4",
                                                ),
                                        ),
                                ),
                            ),
                        failedOrUnavailable = persistentListOf(),
                        completed =
                            persistentListOf(
                                DownloadGroupItem(
                                    state =
                                        DownloadState.Completed(
                                            localId = LocalMediaIdentifier("3"),
                                            mediaId = Media.MediaId.Movie(MovieId(3)),
                                            quality =
                                                QualityDescriptor(
                                                    resolution = Resolution(1920, 1080),
                                                    label = "1080p",
                                                    bitrate = 5000,
                                                    mimeType = "video/mp4",
                                                ),
                                            downloadedAtEpochSeconds = Instant.fromEpochSeconds(1700000000),
                                            sizeBytes = 2_500_000_000L,
                                            videoPath = OfflineRelativePath("movie3.mp4"),
                                        ),
                                ),
                            ),
                        seasonQueues = persistentListOf(),
                    ),
            )
        }
    }
}
