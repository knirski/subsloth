package net.subsloth.core.model.identifier

import androidx.compose.runtime.Immutable

/**
 * Typed identifier for an Media episode resource.
 */
@Immutable
@JvmInline
value class EpisodeId(
    val value: Int,
)
