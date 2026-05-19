package net.subsloth.testing.contract

enum class HttpMethod {
    GET,
    POST,
    DELETE,
}

enum class ResponseKind(
    val fileExtension: String,
    val contentType: String?,
) {
    Json("json", "application/json"),
    JavaScript("js", "text/javascript"),
    SubRip("srt", "text/plain"),
    RedirectLocation("location", null),
}

/**
 * Models every known Media endpoint and the replay metadata derived from the
 * committed fixture files. The fixture files are the single source of truth
 * for replay bodies; the endpoint metadata defines how those files are served.
 */
enum class Endpoint(
    val urlPattern: String,
    val examplePath: String,
    val category: FixtureCategory,
    val kodiSource: Boolean,
    val methods: Set<HttpMethod>,
    val responseKind: ResponseKind,
    val responseStatus: Int = 200,
) {
    Movies(
        urlPattern = "/movies",
        examplePath = "/movies",
        category = FixtureCategory.Native,
        kodiSource = true,
        methods = setOf(HttpMethod.GET),
        responseKind = ResponseKind.Json,
    ),

    Shows(
        urlPattern = "/shows",
        examplePath = "/shows",
        category = FixtureCategory.Native,
        kodiSource = true,
        methods = setOf(HttpMethod.GET),
        responseKind = ResponseKind.Json,
    ),

    MovieDetail(
        urlPattern = "/movies/.*",
        examplePath = "/movies/1",
        category = FixtureCategory.Native,
        kodiSource = true,
        methods = setOf(HttpMethod.GET),
        responseKind = ResponseKind.Json,
    ),

    ShowDetail(
        urlPattern = "/shows/.*",
        examplePath = "/shows/1",
        category = FixtureCategory.Native,
        kodiSource = true,
        methods = setOf(HttpMethod.GET),
        responseKind = ResponseKind.Json,
    ),

    EpisodeDetail(
        urlPattern = "/episodes/.*",
        examplePath = "/episodes/1",
        category = FixtureCategory.Native,
        kodiSource = true,
        methods = setOf(HttpMethod.GET),
        responseKind = ResponseKind.Json,
    ),

    Comments(
        urlPattern = "/api/frontend/comments.*",
        examplePath = "/api/frontend/comments",
        category = FixtureCategory.WebDiscovery,
        kodiSource = false,
        methods = setOf(HttpMethod.GET),
        responseKind = ResponseKind.Json,
    ),

    CatalogFilters(
        urlPattern = "/(en|pl|ru|de|fr|es|pt|ja|ko|zh)/(shows|movies).*",
        examplePath = "/en/shows",
        category = FixtureCategory.WebDiscovery,
        kodiSource = false,
        methods = setOf(HttpMethod.GET),
        responseKind = ResponseKind.Json,
    ),

    Statistics(
        urlPattern = "/message-bus/.*/poll.*",
        examplePath = "/message-bus/session/poll",
        category = FixtureCategory.WebDiscovery,
        kodiSource = false,
        methods = setOf(HttpMethod.POST),
        responseKind = ResponseKind.Json,
    ),

    PushSubscriptions(
        urlPattern = "/push_subscriptions.*",
        examplePath = "/push_subscriptions",
        category = FixtureCategory.WebDiscovery,
        kodiSource = false,
        methods = setOf(HttpMethod.POST),
        responseKind = ResponseKind.Json,
    ),

    FavoriteMedia(
        urlPattern = "/(en|pl|ru|de|fr|es|pt|ja|ko|zh)/favorite_media.*",
        examplePath = "/en/favorite_media",
        category = FixtureCategory.WebDiscovery,
        kodiSource = false,
        methods = setOf(HttpMethod.POST, HttpMethod.DELETE),
        responseKind = ResponseKind.JavaScript,
    ),

    WatchedMedia(
        urlPattern = "/(en|pl|ru|de|fr|es|pt|ja|ko|zh)/watched_media/.*",
        examplePath = "/en/watched_media/toggle",
        category = FixtureCategory.WebDiscovery,
        kodiSource = false,
        methods = setOf(HttpMethod.POST),
        responseKind = ResponseKind.JavaScript,
    ),

    Subscriptions(
        urlPattern = "/(en|pl|ru|de|fr|es|pt|ja|ko|zh)/shows/.*/subscriptions.*",
        examplePath = "/en/shows/demo/subscriptions",
        category = FixtureCategory.WebDiscovery,
        kodiSource = false,
        methods = setOf(HttpMethod.POST, HttpMethod.DELETE),
        responseKind = ResponseKind.JavaScript,
    ),

    SubtitleDownload(
        urlPattern = "/(en|pl|ru|de|fr|es|pt|ja|ko|zh)/.*/download_subtitle/.*",
        examplePath = "/en/shows/demo/videos/1/download_subtitle/en",
        category = FixtureCategory.WebDiscovery,
        kodiSource = false,
        methods = setOf(HttpMethod.GET),
        responseKind = ResponseKind.SubRip,
    ),

    Download(
        urlPattern = "/(en|pl|ru|de|fr|es|pt|ja|ko|zh)/.*/download",
        examplePath = "/en/shows/demo/videos/1/download",
        category = FixtureCategory.WebDiscovery,
        kodiSource = false,
        methods = setOf(HttpMethod.GET),
        responseKind = ResponseKind.RedirectLocation,
        responseStatus = 302,
    ),

    Speedtests(
        urlPattern = "/speedtest.*",
        examplePath = "/speedtest",
        category = FixtureCategory.WebDiscovery,
        kodiSource = false,
        methods = setOf(HttpMethod.GET),
        responseKind = ResponseKind.Json,
    ),
    ;

    val fixtureName: String
        get() = name

    val contentType: String?
        get() = responseKind.contentType

    val resourcePath: String
        get() = resourcePathFor()

    fun resourcePathFor(method: HttpMethod? = null): String {
        val basePath =
            when (category) {
                FixtureCategory.Native -> "/media"
                FixtureCategory.WebDiscovery -> "/media/web-discovery"
            }
        val resolvedMethod =
            when {
                methods.size == 1 -> methods.single()
                method != null -> method
                else -> error("Endpoint $fixtureName requires a method-specific fixture path")
            }

        return if (methods.size == 1) {
            "$basePath/$fixtureName.${responseKind.fileExtension}"
        } else {
            "$basePath/$fixtureName.${resolvedMethod.name.lowercase()}.${responseKind.fileExtension}"
        }
    }

    fun acceptsMethod(method: String): Boolean = methods.any { it.name.equals(method, ignoreCase = true) }

    enum class FixtureCategory {
        Native,
        WebDiscovery,
    }

    companion object {
        @Suppress("CyclomaticComplexMethod") // Many URL patterns to match — extracting wouldn't clarify.
        fun parse(url: String): Endpoint? {
            val rawPath = extractPath(url)
            val path = normalisePrefixes(rawPath)
            val hasFilterParams = url.contains("filters%5B") || url.contains("filters[")
            // Native API requests always use an /api/ prefix in the raw path;
            // web-frontend requests use a language prefix (e.g. /en/movies).
            // After normalisePrefixes both become /movies or /shows, so we check
            // the original path to avoid misclassifying web requests as native.
            val isNativeApi = rawPath.startsWith("/api/")

            return when {
                (path == "/movies" || path == "/shows") && (hasFilterParams || !isNativeApi) -> CatalogFilters

                path == "/movies" && isNativeApi -> Movies
                path == "/shows" && isNativeApi -> Shows

                path.endsWith("/download") &&
                    (path.startsWith("/shows/") || path.startsWith("/movies/")) -> Download

                path.contains("/download_subtitle/") -> SubtitleDownload

                path.contains("/subscriptions") -> Subscriptions

                path.startsWith("/movies/") -> MovieDetail
                path.startsWith("/shows/") -> ShowDetail
                path.startsWith("/episodes/") -> EpisodeDetail

                path.startsWith("/favorite_media") || path.startsWith("/favorite-media") -> FavoriteMedia
                path.startsWith("/watched_media") -> WatchedMedia

                path.startsWith("/push_subscriptions") -> PushSubscriptions

                path.startsWith("/message-bus/") -> Statistics
                path == "/comments" -> Comments

                path.startsWith("/catalog/filters") ||
                    path.startsWith("/catalog-filters") -> CatalogFilters

                path.startsWith("/speedtest") -> Speedtests

                else -> null
            }
        }

        fun byFixtureName(name: String): Endpoint? = entries.find { it.fixtureName == name }

        fun kodiEndpoints(): List<Endpoint> = entries.filter { it.kodiSource }

        private fun extractPath(url: String): String =
            try {
                java.net.URI(url).path?.trimEnd('/').let { path ->
                    if (path.isNullOrEmpty()) url.substringBefore("?").trimEnd('/') else path
                }
            } catch (_: java.net.URISyntaxException) {
                url.substringBefore("?").trimEnd('/')
            }

        private fun normalisePrefixes(rawPath: String): String =
            rawPath
                .replaceFirst(
                    Regex("^/(en|pl|ru|de|fr|es|pt|ja|ko|zh)(?=/|$)"),
                    "",
                ).replaceFirst(
                    Regex("^/api/(v\\d+|frontend)(?=/|$)"),
                    "",
                ).ifEmpty { rawPath }
    }
}
