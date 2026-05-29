package net.subsloth.core.model.identifier

/**
 * ISO 639 language code identifying a subtitle or audio track language.
 *
 * Typically stored as a two-letter (ISO 639-1) or three-letter (ISO 639-2) code.
 */
data class LanguageCode(
    val value: String,
)
