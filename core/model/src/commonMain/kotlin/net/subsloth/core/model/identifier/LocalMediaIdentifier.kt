package net.subsloth.core.model.identifier

import kotlin.jvm.JvmInline

/**
 * Identifier for a locally stored (offline-downloaded) media item.
 */
@JvmInline
value class LocalMediaIdentifier(val value: String)
