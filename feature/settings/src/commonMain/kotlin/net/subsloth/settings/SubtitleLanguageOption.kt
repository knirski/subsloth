package net.subsloth.settings

import androidx.compose.runtime.Immutable

/**
 * A subtitle language offered by the Settings picker.
 *
 * [code] is the value stored in preferences; the player matches it against
 * the subtitle tracks advertised by the API.
 */
@Immutable
data class SubtitleLanguageOption(val code: String, val label: String)

/**
 * Curated picker options. The stored preference is free-form, so a value
 * outside this list still displays as-is rather than being reset.
 */
val SubtitleLanguageOptions: List<SubtitleLanguageOption> =
    listOf(
        SubtitleLanguageOption("en", "English"),
        SubtitleLanguageOption("pl", "Polish"),
        SubtitleLanguageOption("de", "German"),
        SubtitleLanguageOption("fr", "French"),
        SubtitleLanguageOption("es", "Spanish"),
        SubtitleLanguageOption("it", "Italian"),
        SubtitleLanguageOption("pt", "Portuguese"),
        SubtitleLanguageOption("nl", "Dutch"),
        SubtitleLanguageOption("sv", "Swedish"),
        SubtitleLanguageOption("da", "Danish"),
        SubtitleLanguageOption("no", "Norwegian"),
        SubtitleLanguageOption("fi", "Finnish"),
        SubtitleLanguageOption("cs", "Czech"),
        SubtitleLanguageOption("uk", "Ukrainian"),
        SubtitleLanguageOption("ru", "Russian"),
        SubtitleLanguageOption("ro", "Romanian"),
        SubtitleLanguageOption("hu", "Hungarian"),
        SubtitleLanguageOption("el", "Greek"),
        SubtitleLanguageOption("tr", "Turkish"),
        SubtitleLanguageOption("ar", "Arabic"),
        SubtitleLanguageOption("he", "Hebrew"),
        SubtitleLanguageOption("hi", "Hindi"),
        SubtitleLanguageOption("ja", "Japanese"),
        SubtitleLanguageOption("ko", "Korean"),
        SubtitleLanguageOption("zh", "Chinese"),
        SubtitleLanguageOption("th", "Thai"),
        SubtitleLanguageOption("vi", "Vietnamese"),
        SubtitleLanguageOption("id", "Indonesian"),
    )

/** Display label for a stored [code], or null when it is not a known option. */
fun subtitleLanguageLabel(code: String): String? = SubtitleLanguageOptions.firstOrNull { it.code == code }?.label
