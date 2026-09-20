@file:Suppress("ktlint:standard:no-wildcard-imports")

package net.subsloth.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.ShowStatus
import net.subsloth.core.ui.MediaArtwork
import net.subsloth.core.ui.SubSlothBackButton
import net.subsloth.core.ui.toDisplayString
import net.subsloth.core.ui.toUiErrorMessage
import net.subsloth.core.ui.tvSafeHorizontalPadding
import org.jetbrains.compose.resources.stringResource
import subsloth.feature.details.generated.resources.*

@Composable
fun SeriesDetailScreen(
    viewModel: ShowDetailViewModel,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    onPlayClick: () -> Unit = {},
    onEpisodeClick: (Episode) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDownloadConfirmation by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        if (onNavigateBack != null) {
            SubSlothBackButton(
                onClick = onNavigateBack,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp),
            )
        }

        when (val s = state) {
            is ShowDetailUiState.Loading -> {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is ShowDetailUiState.Content -> {
                ShowDetailContent(
                    state = s,
                    onSeasonSelect = { viewModel.selectSeason(it) },
                    modifier = Modifier.weight(1f),
                    onPlayClick = onPlayClick,
                    onEpisodeClick = onEpisodeClick,
                    onFavoriteClick = viewModel::toggleFavorite,
                    onWatchLaterClick = viewModel::toggleWatchLater,
                    onDownloadSeason = { showDownloadConfirmation = true },
                )
            }

            is ShowDetailUiState.Error -> {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = s.error.toUiErrorMessage().toDisplayString(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        if (showDownloadConfirmation && state is ShowDetailUiState.Content) {
            val content = state as ShowDetailUiState.Content
            val episodeCount = content.details.seasons
                .firstOrNull { it.seasonNumber == content.selectedSeason }
                ?.episodes
                ?.size
                ?: 0
            AlertDialog(
                onDismissRequest = { showDownloadConfirmation = false },
                title = {
                    Text(
                        stringResource(
                            Res.string.detail_download_season_title,
                            content.selectedSeason,
                        ),
                    )
                },
                text = {
                    Text(stringResource(Res.string.detail_download_season_message, episodeCount))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDownloadConfirmation = false
                            viewModel.downloadSeason()
                        },
                    ) {
                        Text(stringResource(Res.string.detail_download_season_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDownloadConfirmation = false }) {
                        Text(stringResource(Res.string.detail_download_season_cancel))
                    }
                },
            )
        }
    }
}

@Composable
fun ShowDetailContent(
    state: ShowDetailUiState.Content,
    modifier: Modifier = Modifier,
    onSeasonSelect: (Int) -> Unit = {},
    onPlayClick: () -> Unit = {},
    onEpisodeClick: (Episode) -> Unit = {},
    onFavoriteClick: () -> Unit = {},
    onWatchLaterClick: () -> Unit = {},
    onDownloadSeason: () -> Unit = {},
) {
    if (isLandscapeWideScreen()) {
        ShowDetailWideLayout(
            state = state,
            onSeasonSelect = onSeasonSelect,
            modifier = modifier,
            onPlayClick = onPlayClick,
            onEpisodeClick = onEpisodeClick,
            onFavoriteClick = onFavoriteClick,
            onWatchLaterClick = onWatchLaterClick,
            onDownloadSeason = onDownloadSeason,
        )
    } else {
        ShowDetailCompactLayout(
            state = state,
            onSeasonSelect = onSeasonSelect,
            modifier = modifier,
            onPlayClick = onPlayClick,
            onEpisodeClick = onEpisodeClick,
            onFavoriteClick = onFavoriteClick,
            onWatchLaterClick = onWatchLaterClick,
            onDownloadSeason = onDownloadSeason,
        )
    }
}

@Composable
private fun ShowDetailWideLayout(
    state: ShowDetailUiState.Content,
    modifier: Modifier,
    onSeasonSelect: (Int) -> Unit,
    onPlayClick: () -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onFavoriteClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    onDownloadSeason: () -> Unit,
) {
    val details = state.details
    val posterContentDescription = stringResource(Res.string.detail_poster_content_desc, details.title)

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = tvSafeHorizontalPadding(24.dp), vertical = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.tertiaryContainer,
                                MaterialTheme.colorScheme.surface,
                            ),
                        ),
                    )
                    .semantics { contentDescription = posterContentDescription },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = details.title.take(1),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.3f),
                )
                MediaArtwork(
                    url = details.backdropUrl ?: details.posterUrl,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = details.title,
                style = MaterialTheme.typography.headlineMedium,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                details.year?.let { year ->
                    Text(text = year.toString(), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                details.rating?.let { rating ->
                    Text(
                        text = "★ $rating",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (details.status) {
                        ShowStatus.ONGOING -> stringResource(Res.string.show_ongoing)
                        ShowStatus.ENDED -> stringResource(Res.string.show_ended)
                        ShowStatus.UPCOMING -> stringResource(Res.string.show_upcoming)
                        ShowStatus.UNKNOWN -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (details.genres.isNotEmpty()) {
                Text(
                    text = details.genres.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            details.plot?.let { plot ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = plot, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            ShowDetailActionButtons(
                isFavorite = state.isFavorite,
                isWatchLater = state.isWatchLater,
                progressFraction = state.progressFraction,
                onPlayClick = onPlayClick,
                onFavoriteClick = onFavoriteClick,
                onWatchLaterClick = onWatchLaterClick,
            )

            SeasonDownloadButton(
                state = seasonDownloadState(state.seasonQueues, state.selectedSeason),
                modifier = Modifier.padding(top = 8.dp),
                onDownloadClick = onDownloadSeason,
            )

            if (details.seasons.size > 1) {
                Spacer(modifier = Modifier.height(16.dp))
                SeasonSelector(
                    seasons = details.seasons,
                    selectedSeason = state.selectedSeason,
                    onSeasonSelect = onSeasonSelect,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val currentSeason = details.seasons.find { it.seasonNumber == state.selectedSeason }
            currentSeason?.let { season ->
                SeasonEpisodesSection(
                    season = season,
                    watchedEpisodeIds = state.watchedEpisodeIds,
                    episodeProgress = state.episodeProgress,
                    onEpisodeClick = onEpisodeClick,
                )
            }
        }
    }
}

@Composable
private fun ShowDetailCompactLayout(
    state: ShowDetailUiState.Content,
    modifier: Modifier,
    onSeasonSelect: (Int) -> Unit,
    onPlayClick: () -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onFavoriteClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    onDownloadSeason: () -> Unit,
) {
    val details = state.details
    val posterContentDescription = stringResource(Res.string.detail_poster_content_desc, details.title)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .aspectRatio(16f / 9f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                )
                .semantics { contentDescription = posterContentDescription },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = details.title.take(1),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.3f),
            )
            MediaArtwork(
                url = details.backdropUrl ?: details.posterUrl,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
            )
        }

        Column(modifier = Modifier.padding(horizontal = tvSafeHorizontalPadding(16.dp), vertical = 16.dp)) {
            Text(
                text = details.title,
                style = MaterialTheme.typography.headlineMedium,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                details.year?.let { year ->
                    Text(text = year.toString(), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                details.rating?.let { rating ->
                    Text(
                        text = "★ $rating",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (details.status) {
                        ShowStatus.ONGOING -> stringResource(Res.string.show_ongoing)
                        ShowStatus.ENDED -> stringResource(Res.string.show_ended)
                        ShowStatus.UPCOMING -> stringResource(Res.string.show_upcoming)
                        ShowStatus.UNKNOWN -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (details.genres.isNotEmpty()) {
                Text(
                    text = details.genres.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            details.plot?.let { plot ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = plot, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))

            ShowDetailActionButtons(
                isFavorite = state.isFavorite,
                isWatchLater = state.isWatchLater,
                progressFraction = state.progressFraction,
                onPlayClick = onPlayClick,
                onFavoriteClick = onFavoriteClick,
                onWatchLaterClick = onWatchLaterClick,
            )

            SeasonDownloadButton(
                state = seasonDownloadState(state.seasonQueues, state.selectedSeason),
                modifier = Modifier.padding(top = 8.dp),
                onDownloadClick = onDownloadSeason,
            )

            if (details.seasons.size > 1) {
                Spacer(modifier = Modifier.height(16.dp))
                SeasonSelector(
                    seasons = details.seasons,
                    selectedSeason = state.selectedSeason,
                    onSeasonSelect = onSeasonSelect,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val currentSeason = details.seasons.find { it.seasonNumber == state.selectedSeason }
            currentSeason?.let { season ->
                SeasonEpisodesSection(
                    season = season,
                    watchedEpisodeIds = state.watchedEpisodeIds,
                    episodeProgress = state.episodeProgress,
                    onEpisodeClick = onEpisodeClick,
                )
            }
        }
    }
}

@Composable
private fun ShowDetailActionButtons(
    isFavorite: Boolean,
    isWatchLater: Boolean,
    progressFraction: Double?,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val playLabel = when (val action = detailPlayAction(progressFraction)) {
            is DetailPlayAction.Play -> stringResource(Res.string.detail_play)
            is DetailPlayAction.Resume -> stringResource(Res.string.detail_resume)
            is DetailPlayAction.ResumeAt -> stringResource(Res.string.detail_resume_play, "${action.percent}%")
        }
        Button(
            onClick = onPlayClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = playLabel)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = onFavoriteClick,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = if (isFavorite) {
                        stringResource(Res.string.detail_favorite_remove)
                    } else {
                        stringResource(Res.string.detail_favorite)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }

            FilledTonalButton(
                onClick = onWatchLaterClick,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = if (isWatchLater) {
                        stringResource(Res.string.detail_watch_later_remove)
                    } else {
                        stringResource(Res.string.detail_watch_later)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}
