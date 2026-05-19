package net.subsloth.testing.contract

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URI
import java.util.Base64

@Suppress("TooManyFunctions") // All functions belong to one cohesive HAR-to-fixture processing pipeline.
object HarProcessor {
    private val json = Json { ignoreUnknownKeys = true }

    data class HarEntry(
        val url: String,
        val method: String,
        val status: Int,
        val body: String?,
        val requestHeaders: Map<String, String>,
        val responseHeaders: Map<String, String>,
    )

    fun parseHarJson(jsonString: String): List<HarEntry> {
        val root = json.parseToJsonElement(jsonString).jsonObject
        val entries = root["log"]?.jsonObject?.get("entries")?.jsonArray ?: return emptyList()

        return entries.mapNotNull { entry ->
            val obj = entry.jsonObject
            val request = obj["request"]?.jsonObject ?: return@mapNotNull null
            val response = obj["response"]?.jsonObject ?: return@mapNotNull null

            HarEntry(
                url = request["url"]?.jsonPrimitive?.content ?: "",
                method = request["method"]?.jsonPrimitive?.content ?: "GET",
                status = response["status"]?.jsonPrimitive?.content?.toIntOrNull() ?: 200,
                body = decodeBody(response),
                requestHeaders = parseHeaders(request["headers"]?.jsonArray),
                responseHeaders = parseHeaders(response["headers"]?.jsonArray),
            )
        }
    }

    fun categorizeEntries(entries: List<HarEntry>): Map<Endpoint, List<HarEntry>> =
        entries
            .mapNotNull { entry ->
                Endpoint.parse(entry.url)?.let { endpoint -> endpoint to entry }
            }.groupBy(
                keySelector = { (endpoint, unused) -> endpoint },
                valueTransform = { (unused, entry) -> entry },
            )

    fun sanitizeStructuredBody(
        rawBody: String,
        rules: SanitizationRules,
    ): String {
        val parsed = json.parseToJsonElement(rawBody)
        val redacted = redactFields(parsed, rules.redactFields)
        val sanitized = rewriteUrlsToString(redacted, rules.compiledUrlRules)
        sanitized.assertBlockedHostsRemoved(rules.blockedHostsLowercase)
        return sanitized
    }

    fun sanitizeTextBody(
        rawBody: String,
        rules: SanitizationRules,
    ): String {
        val sanitized = rawBody.applyUrlRewrites(rules.compiledUrlRules)
        sanitized.assertBlockedHostsRemoved(rules.blockedHostsLowercase)
        return sanitized
    }

    fun export(
        harFiles: Set<File>,
        rules: SanitizationRules,
        nativeOutputDir: File,
        webOutputDir: File,
        keepRaw: Boolean,
    ): Pair<List<File>, List<File>> {
        val allEntries =
            readHarFiles(harFiles, keepRaw)
                .map { entry -> entry.sanitizedHeaders(rules) }

        if (allEntries.isEmpty()) {
            println("[export-fixtures] No recognised Media endpoints found in HAR files")
            return Pair(emptyList(), emptyList())
        }

        val categorised = categorizeEntries(allEntries)
        return writeCategorizedFixtures(categorised, rules, nativeOutputDir, webOutputDir)
    }

    private fun readHarFiles(
        harFiles: Set<File>,
        keepRaw: Boolean,
    ): List<HarEntry> {
        val allEntries = mutableListOf<HarEntry>()

        for (harFile in harFiles) {
            if (!harFile.isFile) {
                println("[export-fixtures] WARNING: File not found, skipping: $harFile")
                continue
            }

            println("[export-fixtures] Processing $harFile ...")

            val parseResult = runCatching { parseHarJson(harFile.readText()) }
            if (parseResult.isFailure) {
                println(
                    "[export-fixtures] WARNING: Failed to parse ${harFile.name}: " +
                        "${parseResult.exceptionOrNull()?.message} - skipping",
                )
                continue
            }
            val entries = parseResult.getOrThrow()

            if (entries.isEmpty()) {
                println("[export-fixtures] WARNING: No entries found in $harFile")
                continue
            }

            allEntries.addAll(entries)

            if (!keepRaw) {
                harFile.delete()
                println("[export-fixtures] Removed raw HAR file: $harFile")
            }
        }

        return allEntries
    }

    private fun writeCategorizedFixtures(
        categorised: Map<Endpoint, List<HarEntry>>,
        rules: SanitizationRules,
        nativeOutputDir: File,
        webOutputDir: File,
    ): Pair<List<File>, List<File>> {
        val nativeWritten = mutableListOf<File>()
        val webWritten = mutableListOf<File>()

        pruneOutputDir(nativeOutputDir)
        pruneOutputDir(webOutputDir)

        for ((endpoint, entries) in categorised) {
            val targetDir =
                when (endpoint.category) {
                    Endpoint.FixtureCategory.Native -> nativeOutputDir
                    Endpoint.FixtureCategory.WebDiscovery -> webOutputDir
                }
            targetDir.mkdirs()

            for (method in endpoint.methods) {
                val canonicalEntry = chooseCanonicalEntry(endpoint, method, entries)
                if (canonicalEntry == null) {
                    println(
                        "[export-fixtures] WARNING: No replayable response captured for " +
                            "${endpoint.fixtureName} $method - skipping",
                    )
                    continue
                }

                val cleanupResult =
                    runCatching {
                        buildFixtureText(endpoint, canonicalEntry, rules)
                    }
                if (cleanupResult.isFailure) {
                    println(
                        "[export-fixtures] WARNING: Failed to sanitize " +
                            "${endpoint.fixtureName} $method: " +
                            "${cleanupResult.exceptionOrNull()?.message} - skipping",
                    )
                    continue
                }
                val cleaned = cleanupResult.getOrThrow()

                val fixtureFile = File(targetDir, endpoint.resourcePathFor(method).substringAfterLast('/'))
                fixtureFile.writeText(cleaned)

                println("[export-fixtures] Wrote $fixtureFile (${entries.size} entries)")

                when (endpoint.category) {
                    Endpoint.FixtureCategory.Native -> nativeWritten.add(fixtureFile)
                    Endpoint.FixtureCategory.WebDiscovery -> webWritten.add(fixtureFile)
                }
            }
        }

        return Pair(nativeWritten, webWritten)
    }

    private fun pruneOutputDir(dir: File) {
        if (!dir.exists()) {
            return
        }

        val files = dir.listFiles() ?: return
        if (files.isNotEmpty()) {
            println("[export-fixtures] Pruning ${files.size} stale file(s) from $dir")
        }
        files.forEach { file ->
            file.deleteRecursively()
        }
    }

    private fun chooseCanonicalEntry(
        endpoint: Endpoint,
        method: HttpMethod,
        entries: List<HarEntry>,
    ): HarEntry? =
        entries
            .filter { entry -> method.name.equals(entry.method, ignoreCase = true) }
            .sortedWith(
                compareBy<HarEntry>(
                    { entry -> if (entry.status == endpoint.responseStatus) 0 else 1 },
                    { entry -> if (hasReplayPayload(endpoint, entry)) 0 else 1 },
                    { entry -> canonicalSelectionKey(entry.url) },
                    { entry -> entry.method },
                    { entry -> entry.body ?: "" },
                ).thenByDescending { entry -> payloadLength(endpoint, entry) },
            ).firstOrNull { entry -> hasReplayPayload(endpoint, entry) }

    private fun buildFixtureText(
        endpoint: Endpoint,
        entry: HarEntry,
        rules: SanitizationRules,
    ): String =
        when (endpoint.responseKind) {
            ResponseKind.Json -> {
                val body = requireNotNull(entry.body) { "JSON body missing" }
                sanitizeStructuredBody(body, rules)
            }

            ResponseKind.JavaScript,
            ResponseKind.SubRip,
            -> {
                val body = requireNotNull(entry.body) { "Text body missing" }
                sanitizeTextBody(body, rules)
            }

            ResponseKind.RedirectLocation -> {
                val location = entry.responseHeaders.getValue("location")
                sanitizeTextBody(location, rules).trim() + "\n"
            }
        }

    private fun hasReplayPayload(
        endpoint: Endpoint,
        entry: HarEntry,
    ): Boolean =
        when (endpoint.responseKind) {
            ResponseKind.Json,
            ResponseKind.JavaScript,
            ResponseKind.SubRip,
            -> !entry.body.isNullOrBlank() && entry.body != "null"

            ResponseKind.RedirectLocation -> !entry.responseHeaders["location"].isNullOrBlank()
        }

    private fun payloadLength(
        endpoint: Endpoint,
        entry: HarEntry,
    ): Int =
        when (endpoint.responseKind) {
            ResponseKind.Json,
            ResponseKind.JavaScript,
            ResponseKind.SubRip,
            -> entry.body?.length ?: 0

            ResponseKind.RedirectLocation -> entry.responseHeaders["location"]?.length ?: 0
        }

    private fun canonicalSelectionKey(url: String): String =
        try {
            val uri = URI(url)
            buildString {
                append(uri.path ?: url.substringBefore("?"))
                val query = uri.query
                if (!query.isNullOrBlank()) {
                    append('?')
                    append(query)
                }
            }
        } catch (_: Exception) {
            url
        }

    private fun HarEntry.sanitizedHeaders(rules: SanitizationRules): HarEntry =
        copy(
            requestHeaders = requestHeaders.withoutHeaders(rules.requestHeaderRedactionSet),
            responseHeaders = responseHeaders.withoutHeaders(rules.responseHeaderRedactionSet),
        )

    private fun parseHeaders(headers: kotlinx.serialization.json.JsonArray?): Map<String, String> {
        if (headers == null) {
            return emptyMap()
        }
        return headers
            .mapNotNull { header ->
                val obj = header.jsonObject
                val name =
                    obj["name"]
                        ?.jsonPrimitive
                        ?.content
                        ?.trim()
                        ?.lowercase()
                val value = obj["value"]?.jsonPrimitive?.content
                if (name.isNullOrBlank() || value == null) {
                    null
                } else {
                    name to value
                }
            }.toMap()
    }

    private fun decodeBody(response: JsonObject): String? {
        val content = response["content"]?.jsonObject ?: return null
        val text = content["text"]?.jsonPrimitive?.content ?: return null
        val encoding = content["encoding"]?.jsonPrimitive?.content
        return if (encoding == "base64") {
            String(Base64.getDecoder().decode(text))
        } else {
            text
        }
    }
}
