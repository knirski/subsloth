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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.subsloth.core.ui.toDisplayString
import net.subsloth.core.ui.toUiErrorMessage
import org.jetbrains.compose.resources.stringResource
import subsloth.feature.details.generated.resources.*

@Composable
fun MovieDetailScreen(viewModel: MovieDetailViewModel, modifier: Modifier = Modifier) {
    val state: MovieDetailUiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val s = state) {
        is MovieDetailUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is MovieDetailUiState.Content -> {
            MovieDetailContent(state = s, modifier = modifier)
        }

        is MovieDetailUiState.Error -> {
            Box(
                modifier = modifier.fillMaxSize().padding(16.dp),
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
}

@Composable
fun MovieDetailContent(state: MovieDetailUiState.Content, modifier: Modifier = Modifier) {
    if (isLandscapeWideScreen()) {
        MovieDetailWideLayout(state = state, modifier = modifier)
    } else {
        MovieDetailCompactLayout(state = state, modifier = modifier)
    }
}

@Composable
private fun MovieDetailWideLayout(state: MovieDetailUiState.Content, modifier: Modifier) {
    val details = state.details
    val posterContentDescription = stringResource(Res.string.detail_poster_content_desc, details.title)

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
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
                                MaterialTheme.colorScheme.primaryContainer,
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
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f),
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
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                details.rating?.let { rating ->
                    Text(
                        text = "★ $rating",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (details.genres.isNotEmpty()) {
                Text(
                    text = details.genres.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            details.durationMinutes?.let { duration ->
                Text(
                    text = stringResource(Res.string.detail_duration_format, duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (details.countries.isNotEmpty()) {
                Text(
                    text = details.countries.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            DetailActionButtons(
                isFavorite = state.isFavorite,
                isWatchLater = state.isWatchLater,
                isDownloaded = state.isDownloaded,
                progressFraction = state.progressFraction,
                onPlayClick = { },
                onFavoriteClick = { },
                onWatchLaterClick = { },
                onDownloadClick = { },
            )

            details.plot?.let { plot ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = plot,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            details.description?.let { desc ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (details.subtitles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(
                        Res.string.subtitles_format,
                        details.subtitles.joinToString(", ") {
                            it.languageDisplayName ?: it.language.value
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (details.qualities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        Res.string.detail_quality_format,
                        details.qualities.joinToString(", ") {
                            it.info.label ?: it.info.resolution.toString()
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun MovieDetailCompactLayout(state: MovieDetailUiState.Content, modifier: Modifier) {
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
                            MaterialTheme.colorScheme.primaryContainer,
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
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f),
            )
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = details.title,
                style = MaterialTheme.typography.headlineMedium,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                details.year?.let { year ->
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                details.rating?.let { rating ->
                    Text(
                        text = "★ $rating",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (details.genres.isNotEmpty()) {
                Text(
                    text = details.genres.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            details.durationMinutes?.let { duration ->
                Text(
                    text = stringResource(Res.string.detail_duration_format, duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (details.countries.isNotEmpty()) {
                Text(
                    text = details.countries.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            DetailActionButtons(
                isFavorite = state.isFavorite,
                isWatchLater = state.isWatchLater,
                isDownloaded = state.isDownloaded,
                progressFraction = state.progressFraction,
                onPlayClick = { },
                onFavoriteClick = { },
                onWatchLaterClick = { },
                onDownloadClick = { },
            )

            details.plot?.let { plot ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = plot,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            details.description?.let { desc ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (details.subtitles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(
                        Res.string.subtitles_format,
                        details.subtitles.joinToString(", ") {
                            it.languageDisplayName ?: it.language.value
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (details.qualities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        Res.string.detail_quality_format,
                        details.qualities.joinToString(", ") {
                            it.info.label ?: it.info.resolution.toString()
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DetailActionButtons(
    isFavorite: Boolean,
    isWatchLater: Boolean,
    isDownloaded: Boolean,
    progressFraction: Double?,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val progressPercent = progressFraction?.let { (it * 100).toInt() }
        Button(
            onClick = onPlayClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (progressPercent != null && progressPercent > 0) {
                    stringResource(Res.string.detail_resume_play, progressPercent)
                } else {
                    stringResource(Res.string.detail_play)
                },
            )
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

            OutlinedButton(
                onClick = onDownloadClick,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = if (isDownloaded) {
                        stringResource(Res.string.detail_downloaded)
                    } else {
                        stringResource(Res.string.detail_download)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun isLandscapeWideScreen(): Boolean {
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val widthDp = with(density) { containerSize.width.toDp().value }
    val heightDp = with(density) { containerSize.height.toDp().value }
    return widthDp > heightDp && widthDp >= 800f
}
