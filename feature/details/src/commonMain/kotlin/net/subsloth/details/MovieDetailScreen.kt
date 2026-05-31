@file:Suppress("ktlint:standard:no-wildcard-imports")

package net.subsloth.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
internal fun MovieDetailContent(state: MovieDetailUiState.Content, modifier: Modifier = Modifier) {
    val details = state.details

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
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
                text = "$duration min",
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
                    details.subtitles.joinToString(", ") { it.languageDisplayName ?: it.language.value },
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (details.qualities.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Quality: ${
                    details.qualities.joinToString(", ") {
                        it.info.label ?: it.info.resolution.toString()
                    }
                }",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
