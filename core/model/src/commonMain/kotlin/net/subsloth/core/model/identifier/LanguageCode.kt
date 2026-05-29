package net.subsloth.core.model.identifier

import kotlin.jvm.JvmInline

/**
 * ISO 639 language code identifying a subtitle or audio track language.
 *
 * Typically stored as a two-letter (ISO 639-1) or three-letter (ISO 639-2) code.
 */
@JvmInline
value class LanguageCode(
    val value: String,
)
