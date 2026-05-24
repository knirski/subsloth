package net.subsloth.core.model.identifier

import androidx.compose.runtime.Immutable

/**
 * Typed identifier for an Media show/series resource.
 */
@Immutable
@JvmInline
value class ShowId(
    val value: Int,
)
