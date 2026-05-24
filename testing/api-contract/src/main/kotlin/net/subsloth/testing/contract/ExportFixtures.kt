package net.subsloth.testing.contract

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.system.exitProcess

/**
 * CLI entry point for the export-fixtures Gradle task.
 *
 * Arguments (positional, all required):
 *   1. comma-separated HAR file paths
 *   2. path to sanitization-rules.json
 *   3. native output directory
 *   4. web output directory
 *   5. "true" to preserve raw HAR files after export
 */
fun main(args: Array<String>) {
    if (args.size < REQUIRED_ARGS) {
        System.err.println(
            "Usage: ExportFixtures <har-files-csv> <rules-json> " +
                "<native-dir> <web-dir> <keepRaw>",
        )
        exitProcess(1)
    }

    val harPaths = args[0].split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val rulesFile = File(args[1])
    val nativeDir = File(args[2])
    val webDir = File(args[3])
    val keepRaw = args[4].toBoolean()

    if (harPaths.isEmpty()) {
        System.err.println("[export-fixtures] ERROR: No HAR files specified.")
        exitProcess(1)
    }

    if (!rulesFile.isFile) {
        System.err.println("[export-fixtures] ERROR: Rules file not found: $rulesFile")
        exitProcess(1)
    }

    val harFiles = harPaths.map(::File).toSet()
    val rules = loadSanitizationRules(rulesFile)

    println("[export-fixtures] Processing ${harFiles.size} HAR file(s) …")
    val (nativeWritten, webWritten) =
        HarProcessor.export(
            harFiles = harFiles,
            rules = rules,
            nativeOutputDir = nativeDir,
            webOutputDir = webDir,
            keepRaw = keepRaw,
        )
    println("[export-fixtures] Wrote ${nativeWritten.size} native + ${webWritten.size} web fixture(s).")
    println("[export-fixtures] Done.")
}

// ────────────────────────────────────────────────────────────────────────────
// Imperative Shell — I/O (file loading, CLI orchestration)
// ────────────────────────────────────────────────────────────────────────────

private const val REQUIRED_ARGS = 5

private val jsonParser = Json { ignoreUnknownKeys = true }

/**
 * Load and parse a [SanitizationRules] configuration from a JSON file on disk.
 */
internal fun loadSanitizationRules(file: File): SanitizationRules {
    val root = jsonParser.parseToJsonElement(file.readText()).jsonObject

    fun requireField(name: String) = root[name] ?: error("Missing required field \"$name\" in sanitization rules")

    return SanitizationRules(
        version = requireField("version").jsonPrimitive.content.toInt(),
        description = requireField("description").jsonPrimitive.content,
        redactFields = requireField("redact_fields").jsonArray.map { it.jsonPrimitive.content },
        urlPatterns = requireField("url_patterns").jsonArray.map { it.parseUrlPattern() },
        hostBlocklist = requireField("host_blocklist").jsonArray.map { it.jsonPrimitive.content },
        responseHeaderRedactions =
            requireField(
                "response_header_redactions",
            ).jsonArray.map { it.jsonPrimitive.content },
        requestHeaderRedactions =
            requireField(
                "request_header_redactions",
            ).jsonArray.map { it.jsonPrimitive.content },
    )
}

private fun JsonElement.parseUrlPattern(): UrlRewriteRule {
    val obj = jsonObject

    fun requireField(name: String) =
        obj[name]?.jsonPrimitive?.content
            ?: error("Missing required field \"$name\" in url_pattern entry")

    return UrlRewriteRule(
        match = requireField("match"),
        pattern = requireField("pattern"),
        replacement = requireField("replacement"),
    )
}
