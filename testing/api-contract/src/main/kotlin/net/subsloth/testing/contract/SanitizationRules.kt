package net.subsloth.testing.contract

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
private val prettyJson =
    Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

data class SanitizationRules(
    val version: Int,
    val description: String,
    val redactFields: List<String>,
    val urlPatterns: List<UrlRewriteRule>,
    val hostBlocklist: List<String>,
    val responseHeaderRedactions: List<String>,
    val requestHeaderRedactions: List<String>,
) {
    val compiledUrlRules: List<UrlRewriteRule.Compiled> by lazy {
        urlPatterns.map { it.compile() }
    }

    val blockedHostsLowercase: Set<String> by lazy {
        hostBlocklist.map { it.lowercase() }.toSet()
    }

    val responseHeaderRedactionSet: Set<String> by lazy {
        responseHeaderRedactions.map { it.lowercase() }.toSet()
    }

    val requestHeaderRedactionSet: Set<String> by lazy {
        requestHeaderRedactions.map { it.lowercase() }.toSet()
    }
}

data class UrlRewriteRule(
    val match: String,
    val pattern: String,
    val replacement: String,
) {
    data class Compiled(
        val match: String,
        val regex: Regex,
        val replacement: String,
    )

    fun compile() =
        Compiled(
            match = match,
            regex = Regex(pattern),
            replacement = replacement,
        )
}

fun String.applyUrlRewrites(rules: List<UrlRewriteRule.Compiled>): String {
    var result = this
    for (rule in rules) {
        result = result.replace(rule.regex, rule.replacement)
    }
    return result
}

fun String.assertBlockedHostsRemoved(blockedHosts: Set<String>) {
    val lowered = lowercase()
    val leaked = blockedHosts.firstOrNull { lowered.contains(it) }
    check(leaked == null) { "Blocked host leaked into sanitized fixture: $leaked" }
}

fun Map<String, String>.withoutHeaders(headersToRemove: Set<String>): Map<String, String> =
    filterKeys { key -> key.lowercase() !in headersToRemove }

fun redactFields(
    element: JsonElement,
    redactFields: List<String>,
): JsonElement {
    val fieldSet = redactFields.map { it.lowercase() }.toSet()

    fun walk(e: JsonElement): JsonElement =
        when (e) {
            is JsonObject -> {
                val newFields =
                    e.entries.associate { (key, value) ->
                        if (key.lowercase() in fieldSet) {
                            key to JsonPrimitive("[REDACTED]")
                        } else {
                            key to walk(value)
                        }
                    }
                JsonObject(newFields)
            }

            is JsonArray -> JsonArray(e.map(::walk))
            is JsonPrimitive -> e
        }
    return walk(element)
}

fun rewriteUrlsToString(
    element: JsonElement,
    urlRules: List<UrlRewriteRule.Compiled>,
): String {
    fun walk(e: JsonElement): JsonElement =
        when (e) {
            is JsonPrimitive -> {
                if (e.isString) {
                    val cleaned = e.content.applyUrlRewrites(urlRules)
                    if (cleaned != e.content) JsonPrimitive(cleaned) else e
                } else {
                    e
                }
            }

            is JsonArray -> JsonArray(e.map(::walk))
            is JsonObject -> JsonObject(e.entries.associate { (key, value) -> key to walk(value) })
        }
    return prettyJson.encodeToString(JsonElement.serializer(), walk(element)) + "\n"
}
