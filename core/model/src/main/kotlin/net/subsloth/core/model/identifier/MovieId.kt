package net.subsloth.core.model.identifier

import androidx.compose.runtime.Immutable

/**
 * Typed identifier for an Media movie resource.
 */
@Immutable
@JvmInline
value class MovieId(
    val value: Int,
)
