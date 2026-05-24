package net.subsloth.core.model.identifier

import androidx.compose.runtime.Immutable

/**
 * Identifier for a locally stored (offline-downloaded) media item.
 */
@Immutable
@JvmInline
value class LocalMediaIdentifier(
    val value: String,
)
